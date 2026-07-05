package com.tma24.keyboard

import android.content.BroadcastReceiver
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TMA24InputMethodService : InputMethodService() {

    // ── Subsystems ────────────────────────────────────────────────────
    private lateinit var stateManager:    KeyboardStateManager
    private lateinit var amharicEngine:   AmharicEngine
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var ocrManager:      OCRManager

    // ── Views ─────────────────────────────────────────────────────────
    private var keyboardRootView:    FrameLayout?   = null
    private var candidatesRootView:  LinearLayout?  = null

    // ── State ─────────────────────────────────────────────────────────
    private var emojiPanelActive     = false
    private var lastSpaceTime        = 0L
    private val DOUBLE_SPACE_MS      = 300L

    // ── Coroutines ────────────────────────────────────────────────────
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Haptic ────────────────────────────────────────────────────────
    private var vibrator: Vibrator? = null
    private val HAPTIC_MS = 28L

    // ── Broadcast receiver — listens for one-handed expand action ─────
    private val layoutRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeyboardLayoutManager.ACTION_REFRESH_LAYOUT) {
                refreshKeyboardLayout()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        stateManager    = KeyboardStateManager()
        amharicEngine   = AmharicEngine()
        clipboardManager = ClipboardManager(applicationContext)
        ocrManager      = OCRManager(applicationContext)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // System clipboard watcher
        val sysClip = getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
        sysClip.addPrimaryClipChangedListener {
            val clip = sysClip.primaryClip ?: return@addPrimaryClipChangedListener
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0)
                    .coerceToText(applicationContext).toString()
                if (text.isNotBlank()) {
                    clipboardManager.addEntry(text)
                    updateCandidatesView()
                }
            }
        }

        // Register broadcast receiver for layout refresh (one-handed expand)
        val filter = IntentFilter(KeyboardLayoutManager.ACTION_REFRESH_LAYOUT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(layoutRefreshReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(layoutRefreshReceiver, filter)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(layoutRefreshReceiver) } catch (e: Exception) { }
        ocrManager.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────
    // Input view
    // ─────────────────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        keyboardRootView = root
        attachKeyboardPanelForCurrentMode(root)
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        emojiPanelActive = false
        refreshKeyboardLayout()
    }

    // ─────────────────────────────────────────────────────────────────
    // Candidates view
    // ─────────────────────────────────────────────────────────────────

    override fun onCreateCandidatesView(): View {
        val inflater = LayoutInflater.from(this)
        val root = inflater.inflate(
            R.layout.view_clipboard_strip, null
        ) as LinearLayout
        candidatesRootView = root
        updateCandidatesView()
        return root
    }

    fun updateCandidatesView() {
        setCandidatesViewShown(true)
        val root = candidatesRootView ?: return

        val orderRow  = root.findViewById<LinearLayout>(R.id.order_labels_row)
        val contentRow = root.findViewById<LinearLayout>(R.id.strip_content_row)
            ?: return

        contentRow.removeAllViews()

        when {
            stateManager.currentMode == KeyboardMode.AMHARIC &&
                    amharicEngine.hasPendingBase() -> {
                orderRow?.visibility = View.VISIBLE
                showAmharicFamilyBar(contentRow)
            }
            clipboardManager.hasEntries() -> {
                orderRow?.visibility = View.GONE
                showClipboardStrip(contentRow)
            }
            else -> {
                orderRow?.visibility = View.GONE
                setCandidatesViewShown(false)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Amharic family bar
    // ─────────────────────────────────────────────────────────────────

    private fun showAmharicFamilyBar(contentRow: LinearLayout) {
        val family = amharicEngine.getCurrentFamily()
        if (family.isEmpty()) return

        family.forEach { ch ->
            val btn = Button(this).apply {
                text     = ch.toString()
                textSize = 24f
                typeface = Typeface.SERIF
                setTextColor(Color.WHITE)
                try {
                    background = getDrawable(R.drawable.bg_family_btn)
                } catch (e: Exception) {
                    setBackgroundColor(Color.parseColor("#2A5A8C"))
                }
                setOnClickListener { commitAmharicCharacter(ch) }
            }
            contentRow.addView(
                btn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun commitAmharicCharacter(char: Char) {
        currentInputConnection?.commitText(char.toString(), 1)
        amharicEngine.clearPendingBase()
        updateCandidatesView()
        performHaptic()
    }

    // ─────────────────────────────────────────────────────────────────
    // Clipboard strip
    // ─────────────────────────────────────────────────────────────────

    private fun showClipboardStrip(contentRow: LinearLayout) {
        val recent = clipboardManager.getRecentEntries(5)
        if (recent.isEmpty()) return

        recent.forEach { entry ->
            val label = if (entry.length > 18) entry.take(18) + "…" else entry

            val chip = TextView(this).apply {
                text      = label
                textSize  = 13f
                maxLines  = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(24, 8, 24, 8)
                try {
                    setTextColor(getColor(R.color.light_chip_text))
                    background = getDrawable(R.drawable.bg_chip)
                } catch (e: Exception) {
                    setTextColor(Color.parseColor("#1A3A5C"))
                    setBackgroundColor(Color.WHITE)
                }
                setOnClickListener {
                    currentInputConnection?.commitText(entry, 1)
                    performHaptic()
                }
                setOnLongClickListener {
                    clipboardManager.removeEntry(entry)
                    updateCandidatesView()
                    true
                }
            }
            contentRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(6, 4, 6, 4) }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Central key dispatcher
    // ─────────────────────────────────────────────────────────────────

    fun onKeyPressed(keyCode: Int, keyChar: Char?, label: String?) {
        val ic = currentInputConnection ?: return
        performHaptic()

        when (keyCode) {

            KeyEvent.KEYCODE_DEL -> {
                if (stateManager.currentMode == KeyboardMode.AMHARIC &&
                    amharicEngine.hasPendingBase()
                ) {
                    amharicEngine.clearPendingBase()
                    updateCandidatesView()
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }

            KeyEvent.KEYCODE_SPACE -> handleSpaceKey(ic)

            KeyEvent.KEYCODE_ENTER -> {
                val ei = currentInputEditorInfo
                if (ei != null &&
                    ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
                ) {
                    val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
                    if (action != EditorInfo.IME_ACTION_NONE &&
                        action != EditorInfo.IME_ACTION_UNSPECIFIED
                    ) {
                        ic.performEditorAction(action)
                        return
                    }
                }
                ic.commitText("\n", 1)
            }

            KeyEvent.KEYCODE_TAB     -> ic.commitText("\t", 1)

            KeyEvent.KEYCODE_ESCAPE  -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_ESCAPE))
            }

            KeyEvent.KEYCODE_DPAD_LEFT  -> sendArrow(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyEvent.KEYCODE_DPAD_UP    -> sendArrow(KeyEvent.KEYCODE_DPAD_UP)
            KeyEvent.KEYCODE_DPAD_DOWN  -> sendArrow(KeyEvent.KEYCODE_DPAD_DOWN)

            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                stateManager.toggleShift()
                refreshKeyboardLayout()
            }

            KEY_SWITCH_TO_ENGLISH -> switchMode(KeyboardMode.ENGLISH)
            KEY_SWITCH_TO_AMHARIC -> switchMode(KeyboardMode.AMHARIC)
            KEY_SWITCH_TO_CODING  -> switchMode(KeyboardMode.CODING)

            KEY_SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            KEY_COPY       -> ic.performContextMenuAction(android.R.id.copy)
            KEY_CUT        -> ic.performContextMenuAction(android.R.id.cut)
            KEY_PASTE -> {
                val clip = clipboardManager.getRecentEntries(1).firstOrNull()
                if (clip != null) ic.commitText(clip, 1)
            }

            KEY_EMOJI -> toggleEmojiPanel()

            KEY_CHARACTER -> {
                if (keyChar == null) return
                if (stateManager.currentMode == KeyboardMode.AMHARIC) {
                    handleAmharicKeyPress(keyChar)
                } else {
                    val ch = if (stateManager.isShiftActive) {
                        keyChar.uppercaseChar()
                    } else {
                        keyChar
                    }
                    ic.commitText(ch.toString(), 1)
                    if (stateManager.shiftState == ShiftState.SINGLE) {
                        stateManager.shiftState = ShiftState.OFF
                        refreshKeyboardLayout()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Space — double-space period
    // ─────────────────────────────────────────────────────────────────

    private fun handleSpaceKey(ic: android.view.inputmethod.InputConnection) {
        if (stateManager.currentMode == KeyboardMode.AMHARIC &&
            amharicEngine.hasPendingBase()
        ) {
            ic.commitText(amharicEngine.getBaseChar().toString(), 1)
            amharicEngine.clearPendingBase()
            updateCandidatesView()
        }

        val prefs = android.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
        val dsp = prefs.getBoolean("pref_double_space_period", true)
        val now = System.currentTimeMillis()

        if (dsp && now - lastSpaceTime < DOUBLE_SPACE_MS) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTime = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpaceTime = now
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Amharic key handling
    // ─────────────────────────────────────────────────────────────────

    private fun handleAmharicKeyPress(keyChar: Char) {
        if (amharicEngine.isDirectCommit(keyChar)) {
            currentInputConnection?.commitText(keyChar.toString(), 1)
            return
        }
        val base = amharicEngine.getBaseForKey(keyChar)
        if (base != null) {
            if (amharicEngine.hasPendingBase() &&
                amharicEngine.getBaseChar() != base
            ) {
                currentInputConnection?.commitText(
                    amharicEngine.getBaseChar().toString(), 1
                )
            }
            amharicEngine.setPendingBase(base)
            updateCandidatesView()
        } else {
            currentInputConnection?.commitText(keyChar.toString(), 1)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Cursor
    // ─────────────────────────────────────────────────────────────────

    private fun sendArrow(keyCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   keyCode))
    }

    // ─────────────────────────────────────────────────────────────────
    // Mode switching
    // ─────────────────────────────────────────────────────────────────

    fun switchMode(mode: KeyboardMode) {
        stateManager.currentMode = mode
        amharicEngine.clearPendingBase()
        emojiPanelActive = false
        refreshKeyboardLayout()
        updateCandidatesView()
    }

    fun refreshKeyboardLayout() {
        val root = keyboardRootView ?: return
        attachKeyboardPanelForCurrentMode(root)
    }

    private fun attachKeyboardPanelForCurrentMode(root: FrameLayout) {
        root.removeAllViews()
        val inflater  = LayoutInflater.from(this)

        val layoutRes = when (stateManager.currentMode) {
            KeyboardMode.ENGLISH -> R.layout.keyboard_main
            KeyboardMode.AMHARIC -> R.layout.keyboard_amharic
            KeyboardMode.CODING  -> R.layout.keyboard_coding
        }

        val panel = inflater.inflate(layoutRes, root, false)

        // Wire resize handle if present in this layout
        val resizeHandle = panel.findViewById<ResizeHandle>(R.id.resize_handle)
        resizeHandle?.onResizeComplete = { refreshKeyboardLayout() }

        // Wire all key buttons
        KeyboardViewBinder.bind(panel, stateManager, this)

        // Apply one-handed mode and height scaling
        val wrappedPanel = KeyboardLayoutManager.applyLayout(this, panel)

        root.addView(wrappedPanel)
    }

    // ─────────────────────────────────────────────────────────────────
    // Emoji panel
    // ─────────────────────────────────────────────────────────────────

    fun toggleEmojiPanel() {
        val root = keyboardRootView ?: return
        emojiPanelActive = !emojiPanelActive

        if (emojiPanelActive) {
            root.removeAllViews()
            val fragment = EmojiPanelFragment().apply {
                onEmojiSelected = { emoji ->
                    currentInputConnection?.commitText(emoji, 1)
                    performHaptic()
                }
            }
            val view = fragment.onCreateView(LayoutInflater.from(this), root, false)
            if (view != null) root.addView(view)
        } else {
            refreshKeyboardLayout()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // OCR
    // ─────────────────────────────────────────────────────────────────

    fun launchOCRPicker() {
        val intent = Intent(
            Intent.ACTION_PICK,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    fun processOCRResult(uri: android.net.Uri) {
        serviceScope.launch {
            try {
                val text = ocrManager.recognizeFromUri(uri)
                if (text.isNotBlank()) {
                    currentInputConnection?.commitText(text, 1)
                }
            } catch (e: Exception) {
                // Never crash the keyboard
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Haptic
    // ─────────────────────────────────────────────────────────────────

    fun performHaptic() {
        val prefs = android.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("pref_haptic", true)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(HAPTIC_MS)
            }
        } catch (e: Exception) { }
    }

    // ─────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────

    companion object {
        const val KEY_CHARACTER         = 1000
        const val KEY_SWITCH_TO_ENGLISH = 1001
        const val KEY_SWITCH_TO_AMHARIC = 1002
        const val KEY_SWITCH_TO_CODING  = 1003
        const val KEY_SELECT_ALL        = 1004
        const val KEY_COPY              = 1005
        const val KEY_PASTE             = 1006
        const val KEY_CUT               = 1007
        const val KEY_EMOJI             = 1008
    }
}