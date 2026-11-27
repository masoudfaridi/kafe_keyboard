package tribixbite.cleverkeys.kafe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import tribixbite.cleverkeys.CleverKeysService
import tribixbite.cleverkeys.customization.ActionType
import tribixbite.cleverkeys.customization.IntentDefinition
import tribixbite.cleverkeys.customization.IntentTargetType
import tribixbite.cleverkeys.customization.ShortSwipeCustomizationManager
import tribixbite.cleverkeys.customization.ShortSwipeMapping
import tribixbite.cleverkeys.customization.SwipeDirection
import java.lang.ref.WeakReference

/**
 * Kafe Keyboard: Ctrl's down slot (opposite its 123+) hides or shows the strip above the keys
 * (the edit toolbar and the word suggestions together), with a short message.
 *
 * Like [KafeSpaceLanguageKeys], the slot is a CleverKeys Short Swipe mapping (INTENT broadcast
 * to this app), so it appears in Ctrl's hold popover and the user can move or remove it. The
 * strip is CleverKeys' `topPane`, the parent of the suggestion bar's scroll view; it is hidden
 * only while it holds that scroll view, so the clipboard and emoji panes, which CleverKeys
 * swaps into the same pane, still open. The choice is kept across restarts.
 *
 * No CleverKeys source file is modified: [attach] is called from [KafeEditToolbar], which
 * already receives the suggestion bar.
 */
object KafeSuggestionBarToggle {
    private const val TAG = "KafeBarToggle"
    private const val CTRL = "ctrl"
    private const val LABEL = "نوار"
    private const val PREFS = "kafe_bundled_packs"
    private const val KEY_INSTALLED = "ctrl_bar_toggle_v1"
    private const val KEY_HIDDEN = "suggestion_strip_hidden"

    @Volatile private var registered = false
    private var hidden = false
    private var bar = WeakReference<View>(null)
    private var topPane = WeakReference<FrameLayout>(null)
    private val main = Handler(Looper.getMainLooper())
    private const val MESSAGE_MS = 1500L
    private var messageShowing = false
    private val endMessage = Runnable { messageShowing = false; apply() }

    private fun action(context: Context) = "${context.packageName}.kafe.TOGGLE_SUGGESTION_STRIP"

    /** Registers the receiver and, once per install, the Ctrl mapping. Call from the service. */
    fun setUp(context: Context) {
        val app = context.applicationContext
        if (!registered) {
            registered = true
            hidden = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDDEN, false)
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(action(app)), ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        Thread({ installMapping(app) }, "kafe-bar-toggle").start()
    }

    /** Follows the current suggestion bar (rebuilt on theme changes). Main thread. */
    fun attach(suggestionBar: View) {
        bar = WeakReference(suggestionBar)
        val pane = (suggestionBar.parent as? View)?.parent as? FrameLayout ?: return
        if (topPane.get() !== pane) {
            topPane = WeakReference(pane)
            pane.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                // CleverKeys swaps the clipboard/emoji pane in and out of this pane.
                override fun onChildViewAdded(parent: View?, child: View?) { main.post { apply() } }
                override fun onChildViewRemoved(parent: View?, child: View?) { main.post { apply() } }
            })
        }
        apply()
    }

    private fun apply() {
        if (messageShowing) return // the strip hides after its "off" message
        val pane = topPane.get() ?: return
        val scroll = bar.get()?.parent as? View
        val showsSuggestions = scroll != null && scroll.parent === pane
        val wanted = if (hidden && showsSuggestions) View.GONE else View.VISIBLE
        if (pane.visibility != wanted) pane.visibility = wanted
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            hidden = !hidden
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_HIDDEN, hidden).apply()
            val text = if (hidden) t(context,
                "نوار ابزار و پیشنهاد کلمات غیرفعال شد", "Toolbar and suggestions off",
                "Araç çubuğu ve öneriler kapalı", "تم إيقاف شريط الأدوات والاقتراحات",
                "تووڵبار و پێشنیارەکان ناچالاک کران")
            else t(context,
                "نوار ابزار و پیشنهاد کلمات فعال شد", "Toolbar and suggestions on",
                "Araç çubuğu ve öneriler açık", "تم تفعيل شريط الأدوات والاقتراحات",
                "تووڵبار و پێشنیارەکان چالاک کران")
            // A Toast draws under the keyboard window (and is blocked when the app's
            // notifications are off), so the message goes in the strip itself: "on" after the
            // strip comes back, "off" before it goes.
            val service = CleverKeysService.getInstance()
            if (service == null) {
                apply()
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            } else if (hidden) {
                messageShowing = true
                topPane.get()?.visibility = View.VISIBLE
                service.showSuggestionBarMessage(text, MESSAGE_MS)
                main.removeCallbacks(endMessage)
                main.postDelayed(endMessage, MESSAGE_MS)
            } else {
                main.removeCallbacks(endMessage)
                messageShowing = false
                apply()
                service.showSuggestionBarMessage(text, MESSAGE_MS)
            }
            Log.i(TAG, "Suggestion strip ${if (hidden) "hidden" else "shown"}")
        }
    }

    private fun t(context: Context, fa: String, en: String, tr: String, ar: String, ckb: String): String =
        when (KafeLanguage.current(context)) {
            KafeLanguage.PERSIAN -> fa
            KafeLanguage.TURKISH -> tr
            KafeLanguage.ARABIC -> ar
            KafeLanguage.KURDISH -> ckb
            else -> en
        }

    private fun installMapping(context: Context) {
        try {
            val flags = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (flags.getBoolean(KEY_INSTALLED, false)) return
            val manager = ShortSwipeCustomizationManager.getInstance(context)
            val ours = action(context)
            runBlocking {
                manager.loadMappings()
                val existing = manager.getMapping(CTRL, SwipeDirection.S)
                if (existing == null || (existing.actionType == ActionType.INTENT && existing.actionValue.contains(ours))) {
                    manager.setMapping(
                        ShortSwipeMapping(
                            keyCode = CTRL,
                            direction = SwipeDirection.S,
                            displayText = LABEL,
                            actionType = ActionType.INTENT,
                            actionValue = com.google.gson.Gson().toJson(
                                IntentDefinition(
                                    name = "Kafe: toggle suggestion strip",
                                    targetType = IntentTargetType.BROADCAST,
                                    action = ours,
                                    packageName = context.packageName,
                                )
                            ),
                        )
                    )
                }
            }
            flags.edit().putBoolean(KEY_INSTALLED, true).apply()
            Log.i(TAG, "Ctrl down = toggle suggestion strip")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Storage not available yet, will retry", e) // direct boot
        } catch (e: Exception) {
            Log.e(TAG, "Installing the Ctrl strip toggle failed", e)
        }
    }
}
