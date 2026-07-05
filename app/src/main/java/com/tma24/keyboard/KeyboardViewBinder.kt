package com.tma24.keyboard

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.graphics.Color

/**
 * KeyboardViewBinder
 *
 * Walks the inflated keyboard panel view tree, finds every tagged button,
 * and attaches:
 *   - onClick   → routes to TMA24InputMethodService.onKeyPressed()
 *   - onLongPress → popup with character variants (e.g. T → Ț Þ Ť Ţ Ŧ)
 *   - Held backspace → repeating delete
 *
 * Button identification: android:tag="key:X" in layout XML.
 *
 * Tag reference:
 *   key:a … key:z     regular character
 *   key:BACKSPACE      delete / cancel Amharic pending
 *   key:SPACE          space / double-space period
 *   key:ENTER          enter / editor action
 *   key:SHIFT          shift toggle
 *   key:TAB            tab character
 *   key:ESC            escape
 *   key:ARROW_L/R/U/D  cursor movement
 *   key:MODE_EN        switch to English
 *   key:MODE_AM        switch to Amharic
 *   key:MODE_CODE      switch to Coding
 *   key:SELECT_ALL     select all text
 *   key:COPY           copy selection
 *   key:PASTE          paste from clipboard
 *   key:CUT            cut selection
 *   key:EMOJI          toggle emoji panel
 *   key:ሐ (any Ethiopic char)  Amharic special key
 */
object KeyboardViewBinder {

    private const val LONG_PRESS_TRIGGER_MS  = 380L
    private const val BACKSPACE_REPEAT_MS    = 45L

    // Long-press character variant map
    private val VARIANTS: Map<Char, List<String>> = mapOf(
        'a' to listOf("à","á","â","ã","ä","å","æ","ā","ă"),
        'c' to listOf("ç","ć","č","ĉ"),
        'e' to listOf("è","é","ê","ë","ě","ē","ĕ","ę"),
        'i' to listOf("ì","í","î","ï","ī","ĭ","į"),
        'n' to listOf("ñ","ń","ň","ņ"),
        'o' to listOf("ò","ó","ô","õ","ö","ø","œ","ō","ő"),
        's' to listOf("ß","ś","š","ş","ŝ"),
        't' to listOf("ț","þ","ť","ţ","ŧ"),
        'u' to listOf("ù","ú","û","ü","ů","ū","ű","ų"),
        'y' to listOf("ý","ÿ"),
        'z' to listOf("ź","ž","ż"),
        'g' to listOf("ĝ","ğ","ġ","ģ"),
        'h' to listOf("ĥ","ħ"),
        'j' to listOf("ĵ"),
        'k' to listOf("ķ","ĸ"),
        'l' to listOf("ĺ","ļ","ľ","ŀ","ł"),
        'r' to listOf("ŕ","ř","ŗ"),
        'w' to listOf("ŵ"),
        'x' to listOf("×"),
        '1' to listOf("¹","½","⅓","¼","⅛"),
        '2' to listOf("²","⅔","½"),
        '3' to listOf("³","¾","⅓"),
        '0' to listOf("°","∅","Ø","∞"),
        '.' to listOf("…","·","•","·"),
        ',' to listOf("،","、","„","‚"),
        '!' to listOf("¡","‼","❗"),
        '?' to listOf("¿","‽","❓"),
        '-' to listOf("–","—","­","‐"),
        '\'' to listOf("'","'","‛","′","`"),
        '"' to listOf(""",""","„","«","»"),
        '<' to listOf("≤","«","‹","←","≪"),
        '>' to listOf("≥","»","›","→","≫"),
        '=' to listOf("≠","≈","≡","≤","≥","±"),
        '+' to listOf("±","†","‡","⊕"),
        '*' to listOf("×","★","✱","·","⊗"),
        '/' to listOf("÷","⁄","∕","//"),
        '@' to listOf("©","®","™"),
        '#' to listOf("№","§","¶"),
        '$' to listOf("€","£","¥","₹","₿"),
        '%' to listOf("‰","‱","℅"),
        '^' to listOf("↑","⌃","△"),
        '&' to listOf("∧","⅋"),
        '|' to listOf("¦","∣","‖"),
        '~' to listOf("≈","∼","≃"),
        '`' to listOf("´","˜","ˆ","˚"),
    )

    // ── Entry point ───────────────────────────────────────────────────

    fun bind(
        rootView: View,
        stateManager: KeyboardStateManager,
        service: TMA24InputMethodService
    ) {
        walkAndBind(rootView, stateManager, service)
    }

    // ── Recursive view walker ─────────────────────────────────────────

    private fun walkAndBind(
        view: View,
        stateManager: KeyboardStateManager,
        service: TMA24InputMethodService
    ) {
        val tag = view.tag as? String
        if (tag != null && tag.startsWith("key:")) {
            val keyTag = tag.removePrefix("key:")
            attachHandlers(view, keyTag, stateManager, service)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walkAndBind(view.getChildAt(i), stateManager, service)
            }
        }
    }

    // ── Handler attachment ────────────────────────────────────────────

    private fun attachHandlers(
        view: View,
        keyTag: String,
        stateManager: KeyboardStateManager,
        service: TMA24InputMethodService
    ) {
        val handler = Handler(Looper.getMainLooper())
        var repeatRunnable: Runnable? = null

        // Touch listener — handles held backspace repeat
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (keyTag == "BACKSPACE") {
                        repeatRunnable = object : Runnable {
                            override fun run() {
                                service.onKeyPressed(KeyEvent.KEYCODE_DEL, null, null)
                                handler.postDelayed(this, BACKSPACE_REPEAT_MS)
                            }
                        }
                        handler.postDelayed(repeatRunnable!!, LONG_PRESS_TRIGGER_MS)
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatRunnable?.let { handler.removeCallbacks(it) }
                    repeatRunnable = null
                    false
                }
                else -> false
            }
        }

        // Click listener
        view.setOnClickListener {
            dispatchKey(keyTag, service)
        }

        // Long press — character variants popup (Latin keys only)
        if (keyTag.length == 1) {
            val keyChar = keyTag[0]
            val variants = VARIANTS[keyChar.lowercaseChar()]
            if (!variants.isNullOrEmpty()) {
                view.setOnLongClickListener {
                    showVariantPopup(view, variants, service)
                    true
                }
            }
        }
    }

    // ── Key dispatch ──────────────────────────────────────────────────

    private fun dispatchKey(keyTag: String, service: TMA24InputMethodService) {
        when (keyTag) {
            "BACKSPACE"  -> service.onKeyPressed(KeyEvent.KEYCODE_DEL,         null, null)
            "SPACE"      -> service.onKeyPressed(KeyEvent.KEYCODE_SPACE,        null, null)
            "ENTER"      -> service.onKeyPressed(KeyEvent.KEYCODE_ENTER,        null, null)
            "SHIFT"      -> service.onKeyPressed(KeyEvent.KEYCODE_SHIFT_LEFT,   null, null)
            "TAB"        -> service.onKeyPressed(KeyEvent.KEYCODE_TAB,          null, null)
            "ESC"        -> service.onKeyPressed(KeyEvent.KEYCODE_ESCAPE,       null, null)
            "ARROW_L"    -> service.onKeyPressed(KeyEvent.KEYCODE_DPAD_LEFT,    null, null)
            "ARROW_R"    -> service.onKeyPressed(KeyEvent.KEYCODE_DPAD_RIGHT,   null, null)
            "ARROW_U"    -> service.onKeyPressed(KeyEvent.KEYCODE_DPAD_UP,      null, null)
            "ARROW_D"    -> service.onKeyPressed(KeyEvent.KEYCODE_DPAD_DOWN,    null, null)
            "MODE_EN"    -> service.onKeyPressed(TMA24InputMethodService.KEY_SWITCH_TO_ENGLISH, null, null)
            "MODE_AM"    -> service.onKeyPressed(TMA24InputMethodService.KEY_SWITCH_TO_AMHARIC, null, null)
            "MODE_CODE"  -> service.onKeyPressed(TMA24InputMethodService.KEY_SWITCH_TO_CODING,  null, null)
            "SELECT_ALL" -> service.onKeyPressed(TMA24InputMethodService.KEY_SELECT_ALL, null, null)
            "COPY"       -> service.onKeyPressed(TMA24InputMethodService.KEY_COPY,       null, null)
            "PASTE"      -> service.onKeyPressed(TMA24InputMethodService.KEY_PASTE,      null, null)
            "CUT"        -> service.onKeyPressed(TMA24InputMethodService.KEY_CUT,        null, null)
            "EMOJI"      -> service.onKeyPressed(TMA24InputMethodService.KEY_EMOJI,      null, null)
            "CTRL_Z"     -> {
                // Undo — send Ctrl+Z key event
                val ic = service.currentInputConnection ?: return
                ic.sendKeyEvent(
                    KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z,
                        0, KeyEvent.META_CTRL_ON)
                )
                ic.sendKeyEvent(
                    KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z,
                        0, KeyEvent.META_CTRL_ON)
                )
            }
            else -> {
                if (keyTag.isEmpty()) return

                if (keyTag.length == 1) {
                    // Single character — Latin or Ethiopic BMP char
                    service.onKeyPressed(
                        TMA24InputMethodService.KEY_CHARACTER,
                        keyTag[0],
                        keyTag
                    )
                } else {
                    // Multi-char string (composed emoji or multi-codepoint sequence)
                    service.currentInputConnection?.commitText(keyTag, 1)
                }
            }
        }
    }

    // ── Long-press variant popup ──────────────────────────────────────

    private fun showVariantPopup(
        anchor: View,
        variants: List<String>,
        service: TMA24InputMethodService
    ) {
        val context = anchor.context
        val popup   = PopupWindow(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EE2D2D2D"))
            setPadding(8, 8, 8, 8)
        }

        variants.forEach { variant ->
            val btn = Button(context).apply {
                text     = variant
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                minWidth = 0
                minimumWidth = 0
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    service.currentInputConnection?.commitText(variant, 1)
                    service.performHaptic()
                    popup.dismiss()
                }
            }
            container.addView(
                btn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        popup.contentView      = container
        popup.width            = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.height           = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.isFocusable      = false

        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        popup.showAtLocation(
            anchor,
            android.view.Gravity.NO_GRAVITY,
            loc[0],
            loc[1] - anchor.height * 2
        )
    }
}