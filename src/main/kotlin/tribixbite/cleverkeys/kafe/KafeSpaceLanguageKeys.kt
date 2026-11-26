package tribixbite.cleverkeys.kafe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import tribixbite.cleverkeys.CleverKeysService
import tribixbite.cleverkeys.DirectBootAwarePreferences
import tribixbite.cleverkeys.customization.ActionType
import tribixbite.cleverkeys.customization.IntentDefinition
import tribixbite.cleverkeys.customization.IntentTargetType
import tribixbite.cleverkeys.customization.ShortSwipeCustomizationManager
import tribixbite.cleverkeys.customization.ShortSwipeMapping
import tribixbite.cleverkeys.customization.SwipeDirection
import tribixbite.cleverkeys.prefs.ExtraKeysPreference
import tribixbite.cleverkeys.prefs.LayoutsPreference
import tribixbite.cleverkeys.prefs.ListGroupPreference

/**
 * Kafe Keyboard: one reserved spacebar slot per language, in the space key's customizable
 * slots (left and right stay the cursor slider, the top-left corner stays free):
 *
 *   up = English, down = فارسی, up-right = کوردی, down-left = العربية, down-right = Türkçe
 *
 * Built on CleverKeys' own Short Swipe customization: each slot of the space key gets an
 * INTENT mapping that broadcasts to this app; [receiver] then selects that language's layout
 * with [CleverKeysService.setTextLayout]. The user can still change or remove any of them in
 * Short Swipe Customization, and a slot the user mapped themselves is never overwritten.
 *
 * Up and down carry CleverKeys' next/previous-layout gestures by default; those are the
 * `switch_forward` / `switch_backward` extra keys, so they are switched off once (through the
 * same preference as the Extra Keys screen) to free the two slots. No CleverKeys source file
 * is modified.
 *
 * A language that is not ticked on the launcher has no layout in the list, so its swipe
 * does nothing.
 */
object KafeSpaceLanguageKeys {
    private const val TAG = "KafeSpaceKeys"
    private const val SPACE = " "
    private const val EXTRA_LANG = "lang"
    private const val ENGLISH = "en"
    private const val PREFS = "kafe_bundled_packs"
    /** v3: full language names as labels (v2: the corrected slots; v1 never shipped). */
    private const val KEY_INSTALLED = "space_language_keys_v3"

    private data class Slot(val direction: SwipeDirection, val lang: String, val label: String)

    private val SLOTS = listOf(
        Slot(SwipeDirection.N, ENGLISH, "EN"),
        Slot(SwipeDirection.S, KafeKeyboardLanguages.Language.PERSIAN.code, KafeKeyboardLanguages.Language.PERSIAN.faName),
        Slot(SwipeDirection.NE, KafeKeyboardLanguages.Language.KURDISH.code, KafeKeyboardLanguages.Language.KURDISH.faName),
        Slot(SwipeDirection.SW, KafeKeyboardLanguages.Language.ARABIC.code, KafeKeyboardLanguages.Language.ARABIC.faName),
        Slot(SwipeDirection.SE, KafeKeyboardLanguages.Language.TURKISH.code, KafeKeyboardLanguages.Language.TURKISH.faName),
    )

    /** CleverKeys' next/previous-layout gestures on space up/down (Extra Keys entries). */
    private val LAYOUT_SWITCH_EXTRA_KEYS = listOf("switch_forward", "switch_backward")

    @Volatile private var registered = false

    private fun action(context: Context) = "${context.packageName}.kafe.SELECT_LANGUAGE"

    /**
     * Registers the receiver (once per process) and, once per install, adds the spacebar
     * mappings on a background thread. Call from the keyboard service.
     */
    fun setUp(context: Context) {
        val app = context.applicationContext
        if (!registered) {
            registered = true
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(action(app)), ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        Thread({ installMappings(app) }, "kafe-space-keys").start()
    }

    private fun installMappings(context: Context) {
        try {
            val flags = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (flags.getBoolean(KEY_INSTALLED, false)) return
            // Free space up/down: turn CleverKeys' layout-switch extra keys off, unless the
            // user already chose a value for them in Extra Keys.
            val prefs = DirectBootAwarePreferences.get_shared_preferences(context)
            val edit = prefs.edit()
            for (name in LAYOUT_SWITCH_EXTRA_KEYS) {
                val key = ExtraKeysPreference.prefKeyOfKeyName(name)
                if (!prefs.contains(key)) edit.putBoolean(key, false)
            }
            edit.apply()

            val manager = ShortSwipeCustomizationManager.getInstance(context)
            val ours = action(context)
            runBlocking {
                manager.loadMappings()
                // Drop Kafe's own mappings outside the current slots (e.g. v1's left/right).
                val wanted = SLOTS.map { it.direction }.toSet()
                for ((direction, mapping) in manager.getMappingsForKey(SPACE).toMap()) {
                    if (direction !in wanted && mapping.isKafe(ours)) manager.removeMapping(SPACE, direction)
                }
                for (slot in SLOTS) {
                    val existing = manager.getMapping(SPACE, slot.direction)
                    if (existing != null && !existing.isKafe(ours)) continue // the user's own
                    manager.setMapping(
                        ShortSwipeMapping(
                            keyCode = SPACE,
                            direction = slot.direction,
                            displayText = slot.label,
                            actionType = ActionType.INTENT,
                            actionValue = intentJson(context, slot.lang),
                        )
                    )
                }
            }
            flags.edit().putBoolean(KEY_INSTALLED, true).apply()
            Log.i(TAG, "Spacebar language swipes installed")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Storage not available yet, will retry", e) // direct boot
        } catch (e: Exception) {
            Log.e(TAG, "Installing spacebar language swipes failed", e)
        }
    }

    private fun ShortSwipeMapping.isKafe(action: String): Boolean =
        actionType == ActionType.INTENT && actionValue.contains(action)

    private fun intentJson(context: Context, lang: String): String =
        com.google.gson.Gson().toJson(
            IntentDefinition(
                name = "Kafe: $lang",
                targetType = IntentTargetType.BROADCAST,
                action = action(context),
                packageName = context.packageName,
                extras = mapOf(EXTRA_LANG to lang),
            )
        )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val lang = intent.getStringExtra(EXTRA_LANG) ?: return
            val index = layoutIndexFor(context, lang)
            if (index < 0) {
                Log.i(TAG, "No enabled layout for '$lang'")
                return
            }
            val loaded = tribixbite.cleverkeys.Config.globalConfigOrNull()?.layouts?.size
            Log.i(TAG, "Select '$lang': layout index $index of $loaded")
            CleverKeysService.getInstance()?.setTextLayout(index)
        }
    }

    /**
     * Position of [lang]'s layout in the layout list, which is also the index the keyboard
     * uses (Config builds its layouts from this list in order). English is the first entry
     * that is not one of the Kafe languages (the system layout or a Latin layout).
     */
    private fun layoutIndexFor(context: Context, lang: String): Int {
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context)
        val layouts = ListGroupPreference.loadFromPreferences(
            LayoutsPreference.KEY, prefs, LayoutsPreference.DEFAULT, LayoutsPreference.SERIALIZER
        ) ?: LayoutsPreference.DEFAULT
        val kafeLayouts = KafeKeyboardLanguages.Language.values().map { it.layout }.toSet()
        if (lang == ENGLISH) {
            return layouts.indexOfFirst {
                (it as? LayoutsPreference.NamedLayout)?.name !in kafeLayouts
            }
        }
        val target = KafeKeyboardLanguages.Language.values().firstOrNull { it.code == lang }?.layout
            ?: return -1
        return layouts.indexOfFirst { (it as? LayoutsPreference.NamedLayout)?.name == target }
    }
}
