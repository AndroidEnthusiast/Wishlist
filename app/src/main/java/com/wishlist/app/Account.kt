package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * You and your settings (owner, 17 November 2026, plan step 4: the pop-up "showing a few items and the rest hidden"
 * looked cheap): a tall sheet from your picture top right, with an X. A card with your photo, name and About me (tap:
 * Profile) and Share (your friend code); four tiles for the places you visit (Gifts I reserved, Received gifts,
 * Notifications, Bin); rows for Theme and App settings; the version at the bottom. Nothing from before went missing.
 */
@Composable
fun AccountSheet(
    onClose: () -> Unit, onProfile: () -> Unit, onReserved: () -> Unit, onReceived: () -> Unit,
    onBin: () -> Unit, onSettings: () -> Unit, onNotes: () -> Unit = {}, onTheme: () -> Unit = onSettings
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
    }
    // The tiles one clear step lighter (dark) or darker (light) than the sheet they sit on.
    val tile = androidx.compose.ui.graphics.lerp(sheetColor(look, cs.surface), cs.onSurface, if (look.dark) 0.08f else 0.05f)
    val shape = if (look.design == "quiet") Squircle(20.dp) else RoundedCornerShape(16.dp)
    FormSheetHost(onClose) {
        val host = LocalFormHost.current!!
        val tall = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
        Column(Modifier.fillMaxWidth().height(tall)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) {
                    IconButton(onClick = host.close, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(AppIcons.Close, tr("Close")) }
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // You: photo, name and About me; tap for Profile. Share gives your friend code.
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(tile).clickable(onClick = onProfile).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(Friends.myPhoto, Shared.lastName, 56.dp)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            Friends.myBio.ifBlank { tr("Profile") }, style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    val code = Friends.state?.code.orEmpty()
                    if (Config.enabled && code.isNotEmpty()) TextButton(onClick = { Friends.shareCode(ctx, code) }) { Text(tr("Share")) }
                }
                @Composable fun Tile(icon: ImageVector, label: String, onClick: () -> Unit, m: Modifier) {
                    Column(
                        m.heightIn(min = 104.dp).clip(shape).background(tile).clickable(onClick = onClick).padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(icon, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(AppIcons.Bag, tr("Gifts I reserved"), onReserved, Modifier.weight(1f))
                    Tile(AppIcons.Gift, tr("Received gifts"), onReceived, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(AppIcons.Notifications, tr("Notifications"), onNotes, Modifier.weight(1f))
                    Tile(AppIcons.Delete, tr("Bin"), onBin, Modifier.weight(1f))
                }
                @Composable fun RowItem(icon: ImageVector, label: String, onClick: () -> Unit) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(tile).clickable(onClick = onClick)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = cs.primary, modifier = Modifier.size(24.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp).weight(1f))
                        Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant)
                    }
                }
                RowItem(AppIcons.Palette, tr("Theme"), onTheme)
                RowItem(AppIcons.Settings, tr("App settings"), onSettings)
                if (version.isNotEmpty()) Text(
                    tr("Version %s", version), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
