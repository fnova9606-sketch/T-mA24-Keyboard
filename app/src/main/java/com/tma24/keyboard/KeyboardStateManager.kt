package com.tma24.keyboard

enum class KeyboardMode { ENGLISH, AMHARIC, CODING }

enum class ShiftState { OFF, SINGLE, LOCKED }

class KeyboardStateManager {

    var currentMode: KeyboardMode = KeyboardMode.ENGLISH

    var shiftState: ShiftState = ShiftState.OFF

    val isShiftActive: Boolean
        get() = shiftState != ShiftState.OFF

    /**
     * Cycles: OFF → SINGLE → LOCKED → OFF
     * Double-tap the shift key to reach CAPS LOCK.
     */
    fun toggleShift() {
        shiftState = when (shiftState) {
            ShiftState.OFF    -> ShiftState.SINGLE
            ShiftState.SINGLE -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
    }

    fun clearShift() { shiftState = ShiftState.OFF }

    var isOneHandedMode: Boolean = false
    var oneHandedSide: Int       = 1    // 0 = left, 1 = right
    var heightScale: Float       = 1.0f
}