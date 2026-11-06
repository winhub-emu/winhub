package com.winhub.hernandez.winhandler

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.PriorityQueue

class SlotReconnectorTest {

    /** A fake clock: tasks run in time order when advanced. */
    private class FakeScheduler : SlotReconnector.Scheduler {
        private var t = 0L
        private var seq = 0L
        private data class Task(val at: Long, val seq: Long, val run: Runnable)
        private val queue = PriorityQueue<Task>(compareBy<Task>({ it.at }, { it.seq }))
        override fun post(task: Runnable, delayMs: Long) { queue.add(Task(t + delayMs, seq++, task)) }
        override fun now() = t
        fun advance(ms: Long) {
            val end = t + ms
            while (queue.isNotEmpty() && queue.peek()!!.at <= end) {
                val task = queue.poll()!!
                t = task.at
                task.run.run()
            }
            t = end
        }
        fun runAll(maxMs: Long = 60_000) = advance(maxMs)
    }

    private class FakeTarget(var held: IntArray) : SlotReconnector.Target {
        val log = mutableListOf<String>()
        val attaches = LongArray(4)
        var attachOnReplug = true
        var frameMs = 0L
        override fun heldSlots() = held.clone()
        override fun neutralise(slots: IntArray) { log += "neutralise ${slots.toList()}" }
        override fun block(slots: IntArray) { log += "block ${slots.toList()}" }
        override fun frameIntervalMs() = frameMs
        override fun unplug(slot: Int) { log += "unplug $slot" }
        override fun replug(slots: IntArray) {
            log += "replug ${slots.toList()}"
            if (attachOnReplug) slots.forEach { attaches[it]++ }
        }
        override fun readerAttaches(slot: Int) = attaches[slot]
    }

    private class FakeJob(val target: FakeTarget, val registry: Boolean = false) : SlotReconnector.Job {
        var registryDone: Runnable? = null
        var applied = 0
        override fun needsRegistry() = registry
        override fun registry(done: Runnable) { target.log += "registry"; registryDone = done }
        override fun applyInGap() { applied++; target.log += "apply" }
    }

    private class Results : SlotReconnector.Listener {
        val done = mutableListOf<Pair<Boolean, Int>>()
        var reconnecting = 0
        override fun onReconnecting(slots: IntArray) { reconnecting++ }
        override fun onReconnected(ok: Boolean, slots: IntArray, attempts: Int) { done += ok to attempts }
    }

    @Test fun sequenceAndTiming() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(0, 2)); val r = Results()
        val rc = SlotReconnector(s, t, r)
        rc.request(FakeJob(t))
        assertEquals(listOf("neutralise [0, 2]", "block [0, 2]"), t.log)
        s.advance(49); assertFalse(t.log.contains("unplug 0"))
        s.advance(1); assertEquals(listOf("neutralise [0, 2]", "block [0, 2]", "unplug 0", "unplug 2", "apply"), t.log)
        s.advance(399); assertFalse(t.log.any { it.startsWith("replug") })
        s.advance(1); assertEquals("replug [0, 2]", t.log.last())
        s.advance(50)
        assertEquals(listOf(true to 1), r.done)
        assertFalse(rc.isRunning)
    }

    @Test fun holdFollowsTheFrameInterval() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(0)); val r = Results()
        t.frameMs = 40 // two frames = 80 ms
        SlotReconnector(s, t, r).request(FakeJob(t))
        s.advance(79); assertFalse(t.log.contains("unplug 0"))
        s.advance(1); assertTrue(t.log.contains("unplug 0"))
    }

    @Test fun rapidRequestsCoalesce() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(0)); val r = Results()
        val rc = SlotReconnector(s, t, r)
        repeat(10) { rc.request(FakeJob(t)); s.advance(30) }
        s.runAll()
        assertEquals(2, r.done.size)
        assertEquals(2, t.log.count { it == "unplug 0" })
    }

    @Test fun registryStepIsWaitedFor() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(1)); val r = Results()
        val job = FakeJob(t, registry = true)
        SlotReconnector(s, t, r).request(job)
        s.advance(1000)
        assertFalse(t.log.contains("unplug 1"))
        job.registryDone!!.run()
        job.registryDone!!.run() // a second call is ignored
        s.runAll()
        assertEquals(1, t.log.count { it == "unplug 1" })
        assertEquals(listOf(true to 1), r.done)
    }

    @Test fun retriesOnceThenReportsFailure() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(0)); val r = Results()
        t.attachOnReplug = false
        SlotReconnector(s, t, r).request(FakeJob(t))
        s.runAll()
        assertEquals(listOf(false to 2), r.done)
        assertEquals(2, t.log.count { it == "unplug 0" })
    }

    @Test fun retrySucceeds() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(0)); val r = Results()
        t.attachOnReplug = false
        val rc = SlotReconnector(s, t, r)
        rc.request(FakeJob(t))
        s.advance(50 + 400 + 2000 + 50) // first confirm times out
        t.attachOnReplug = true
        s.runAll()
        assertEquals(listOf(true to 2), r.done)
    }

    @Test fun slotLeftDuringTheGapIsNotReplugged() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf(0, 1)); val r = Results()
        SlotReconnector(s, t, r).request(FakeJob(t))
        s.advance(60)
        t.held = intArrayOf(0) // pad 1 unplugged meanwhile
        s.runAll()
        assertEquals("replug [0]", t.log.first { it.startsWith("replug") })
        assertEquals(listOf(true to 1), r.done)
    }

    @Test fun nothingHeldStillApplies() {
        val s = FakeScheduler(); val t = FakeTarget(intArrayOf()); val r = Results()
        val job = FakeJob(t)
        SlotReconnector(s, t, r).request(job)
        s.runAll()
        assertEquals(1, job.applied)
        assertEquals(listOf(true to 1), r.done)
    }

    @Test fun gapIsClamped() {
        val rc = SlotReconnector(FakeScheduler(), FakeTarget(intArrayOf()), Results())
        rc.setGapMs(50); assertEquals(250, rc.gapMs)
        rc.setGapMs(5000); assertEquals(1000, rc.gapMs)
        assertArrayEquals(intArrayOf(1, 3), SlotReconnector.intersect(intArrayOf(3, 1, 2), intArrayOf(1, 3)))
    }
}
