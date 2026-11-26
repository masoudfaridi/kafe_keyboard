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
        /** Full Persian name: card labels in the Persian UI and the spacebar labels. */
        val faName: String,
        /** Full English name: card labels in the English UI. */
        val enName: String,
        /** Full Turkish name: card labels in the Turkish UI. */
        val trName: String,
        /** Full Arabic name: card labels in the Arabic UI. */
        val arName: String,
        val packAsset: String?,
        val packVersion: Int,
        val bindLanguage: String?,
        /** Full Kurdish (Sorani) name: card labels in the Kurdish UI. */
        val ckbName: String,
    ) {
        PERSIAN("fa", "arab_pc_ir", "فارسی", "Persian", "Farsça", "الفارسية", "kafe/langpack-fa.zip", 2, null, "فارسی"), // XML declares fa
        // Central Kurdish on the Persian layout (CleverKeys' arab_pc_ckb_fa), familiar to Persian
        // typists. Earlier builds used arab_pc_ckb; see [migrateKurdishLayout].
        // Pack: scripts/kafe/build_ckb_langpack.py (kww Kurdish Wikipedia counts + Sorani Hunspell).
        KURDISH("ckb", "arab_pc_ckb_fa", "کُردی", "Kurdish", "Kürtçe", "الكردية", "kafe/langpack-ckb.zip", 1, "ckb", "کوردی"),
        TURKISH("tr", "latn_qwerty_tr", "ترکی", "Turkish", "Türkçe", "التركية", "kafe/langpack-tr.zip", 2, "tr", "تورکی"),
        // Pack: scripts/kafe/build_ar_langpack.py (wordfreq ar, 50,000 words).
        ARABIC("ar", "arab_pc", "عربی", "Arabic", "Arapça", "العربية", "kafe/langpack-ar.zip", 1, "ar", "عەرەبی");

        /** True when a dictionary (suggestions, swipe) ships for this language. */
        val hasDictionary: Boolean get() = packAsset != null
    }

    /** Languages whose layout is currently in the layout list. */
    fun enabled(context: Context): Set<Language> {
        val names = layoutNames(context)
        return Language.values().filter { it.layout in names }.toSet()
    }

    /**
     * The languages the user ticked, kept apart from the layout list: CleverKeys' first-run
     * migration (Config, when the keyboard service starts for the first time) rewrites the
     * layout list to the system layout alone, which wiped ticks made on the launcher before
     * the keyboard was ever selected. [restoreTicked] puts them back afterwards.
     */
    private const val PREFS = "kafe_keyboard_languages"
    private const val KEY_TICKED = "ticked"

    private fun tickedPrefs(context: Context) =
        context.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun rememberTick(context: Context, language: Language, on: Boolean) {
        val p = tickedPrefs(context)
        val set = p.getStringSet(KEY_TICKED, emptySet())!!.toMutableSet()
        if (on) set += language.code else set -= language.code
        p.edit().putStringSet(KEY_TICKED, set).apply()
    }

    /** v2: the Kurdish (ckb) pack joined fa/tr/ar. */
    private const val KEY_ALL_PACKS = "all_packs_v2"
    private const val OLD_KURDISH_LAYOUT = "arab_pc_ckb"

    /**
     * Kurdish moved from arab_pc_ckb to arab_pc_ckb_fa: replace the old layout entry in place
     * (same position, so the spacebar order stays) on installs that ticked Kurdish before.
     * Blocking; call off the main thread.
     */
    fun migrateKurdishLayout(context: Context) {
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context.applicationContext)
        val current = load(prefs)
        if (current.none { it.nameOf() == OLD_KURDISH_LAYOUT }) return
        val hasNew = current.any { it.nameOf() == Language.KURDISH.layout }
        val updated = current.mapNotNull {
            if (it.nameOf() != OLD_KURDISH_LAYOUT) it
            else if (hasNew) null
            else LayoutsPreference.NamedLayout(Language.KURDISH.layout, Language.KURDISH.bindLanguage)
        }
        val edit = prefs.edit()
        LayoutsPreference.saveToPreferences(edit, updated)
        edit.apply()
        Log.i(TAG, "Kurdish layout moved to ${Language.KURDISH.layout}")
    }

    /**
     * Installs every bundled dictionary pack (fa, tr, ar, ckb) once, whether or not its language
     * is ticked, so each language has its suggestions and swipe ready the moment it is turned
     * on. Bump [KEY_ALL_PACKS] when a new pack ships. Blocking; call off the main thread.
     */
    fun installAllPacks(context: Context) {
        val p = tickedPrefs(context)
        if (p.getBoolean(KEY_ALL_PACKS, false)) return
        var ok = true
        for (lang in Language.values()) {
            val asset = lang.packAsset ?: continue
            ok = BundledPersianPack.installPack(context, asset, lang.code, lang.packVersion) && ok
        }
        if (ok) p.edit().putBoolean(KEY_ALL_PACKS, true).apply()
    }

    /**
     * Re-adds the layout of every language the user ticked that is missing from the layout
     * list (never removes anything). Blocking; call off the main thread, after the keyboard
     * service's first-run setup.
     */
    fun restoreTicked(context: Context) {
        val ticked = tickedPrefs(context).getStringSet(KEY_TICKED, null) ?: return
        val wanted = Language.values().filter { it.code in ticked }
        if (wanted.isEmpty()) return
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context.applicationContext)
        val current = load(prefs)
        val names = current.mapNotNull { it.nameOf() }.toSet()
        val missing = wanted.filter { it.layout !in names }
        if (missing.isEmpty()) return
        for (lang in missing) {
            if (lang.packAsset != null) {
                BundledPersianPack.installPack(context, lang.packAsset, lang.code, lang.packVersion)
            }
        }
        val edit = prefs.edit()
        LayoutsPreference.saveToPreferences(
            edit, current + missing.map { LayoutsPreference.NamedLayout(it.layout, it.bindLanguage) }
        )
        edit.apply()
        Log.i(TAG, "Restored ticked layouts: ${missing.joinToString { it.code }}")
    }

    /**
     * Turns [language] on (adds its layout, installs its pack in the background) or off
     * (removes its layout). English is never touched. [onDone] runs on a background thread.
     */
    fun setEnabled(context: Context, language: Language, on: Boolean, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        rememberTick(app, language, on)
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
