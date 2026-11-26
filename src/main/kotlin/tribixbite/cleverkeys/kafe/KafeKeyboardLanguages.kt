package tribixbite.cleverkeys.kafe

import android.content.Context
import android.util.Log
import tribixbite.cleverkeys.DirectBootAwarePreferences
import tribixbite.cleverkeys.prefs.LayoutsPreference
import tribixbite.cleverkeys.prefs.ListGroupPreference

/**
 * Kafe Keyboard: the keyboard languages a user can tick on the launcher. English (the system
 * layout) is always on; each ticked language adds its layout to CleverKeys' layout list, so
 * the spacebar's swipe up/down cycles through them, and installs its bundled dictionary pack
 * when one ships.
 *
 * The layout list (`layouts` preference) is the single source of truth: a language is "on"
 * when its layout is in the list, so changes made in Layout Manager show up here too.
 * Nothing in CleverKeys itself is modified.
 */
object KafeKeyboardLanguages {
    private const val TAG = "KafeKbdLanguages"

    /**
     * One tickable language. [layout] is a built-in layout in srcs/layouts. [bindLanguage]
     * binds the layout entry to a language code when its XML declares none, so its dictionary
     * (or no dictionary, rather than English suggestions) follows the layout.
     */
    enum class Language(
        val code: String,
        val layout: String,
        val nativeName: String,
        val packAsset: String?,
        val packVersion: Int,
        val bindLanguage: String?,
    ) {
        PERSIAN("fa", "arab_pc_ir", "فارسی", "kafe/langpack-fa.zip", 2, null), // XML declares fa
        KURDISH("ckb", "arab_pc_ckb", "کوردی", null, 0, "ckb"),
        TURKISH("tr", "latn_qwerty_tr", "Türkçe", "kafe/langpack-tr.zip", 2, "tr"),
        ARABIC("ar", "arab_pc", "العربية", null, 0, "ar");

        /** True when a dictionary (suggestions, swipe) ships for this language. */
        val hasDictionary: Boolean get() = packAsset != null
    }

    /** Languages whose layout is currently in the layout list. */
    fun enabled(context: Context): Set<Language> {
        val names = layoutNames(context)
        return Language.values().filter { it.layout in names }.toSet()
    }

    /**
     * Turns [language] on (adds its layout, installs its pack in the background) or off
     * (removes its layout). English is never touched. [onDone] runs on a background thread.
     */
    fun setEnabled(context: Context, language: Language, on: Boolean, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        Thread({
            try {
                if (on && language.packAsset != null) {
                    BundledPersianPack.installPack(app, language.packAsset, language.code, language.packVersion)
                }
                val prefs = DirectBootAwarePreferences.get_shared_preferences(app)
                val current = load(prefs)
                val updated = if (on) {
                    if (current.any { it.nameOf() == language.layout }) current
                    else current + LayoutsPreference.NamedLayout(language.layout, language.bindLanguage)
                } else {
                    current.filterNot { it.nameOf() == language.layout }
                        .ifEmpty { LayoutsPreference.DEFAULT.take(1) }
                }
                if (updated != current) {
                    val edit = prefs.edit()
                    LayoutsPreference.saveToPreferences(edit, updated)
                    edit.apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Changing ${language.code} failed", e)
            }
            onDone()
        }, "kafe-kbd-lang").start()
    }

    private fun layoutNames(context: Context): Set<String> =
        load(DirectBootAwarePreferences.get_shared_preferences(context.applicationContext))
            .mapNotNull { it.nameOf() }.toSet()

    private fun load(prefs: android.content.SharedPreferences): List<LayoutsPreference.Layout> =
        ListGroupPreference.loadFromPreferences(
            LayoutsPreference.KEY, prefs, LayoutsPreference.DEFAULT, LayoutsPreference.SERIALIZER
        ) ?: LayoutsPreference.DEFAULT

    private fun LayoutsPreference.Layout.nameOf(): String? =
        (this as? LayoutsPreference.NamedLayout)?.name
}
