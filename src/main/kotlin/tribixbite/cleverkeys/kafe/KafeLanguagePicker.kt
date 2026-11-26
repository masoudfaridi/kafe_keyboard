package tribixbite.cleverkeys.kafe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kafe Keyboard: the language dropdown at the top of the launcher screen
 * (فارسی / English). Picking one switches the whole app and the keyboard.
 */
@Composable
fun KafeLanguagePicker(chipColor: Color, tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = remember { KafeLanguage.current(context) }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .background(chipColor, RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(start = 14.dp, end = 8.dp)
                .semantics { contentDescription = "Language / زبان" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(KafeLanguage.displayName(current), color = tint, fontSize = 15.sp)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (lang in KafeLanguage.SUPPORTED) {
                DropdownMenuItem(
                    text = { Text(KafeLanguage.displayName(lang)) },
                    trailingIcon = {
                        if (lang == current) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        expanded = false
                        if (lang != current) KafeLanguage.set(context, lang)
                    }
                )
            }
        }
    }
}
