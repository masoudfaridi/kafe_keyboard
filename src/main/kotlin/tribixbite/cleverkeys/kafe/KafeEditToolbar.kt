package tribixbite.cleverkeys.kafe

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import tribixbite.cleverkeys.CleverKeysService
import java.util.Locale
import java.util.WeakHashMap

/**
 * Kafe Keyboard: edit toolbar inside the suggestion bar.
 *
 * Order: [toggle] Select All, Copy, ◀, ▶, Cut, Paste. The round toggle is shown in both modes
 * (first in the suggestion row, first in the toolbar) and switches between them. The toolbar
 * also shows by itself whenever there are no suggestions. The arrows repeat while held.
 *
 * Edits go straight to the editor's InputConnection, so this does not depend on CleverKeys'
 * internal key handling. The only upstream touch points are two calls in
 * SuggestionBar.rebindSuggestionViews().
 */
object KafeEditToolbar {
    private const val REPEAT_DELAY_MS = 400L
    private const val REPEAT_INTERVAL_MS = 50L
    private const val TEXT_SP = 15f
    private const val TOGGLE_SLOT_DP = 44
    private const val TOGGLE_CIRCLE_DP = 34

    private enum class Action { SELECT_ALL, COPY, LEFT, RIGHT, CUT, PASTE }

    /** Per-bar state: views are created once and reused on every rebind. */
    private class BarState(
        val toggle: FrameLayout,
        val toggleLabel: TextView,
        val buttons: List<TextView>,
        var toolbarMode: Boolean = false,
        var rerender: () -> Unit = {},
    )

    private val states = WeakHashMap<LinearLayout, BarState>()
    private val handler = Handler(Looper.getMainLooper())

    /** True when [bar] should show the toolbar instead of its [suggestionCount] suggestions. */
    fun showsToolbar(bar: LinearLayout, suggestionCount: Int): Boolean =
        suggestionCount == 0 || states[bar]?.toolbarMode == true

    /** Children for toolbar mode: the toggle, then the six edit buttons sharing the rest. */
    fun toolbarViews(bar: LinearLayout, textColor: Int, rerender: () -> Unit): List<View> {
        val s = stateFor(bar, rerender)
        style(s, textColor, toolbar = true)
        val toggleWidth = dp(bar, TOGGLE_SLOT_DP)
        val visibleWidth = (bar.parent as? View)?.width?.takeIf { it > 0 }
            ?: bar.resources.displayMetrics.widthPixels
        val each = ((visibleWidth - bar.paddingLeft - bar.paddingRight - toggleWidth) / s.buttons.size)
            .coerceAtLeast(1)
        for (b in s.buttons) setWidth(b, each)
        return listOf<View>(s.toggle) + s.buttons
    }

    /** The toggle to put first in suggestion mode. */
    fun toggleView(bar: LinearLayout, textColor: Int, rerender: () -> Unit): View {
        val s = stateFor(bar, rerender)
        style(s, textColor, toolbar = false)
        return s.toggle
    }

    private fun stateFor(bar: LinearLayout, rerender: () -> Unit): BarState {
        val s = states.getOrPut(bar) {
            val (toggle, label) = createToggle(bar)
            BarState(toggle, label, Action.values().map { createButton(bar, it) })
        }
        s.rerender = rerender
        return s
    }

    private fun style(s: BarState, textColor: Int, toolbar: Boolean) {
        s.toggleLabel.text = if (toolbar) "Aa" else "✎"
        s.toggleLabel.contentDescription =
            if (toolbar) t("پیشنهاد کلمات", "Word suggestions") else t("ابزار ویرایش", "Edit tools")
        s.toggleLabel.setTextColor(textColor)
        (s.toggleLabel.background as? GradientDrawable)?.setStroke(dp(s.toggleLabel, 1), textColor)
        s.buttons.forEach { it.setTextColor(textColor) }
    }

    private fun createToggle(bar: LinearLayout): Pair<FrameLayout, TextView> {
        val label = TextView(bar.context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
            }
            val size = dp(bar, TOGGLE_CIRCLE_DP)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        }
        val slot = FrameLayout(bar.context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(bar, TOGGLE_SLOT_DP), ViewGroup.LayoutParams.MATCH_PARENT)
            isClickable = true
            addView(label)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val s = states[bar] ?: return@setOnClickListener
                s.toolbarMode = !s.toolbarMode
                s.rerender()
            }
        }
        return slot to label
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton(bar: LinearLayout, action: Action): TextView =
        TextView(bar.context).apply {
            text = label(action)
            contentDescription = description(action)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            maxLines = 1
            isClickable = true
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (action == Action.LEFT || action == Action.RIGHT) {
                setOnTouchListener(RepeatTouchListener(action))
            } else {
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    perform(action)
                }
            }
        }

    private fun setWidth(v: View, width: Int) {
        val lp = v.layoutParams as? LinearLayout.LayoutParams
        if (lp == null || lp.width != width) {
            v.layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun perform(action: Action) {
        val ic = CleverKeysService.getInstance()?.currentInputConnection ?: return
        when (action) {
            Action.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            Action.COPY -> ic.performContextMenuAction(android.R.id.copy)
            Action.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            Action.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            Action.CUT -> ic.performContextMenuAction(android.R.id.cut)
            Action.PASTE -> ic.performContextMenuAction(android.R.id.paste)
        }
    }

    private fun sendKey(ic: InputConnection, code: Int) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Arrow buttons: one step on press, then repeat while held. */
    private class RepeatTouchListener(private val action: Action) : View.OnTouchListener {
        private val repeat = object : Runnable {
            override fun run() {
                perform(action)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    perform(action)
                    handler.postDelayed(repeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            return true
        }
    }

    private fun dp(v: View, value: Int): Int =
        (value * v.resources.displayMetrics.density + 0.5f).toInt()

    private fun t(fa: String, en: String): String =
        if (Locale.getDefault().language == "fa") fa else en

    private fun label(action: Action): String = when (action) {
        Action.SELECT_ALL -> t("همه", "All")
        Action.COPY -> t("کپی", "Copy")
        Action.LEFT -> "◀"
        Action.RIGHT -> "▶"
        Action.CUT -> t("برش", "Cut")
        Action.PASTE -> t("چسباندن", "Paste")
    }

    private fun description(action: Action): String = when (action) {
        Action.SELECT_ALL -> t("انتخاب همه", "Select all")
        Action.LEFT -> t("حرکت کرسر به چپ", "Move cursor left")
        Action.RIGHT -> t("حرکت کرسر به راست", "Move cursor right")
        else -> label(action)
    }
}
