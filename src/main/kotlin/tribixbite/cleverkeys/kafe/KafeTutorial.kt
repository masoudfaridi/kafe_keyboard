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
import androidx.compose.foundation.layout.heightIn
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
 * Kafe Keyboard: two tutorials (basic: the keyboard; advanced: the settings), one tip per
 * page, each with a screenshot of the
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

/** Basic tutorial: the keyboard itself, one gesture or key per page. */
private val BASIC_PAGES = listOf(
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

/** Advanced tutorial: the settings, one screen per page. */
private val ADVANCED_PAGES = listOf(
    TutorialPage(
        R.drawable.kafe_tut2_languages,
        Tr("زبان برنامه و زبان‌های کیبورد", "App and keyboard languages", "Uygulama ve klavye dilleri",
            "لغة التطبيق ولغات لوحة المفاتيح", "زمانی بەرنامە و زمانەکانی تەختەکلیل"),
        Tr("در صفحهٔ اول، منوی بالا زبان برنامه را عوض می‌کند (فارسی، English، کوردی، العربية، Türkçe). در کارت «زبان‌های صفحه‌کلید» زبان‌هایی را که لازم دارید تیک بزنید؛ واژه‌نامهٔ همهٔ زبان‌ها از قبل نصب است.",
            "On the start screen, the menu at the top changes the app language (فارسی, English, کوردی, العربية, Türkçe). In the “Keyboard languages” card, tick the languages you need; every language's dictionary is already installed.",
            "Başlangıç ekranında üstteki menü uygulama dilini değiştirir. “Klavye dilleri” kartında ihtiyacınız olan dilleri işaretleyin; tüm dillerin sözlüğü zaten yüklüdür.",
            "في شاشة البداية، القائمة في الأعلى تغيّر لغة التطبيق. في بطاقة «لغات لوحة المفاتيح» حدّد اللغات التي تحتاجها؛ قاموس كل لغة مثبّت مسبقًا.",
            "لە پەڕەی سەرەتادا، لیستی سەرەوە زمانی بەرنامە دەگۆڕێت. لە کارتی «زمانەکانی تەختەکلیل» ئەو زمانانە دیاری بکە کە پێویستتە؛ فەرهەنگی هەموو زمانەکان پێشتر دامەزراوە."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_theme,
        Tr("تم و رنگ‌ها", "Themes and colours", "Temalar ve renkler", "السمات والألوان", "ڕووکار و ڕەنگەکان"),
        Tr("تنظیمات ← فعالیت‌ها ← مدیر تم. روی یک تم بزنید تا همان لحظه اعمال شود. با دکمهٔ + بالای صفحه تم خودتان را با رنگ‌های دلخواه بسازید. ظاهرهای آشنا مثل iOS، Gboard و SwiftKey هم آماده‌اند.",
            "Settings → Activities → Theme Manager. Tap a theme to apply it at once. Use the + at the top to make your own theme with your colours. Familiar looks such as iOS, Gboard and SwiftKey are ready too.",
            "Ayarlar → Etkinlikler → Tema Yöneticisi. Bir temaya dokunun, hemen uygulanır. Üstteki + ile kendi renklerinizle tema oluşturun. iOS, Gboard ve SwiftKey gibi tanıdık görünümler de hazır.",
            "الإعدادات ← الأنشطة ← مدير السمات. اضغط على سمة لتُطبَّق فورًا. استخدم + في الأعلى لصنع سمتك بألوانك. المظاهر المألوفة مثل iOS وGboard وSwiftKey جاهزة أيضًا.",
            "ڕێکخستنەکان ← چالاکییەکان ← بەڕێوەبەری ڕووکار. لەسەر ڕووکارێک بدە تا یەکسەر جێبەجێ ببێت. بە + ی سەرەوە ڕووکاری خۆت بە ڕەنگی دڵخواز دروست بکە. ڕووکاری ناسراوی وەک iOS و Gboard و SwiftKey ئامادەن."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_appearance,
        Tr("ارتفاع و ظاهر کیبورد", "Keyboard height and look", "Klavye yüksekliği ve görünüm",
            "ارتفاع لوحة المفاتيح ومظهرها", "بەرزی و ڕووکاری تەختەکلیل"),
        Tr("تنظیمات ← ظاهر. ارتفاع کیبورد را برای حالت عمودی و افقی جدا تنظیم کنید (پیش‌فرض Kafe ۳۰٪). اندازهٔ حروف، فاصلهٔ کلیدها و حاشیه‌ها هم همین‌جاست.",
            "Settings → Appearance. Set the keyboard height separately for portrait and landscape (Kafe's default is 30%). Label size, key spacing and margins are here too.",
            "Ayarlar → Görünüm. Klavye yüksekliğini dikey ve yatay için ayrı ayarlayın (Kafe varsayılanı %30). Harf boyutu, tuş aralığı ve kenar boşlukları da burada.",
            "الإعدادات ← المظهر. اضبط ارتفاع لوحة المفاتيح للوضع العمودي والأفقي كلٌّ على حدة (الافتراضي في Kafe ‏30٪). حجم الحروف والمسافات بين المفاتيح والهوامش هنا أيضًا.",
            "ڕێکخستنەکان ← ڕووکار. بەرزی تەختەکلیل بۆ دۆخی ستوونی و ئاسۆیی جیا ڕێکبخە (بنەڕەتی Kafe ‏30٪). قەبارەی پیت و بۆشایی کلیلەکان و پەراوێزەکانیش لێرەن."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_layouts,
        Tr("مدیر چیدمان", "Layout Manager", "Düzen Yöneticisi", "مدير التخطيطات", "بەڕێوەبەری ڕیزبەندی"),
        Tr("تنظیمات ← فعالیت‌ها ← مدیر چیدمان. ترتیب چیدمان‌ها را با کشیدن ≡ عوض کنید، با مداد ویرایش کنید یا چیدمان تازه اضافه کنید. «بازگشت به اصلی» چیدمان ۱۰ ستونهٔ Kafe را به چیدمان ۱۱ ستونهٔ CleverKeys برمی‌گرداند.",
            "Settings → Activities → Layout Manager. Drag ≡ to reorder layouts, edit with the pencil, or add a new one. “Revert to original” turns Kafe's 10-column layout back into CleverKeys' 11-column one.",
            "Ayarlar → Etkinlikler → Düzen Yöneticisi. Sıralamak için ≡ simgesini sürükleyin, kalemle düzenleyin veya yeni düzen ekleyin. “Orijinale dön”, Kafe'nin 10 sütunlu düzenini CleverKeys'in 11 sütunlu düzenine geri çevirir.",
            "الإعدادات ← الأنشطة ← مدير التخطيطات. اسحب ≡ لإعادة الترتيب، وحرّر بالقلم أو أضف تخطيطًا جديدًا. «العودة إلى الأصل» يعيد تخطيط Kafe ذا الأعمدة العشرة إلى تخطيط CleverKeys ذي الأعمدة الإحدى عشرة.",
            "ڕێکخستنەکان ← چالاکییەکان ← بەڕێوەبەری ڕیزبەندی. ≡ ڕابکێشە بۆ گۆڕینی ڕیزبەندی، بە قەڵەم دەستکاری بکە یان ڕیزبەندیی نوێ زیاد بکە. «گەڕانەوە بۆ ئەسڵی» ڕیزبەندیی ١٠ ستوونیی Kafe دەگەڕێنێتەوە بۆ ١١ ستوونیی CleverKeys."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_dictionary,
        Tr("مدیر واژه‌نامه", "Dictionary Manager", "Sözlük Yöneticisi", "مدير القاموس", "بەڕێوەبەری فەرهەنگ"),
        Tr("تنظیمات ← فعالیت‌ها ← مدیر واژه‌نامه. واژه‌های خودتان (نام‌ها، اصطلاحات) را اضافه کنید، واژه‌ای را که نمی‌خواهید پیشنهاد شود خاموش کنید و بسامد واژه‌ها را ببینید.",
            "Settings → Activities → Dictionary Manager. Add your own words (names, terms), switch off a word you never want suggested, and see how common each word is.",
            "Ayarlar → Etkinlikler → Sözlük Yöneticisi. Kendi kelimelerinizi (adlar, terimler) ekleyin, önerilmesini istemediğiniz kelimeyi kapatın ve kelime sıklıklarını görün.",
            "الإعدادات ← الأنشطة ← مدير القاموس. أضف كلماتك (أسماء، مصطلحات)، وأوقف كلمة لا تريد اقتراحها، واطّلع على شيوع كل كلمة.",
            "ڕێکخستنەکان ← چالاکییەکان ← بەڕێوەبەری فەرهەنگ. وشەکانی خۆت (ناو، زاراوە) زیاد بکە، ئەو وشەیەی ناتەوێت پێشنیار بکرێت بیکوژێنەوە و بڕی بەکارهێنانی وشەکان ببینە."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_shortswipe,
        Tr("کشیدن‌های سفارشی هر کلید", "Custom swipes on any key", "Her tuşa özel kaydırmalar",
            "سحبات مخصّصة لأي مفتاح", "ڕاکێشانی تایبەت بۆ هەر کلیلێک"),
        Tr("تنظیمات ← فعالیت‌ها ← سفارشی‌سازی کنش‌های هر کلید. روی هر کلید بزنید و به هر جهتش یک کار بدهید: تایپ یک متن، باز کردن برنامه یا یک فرمان کیبورد. زبان‌های روی Space و «نوار» روی Ctrl هم همین‌جا عوض می‌شوند.",
            "Settings → Activities → Key action customization. Tap any key and give each direction a job: type a text, open an app, or run a keyboard command. The Space languages and «نوار» on Ctrl are changed here too.",
            "Ayarlar → Etkinlikler → Tuş eylemi özelleştirme. Bir tuşa dokunun ve her yöne bir iş verin: metin yazmak, uygulama açmak ya da klavye komutu. Boşluk dilleri ve Ctrl üzerindeki «نوار» da burada değişir.",
            "الإعدادات ← الأنشطة ← تخصيص إجراءات المفاتيح. اضغط على أي مفتاح وامنح كل اتجاه مهمة: كتابة نص، فتح تطبيق، أو أمر لوحة مفاتيح. لغات المسافة و«نوار» على Ctrl تُغيَّر هنا أيضًا.",
            "ڕێکخستنەکان ← چالاکییەکان ← تایبەتکردنی کاری کلیلەکان. لەسەر هەر کلیلێک بدە و کارێک بدە بە هەر ئاراستەیەک: نووسینی دەق، کردنەوەی بەرنامە یان فەرمانی تەختەکلیل. زمانەکانی Space و «نوار» لەسەر Ctrl لێرە دەگۆڕدرێن."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_extrakeys,
        Tr("کلیدهای اضافی", "Extra keys", "Ek tuşlar", "مفاتيح إضافية", "کلیلی زیادە"),
        Tr("تنظیمات ← فعالیت‌ها ← پیکربندی کلیدهای اضافی. کلیدهایی مثل Alt، Meta، صفحهٔ یونانی/ریاضی یا میان‌برها را روشن کنید تا در گوشهٔ کلیدها اضافه شوند.",
            "Settings → Activities → Extra keys. Switch on keys such as Alt, Meta, the Greek/math page or shortcuts, and they are added to the key corners.",
            "Ayarlar → Etkinlikler → Ek tuşlar. Alt, Meta, Yunanca/matematik sayfası veya kısayollar gibi tuşları açın; tuş köşelerine eklenirler.",
            "الإعدادات ← الأنشطة ← المفاتيح الإضافية. فعّل مفاتيح مثل Alt وMeta وصفحة اليونانية/الرياضيات أو الاختصارات، فتُضاف إلى زوايا المفاتيح.",
            "ڕێکخستنەکان ← چالاکییەکان ← کلیلی زیادە. کلیلی وەک Alt و Meta و پەڕەی یۆنانی/بیرکاری یان کورتەڕێکان چالاک بکە تا بخرێنە گۆشەی کلیلەکان."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_autocorrect,
        Tr("تصحیح خودکار", "Autocorrect", "Otomatik düzeltme", "التصحيح التلقائي", "ڕاستکردنەوەی خۆکار"),
        Tr("تنظیمات ← تصحیح خودکار. در Kafe به‌صورت پیش‌فرض خاموش است تا واژه‌ها بی‌اجازه عوض نشوند؛ اگر خواستید روشنش کنید. پیشنهاد کلمات در هر حال کار می‌کند.",
            "Settings → Autocorrect. Kafe ships with it off, so words are never changed without asking; switch it on if you like. Word suggestions work either way.",
            "Ayarlar → Otomatik düzeltme. Kafe'de varsayılan olarak kapalıdır, kelimeler izinsiz değişmez; isterseniz açın. Kelime önerileri her durumda çalışır.",
            "الإعدادات ← التصحيح التلقائي. يأتي معطّلًا في Kafe كي لا تتغيّر الكلمات دون إذن؛ فعّله إن شئت. اقتراحات الكلمات تعمل في الحالتين.",
            "ڕێکخستنەکان ← ڕاستکردنەوەی خۆکار. لە Kafe بە بنەڕەت کوژاوەتەوە تا وشەکان بێ ئیزن نەگۆڕدرێن؛ ئەگەر ویستت چالاکی بکە. پێشنیاری وشە لە هەردوو حاڵەتدا کار دەکات."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_clipboard,
        Tr("بریده‌دان (کلیپ‌بورد)", "Clipboard history", "Pano geçmişi", "سجل الحافظة", "مێژووی کلیپبۆرد"),
        Tr("تنظیمات ← بریده‌دان. تاریخچهٔ کپی‌ها را روشن کنید و تعداد و مدت نگه‌داری را تعیین کنید. در کیبورد: Ctrl را نگه دارید و آیکون کلیپ‌بورد را بزنید.",
            "Settings → Clipboard. Turn on the copy history and choose how many items to keep and for how long. On the keyboard: hold Ctrl and pick the clipboard icon.",
            "Ayarlar → Pano. Kopyalama geçmişini açın, kaç öğe ve ne kadar süre tutulacağını seçin. Klavyede: Ctrl'ye basılı tutun ve pano simgesini seçin.",
            "الإعدادات ← الحافظة. فعّل سجل النسخ واختر عدد العناصر ومدة الاحتفاظ بها. في لوحة المفاتيح: اضغط مطوّلًا على Ctrl واختر أيقونة الحافظة.",
            "ڕێکخستنەکان ← کلیپبۆرد. مێژووی لەبەرگرتنەوە چالاک بکە و ژمارە و ماوەی هەڵگرتن دیاری بکە. لە تەختەکلیلدا: Ctrl ڕابگرە و ئایکۆنی کلیپبۆرد هەڵبژێرە."),
    ),
    TutorialPage(
        R.drawable.kafe_tut2_backup,
        Tr("پشتیبان‌گیری و بازیابی", "Backup and restore", "Yedekleme ve geri yükleme",
            "النسخ الاحتياطي والاستعادة", "پاشەکەوت و گەڕاندنەوە"),
        Tr("تنظیمات ← پشتیبان‌گیری و بازیابی. با «برون‌بری پیکربندی» همهٔ تنظیمات را در یک فایل ذخیره کنید و در گوشی جدید با «وارد کردن پیکربندی» برگردانید. واژه‌نامه و تاریخچهٔ کلیپ‌بورد هم جدا ذخیره می‌شوند.",
            "Settings → Backup & restore. “Export configuration” saves every setting to one file; “Import configuration” brings it back on a new phone. The dictionary and clipboard history have their own export too.",
            "Ayarlar → Yedekleme ve geri yükleme. “Yapılandırmayı dışa aktar” tüm ayarları tek dosyaya kaydeder; yeni telefonda “İçe aktar” ile geri getirin. Sözlük ve pano geçmişinin de ayrı dışa aktarımı var.",
            "الإعدادات ← النسخ الاحتياطي والاستعادة. «تصدير الإعدادات» يحفظ كل الإعدادات في ملف واحد، و«استيراد الإعدادات» يعيدها على هاتف جديد. للقاموس وسجل الحافظة تصدير خاص بهما أيضًا.",
            "ڕێکخستنەکان ← پاشەکەوت و گەڕاندنەوە. «هەناردەکردنی ڕێکخستن» هەموو ڕێکخستنەکان لە یەک فایلدا پاشەکەوت دەکات؛ لە مۆبایلی نوێدا بە «هاوردەکردن» بیگەڕێنەوە. فەرهەنگ و مێژووی کلیپبۆرد هەناردەی جیایان هەیە."),
    ),
)

private fun t(lang: String, fa: String, en: String, tr: String, ar: String, ckb: String) =
    Tr(fa, en, tr, ar, ckb).get(lang)

/** The two tutorials. */
enum class KafeTutorialKind { BASIC, ADVANCED }

private val BASIC_TITLE = Tr("آموزش مقدماتی", "Basic tutorial", "Temel eğitim", "الشرح الأساسي", "فێرکاریی سەرەتایی")
private val ADVANCED_TITLE = Tr("آموزش پیشرفته", "Advanced tutorial", "İleri eğitim", "الشرح المتقدّم", "فێرکاریی پێشکەوتوو")

/** The tutorial dialog: one tip per page, swipe or use the buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KafeTutorialDialog(kind: KafeTutorialKind = KafeTutorialKind.BASIC, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = remember { KafeLanguage.current(context) }
    val PAGES = if (kind == KafeTutorialKind.BASIC) BASIC_PAGES else ADVANCED_PAGES
    val title = if (kind == KafeTutorialKind.BASIC) BASIC_TITLE else ADVANCED_TITLE
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.get(lang),
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
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(16.dp))
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

private val CARD_SUBTITLE = Tr(
    "نکات کیبورد و تنظیمات، صفحه به صفحه با عکس",
    "Keyboard and settings tips, page by page with pictures",
    "Klavye ve ayar ipuçları, resimli sayfalar halinde",
    "نصائح لوحة المفاتيح والإعدادات، صفحة بصفحة مع صور",
    "خاڵەکانی تەختەکلیل و ڕێکخستنەکان، پەڕە بە پەڕە بە وێنە",
)
private val BASIC_SHORT = Tr("مقدماتی", "Basic", "Temel", "أساسي", "سەرەتایی")
private val ADVANCED_SHORT = Tr("پیشرفته", "Advanced", "İleri", "متقدّم", "پێشکەوتوو")

/** Launcher card, under the keyboard languages: opens either tutorial. */
@Composable
fun KafeTutorialCard(titleColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = remember { KafeLanguage.current(context) }
    var open by remember { mutableStateOf<KafeTutorialKind?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                t(lang, "آموزش", "Tutorial", "Eğitim", "الشرح", "فێرکاری"),
                color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Text(CARD_SUBTITLE.get(lang), color = textColor, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { open = KafeTutorialKind.BASIC }, modifier = Modifier.weight(1f)) {
                    Text(BASIC_TITLE.get(lang))
                }
                OutlinedButton(onClick = { open = KafeTutorialKind.ADVANCED }, modifier = Modifier.weight(1f)) {
                    Text(ADVANCED_TITLE.get(lang))
                }
            }
        }
    }
    open?.let { kind -> KafeTutorialDialog(kind) { open = null } }
}

/** First item of Settings: both tutorials. */
@Composable
fun KafeTutorialSettingsItem(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = remember { KafeLanguage.current(context) }
    var open by remember { mutableStateOf<KafeTutorialKind?>(null) }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 26.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t(lang, "آموزش", "Tutorial", "Eğitim", "الشرح", "فێرکاری"),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(CARD_SUBTITLE.get(lang), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { open = KafeTutorialKind.BASIC }, modifier = Modifier.weight(1f)) {
                    Text(BASIC_SHORT.get(lang))
                }
                OutlinedButton(onClick = { open = KafeTutorialKind.ADVANCED }, modifier = Modifier.weight(1f)) {
                    Text(ADVANCED_SHORT.get(lang))
                }
            }
        }
    }
    open?.let { kind -> KafeTutorialDialog(kind) { open = null } }
}
