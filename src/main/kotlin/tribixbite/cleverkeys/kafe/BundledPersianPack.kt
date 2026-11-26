package tribixbite.cleverkeys.kafe

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import tribixbite.cleverkeys.DirectBootAwarePreferences
import tribixbite.cleverkeys.LanguageDisplayNames
import tribixbite.cleverkeys.LanguagePrefs
import tribixbite.cleverkeys.langpack.ImportResult
import tribixbite.cleverkeys.langpack.LanguagePackManager
import tribixbite.cleverkeys.prefs.LayoutsPreference
import tribixbite.cleverkeys.prefs.ListGroupPreference
import java.io.File
import java.util.Locale

/**
 * Kafe Keyboard: installs the Persian language pack that ships in the APK
 * (`assets/kafe/langpack-fa.zip`, a copy of `scripts/dictionaries/langpack-fa.zip`)
 * so Persian suggestions and swipe work without a manual "Import Pack", then turns
 * Multi-Language on with Persian as the secondary language.
 *
 * It goes through [LanguagePackManager.importLanguagePack], the same validated path
 * as a user import. Each [PACK_VERSION] is installed at most once: if the user deletes
 * the pack afterwards it stays deleted until a new pack version ships.
 *
 * Kept in its own package so upstream CleverKeys merges do not conflict with it.
 */
object BundledPersianPack {
    private const val TAG = "KafePersianPack"
    private const val ASSET = "kafe/langpack-fa.zip"
    private const val CODE = "fa"

    /** Bump when assets/kafe/langpack-fa.zip is replaced, to reinstall it once. */
    private const val PACK_VERSION = 2
    private const val PREFS = "kafe_bundled_packs"
    private const val KEY_INSTALLED_VERSION = "fa_installed_version"
    private const val KEY_LANG_DEFAULTS_SEEDED = "lang_defaults_seeded"
    private const val KEY_LAYOUT_SEEDED = "layout_seeded"

    @Volatile private var started = false

    /**
     * Installs the pack on a background thread when needed. Safe to call repeatedly.
     * Posted to the main looper so it starts after the service's onCreate, i.e. after
     * Config's first-run migration, which rewrites the layout list on a fresh install.
     */
    fun ensureInstalledAsync(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        // The keyboard follows the app language too (Persian by default).
        KafeLanguage.ensureApplied(app)
        Handler(Looper.getMainLooper()).post {
            Thread({ ensureInstalled(app) }, "kafe-fa-pack").start()
        }
    }

    private fun ensureInstalled(context: Context) {
        try {
            // Direct boot: credential-protected storage (prefs, pack files) throws until unlock.
            val kafePrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (kafePrefs.getInt(KEY_INSTALLED_VERSION, 0) >= PACK_VERSION) {
                seedLanguageDefaults(context, kafePrefs)
                return
            }

            if (installPack(context, ASSET, CODE, PACK_VERSION)) {
                kafePrefs.edit().putInt(KEY_INSTALLED_VERSION, PACK_VERSION).apply()
                seedLanguageDefaults(context, kafePrefs)
            }
        } catch (e: IllegalStateException) {
            // Locked device (direct boot): retry on the next service start.
            started = false
            Log.w(TAG, "Storage not available yet, will retry", e)
        } catch (e: Exception) {
            Log.e(TAG, "Installing bundled Persian pack failed", e)
        }
    }

    /**
     * Installs the bundled pack [asset] (language [code]) unless that pack, at [version] or
     * newer, is already installed. Blocking: call off the main thread.
     * @return true when the pack is installed afterwards
     */
    fun installPack(context: Context, asset: String, code: String, version: Int): Boolean {
        val manager = LanguagePackManager.getInstance(context)
        val installed = manager.getInstalledPacks().firstOrNull { it.code == code }
        if (installed != null && installed.version >= version) return true

        val tmp = File(context.cacheDir, "kafe-langpack-$code.zip")
        context.assets.open(asset).use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        try {
            return when (val result = manager.importLanguagePack(Uri.fromFile(tmp))) {
                is ImportResult.Success -> {
                    Log.i(TAG, "Installed bundled $code pack v${result.manifest.version}")
                    true
                }
                is ImportResult.Error -> {
                    Log.e(TAG, "Bundled $code pack refused: ${result.message}")
                    false
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /**
     * Kafe defaults, applied once and only after the Persian pack is installed:
     * Multi-Language on, Persian as the secondary language. Keys the user already set are
     * left alone, and it runs only once, so turning either off later sticks.
     */
    private fun seedLanguageDefaults(context: Context, kafePrefs: SharedPreferences) {
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context)
        // Own flag: installs that already seeded the languages (kafe-v0.1.0) still get it once.
        if (!kafePrefs.getBoolean(KEY_LAYOUT_SEEDED, false)) {
            val edit = prefs.edit()
            addSecondLayout(context, prefs, edit)
            edit.apply()
            kafePrefs.edit().putBoolean(KEY_LAYOUT_SEEDED, true).apply()
        }
        if (kafePrefs.getBoolean(KEY_LANG_DEFAULTS_SEEDED, false)) return
        val edit = prefs.edit()
        if (!prefs.contains(LanguagePrefs.MULTILANG)) {
            edit.putBoolean(LanguagePrefs.MULTILANG, true)
        }
        val secondary = prefs.getString(LanguagePrefs.SECONDARY, null)
        if (secondary == null || secondary == LanguageDisplayNames.NONE) {
            edit.putString(LanguagePrefs.SECONDARY, CODE)
        }
        edit.apply()
        kafePrefs.edit().putBoolean(KEY_LANG_DEFAULTS_SEEDED, true).apply()
        Log.i(TAG, "Seeded Multi-Language with Persian as secondary")
    }

    /**
     * Makes sure an English and a Persian layout are both enabled, so the spacebar's
     * swipe up/down (next/previous layout, shown only with 2+ layouts) switches the keys,
     * and with them the language: Persian PC is bound to `fa` in its XML.
     * On a phone whose system language is Persian the system layout is already Persian,
     * so English QWERTY is added instead.
     */
    private fun addSecondLayout(context: Context, prefs: SharedPreferences, edit: SharedPreferences.Editor) {
        val current = ListGroupPreference.loadFromPreferences(
            LayoutsPreference.KEY, prefs, LayoutsPreference.DEFAULT, LayoutsPreference.SERIALIZER
        ) ?: LayoutsPreference.DEFAULT
        val names = current.mapNotNull { (it as? LayoutsPreference.NamedLayout)?.name }
        // The PHONE's language, not the app's: KafeLanguage switches the app to Persian,
        // which would otherwise read as a Persian phone.
        val systemIsPersian = KafeLanguage.systemLanguage(context) == CODE
        val wanted = if (systemIsPersian) ENGLISH_LAYOUT else PERSIAN_LAYOUT
        if (wanted in names) return
        if (!systemIsPersian && current.size > 1) return // the user already picked layouts
        LayoutsPreference.saveToPreferences(edit, current + LayoutsPreference.NamedLayout(wanted))
    }

    private const val PERSIAN_LAYOUT = "arab_pc_ir"
    private const val ENGLISH_LAYOUT = "latn_qwerty_us"
}
