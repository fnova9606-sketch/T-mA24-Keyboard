package com.tma24.keyboard

import android.content.Context
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.preference.PreferenceManager

/**
 * KeyboardLayoutManager
 *
 * Applies one-handed mode and height scaling to the keyboard root view.
 *
 * One-handed mode:
 *   - Scales the keyboard to 70% of screen width
 *   - Anchors it to the left or right edge
 *   - Adds a "return to full" button on the empty side
 *
 * Height scaling:
 *   - Reads pref_height_scale (50–150, default 100)
 *   - Multiplies each key row's natural height by the scale factor
 *
 * Usage — called from TMA24InputMethodService.attachKeyboardPanelForCurrentMode():
 *
 *   val wrapped = KeyboardLayoutManager.applyLayout(context, panel)
 *   root.addView(wrapped)
 */
object KeyboardLayoutManager {

    // Keyboard occupies this fraction of screen width in one-handed mode
    private const val ONE_HANDED_WIDTH_FRACTION = 0.72f

    /**
     * Wraps [panel] in a correctly sized FrameLayout according to
     * the user's one-handed and height-scale preferences.
     *
     * Returns the wrapper to be added to the root FrameLayout.
     */
    fun applyLayout(context: Context, panel: View): View {
        val prefs         = PreferenceManager.getDefaultSharedPreferences(context)
        val oneHanded     = prefs.getBoolean("pref_one_handed", false)
        val side          = prefs.getString("pref_one_handed_side", "1")?.toIntOrNull() ?: 1
        val heightPercent = prefs.getInt("pref_height_scale", 100)
        val heightScale   = heightPercent / 100f

        // Apply height scale to the panel
        applyHeightScale(panel, heightScale)

        return if (oneHanded) {
            buildOneHandedWrapper(context, panel, side)
        } else {
            // Full-width — just return the panel itself
            panel
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Height scaling
    // ─────────────────────────────────────────────────────────────────

    /**
     * Scales the keyboard panel's height by [scale].
     * We apply scaleY and adjust the layout height so the IME window
     * sizes correctly.
     *
     * Scale range: 0.5 (50%) to 1.5 (150%).
     */
    private fun applyHeightScale(panel: View, scale: Float) {
        val clamped = scale.coerceIn(0.5f, 1.5f)
        panel.scaleY   = clamped
        panel.pivotY   = 0f   // scale from top edge

        // Compensate layout so the IME window height matches visual size
        panel.post {
            val naturalHeight = panel.height
            if (naturalHeight > 0) {
                val params = panel.layoutParams
                    ?: FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                params.height = (naturalHeight * clamped).toInt()
                panel.layoutParams = params
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // One-handed mode
    // ─────────────────────────────────────────────────────────────────

    /**
     * Wraps [panel] in a full-width FrameLayout.
     * The panel occupies [ONE_HANDED_WIDTH_FRACTION] of the screen.
     * The remaining space shows a "expand" button to exit one-handed mode.
     *
     * [side] — 0 = keyboard on left, 1 = keyboard on right.
     */
    private fun buildOneHandedWrapper(
        context: Context,
        panel: View,
        side: Int   // 0 = left, 1 = right
    ): View {
        val screenWidth   = getScreenWidth(context)
        val keyboardWidth = (screenWidth * ONE_HANDED_WIDTH_FRACTION).toInt()
        val expandWidth   = screenWidth - keyboardWidth

        // Root — full width, transparent
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Keyboard panel — constrained width
        val panelParams = FrameLayout.LayoutParams(
            keyboardWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (side == 0) Gravity.START else Gravity.END
        }
        root.addView(panel, panelParams)

        // Expand button — sits on the empty side
        val expandBtn = buildExpandButton(context, side)
        val expandParams = FrameLayout.LayoutParams(
            expandWidth,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = if (side == 0) Gravity.END else Gravity.START
        }
        root.addView(expandBtn, expandParams)

        return root
    }

    /**
     * The translucent arrow button on the empty side of the keyboard
     * in one-handed mode. Tapping it writes the preference and triggers
     * the keyboard to rebuild in full-width mode.
     */
    private fun buildExpandButton(context: Context, side: Int): View {
        val arrow = if (side == 0) "▶" else "◀"

        return android.widget.TextView(context).apply {
            text      = arrow
            textSize  = 22f
            gravity   = Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#88FFFFFF"))
            setBackgroundColor(android.graphics.Color.parseColor("#441A3A5C"))
            setOnClickListener {
                // Disable one-handed mode and let the service rebuild
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                prefs.edit().putBoolean("pref_one_handed", false).apply()
                // The service will re-inflate on next key press or mode switch;
                // we force it immediately by simulating a layout refresh.
                // The host service picks this up via a broadcast.
                context.sendBroadcast(
                    android.content.Intent(ACTION_REFRESH_LAYOUT)
                        .setPackage(context.packageName)
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private fun getScreenWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(dm)
            dm.widthPixels
        }
    }

    // Broadcast action — service listens for this to rebuild layout
    const val ACTION_REFRESH_LAYOUT = "com.tma24.keyboard.REFRESH_LAYOUT"
}