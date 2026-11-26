package tribixbite.cleverkeys.kafe

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import tribixbite.cleverkeys.DirectBootAwarePreferences
import tribixbite.cleverkeys.prefs.LayoutsPreference
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kafe Keyboard: "Keyboard languages" card on the launcher, under the language picker.
 * English is always on; Persian, Kurdish, Turkish and Arabic can be ticked. A ticked
 * language's layout joins the spacebar's up/down layout cycle ([KafeKeyboardLanguages]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KafeKeyboardLanguagesCard(titleColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uiLang = remember { KafeLanguage.current(context) }
    fun t(fa: String, en: String, tr: String = en, ar: String = en, ckb: String = en) = when (uiLang) {
        KafeLanguage.PERSIAN -> fa
        KafeLanguage.TURKISH -> tr
        KafeLanguage.ARABIC -> ar
        KafeLanguage.KURDISH -> ckb
        else -> en
    }

    var enabled by remember { mutableStateOf(KafeKeyboardLanguages.enabled(context)) }
    var busy by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }

    // Follow the layout list: the first-run setup and Layout Manager change it too.
    DisposableEffect(Unit) {
        val prefs = DirectBootAwarePreferences.get_shared_preferences(context.applicationContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LayoutsPreference.KEY) main.post { enabled = KafeKeyboardLanguages.enabled(context) }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                t("زبان‌های صفحه‌کلید", "Keyboard languages", "Klavye dilleri", "لغات لوحة المفاتيح", "زمانەکانی تەختەکلیل"),
                color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Text(
                t(
                    "روی Space بکشید: بالا English، پایین فارسی، بالا-راست کُردی، پایین-چپ عربی، پایین-راست ترکی.",
                    "Swipe on the spacebar: up English, down Persian, up-right Kurdish, down-left Arabic, down-right Turkish.",
                    "Boşluk tuşunda kaydırın: yukarı İngilizce, aşağı Farsça, sağ üst Kürtçe, sol alt Arapça, sağ alt Türkçe.",
                    "اسحب على مفتاح المسافة: للأعلى الإنجليزية، للأسفل الفارسية، أعلى اليمين الكردية، أسفل اليسار العربية، أسفل اليمين التركية.",
                    "لەسەر کلیلی بۆشایی ڕایبکێشە: سەرەوە ئینگلیزی، خوارەوە فارسی، سەرەوەی ڕاست کوردی، خوارەوەی چەپ عەرەبی، خوارەوەی ڕاست تورکی."
                ),
                color = textColor, fontSize = 13.sp
            )
            FlowRow(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LanguageCheck("English", checked = true, enabled = false, textColor) {}
                for (lang in KafeKeyboardLanguages.Language.values()) {
                    LanguageCheck(t(lang.faName, lang.enName, lang.trName, lang.arName, lang.ckbName), lang in enabled, enabled = !busy, textColor) { on ->
                        busy = true
                        enabled = if (on) enabled + lang else enabled - lang
                        KafeKeyboardLanguages.setEnabled(context, lang, on) {
                            main.post {
                                enabled = KafeKeyboardLanguages.enabled(context)
                                busy = false
                            }
                        }
                    }
                }
            }
            if (enabled.any { !it.hasDictionary }) {
                Text(
                    t(
                        "کُردی فعلاً پیشنهاد کلمه و swipe ندارد.",
                        "Kurdish has no word suggestions or swipe yet.",
                        "Kürtçe için henüz kelime önerisi ve kaydırarak yazma yok.",
                        "لا تتوفر للكردية بعدُ اقتراحات الكلمات ولا الكتابة بالسحب."
                    ),
                    color = textColor, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun LanguageCheck(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    textColor: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, color = textColor, fontSize = 15.sp)
    }
}
