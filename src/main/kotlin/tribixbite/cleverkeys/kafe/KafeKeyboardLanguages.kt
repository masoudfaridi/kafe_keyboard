package tribixbite.cleverkeys.kafe

import android.content.Context
import android.util.Log
import tribixbite.cleverkeys.DirectBootAwarePreferences
import tribixbite.cleverkeys.prefs.LayoutsPreference
import tribixbite.cleverkeys.prefs.ListGroupPreference

/**
 * Kafe Keyboard: the keyboard languages a user can tick on the launcher. English (the system
 * layout) is always on; each ticked language adds its layout to CleverKeys' layout list and
 * installs its bundled dictionary pack.
 *
 * Persian, Arabic and Kurdish use Kafe's own 10-column layouts (assets/kafe/layouts/), stored
 * as CleverKeys custom layouts whose `origin` is the built-in layout they derive from. Layout
 * Manager therefore shows them as a "modified copy" of that layout, and its "Revert to
 * original" brings back the 11-column built-in. Nothing in CleverKeys is modified.
 *
 * The layout list (`layouts` preference) is the single source of truth: a language is "on"
 * when an entry of the list belongs to it ([matches]), so Layout Manager edits show up here.
 */
object KafeKeyboardLanguages {
    private const val TAG = "KafeKbdLanguages"

    /**
     * One tickable language. [layout] is the CleverKeys built-in it derives from; [tenAsset] is
     * Kafe's 10-column layout for it (null: the built-in is used as is). [bindLanguage] binds
     * the entry to a language code, so its dictionary (or none, rather than English
     * suggestions) follows the layout.
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
        /** Kafe's 10-column layout, or null to use [layout] itself. */
        val tenAsset: String?,
    ) {
        PERSIAN("fa", "arab_pc_ir", "فارسی", "Persian", "Farsça", "الفارسية",
            "kafe/langpack-fa.zip", 2, "fa", "فارسی", "kafe/layouts/kafe_fa_10.xml"),
        // Central Kurdish on the Persian positions (from CleverKeys' arab_pc_ckb_fa). Earlier
        // builds used arab_pc_ckb; see [migrateKurdishLayout].
        // Pack: scripts/kafe/build_ckb_langpack.py (kww Kurdish Wikipedia counts + Sorani Hunspell).
        KURDISH("ckb", "arab_pc_ckb_fa", "کُردی", "Kurdish", "Kürtçe", "الكردية",
            "kafe/langpack-ckb.zip", 1, "ckb", "کوردی", "kafe/layouts/kafe_ckb_10.xml"),
        TURKISH("tr", "latn_qwerty_tr", "ترکی", "Turkish", "Türkçe", "التركية",
            "kafe/langpack-tr.zip", 2, "tr", "تورکی", null),
        // Pack: scripts/kafe/build_ar_langpack.py (wordfreq ar, 50,000 words).
        ARABIC("ar", "arab_pc", "عربی", "Arabic", "Arapça", "العربية",
            "kafe/langpack-ar.zip", 1, "ar", "عەرەبی", "kafe/layouts/kafe_ar_10.xml");

        /** True when a dictionary (suggestions, swipe) ships for this language. */
        val hasDictionary: Boolean get() = packAsset != null
    }

    /**
     * True when [entry] belongs to [language]: its built-in layout, or a custom layout derived
     * from it (Kafe's 10-column one, or the user's own modified copy).
     */
    fun matches(entry: LayoutsPreference.Layout, language: Language): Boolean = when (entry) {
        is LayoutsPreference.NamedLayout -> entry.name == language.layout
        is LayoutsPreference.CustomLayout -> entry.origin == language.layout
        else -> false
    }

    /** The Kafe language [entry] belongs to, or null (English / system / other layouts). */
    fun languageOf(entry: LayoutsPreference.Layout): Language? =
        Language.values().firstOrNull { matches(entry, it) }

    /** Languages with an entry in the layout list. */
    fun enabled(context: Context): Set<Language> {
        val layouts = load(DirectBootAwarePreferences.get_shared_preferences(context.applicationContext))
        return Language.values().filter { lang -> layouts.any { matches(it, lang) } }.toSet()
    }

    /** The layout entry Kafe adds for [language]: its 10-column layout, or its built-in. */
    private fun newEntry(context: Context, language: Language): LayoutsPreference.Layout {
        val asset = language.tenAsset
            ?: return LayoutsPreference.NamedLayout(language.layout, language.bindLanguage)
        val xml = context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val custom = LayoutsPreference.CustomLayout.parse(
            xml, language = language.bindLanguage, origin = language.layout,
        )
        if (custom.parsed == null) {
            Log.e(TAG, "$asset does not parse; using ${language.layout}")
            return LayoutsPreference.NamedLayout(language.layout, language.bindLanguage)
        }
        return custom
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
    private const val KEY_TEN_COLUMNS = "ten_columns_v1"
    private const val OLD_KURDISH_LAYOUT = "arab_pc_ckb"

    /**
     * Kurdish moved from arab_pc_ckb to arab_pc_ckb_fa: replace the old layout entry in place
     * (same position, so the spacebar order stays) on installs that ticked Kurdish before.
     * Blocking; call off the main thread, before [migrateToTenColumns].
     */
    fun migrateKurdishLayout(context: Context) {
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context.applicationContext)
        val current = load(prefs)
        if (current.none { it.nameOf() == OLD_KURDISH_LAYOUT }) return
        val hasNew = current.any { matches(it, Language.KURDISH) }
        val updated = current.mapNotNull {
            if (it.nameOf() != OLD_KURDISH_LAYOUT) it
            else if (hasNew) null
            else LayoutsPreference.NamedLayout(Language.KURDISH.layout, Language.KURDISH.bindLanguage)
        }
        save(prefs, updated)
        Log.i(TAG, "Kurdish layout moved to ${Language.KURDISH.layout}")
    }

    /**
     * Once per install: each built-in Persian / Arabic / Kurdish entry becomes Kafe's 10-column
     * layout, in place (the spacebar order stays). The user's own custom copies are left alone,
     * and a later "Revert to original" in Layout Manager sticks.
     * Blocking; call off the main thread.
     */
    fun migrateToTenColumns(context: Context) {
        val flags = tickedPrefs(context)
        if (flags.getBoolean(KEY_TEN_COLUMNS, false)) return
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context.applicationContext)
        val current = load(prefs)
        var changed = false
        val updated = current.map { entry ->
            val lang = (entry as? LayoutsPreference.NamedLayout)?.let { languageOf(it) }
            if (lang?.tenAsset != null) { changed = true; newEntry(context, lang) } else entry
        }
        if (changed) save(prefs, updated)
        flags.edit().putBoolean(KEY_TEN_COLUMNS, true).apply()
        Log.i(TAG, "10-column layouts: ${if (changed) "applied" else "nothing to change"}")
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
        val missing = wanted.filter { lang -> current.none { matches(it, lang) } }
        if (missing.isEmpty()) return
        for (lang in missing) {
            if (lang.packAsset != null) {
                BundledPersianPack.installPack(context, lang.packAsset, lang.code, lang.packVersion)
            }
        }
        save(prefs, current + missing.map { newEntry(context, it) })
        Log.i(TAG, "Restored ticked layouts: ${missing.joinToString { it.code }}")
    }

    /**
     * Turns [language] on (adds its layout, installs its pack in the background) or off
     * (removes every entry of it). English is never touched. [onDone] runs on a background
     * thread.
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
                    if (current.any { matches(it, language) }) current
                    else current + newEntry(app, language)
                } else {
                    current.filterNot { matches(it, language) }
                        .ifEmpty { LayoutsPreference.DEFAULT.take(1) }
                }
                if (updated != current) save(prefs, updated)
            } catch (e: Exception) {
                Log.e(TAG, "Changing ${language.code} failed", e)
            }
            onDone()
        }, "kafe-kbd-lang").start()
    }

    private fun save(prefs: android.content.SharedPreferences, layouts: List<LayoutsPreference.Layout>) {
        val edit = prefs.edit()
        LayoutsPreference.saveToPreferences(edit, layouts)
        edit.apply()
    }

    private fun load(prefs: android.content.SharedPreferences): List<LayoutsPreference.Layout> =
        ListGroupPreference.loadFromPreferences(
            LayoutsPreference.KEY, prefs, LayoutsPreference.DEFAULT, LayoutsPreference.SERIALIZER
        ) ?: LayoutsPreference.DEFAULT

    private fun LayoutsPreference.Layout.nameOf(): String? =
        (this as? LayoutsPreference.NamedLayout)?.name
}
