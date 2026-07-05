package com.tma24.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat

/**
 * SettingsActivity
 *
 * Serves dual purpose:
 *   1. Launcher activity — the app icon opens this screen
 *   2. IME settings target — system keyboard settings links here
 *
 * Built entirely in Kotlin (no preferences XML file needed).
 * All preference keys are read by TMA24InputMethodService at runtime.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme before inflation
        applyThemeFromPrefs()

        setContentView(R.layout.activity_settings)

        // Wire the toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(false)
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    private fun applyThemeFromPrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        when (prefs.getString("pref_theme", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
            "dark"  -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
            else    -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Settings Fragment
    // ─────────────────────────────────────────────────────────────────

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx    = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)

            // ── Setup ─────────────────────────────────────────────────

            val setupCat = PreferenceCategory(ctx).apply { title = "Keyboard Setup" }
            screen.addPreference(setupCat)

            setupCat.addPreference(Preference(ctx).apply {
                title   = "Enable T-mA24 Keyboard"
                summary = "Open system keyboard settings to enable"
                setOnPreferenceClickListener {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    true
                }
            })

            setupCat.addPreference(Preference(ctx).apply {
                title   = "Switch to T-mA24 Keyboard"
                summary = "Select T-mA24 as your active keyboard"
                setOnPreferenceClickListener {
                    val imm = requireContext()
                        .getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                    true
                }
            })

            // ── Appearance ────────────────────────────────────────────

            val appearCat = PreferenceCategory(ctx).apply { title = "Appearance" }
            screen.addPreference(appearCat)

            appearCat.addPreference(ListPreference(ctx).apply {
                key          = "pref_theme"
                title        = "Theme"
                entries      = arrayOf("Light", "Dark", "Follow System")
                entryValues  = arrayOf("light", "dark", "system")
                setDefaultValue("system")
                summary      = "%s"
                setOnPreferenceChangeListener { _, newValue ->
                    when (newValue as String) {
                        "light"  -> AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_NO)
                        "dark"   -> AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_YES)
                        else     -> AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                    true
                }
            })

            appearCat.addPreference(SeekBarPreference(ctx).apply {
                key            = "pref_height_scale"
                title          = "Keyboard Height"
                summary        = "Adjust keyboard height (50–150%)"
                min            = 50
                max            = 150
                setDefaultValue(100)
                showSeekBarValue = true
            })

            appearCat.addPreference(SwitchPreferenceCompat(ctx).apply {
                key     = "pref_one_handed"
                title   = "One-Handed Mode"
                summary = "Shrink keyboard to one side"
                setDefaultValue(false)
            })

            appearCat.addPreference(ListPreference(ctx).apply {
                key         = "pref_one_handed_side"
                title       = "One-Handed Side"
                entries     = arrayOf("Left", "Right")
                entryValues = arrayOf("0", "1")
                setDefaultValue("1")
                summary     = "%s"
                dependency  = "pref_one_handed"
            })

            // ── Typing ────────────────────────────────────────────────

            val typingCat = PreferenceCategory(ctx).apply { title = "Typing" }
            screen.addPreference(typingCat)

            typingCat.addPreference(SwitchPreferenceCompat(ctx).apply {
                key     = "pref_haptic"
                title   = "Haptic Feedback"
                summary = "Vibrate on each key press"
                setDefaultValue(true)
            })

            typingCat.addPreference(SwitchPreferenceCompat(ctx).apply {
                key     = "pref_sound"
                title   = "Key Sounds"
                summary = "Play click sound on key press"
                setDefaultValue(false)
            })

            typingCat.addPreference(SwitchPreferenceCompat(ctx).apply {
                key     = "pref_auto_caps"
                title   = "Auto-Capitalise"
                summary = "Capitalise first letter of sentences"
                setDefaultValue(true)
            })

            typingCat.addPreference(SwitchPreferenceCompat(ctx).apply {
                key     = "pref_double_space_period"
                title   = "Double-Space Period"
                summary = "Double-tap spacebar to insert  \". \""
                setDefaultValue(true)
            })

            // ── Clipboard ─────────────────────────────────────────────

            val clipCat = PreferenceCategory(ctx).apply { title = "Clipboard" }
            screen.addPreference(clipCat)

            clipCat.addPreference(Preference(ctx).apply {
                title   = "Clear Clipboard History"
                summary = "Delete all saved clipboard entries"
                setOnPreferenceClickListener {
                    ClipboardManager(requireContext()).clearAll()
                    Toast.makeText(
                        requireContext(),
                        "Clipboard history cleared",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            })

            // ── Privacy ───────────────────────────────────────────────

            val privacyCat = PreferenceCategory(ctx).apply { title = "Privacy" }
            screen.addPreference(privacyCat)

            privacyCat.addPreference(Preference(ctx).apply {
                title        = "100% Private & Offline"
                summary      = "No internet permission. Zero data leaves your " +
                               "device. No analytics. No ads. No cloud sync."
                isSelectable = false
            })

            // ── About ─────────────────────────────────────────────────

            val aboutCat = PreferenceCategory(ctx).apply { title = "About" }
            screen.addPreference(aboutCat)

            aboutCat.addPreference(Preference(ctx).apply {
                title        = "T-mA24 Keyboard"
                summary      = "Version 1.0.0 — Built for Ethiopia 🇪🇹"
                isSelectable = false
            })

            preferenceScreen = screen
        }
    }
}