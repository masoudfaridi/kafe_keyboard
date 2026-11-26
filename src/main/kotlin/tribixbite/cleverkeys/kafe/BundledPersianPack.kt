package tribixbite.cleverkeys.kafe

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import tribixbite.cleverkeys.DirectBootAwarePreferences
import tribixbite.cleverkeys.LanguageDisplayNames
import tribixbite.cleverkeys.LanguagePrefs
import tribixbite.cleverkeys.langpack.ImportResult
import tribixbite.cleverkeys.langpack.LanguagePackManager
import java.io.File

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

    @Volatile private var started = false

    /** Installs the pack on a background thread when needed. Safe to call repeatedly. */
    fun ensureInstalledAsync(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread({ ensureInstalled(app) }, "kafe-fa-pack").start()
    }

    private fun ensureInstalled(context: Context) {
        try {
            // Direct boot: credential-protected storage (prefs, pack files) throws until unlock.
            val kafePrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (kafePrefs.getInt(KEY_INSTALLED_VERSION, 0) >= PACK_VERSION) {
                seedLanguageDefaults(context, kafePrefs)
                return
            }

            val manager = LanguagePackManager.getInstance(context)
            val installed = manager.getInstalledPacks().firstOrNull { it.code == CODE }
            if (installed != null && installed.version >= PACK_VERSION) {
                kafePrefs.edit().putInt(KEY_INSTALLED_VERSION, PACK_VERSION).apply()
                seedLanguageDefaults(context, kafePrefs)
                return
            }

            val tmp = File(context.cacheDir, "kafe-langpack-fa.zip")
            context.assets.open(ASSET).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            try {
                when (val result = manager.importLanguagePack(Uri.fromFile(tmp))) {
                    is ImportResult.Success -> {
                        kafePrefs.edit().putInt(KEY_INSTALLED_VERSION, PACK_VERSION).apply()
                        Log.i(TAG, "Installed bundled Persian pack v${result.manifest.version}")
                        seedLanguageDefaults(context, kafePrefs)
                    }
                    is ImportResult.Error -> Log.e(TAG, "Bundled Persian pack refused: ${result.message}")
                }
            } finally {
                tmp.delete()
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
     * Kafe defaults, applied once and only after the Persian pack is installed:
     * Multi-Language on, Persian as the secondary language. Keys the user already set are
     * left alone, and it runs only once, so turning either off later sticks.
     */
    private fun seedLanguageDefaults(context: Context, kafePrefs: SharedPreferences) {
        if (kafePrefs.getBoolean(KEY_LANG_DEFAULTS_SEEDED, false)) return
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context)
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
}
