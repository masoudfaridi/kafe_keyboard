package tribixbite.cleverkeys.kafe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tribixbite.cleverkeys.R

/**
 * Kafe Keyboard: a short tutorial, one key tip per page, each with a screenshot of the
 * keyboard (res-kafe/drawable-nodpi/kafe_tut_*.webp, marked where it matters).
 *
 * Opened from a card on the launcher ([KafeTutorialCard], under the keyboard languages) and
 * from the first item of Settings ([KafeTutorialSettingsItem]). It is a full-screen Compose
 * dialog, so no activity (and no AndroidManifest change) is needed. Text follows the app
 * language (fa, en, tr, ar, ckb).
 */
private class TutorialPage(val image: Int, val title: Tr, val body: Tr)

/** One text in the five UI languages. */
private class Tr(val fa: String, val en: String, val tr: String, val ar: String, val ckb: String) {
    fun get(lang: String) = when (lang) {
        KafeLanguage.PERSIAN -> fa
        KafeLanguage.TURKISH -> tr
        KafeLanguage.ARABIC -> ar
        KafeLanguage.KURDISH -> ckb
        else -> en
    }
}

private val PAGES = listOf(
    TutorialPage(
        R.drawable.kafe_tut_space,
        Tr("تعویض زبان با Space", "Switch language with Space", "Boşluk ile dil değiştirme",
            "تبديل اللغة بمفتاح المسافة", "گۆڕینی زمان بە Space"),
        Tr("روی Space بکشید: بالا English، پایین فارسی، بالا‌راست کُردی، پایین‌چپ عربی، پایین‌راست ترکی. چپ و راست مکان‌نما را جابه‌جا می‌کند. با نگه داشتن Space همهٔ گزینه‌ها دیده می‌شوند.",
            "Swipe on Space: up English, down Persian, up-right Kurdish, down-left Arabic, down-right Turkish. Left and right move the cursor. Hold Space to see every option.",
            "Boşlukta kaydırın: yukarı İngilizce, aşağı Farsça, sağ üst Kürtçe, sol alt Arapça, sağ alt Türkçe. Sol ve sağ imleci taşır. Tüm seçenekler için Boşluğa basılı tutun.",
            "اسحب على المسافة: للأعلى الإنجليزية، للأسفل الفارسية، أعلى اليمين الكردية، أسفل اليسار العربية، أسفل اليمين التركية. اليسار واليمين يحرّكان المؤشر. اضغط مطوّلًا لرؤية كل الخيارات.",
            "لەسەر Space ڕایبکێشە: سەرەوە ئینگلیزی، خوارەوە فارسی، سەرەوەی ڕاست کوردی، خوارەوەی چەپ عەرەبی، خوارەوەی ڕاست تورکی. چەپ و ڕاست نیشاندەر دەجوڵێنن. بۆ بینینی هەموو بژاردەکان Space ڕابگرە."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_swipetype,
        Tr("تایپ با کشیدن انگشت", "Swipe typing", "Kaydırarak yazma", "الكتابة بالسحب", "نووسین بە ڕاکێشان"),
        Tr("انگشت را بدون برداشتن از روی حروف کلمه بکشید؛ کلمه خودش نوشته می‌شود و پیشنهادهای دیگر بالای کیبورد می‌آیند.",
            "Slide your finger over the letters of a word without lifting it; the word is typed for you and other suggestions appear above the keys.",
            "Parmağınızı kaldırmadan kelimenin harfleri üzerinde kaydırın; kelime yazılır ve diğer öneriler tuşların üstünde görünür.",
            "مرّر إصبعك فوق حروف الكلمة دون رفعه؛ تُكتب الكلمة وتظهر اقتراحات أخرى فوق المفاتيح.",
            "پەنجەت بەسەر پیتەکانی وشەدا ڕابکێشە بێ ئەوەی هەڵیبگریت؛ وشەکە دەنووسرێت و پێشنیاری تر لە سەرەوە دەردەکەون."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_toolbar,
        Tr("نوار ابزار ویرایش", "Edit toolbar", "Düzenleme araç çubuğu", "شريط أدوات التحرير", "تووڵباری دەستکاری"),
        Tr("انتخاب همه، کپی، جابه‌جایی مکان‌نما، برش، چسباندن و چرخ‌دنده برای تنظیمات. دکمهٔ گرد Aa بین نوار ابزار و پیشنهاد کلمات جابه‌جا می‌کند.",
            "Select all, copy, move the cursor, cut, paste, and the gear for settings. The round Aa button switches between the toolbar and word suggestions.",
            "Tümünü seç, kopyala, imleci taşı, kes, yapıştır ve ayarlar için dişli. Yuvarlak Aa düğmesi araç çubuğu ile öneriler arasında geçiş yapar.",
            "تحديد الكل، نسخ، تحريك المؤشر، قص، لصق، والترس للإعدادات. الزر الدائري Aa يبدّل بين شريط الأدوات واقتراحات الكلمات.",
            "هەمووی دیاری بکە، لەبەرگرتنەوە، جوڵاندنی نیشاندەر، بڕین، لکاندن و چەرخ بۆ ڕێکخستنەکان. دوگمەی خڕی Aa لە نێوان تووڵبار و پێشنیارەکان دەگۆڕێت."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_swipeup,
        Tr("حروف دوم: کشیدن به بالا", "Second letters: swipe up", "İkinci harfler: yukarı kaydır",
            "الحروف الثانية: اسحب للأعلى", "پیتی دووەم: ڕاکێشان بۆ سەرەوە"),
        Tr("حرف کوچکِ بالای کلید با کشیدن انگشت به بالا تایپ می‌شود: ض روی ص، غ روی ع، ظ روی ط، ژ روی ز، ذ روی د.",
            "The small letter at the top of a key is typed by swiping up: ض on ص, غ on ع, ظ on ط, ژ on ز, ذ on د.",
            "Tuşun üstündeki küçük harf yukarı kaydırarak yazılır: ص üstünde ض, ع üstünde غ, ط üstünde ظ, ز üstünde ژ, د üstünde ذ.",
            "الحرف الصغير أعلى المفتاح يُكتب بالسحب للأعلى: ض على ص، غ على ع، ظ على ط، ژ على ز، ذ على د.",
            "پیتە بچووکەکەی سەرەوەی کلیل بە ڕاکێشان بۆ سەرەوە دەنووسرێت: ض لەسەر ص، غ لەسەر ع، ظ لەسەر ط، ژ لەسەر ز، ذ لەسەر د."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_hold,
        Tr("نگه داشتن کلید", "Hold a key", "Tuşa basılı tutun", "الضغط المطوّل", "ڕاگرتنی کلیل"),
        Tr("کلید را نگه دارید تا گزینه‌هایش دور انگشت باز شود؛ انگشت را روی یکی ببرید و رها کنید. رها کردن در وسط یعنی لغو.",
            "Hold a key to open its options around your finger; slide onto one and let go. Letting go in the middle cancels.",
            "Seçeneklerini açmak için tuşa basılı tutun; birinin üzerine kaydırıp bırakın. Ortada bırakmak iptal eder.",
            "اضغط مطوّلًا على المفتاح لتظهر خياراته حول إصبعك؛ مرّر إلى أحدها وارفع إصبعك. الرفع في المنتصف يلغي.",
            "کلیلەکە ڕابگرە تا بژاردەکانی بە دەوری پەنجەتدا بکرێنەوە؛ بۆ سەر یەکێکیان بڕۆ و بەری بدە. بەردان لە ناوەڕاست هەڵدەوەشێتەوە."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_fn,
        Tr("Fn: اعراب و علائم", "Fn: diacritics and punctuation", "Fn: harekeler ve noktalama",
            "Fn: الحركات وعلامات الترقيم", "Fn: بزوێنەکان و خاڵبەندی"),
        Tr("یک بار Fn بزنید: حروف جفت جابه‌جا می‌شوند (ض اصلی می‌شود) و بقیه اعراب (ـَ ـِ ـُ ـّ ـً) و علائم (؟ ! « » ، ؛) می‌شوند. با دوبار زدن، Fn روشن می‌ماند.",
            "Tap Fn once: paired letters swap (ض becomes the main one) and the others turn into diacritics and punctuation (؟ ! « » ، ؛). Tap twice to keep Fn on.",
            "Fn'e bir kez dokunun: çift harfler yer değiştirir ve diğerleri hareke ve noktalama olur (؟ ! « » ، ؛). Açık tutmak için iki kez dokunun.",
            "اضغط Fn مرة: تتبادل الحروف المزدوجة ويتحوّل الباقي إلى حركات وعلامات ترقيم (؟ ! « » ، ؛). اضغط مرتين لإبقائه مفعّلًا.",
            "جارێک Fn دابگرە: پیتە جووتەکان جێگۆڕکێ دەکەن و ئەوانی تر دەبنە بزوێن و نیشانەی خاڵبەندی (؟ ! « » ، ؛). دووجار بۆ ئەوەی کارا بمێنێت."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_halfspace,
        Tr("نیم‌فاصله", "Half-space (ZWNJ)", "Yarım boşluk (ZWNJ)", "نصف مسافة (ZWNJ)", "نیوە بۆشایی (ZWNJ)"),
        Tr("برای «می‌شود» و «کتاب‌ها» کلید نیم‌فاصلهٔ کنار Space را بزنید، یا روی ت به بالا‌چپ بکشید.",
            "For Persian words like «می‌شود», use the half-space key next to Space, or swipe up-left on ت.",
            "Farsça «می‌شود» gibi kelimeler için Boşluğun yanındaki yarım boşluk tuşunu kullanın ya da ت üzerinde sol üste kaydırın.",
            "لكلمات فارسية مثل «می‌شود» استخدم مفتاح نصف المسافة بجانب المسافة، أو اسحب إلى أعلى اليسار على ت.",
            "بۆ وشەی فارسیی وەک «می‌شود» کلیلی نیوە بۆشایی تەنیشت Space بەکاربهێنە، یان لەسەر ت بۆ سەرەوەی چەپ ڕایبکێشە."),
    ),
    TutorialPage(
        R.drawable.kafe_tut_ctrl,
        Tr("Ctrl: نوار و کلیپ‌بورد", "Ctrl: strip and clipboard", "Ctrl: şerit ve pano", "Ctrl: الشريط والحافظة", "Ctrl: شریت و کلیپبۆرد"),
        Tr("Ctrl را نگه دارید: «نوار» نوار ابزار و پیشنهادها را پنهان یا نمایان می‌کند، ۱۲۳+ صفحهٔ اعداد را باز می‌کند و آیکون کلیپ‌بورد تاریخچهٔ کپی‌ها را.",
            "Hold Ctrl: «نوار» hides or shows the toolbar and suggestions, 123+ opens the number pad and the clipboard icon your copy history.",
            "Ctrl'ye basılı tutun: «نوار» araç çubuğunu ve önerileri gizler/gösterir, 123+ sayı tuşlarını, pano simgesi kopyalama geçmişini açar.",
            "اضغط مطوّلًا على Ctrl: «نوار» يُخفي أو يُظهر شريط الأدوات والاقتراحات، و123+ يفتح لوحة الأرقام، وأيقونة الحافظة سجل النسخ.",
            "Ctrl ڕابگرە: «نوار» تووڵبار و پێشنیارەکان دەشارێتەوە یان پیشان دەدات، 123+ تەختەی ژمارە دەکاتەوە و ئایکۆنی کلیپبۆرد مێژووی لەبەرگرتنەوە."),
    ),
)

private fun t(lang: String, fa: String, en: String, tr: String, ar: String, ckb: String) =
    Tr(fa, en, tr, ar, ckb).get(lang)

/** The tutorial dialog: one tip per page, swipe or use the buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KafeTutorialDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = remember { KafeLanguage.current(context) }
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t(lang, "آموزش", "Tutorial", "Eğitim", "الشرح", "فێرکاری"),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${pager.currentPage + 1} / ${PAGES.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                    )
                    TextButton(onClick = onDismiss) {
                        Text(t(lang, "بستن", "Close", "Kapat", "إغلاق", "داخستن"))
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                    val page = PAGES[index]
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(page.image),
                            contentDescription = page.title.get(lang),
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            page.title.get(lang), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            page.body.get(lang), fontSize = 16.sp, lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(PAGES.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0, modifier = Modifier.weight(1f)
                    ) { Text(t(lang, "قبلی", "Back", "Geri", "السابق", "پێشوو")) }
                    val last = pager.currentPage == PAGES.size - 1
                    Button(
                        onClick = {
                            if (last) onDismiss()
                            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (last) t(lang, "پایان", "Done", "Bitti", "تم", "تەواو")
                            else t(lang, "بعدی", "Next", "İleri", "التالي", "دواتر")
                        )
                    }
                }
            }
        }
    }
}

/** Launcher card, under the keyboard languages: opens the tutorial. */
@Composable
fun KafeTutorialCard(titleColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = remember { KafeLanguage.current(context) }
    var open by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t(lang, "آموزش", "Tutorial", "Eğitim", "الشرح", "فێرکاری"),
                    color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    t(lang, "نکات کلیدی کیبورد، صفحه به صفحه با عکس",
                        "The keyboard's key tips, page by page with pictures",
                        "Klavyenin temel ipuçları, resimli sayfalar halinde",
                        "أهم نصائح لوحة المفاتيح، صفحة بصفحة مع صور",
                        "خاڵە سەرەکییەکانی تەختەکلیل، پەڕە بە پەڕە بە وێنە"),
                    color = textColor, fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { open = true }) { Text(t(lang, "شروع", "Start", "Başla", "ابدأ", "دەستپێکردن")) }
        }
    }
    if (open) KafeTutorialDialog { open = false }
}

/** First item of Settings: opens the tutorial. */
@Composable
fun KafeTutorialSettingsItem(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = remember { KafeLanguage.current(context) }
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { open = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📖", fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t(lang, "آموزش", "Tutorial", "Eğitim", "الشرح", "فێرکاری"),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    t(lang, "نکات کلیدی کیبورد، صفحه به صفحه با عکس",
                        "The keyboard's key tips, page by page with pictures",
                        "Klavyenin temel ipuçları, resimli sayfalar halinde",
                        "أهم نصائح لوحة المفاتيح، صفحة بصفحة مع صور",
                        "خاڵە سەرەکییەکانی تەختەکلیل، پەڕە بە پەڕە بە وێنە"),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    if (open) KafeTutorialDialog { open = false }
}
