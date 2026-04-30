package com.example.sermontimer.util

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.getSystemService
import com.example.sermontimer.domain.model.Segment

/**
 * Haptic patterns for the sermon timer.
 *
 * Design constraints (v1.24, after user feedback "иногда пропускаешь вибро в стрессе"):
 *  - **Boundary transitions are 10 s long, completion is 20 s long.** Short bursts
 *    (the previous ≤1.5 s patterns) get habituated and missed under speaking stress.
 *    A sustained multi-second pattern is physiologically impossible to ignore — the
 *    nervous system can't tune out a 10 s wrist alarm even if attention is elsewhere.
 *  - **All boundary haptics use `USAGE_ALARM`.** USAGE_NOTIFICATION is volume-attenuated
 *    by Wear silent / DND modes; USAGE_ALARM bypasses those AND requests max amplitude
 *    from the vibrator HAL. Previous code used USAGE_NOTIFICATION for boundaries — the
 *    main reason patterns felt weak even at amplitude 255.
 *  - **Distinct cadence per transition** so the preacher can identify which boundary
 *    fired *by feel alone* without looking at the watch:
 *      - INTRO start:  long sustained pulses (1.5 s on / 0.3 s off) — "settled in"
 *      - MAIN start:   2-beat rhythm (400 / 100 / 400 / 200)         — "you're in the meat"
 *      - OUTRO start:  3-beat thumping (short / short / long)        — "wrap up incoming"
 *      - OVERTIME:     rapid staccato (200 / 100)                    — "you went over!"
 *      - COMPLETION:   3-phase escalation over 20 s                  — "STOP NOW"
 *  - **Amplitude 255** on every ON segment. Galaxy Watch 5's LRA is calibrated for
 *    255 = max physical intensity. Anything less is a deliberate softening; we don't
 *    want softening at boundaries.
 *  - **`vibrator.cancel()` before each new effect** so a still-running 10 s pattern
 *    doesn't interleave with a new event.
 *  - **Single `createWaveform` call**. The system vibrator daemon plays the full
 *    waveform autonomously — even if our process is frozen by Samsung Freecess, the
 *    vibration continues. This is critical for long sermons where the FGS may be
 *    suspended between exact alarms.
 */
class HapticPatterns(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: context.getSystemService<Vibrator>()!!
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val feedbackAttributes: VibrationAttributes by lazy {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
            .build()
    }

    private val alarmAttributes: VibrationAttributes by lazy {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build()
    }

    /**
     * Boundary haptic — 10 s sustained pattern, distinct cadence per target segment.
     * Called when the engine emits BoundaryReached(nextSegment).
     *
     * Always cancels any in-flight vibration first so a previous boundary's tail
     * doesn't muddy the new pattern.
     */
    fun playBoundaryPattern(segment: Segment) {
        if (!vibrator.hasVibrator()) return

        val style = when (segment) {
            Segment.INTRO -> BoundaryStyle.INTRO_GO
            Segment.MAIN -> BoundaryStyle.MAIN_RHYTHMIC
            Segment.OUTRO -> BoundaryStyle.OUTRO_TRIPLE
            Segment.DONE -> BoundaryStyle.OVERTIME_URGENT // crossed final boundary
        }
        val (timings, amps) = buildBoundaryWaveform(style, BOUNDARY_DURATION_MS)
        Log.i("HAPTIC", "playBoundaryPattern segment=$segment style=$style duration=${timings.sum()}ms")
        vibrator.cancel()
        val effect = VibrationEffect.createWaveform(timings, amps, -1)
        vibrator.vibrate(effect, alarmAttributes)
    }

    /**
     * Completion haptic — 20 s 3-phase escalation. Fires on natural Completed and on
     * OvertimeCapped (engine emits both at the cap). The first call defines the 20 s
     * pattern; the second cancels and replays it (net: one 20 s pattern, perceptually
     * uninterrupted because the cancel-and-replay happens in the same coroutine tick).
     */
    fun playCompletionPattern() {
        if (!vibrator.hasVibrator()) return
        val (timings, amps) = buildCompletionWaveform()
        Log.i("HAPTIC", "playCompletionPattern duration=${timings.sum()}ms")
        vibrator.cancel()
        val effect = VibrationEffect.createWaveform(timings, amps, -1)
        vibrator.vibrate(effect, alarmAttributes)
    }

    /**
     * Preroll → Intro transition — same 10 s INTRO_GO cadence as a regular
     * boundary, so the user feels "GO!" the same way every time, whether or not
     * preroll was configured.
     */
    fun playPrerollEndedPattern() {
        if (!vibrator.hasVibrator()) return
        val (timings, amps) = buildBoundaryWaveform(BoundaryStyle.INTRO_GO, BOUNDARY_DURATION_MS)
        Log.i("HAPTIC", "playPrerollEndedPattern duration=${timings.sum()}ms")
        vibrator.cancel()
        val effect = VibrationEffect.createWaveform(timings, amps, -1)
        vibrator.vibrate(effect, alarmAttributes)
    }

    /**
     * Outro → Overtime transition — 10 s OVERTIME_URGENT cadence (rapid staccato).
     */
    fun playOvertimeStartedPattern() {
        if (!vibrator.hasVibrator()) return
        val (timings, amps) = buildBoundaryWaveform(BoundaryStyle.OVERTIME_URGENT, BOUNDARY_DURATION_MS)
        Log.i("HAPTIC", "playOvertimeStartedPattern duration=${timings.sum()}ms")
        vibrator.cancel()
        val effect = VibrationEffect.createWaveform(timings, amps, -1)
        vibrator.vibrate(effect, alarmAttributes)
    }

    /** Soft single tap — used to confirm Start, Pause, Resume actions. NOT for boundaries. */
    fun playLightTick() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(60L, 140)
        vibrator.vibrate(effect, feedbackAttributes)
    }

    /** Subtle paired tap — when the timer first starts (or preroll begins). */
    fun playStartPattern() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(
            START_PATTERN.first,
            START_PATTERN.second,
            -1,
        )
        vibrator.vibrate(effect, feedbackAttributes)
    }

    /**
     * T-10 countdown — N quick pulses at 500 ms cadence leading into the boundary.
     * Stays at amplitude 200 (not 255) so it's distinguishable from the boundary itself
     * — the boundary's max-amplitude 10 s pattern feels "harder" than the build-up.
     */
    fun startCountdownVibration(remainingSeconds: Int) {
        if (!vibrator.hasVibrator() || remainingSeconds !in 1..10) return
        stopCountdownVibration()
        val (timings, amplitudes) = buildCountdownWaveform(remainingSeconds)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        Log.i("HAPTIC", "startCountdownVibration pulses=$remainingSeconds")
        vibrator.vibrate(effect, alarmAttributes)
    }

    fun stopCountdownVibration() {
        vibrator.cancel()
    }

    private enum class BoundaryStyle {
        INTRO_GO,        // long sustained pulses, "settling in"
        MAIN_RHYTHMIC,   // 2-beat rhythm, "you're in the meat now"
        OUTRO_TRIPLE,    // 3-beat thumping, "wrap up incoming"
        OVERTIME_URGENT, // rapid staccato, "you went over!"
    }

    companion object {
        private const val BOUNDARY_DURATION_MS = 10_000L
        private const val COMPLETION_DURATION_MS = 20_000L

        // Soft "ready, set" feel at start. Stays short and gentle by design — no
        // urgency yet, the timer hasn't actually begun.
        private val START_PATTERN = Pair(
            longArrayOf(0, 60, 80, 100),
            intArrayOf(0, 160, 0, 200),
        )

        /**
         * Build a 10 s sustained boundary waveform with cadence determined by [style].
         * Each style has a base cycle that's repeated until the requested total
         * duration is filled. The ON-amplitude is always 255 so the user feels max
         * physical intensity — the styles differ only in *rhythm*, which is what makes
         * each transition identifiable by feel alone.
         */
        private fun buildBoundaryWaveform(
            style: BoundaryStyle,
            durationMs: Long,
        ): Pair<LongArray, IntArray> {
            val cycle: List<Pair<Long, Int>> = when (style) {
                BoundaryStyle.INTRO_GO -> listOf(
                    1500L to 255, 300L to 0,
                )
                BoundaryStyle.MAIN_RHYTHMIC -> listOf(
                    400L to 255, 100L to 0,
                    400L to 255, 200L to 0,
                )
                BoundaryStyle.OUTRO_TRIPLE -> listOf(
                    300L to 255, 100L to 0,
                    300L to 255, 100L to 0,
                    600L to 255, 300L to 0,
                )
                BoundaryStyle.OVERTIME_URGENT -> listOf(
                    200L to 255, 100L to 0,
                )
            }
            return fillCycle(cycle, durationMs)
        }

        /**
         * Build a 20 s 3-phase escalating completion waveform.
         *  - 0–5 s:  rapid staccato, "the timer just ended"
         *  - 5–13 s: sustained heavy, "you really need to wrap up"
         *  - 13–20 s: ultra-heavy long pulses, "STOP NOW"
         * The escalation is the safety net: even if the preacher tunes out the first
         * phase from speaking adrenaline, phase 3 thumps are unmissable.
         */
        private fun buildCompletionWaveform(): Pair<LongArray, IntArray> {
            val timings = mutableListOf<Long>(0L)
            val amplitudes = mutableListOf<Int>(0)

            // Phase 1: rapid attention grab (5 s)
            appendCycle(
                timings, amplitudes,
                listOf(200L to 255, 100L to 0),
                5_000L,
            )
            // Phase 2: sustained heavy (8 s)
            appendCycle(
                timings, amplitudes,
                listOf(1000L to 255, 250L to 0),
                8_000L,
            )
            // Phase 3: ultra heavy (7 s)
            appendCycle(
                timings, amplitudes,
                listOf(2000L to 255, 300L to 0),
                7_000L,
            )

            return Pair(timings.toLongArray(), amplitudes.toIntArray())
        }

        private fun fillCycle(
            cycle: List<Pair<Long, Int>>,
            durationMs: Long,
        ): Pair<LongArray, IntArray> {
            val timings = mutableListOf<Long>(0L)
            val amplitudes = mutableListOf<Int>(0)
            appendCycle(timings, amplitudes, cycle, durationMs)
            return Pair(timings.toLongArray(), amplitudes.toIntArray())
        }

        private fun appendCycle(
            timings: MutableList<Long>,
            amplitudes: MutableList<Int>,
            cycle: List<Pair<Long, Int>>,
            durationMs: Long,
        ) {
            var elapsed = 0L
            while (elapsed < durationMs) {
                for ((d, a) in cycle) {
                    if (elapsed >= durationMs) break
                    timings += d
                    amplitudes += a
                    elapsed += d
                }
            }
        }

        private fun buildCountdownWaveform(seconds: Int): Pair<LongArray, IntArray> {
            val pulses = seconds.coerceIn(1, 10)
            val steps = pulses * 2 + 1
            val timings = LongArray(steps)
            val amplitudes = IntArray(steps)
            timings[0] = 0L
            amplitudes[0] = 0
            var idx = 1
            repeat(pulses) {
                timings[idx] = 500L; amplitudes[idx] = 200; idx++
                timings[idx] = 500L; amplitudes[idx] = 0; idx++
            }
            return Pair(timings, amplitudes)
        }
    }
}
