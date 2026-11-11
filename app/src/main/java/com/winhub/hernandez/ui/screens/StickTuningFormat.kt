package com.winhub.hernandez.ui.screens

import com.winhub.hernandez.inputcontrols.StickShaper
import java.util.Locale

/** Slider steps between the range ends: 1% deadzone steps, 0.05x sensitivity steps. */
internal const val STICK_DEADZONE_STEPS = 49
internal const val STICK_SENSITIVITY_STEPS = 29

internal val STICK_DEADZONE_RANGE: ClosedFloatingPointRange<Float> =
    StickShaper.DEADZONE_MIN..StickShaper.DEADZONE_MAX
internal val STICK_SENSITIVITY_RANGE: ClosedFloatingPointRange<Float> =
    StickShaper.SENSITIVITY_MIN..StickShaper.SENSITIVITY_MAX

internal fun stickDeadzoneText(v: Float): String = "${Math.round(v * 100f)}%"

internal fun stickSensitivityText(v: Float): String = String.format(Locale.US, "%.2fx", v)

/** Value written to a shortcut extra: two decimals is plenty for 1% / 0.05x slider steps. */
internal fun stickExtraText(v: Float): String = String.format(Locale.US, "%.2f", v)
