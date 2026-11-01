package com.winhub.hernandez.store.xbox

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Controller state for the native cloud player, from Android key and motion events (no Gamepad API). Button
 * bits follow the xCloud input report (Greenlight's packet.ts): Nexus 2, Menu 4, View 8, A 16, B 32, X 64,
 * Y 128, D-pad 256..2048, shoulders 4096 / 8192, stick clicks 16384 / 32768.
 */
internal class XboxNativePad {
    data class State(val buttons: Int, val lx: Float, val ly: Float, val rx: Float, val ry: Float, val lt: Float, val rt: Float) {
        /** Greenlight's rule: any button change, a stick moved past 0.08 or held past 0.15, a trigger past 0.01. */
        fun differsFrom(o: State): Boolean = buttons != o.buttons ||
            listOf(lx to o.lx, ly to o.ly, rx to o.rx, ry to o.ry).any { (a, b) -> Math.abs(a - b) > 0.08f || Math.abs(a) > 0.15f } ||
            Math.abs(lt - o.lt) > 0.01f || Math.abs(rt - o.rt) > 0.01f
    }

    @Volatile private var buttons = 0
    @Volatile private var hatButtons = 0
    @Volatile private var lx = 0f; @Volatile private var ly = 0f
    @Volatile private var rx = 0f; @Volatile private var ry = 0f
    @Volatile private var lt = 0f; @Volatile private var rt = 0f
    /** Called after any change (instant controller input). */
    @Volatile var onChanged: (() -> Unit)? = null

    /** Buttons pressed on behalf of the app (the menu's Xbox button), OR-ed in. */
    @Volatile var overrideButtons = 0

    fun snapshot() = State(buttons or hatButtons or overrideButtons, lx, ly, rx, ry, lt, rt)

    /** True when the event was a controller button this pad tracks. */
    fun onKey(event: KeyEvent): Boolean {
        val bit = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> 16
            KeyEvent.KEYCODE_BUTTON_B -> 32
            KeyEvent.KEYCODE_BUTTON_X -> 64
            KeyEvent.KEYCODE_BUTTON_Y -> 128
            KeyEvent.KEYCODE_DPAD_UP -> 256
            KeyEvent.KEYCODE_DPAD_DOWN -> 512
            KeyEvent.KEYCODE_DPAD_LEFT -> 1024
            KeyEvent.KEYCODE_DPAD_RIGHT -> 2048
            KeyEvent.KEYCODE_BUTTON_L1 -> 4096
            KeyEvent.KEYCODE_BUTTON_R1 -> 8192
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 16384
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 32768
            KeyEvent.KEYCODE_BUTTON_START -> 4
            KeyEvent.KEYCODE_BUTTON_SELECT -> 8
            // Digital triggers on pads without analog axes.
            KeyEvent.KEYCODE_BUTTON_L2 -> { lt = if (event.action == KeyEvent.ACTION_DOWN) 1f else 0f; onChanged?.invoke(); return true }
            KeyEvent.KEYCODE_BUTTON_R2 -> { rt = if (event.action == KeyEvent.ACTION_DOWN) 1f else 0f; onChanged?.invoke(); return true }
            else -> return false
        }
        buttons = if (event.action == KeyEvent.ACTION_DOWN) buttons or bit else buttons and bit.inv()
        onChanged?.invoke()
        return true
    }

    /** True when the event came from a joystick (sticks, triggers, hat). */
    fun onMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device
        fun axis(code: Int): Float {
            val value = event.getAxisValue(code)
            val flat = device?.getMotionRange(code, event.source)?.flat ?: 0f
            return if (Math.abs(value) > maxOf(flat, 0.08f)) value else 0f
        }
        lx = axis(MotionEvent.AXIS_X); ly = axis(MotionEvent.AXIS_Y)
        rx = axis(MotionEvent.AXIS_Z); ry = axis(MotionEvent.AXIS_RZ)
        lt = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE))
        rt = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS))
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X); val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        hatButtons = (if (hy < -0.5f) 256 else 0) or (if (hy > 0.5f) 512 else 0) or (if (hx < -0.5f) 1024 else 0) or (if (hx > 0.5f) 2048 else 0)
        onChanged?.invoke()
        return true
    }

    fun reset() { buttons = 0; hatButtons = 0; overrideButtons = 0; lx = 0f; ly = 0f; rx = 0f; ry = 0f; lt = 0f; rt = 0f }
}
