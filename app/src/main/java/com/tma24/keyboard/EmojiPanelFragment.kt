package com.tma24.keyboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * EmojiPanelFragment
 *
 * A self-contained emoji picker shown when the user taps the emoji button.
 * Organised into category tabs: Smileys, People, Animals, Food,
 * Travel, Objects, Symbols, Flags.
 *
 * All emoji are Unicode — no assets, no internet.
 * Tapping an emoji commits it via the InputConnection.
 *
 * Integration: TMA24InputMethodService shows this view by replacing
 * the keyboard panel FrameLayout with this fragment's view.
 */
class EmojiPanelFragment : Fragment() {

    // Callback to commit emoji to the input field
    var onEmojiSelected: ((String) -> Unit)? = null

    private var currentCategoryIndex = 0

    // ── Emoji data ─────────────────────────────────────────────────────

    data class EmojiCategory(val label: String, val emojis: List<String>)

    private val categories = listOf(
        EmojiCategory("😀", listOf(
            "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊",
            "😋","😎","😍","🥰","😘","😗","😙","😚","🙂","🤗",
            "🤩","🤔","🤨","😐","😑","😶","🙄","😏","😣","😥",
            "😮","🤐","😯","😪","😫","🥱","😴","😌","😛","😜",
            "😝","🤤","😒","😓","😔","😕","🙃","🤑","😲","☹️",
            "🙁","😖","😞","😟","😤","😢","😭","😦","😧","😨",
            "😩","🤯","😬","😰","😱","🥵","🥶","😳","🤪","😵",
            "🥴","😠","😡","🤬","😷","🤒","🤕","🤧","🥳","🥺"
        )),
        EmojiCategory("👋", listOf(
            "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞",
            "🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍",
            "👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝",
            "🙏","✍️","💅","🤳","💪","🦾","🦵","🦶","👂","🦻",
            "👃","🧠","🦷","🦴","👀","👁️","👅","👄","💋","👶",
            "🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴",
            "👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦",
            "🤷","👮","🕵️","💂","🥷","👷","🫅","🤴","👸","👳"
        )),
        EmojiCategory("🐶", listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
            "🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧",
            "🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄",
            "🐝","🐛","🦋","🐌","🐞","🐜","🦟","🦗","🦂","🐢",
            "🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡",
            "🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆","🦓",
            "🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘",
            "🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐"
        )),
        EmojiCategory("🍎", listOf(
            "🍎","🍊","🍋","🍇","🍓","🫐","🍈","🍒","🍑","🥭",
            "🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶️",
            "🫑","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨",
            "🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖",
            "🌭","🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯",
            "🫔","🥗","🥘","🫕","🥫","🍱","🍘","🍙","🍚","🍛",
            "🍜","🍝","🍠","🍢","🍣","🍤","🍥","🥮","🍡","🥟",
            "🥠","🥡","🍦","🍧","🍨","🍩","🍪","🎂","🍰","🧁"
        )),
        EmojiCategory("✈️", listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐",
            "🛻","🚚","🚛","🚜","🏍️","🛵","🛺","🚲","🛴","🛹",
            "🚁","🛸","✈️","🛩️","🚀","🛶","⛵","🚤","🛥️","🛳️",
            "⛴️","🚂","🚃","🚄","🚅","🚆","🚇","🚈","🚉","🚊",
            "🚝","🚞","🚋","🚌","🚍","🚎","🚐","🚑","🚒","⛽",
            "🛞","🚨","🚥","🚦","🛑","🚧","⚓","🪝","⛵","🗺️",
            "🧭","⛰️","🌋","🏔️","🗻","🏕️","🏖️","🏜️","🏝️","🏞️",
            "🏟️","🏛️","🏗️","🏘️","🏚️","🏠","🏡","🏢","🏣","🏤"
        )),
        EmojiCategory("💡", listOf(
            "⌚","📱","💻","⌨️","🖥️","🖨️","🖱️","🖲️","💽","💾",
            "💿","📀","📷","📸","📹","🎥","📽️","🎞️","📞","☎️",
            "📟","📠","📺","📻","🎙️","🎚️","🎛️","🧭","⏱️","⏲️",
            "⏰","🕰️","⌛","⏳","📡","🔋","🪫","🔌","💡","🔦",
            "🕯️","🪔","🧯","🛢️","💰","💴","💵","💶","💷","💸",
            "💳","🪙","💹","✉️","📧","📨","📩","📪","📫","📬",
            "📭","📮","🗳️","✏️","✒️","🖋️","🖊️","📝","📁","📂",
            "🗂️","📅","📆","🗒️","🗓️","📇","📈","📉","📊","📋"
        )),
        EmojiCategory("❤️", listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝",
            "💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯","🕎","☯️",
            "☦️","🛐","⛎","♈","♉","♊","♋","♌","♍","♎",
            "♏","♐","♑","♒","♓","🆔","⚛️","🉑","☢️","☣️",
            "📴","📳","🈶","🈚","🈸","🈺","🈷️","✴️","🆚","💮",
            "🉐","㊙️","㊗️","🈴","🈵","🈹","🈲","🅰️","🅱️","🆎",
            "🆑","🅾️","🆘","❌","⭕","🛑","⛔","📛","🚫","💯"
        )),
        EmojiCategory("🏳️", listOf(
            "🏳️","🏴","🏁","🚩","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️",
            "🇦🇫","🇦🇱","🇩🇿","🇦🇩","🇦🇴","🇦🇷","🇦🇲","🇦🇺",
            "🇦🇹","🇦🇿","🇧🇸","🇧🇭","🇧🇩","🇧🇧","🇧🇾","🇧🇪",
            "🇧🇷","🇧🇬","🇰🇭","🇨🇦","🇨🇳","🇨🇴","🇨🇷","🇨🇺",
            "🇨🇾","🇨🇿","🇩🇰","🇩🇴","🇪🇨","🇪🇬","🇸🇻","🇪🇪",
            "🇪🇹","🇫🇮","🇫🇷","🇩🇪","🇬🇭","🇬🇷","🇬🇹","🇭🇳",
            "🇭🇺","🇮🇳","🇮🇩","🇮🇷","🇮🇶","🇮🇪","🇮🇱","🇮🇹",
            "🇯🇲","🇯🇵","🇯🇴","🇰🇿","🇰🇪","🇰🇷","🇰🇼","🇱🇧"
        ))
    )

    // ── View building ──────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#F5F0E8"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // ── Category tab row ─────────────────────────────────────────
        val tabRow = buildCategoryTabRow(root)
        root.addView(tabRow)

        // ── Emoji grid ───────────────────────────────────────────────
        val gridContainer = FrameLayoutCompat(context)
        root.addView(
            gridContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(220)
            )
        )

        // Show first category immediately
        showCategory(gridContainer, 0)

        return root
    }

    private fun buildCategoryTabRow(parent: LinearLayout): android.widget.HorizontalScrollView {
        val context = parent.context

        val scrollView = android.widget.HorizontalScrollView(context).apply {
            scrollbars = View.SCROLLBARS_INSIDE_OVERLAY
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(android.graphics.Color.parseColor("#E8E0D0"))
        }

        val tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 4, 4, 4)
        }

        categories.forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.label
                textSize = 22f
                setPadding(12, 8, 12, 8)
                setOnClickListener {
                    currentCategoryIndex = index
                    val gridContainer = (parent.parent as? LinearLayout)
                        ?.getChildAt(1) as? android.widget.FrameLayout
                    if (gridContainer != null) {
                        showCategory(gridContainer, index)
                    }
                }
            }
            tabContainer.addView(tab)
        }

        scrollView.addView(tabContainer)
        return scrollView
    }

    private fun showCategory(container: android.widget.FrameLayout, categoryIndex: Int) {
        container.removeAllViews()
        val context = container.context
        val emojis = categories[categoryIndex].emojis

        val scrollView = android.widget.ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val grid = buildEmojiGrid(context, emojis)
        scrollView.addView(grid)
        container.addView(scrollView)
    }

    private fun buildEmojiGrid(
        context: android.content.Context,
        emojis: List<String>
    ): android.widget.GridLayout {
        val columnCount = 8

        return android.widget.GridLayout(context).apply {
            this.columnCount = columnCount
            setPadding(4, 4, 4, 4)

            emojis.forEachIndexed { index, emoji ->
                val cell = TextView(context).apply {
                    text = emoji
                    textSize = 26f
                    gravity = android.view.Gravity.CENTER
                    setPadding(6, 6, 6, 6)
                    setOnClickListener {
                        onEmojiSelected?.invoke(emoji)
                    }
                }

                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = dpToPx(40)
                    height = dpToPx(44)
                    columnSpec = android.widget.GridLayout.spec(
                        index % columnCount,
                        android.widget.GridLayout.FILL,
                        1f
                    )
                }
                addView(cell, params)
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // Alias to avoid import conflict with our ClipboardManager
    private class FrameLayoutCompat(context: android.content.Context) :
        android.widget.FrameLayout(context)
}