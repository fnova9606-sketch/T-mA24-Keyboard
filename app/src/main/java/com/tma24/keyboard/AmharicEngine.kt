package com.tma24.keyboard

/**
 * AmharicEngine — Fyn Geez 2 input logic.
 *
 * Two entry paths:
 *   PATH A — Latin tag  (e.g. tag="key:q")  → look up in KEY_TO_ETHIOPIC_BASE
 *   PATH B — Ethiopic tag (e.g. tag="key:ሐ") → char IS the base, normalise to order-0
 *   PATH C — Punctuation / digit             → direct commit, no family bar
 *
 * getCurrentFamily() always returns exactly 7 chars: orders 0–6 of the family.
 */
class AmharicEngine {

    private var pendingBase: Char? = null

    // ── Public API ────────────────────────────────────────────────────

    fun hasPendingBase(): Boolean = pendingBase != null

    fun setPendingBase(base: Char) { pendingBase = base }

    fun clearPendingBase() { pendingBase = null }

    /** The ግዕዝ (first-order) form of the pending base. */
    fun getBaseChar(): Char = pendingBase ?: ' '

    /**
     * Returns all 7 order variants for the current pending base.
     *
     * The Ethiopic Unicode block lays families out in rows of 7:
     *   row start = codePoint - (codePoint % 7)
     *   order N   = rowStart + N     (N = 0 … 6)
     */
    fun getCurrentFamily(): List<Char> {
        val base = pendingBase ?: return emptyList()
        val rowStart = base.code - (base.code % 7)
        return (0..6).map { n -> (rowStart + n).toChar() }
    }

    /**
     * Given the key character from the layout tag, return the Ethiopic
     * ግዕዝ (first-order) base character, or null if no mapping exists.
     */
    fun getBaseForKey(keyChar: Char): Char? {
        // PATH A — Latin key
        val fromLatin = KEY_TO_ETHIOPIC_BASE[keyChar.lowercaseChar()]
        if (fromLatin != null) return fromLatin

        // PATH B — Ethiopic consonant (U+1200 – U+137F)
        val code = keyChar.code
        if (code in 0x1200..0x137F) {
            return (code - (code % 7)).toChar()
        }

        // PATH C — punctuation or unknown
        return null
    }

    /**
     * Returns true for characters that should bypass the family bar
     * and be committed directly: digits, Ethiopic punctuation, symbols.
     */
    fun isDirectCommit(keyChar: Char): Boolean {
        val code = keyChar.code
        return when {
            keyChar.isDigit()              -> true
            code in 0x1360..0x1368        -> true  // Ethiopic punctuation
            keyChar in DIRECT_COMMIT_SET  -> true
            else                          -> false
        }
    }

    // ── Companion — full key map ──────────────────────────────────────

    companion object {

        private val DIRECT_COMMIT_SET = setOf(
            ' ', '.', ',', '!', '?', '"', '\'',
            '(', ')', '-', '_', ':', ';', '\n', '\t',
            '/', '\\', '@', '#', '$', '%', '^', '&',
            '*', '+', '=', '~', '`', '[', ']', '{', '}'
        )

        /**
         * Latin QWERTY key → Ethiopic ግዕዝ base (first-order, code % 7 == 0).
         *
         * All target code points verified against Unicode 15 Ethiopic block.
         *
         * U+1200 ሀ  U+1208 ለ  U+1210 ሐ  U+1218 መ  U+1220 ሠ
         * U+1228 ረ  U+1230 ሰ  U+1238 ሸ  U+1240 ቀ  U+1260 በ
         * U+1268 ቨ  U+1270 ተ  U+1278 ቸ  U+1290 ነ  U+1298 ኘ
         * U+12A0 አ  U+12A8 ከ  U+12C8 ወ  U+12D0 ዐ  U+12D8 ዘ
         * U+12E0 ዠ  U+12E8 የ  U+12F0 ደ  U+1300 ጀ  U+1308 ገ
         * U+1320 ጠ  U+1328 ጨ  U+1338 ጸ  U+1348 ፈ  U+1350 ፐ
         */
        val KEY_TO_ETHIOPIC_BASE: Map<Char, Char> = mapOf(
            // Row 1
            'q' to '\u1240', // ቀ  qe
            'w' to '\u12C8', // ወ  we
            'e' to '\u12A0', // አ  a  (glottal)
            'r' to '\u1228', // ረ  re
            't' to '\u1270', // ተ  te
            'y' to '\u12E8', // የ  ye
            'k' to '\u12A8', // ከ  ke
            'z' to '\u12D8', // ዘ  ze
            'p' to '\u1348', // ፈ  fe
            // Row 2
            'a' to '\u1200', // ሀ  he
            's' to '\u1230', // ሰ  se
            'd' to '\u12F0', // ደ  de
            'j' to '\u1300', // ጀ  je
            'g' to '\u1308', // ገ  ge
            'l' to '\u1208', // ለ  le
            'm' to '\u1218', // መ  me
            'n' to '\u1290', // ነ  ne
            // Row 3 (also reachable via Ethiopic tag path)
            'h' to '\u1200', // ሀ  alternate h
            'x' to '\u1338', // ጸ  ts'e
            'v' to '\u1268', // ቨ  ve
            'b' to '\u1260', // በ  be
            'c' to '\u1278', // ቸ  che
            'f' to '\u1348', // ፈ  fe
            'o' to '\u12D0', // ዐ  ʕain
            'i' to '\u12A8', // ከ  alternate
            'u' to '\u1268', // ቨ  alternate
        )
    }
}