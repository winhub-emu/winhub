package com.winhub.hernandez.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * The app's own tiny sound engine for the Deck navigation sounds: the clips are decoded to PCM once, and one thread mixes whatever is
 * playing into a single AudioTrack on the media stream. Nothing here asks the system for a click sound, so it does not depend on the
 * phone's touch-sounds switch, its sound-effect volume, or a vendor's implementation of either: it plays wherever media plays.
 * The track runs only while a clip is sounding, then stops, so it does not hold the audio device open.
 */
internal class DeckSoundMixer private constructor(private val clips: Array<ShortArray>) {
    private val lock = Object()
    // Each voice is [clip index, next sample]. At most MAX_VOICES at once: a held D-pad key must not pile clips up.
    private val voices = ArrayList<IntArray>()
    @Volatile private var alive = true
    @Volatile var failed = false
        private set

    private val worker = Thread({ run() }, "deck-sound").apply {
        isDaemon = true
        start()
    }

    fun play(clip: Int) {
        if (failed || clip !in clips.indices) return
        synchronized(lock) {
            if (voices.size < MAX_VOICES) voices.add(intArrayOf(clip, 0))
            lock.notifyAll()
        }
    }

    fun release() {
        alive = false
        synchronized(lock) { lock.notifyAll() }
    }

    private fun run() {
        val track = try {
            val minBytes = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minBytes, CHUNK * 2 * 2))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the sound output; navigation sounds fall back to the phone's", e)
            failed = true
            return
        }
        val mix = IntArray(CHUNK)
        val out = ShortArray(CHUNK)
        try {
            while (alive) {
                synchronized(lock) {
                    while (alive && voices.isEmpty()) lock.wait()
                }
                if (!alive) break
                track.play()
                var more = true
                while (more && alive) {
                    java.util.Arrays.fill(mix, 0)
                    synchronized(lock) {
                        val it = voices.iterator()
                        while (it.hasNext()) {
                            val v = it.next()
                            val data = clips[v[0]]
                            var n = 0
                            while (n < CHUNK && v[1] < data.size) {
                                mix[n] += data[v[1]].toInt()
                                v[1]++
                                n++
                            }
                            if (v[1] >= data.size) it.remove()
                        }
                        more = voices.isNotEmpty()
                    }
                    for (k in 0 until CHUNK) out[k] = mix[k].coerceIn(-32768, 32767).toShort()
                    track.write(out, 0, CHUNK)
                }
                // Stops after the last written chunk has played, so the tail of a clip is not cut.
                track.stop()
            }
        } catch (e: InterruptedException) {
            Log.w(TAG, "Sound thread interrupted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Sound output failed; navigation sounds fall back to the phone's", e)
            failed = true
        } finally {
            runCatching { track.release() }
        }
    }

    companion object {
        private const val TAG = "DeckSoundMixer"
        private const val RATE = 44100
        private const val CHUNK = 512
        private const val MAX_VOICES = 6

        /** Decodes the clips (16-bit mono WAV raw resources, in the order of [ids]) and starts the engine; null when one cannot be read. */
        fun create(context: Context, ids: IntArray): DeckSoundMixer? {
            val clips = try {
                Array(ids.size) { i -> context.resources.openRawResource(ids[i]).use { decodeWav(it.readBytes()) } }
            } catch (e: Exception) {
                Log.e(TAG, "Could not read the navigation sound clips", e)
                return null
            }
            return DeckSoundMixer(clips)
        }

        /** The samples of a 16-bit mono PCM WAV: everything after its "data" chunk header. */
        private fun decodeWav(bytes: ByteArray): ShortArray {
            var p = 12
            while (p + 8 <= bytes.size) {
                val size = (bytes[p + 4].toInt() and 0xFF) or ((bytes[p + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[p + 6].toInt() and 0xFF) shl 16) or ((bytes[p + 7].toInt() and 0xFF) shl 24)
                val isData = bytes[p] == 'd'.code.toByte() && bytes[p + 1] == 'a'.code.toByte() &&
                    bytes[p + 2] == 't'.code.toByte() && bytes[p + 3] == 'a'.code.toByte()
                if (isData) {
                    val start = p + 8
                    val end = minOf(bytes.size, start + size)
                    val samples = ShortArray((end - start) / 2)
                    for (i in samples.indices) {
                        val lo = bytes[start + 2 * i].toInt() and 0xFF
                        val hi = bytes[start + 2 * i + 1].toInt()
                        samples[i] = ((hi shl 8) or lo).toShort()
                    }
                    return samples
                }
                p += 8 + size + (size and 1)
            }
            throw IllegalArgumentException("No data chunk in the WAV")
        }
    }
}
