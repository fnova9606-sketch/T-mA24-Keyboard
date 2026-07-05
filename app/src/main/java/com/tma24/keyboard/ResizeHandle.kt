package com.tma24.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager

/**
 * ResizeHandle
 *
 * A thin draggable bar drawn at the top edge of the keyboard.
 * The user drags it up or down to resize the keyboard height.
 *
 * Drag mechanics:
 *   - Records Y position on ACTION_DOWN
 *   - On ACTION_MOVE calculates delta from initial touch
 *   - Converts delta to a height-scale percentage (50–150)
 *   - Persists the preference so the keyboard rebuilds at the new size
 *   - Fires [onResizeComplete] so the service can rebuild the panel
 *
 * Integration: add this view at the TOP of keyboard_main.xml,
 * keyboard_amharic.xml, and keyboard_coding.xml (done below in the
 * updated layout files).
 */
class ResizeHandle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?   = null,
    defStyle: Int          = 0
) : View(context, attrs, defStyle) {

    var onResizeComplete: (() -> Unit)? = null

    // ── Paint ─────────────────────────────────────────────────────────

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#881A3A5C")
        style = Paint.Style.FILL
    }

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#AAC8962A")   // brass
        style       = Paint.Style.FILL
        strokeWidth = 3f
    }

    private val gripRect = RectF()

    // ── Touch tracking ────────────────────────────────────────────────

    private var touchStartY      = 0f
    private var startHeightScale = 100   // percent

    // ── Draw ──────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Background bar
        canvas.drawRect(0f, 0f, w, h, handlePaint)

        // Three grip dots centred
        val dotR  = h * 0.18f
        val dotY  = h / 2f
        val gap   = dotR * 3.5f
        val start = w / 2f - gap

        repeat(3) { i ->
            gripRect.set(
                start + i * gap - dotR,
                dotY - dotR,
                start + i * gap + dotR,
                dotY + dotR
            )
            canvas.drawRoundRect(gripRect, dotR, dotR, gripPaint)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY      = event.rawY
                startHeightScale = prefs.getInt("pref_height_scale", 100)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY      = event.rawY - touchStartY
                // Dragging UP (negative delta) → make keyboard taller
                // Dragging DOWN (positive delta) → make keyboard shorter
                // Scale: 100px drag ≈ 20% change
                val deltaPercent = (-deltaY / 5f).toInt()
                val newScale     = (startHeightScale + deltaPercent)
                    .coerceIn(50, 150)

                prefs.edit().putInt("pref_height_scale", newScale).apply()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Notify the service to rebuild with the new scale
                onResizeComplete?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}