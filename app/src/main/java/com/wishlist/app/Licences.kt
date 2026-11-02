package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * App settings > About > Licences. Every icon, font and library in the app is our own or free to use under an open
 * licence; those licences ask that their text comes with the app, so here it is.
 *  - Icons: Google's Material Icons (Apache License 2.0). The other icons (menu, view buttons, prices, wishlist,
 *    gift, crop, light, new photo, colour ball) are drawn in this app's own code.
 *  - Fonts (2.97): Google Sans (built in) and Figtree, Rajdhani, Share Tech Mono, Bungee, Bricolage Grotesque, Caveat
 *    (downloaded from Google Fonts): SIL Open Font License 1.1.
 *  - Libraries: AndroidX / Jetpack Compose, Kotlin, Coil (Apache License 2.0).
 * The full texts are in res/raw (license_apache.txt, license_ofl.txt).
 */

/** The licences: who made what, and the full licence texts (tap to show). */
@Composable
fun LicencesDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var full by remember { mutableStateOf<Int?>(null) }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Licences")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("Wishlist uses its own drawings and these free, open-source parts:"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Credit(tr("Icons"), "Material Icons, Google — Apache License 2.0")
                Credit(
                    tr("Fonts"),
                    "Google Sans, Figtree, Rajdhani, Share Tech Mono, Bungee, Bricolage Grotesque, Caveat — SIL Open Font License 1.1"
                )
                Credit(tr("Building blocks"), "AndroidX, Jetpack Compose, Kotlin, Coil — Apache License 2.0")
                Text(
                    tr("Our own icons: menu, view buttons, prices, wishlist, gift, crop, light, new photo and the colour ball."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                TextButton(onClick = { full = if (full == R.raw.license_apache) null else R.raw.license_apache }) {
                    Text(tr("Apache License 2.0 (full text)"))
                }
                TextButton(onClick = { full = if (full == R.raw.license_ofl) null else R.raw.license_ofl }) {
                    Text(tr("SIL Open Font License 1.1 (full text)"))
                }
                full?.let { res ->
                    val text = remember(res) {
                        try {
                            ctx.resources.openRawResource(res).bufferedReader().use { it.readText() }
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, color = cs.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } }
    )
}

@Composable
private fun Credit(what: String, who: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(what, style = MaterialTheme.typography.labelLarge)
        Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
