package com.winhub.hernandez.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [UbisoftPendingLaunch.verdict]: when the game starts by itself after the Ubisoft Connect setup, and when it must not. */
class UbisoftPendingLaunchTest {

    private val minute = 60_000L

    @Test
    fun theGameStartsOnlyWhenConnectIsInstalledAfterTheAppRestarted() {
        assertEquals(UbisoftPendingLaunch.Verdict.LAUNCH, UbisoftPendingLaunch.verdict(false, 3 * minute, true))
        assertEquals(UbisoftPendingLaunch.Verdict.LAUNCH, UbisoftPendingLaunch.verdict(false, 0L, true))
        assertEquals(UbisoftPendingLaunch.Verdict.LAUNCH, UbisoftPendingLaunch.verdict(false, UbisoftPendingLaunch.TTL_MS, true))
    }

    @Test
    fun aSetupThatDidNotFinishNeverStartsTheGame() {
        assertEquals(UbisoftPendingLaunch.Verdict.NOT_FINISHED, UbisoftPendingLaunch.verdict(false, 3 * minute, false))
        assertEquals(UbisoftPendingLaunch.Verdict.NOT_FINISHED, UbisoftPendingLaunch.verdict(false, 0L, false))
    }

    @Test
    fun aMarkerFromThisProcessIsLeftAloneBecauseTheSessionMayStillBeRunning() {
        assertEquals(UbisoftPendingLaunch.Verdict.WAIT, UbisoftPendingLaunch.verdict(true, 5 * minute, true))
        assertEquals(UbisoftPendingLaunch.Verdict.WAIT, UbisoftPendingLaunch.verdict(true, 5 * minute, false))
        assertEquals(UbisoftPendingLaunch.Verdict.WAIT, UbisoftPendingLaunch.verdict(true, UbisoftPendingLaunch.TTL_MS * 2, true))
    }

    @Test
    fun anOldOrFutureMarkerIsDroppedWithoutLaunchingOrTellingAnyone() {
        assertEquals(UbisoftPendingLaunch.Verdict.EXPIRED, UbisoftPendingLaunch.verdict(false, UbisoftPendingLaunch.TTL_MS + 1, true))
        assertEquals(UbisoftPendingLaunch.Verdict.EXPIRED, UbisoftPendingLaunch.verdict(false, UbisoftPendingLaunch.TTL_MS + 1, false))
        assertEquals(UbisoftPendingLaunch.Verdict.EXPIRED, UbisoftPendingLaunch.verdict(false, -1L, true))
    }

    @Test
    fun theLifetimeCoversTheSetupSessionsOwnFifteenMinuteCap() {
        assertTrue(UbisoftPendingLaunch.TTL_MS > 15 * minute)
    }
}
