package tribixbite.cleverkeys.kafe

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import java.util.Locale

/**
 * Kafe Keyboard: the app's own UI language (Persian or English), Persian by default.
 *
 * Android 13+ (API 33): the per-app language of [LocaleManager]. The system stores it,
 * applies it to every component of the process (activities, settings, the keyboard
 * service) and recreates open activities when it changes.
 *
 * Android 7–12: best effort. The default locale and the application resources are
 * switched, and each activity's resources are switched just before it is created
 * (API 29+). On Android 7–9 an activity opened before the change keeps the old language
 * until it is reopened.
 *
 * The choice lives in device-protected storage, because the activities and the keyboard
 * are direct-boot aware.
 */
object KafeLanguage {
    const val PERSIAN = "fa"
    const val ENGLISH = "en"
    /** Turkish: CleverKeys ships a full Turkish translation (res/values-tr). */
    const val TURKISH = "tr"
    /** Arabic: Kafe's own translation (res-kafe/values-ar). */
    const val ARABIC = "ar"
    /** Central Kurdish (Sorani): Kafe's own translation (res-kafe/values-b+ckb). */
    const val KURDISH = "ckb"
    val SUPPORTED = listOf(PERSIAN, ENGLISH, KURDISH, ARABIC, TURKISH)

    private const val TAG = "KafeLanguage"
    private const val PREFS = "kafe_language"
    private const val KEY = "app_language"

    @Volatile private var legacyCallbacksRegistered = false

    /** The current UI language: [PERSIAN] or [ENGLISH]. */
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = localeManager(context)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                val lang = locales[0].language
                if (lang in SUPPORTED) return lang
            }
        }
        return prefs(context).getString(KEY, null)?.takeIf { it in SUPPORTED } ?: PERSIAN
    }

    /**
     * First run: switch the app to Persian. Afterwards: re-apply the stored choice
     * (needed below Android 13, where nothing persists it for us). Call early from
     * the launcher and the keyboard service.
     */
    fun ensureApplied(context: Context) {
        try {
            val p = prefs(context)
            if (!p.contains(KEY)) {
                set(context, PERSIAN)
                return
            }
            if (Build.VERSION.SDK_INT < 33) applyLegacy(context, current(context))
        } catch (e: Exception) {
            Log.e(TAG, "Applying the app language failed", e)
        }
    }

    /** Switches the UI language. Open activities follow (Android 13+ recreates them). */
    fun set(context: Context, language: String) {
        val lang = language.takeIf { it in SUPPORTED } ?: PERSIAN
        prefs(context).edit().putString(KEY, lang).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            localeManager(context)?.applicationLocales = LocaleList.forLanguageTags(lang)
        } else {
            applyLegacy(context, lang)
            (context as? Activity)?.recreate()
        }
    }

    /**
     * The phone's own language, unaffected by the app language. On Android 13+ the per-app
     * language also rewrites `Resources.getSystem()` inside the app, so only
     * [LocaleManager.getSystemLocales] tells the truth there.
     */
    fun systemLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = localeManager(context)?.systemLocales
            if (locales != null && !locales.isEmpty) return locales[0].language
        }
        val locales = android.content.res.Resources.getSystem().configuration.locales
        return if (locales.isEmpty) Locale.getDefault().language else locales[0].language
    }

    /** The localized name of [language], written in that language. */
    fun displayName(language: String): String = when (language) {
        PERSIAN -> "فارسی"
        KURDISH -> "کوردی"
        ARABIC -> "العربية"
        TURKISH -> "Türkçe"
        else -> "English"
    }

    private fun localeManager(context: Context): LocaleManager? =
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java) else null

    private fun prefs(context: Context): SharedPreferences {
        val storage = context.applicationContext.createDeviceProtectedStorageContext()
        return storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ---- Android 7–12 -------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun applyLegacy(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        updateResources(context.applicationContext, locale)
        if (context is Activity) updateResources(context, locale)
        registerLegacyCallbacks(context.applicationContext as? Application ?: return)
    }

    @Suppress("DEPRECATION")
    private fun updateResources(context: Context, locale: Locale) {
        val res = context.resources
        val config = Configuration(res.configuration)
        if (config.locales.isEmpty || config.locales[0] != locale) {
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            res.updateConfiguration(config, res.displayMetrics)
        }
    }

    private fun registerLegacyCallbacks(app: Application) {
        if (legacyCallbacksRegistered || Build.VERSION.SDK_INT >= 33) return
        legacyCallbacksRegistered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                updateResources(activity, Locale(current(activity)))
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (Build.VERSION.SDK_INT < 29) updateResources(activity, Locale(current(activity)))
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
