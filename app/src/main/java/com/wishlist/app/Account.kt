package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
 * The profile sheet (owner, 7 November 2026: the side menu is gone for a cleaner screen). Your picture at the top right
 * opens it from the bottom: your photo and name, then Profile, Gifts I reserved, Received gifts, Bin and App settings,
 * and the app's version. Gift exchange moved to Home; "Create list" was already on Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    onClose: () -> Unit, onProfile: () -> Unit, onReserved: () -> Unit, onReceived: () -> Unit,
    onBin: () -> Unit, onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            // You: photo and name; tapping them opens Profile too.
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(Friends.myPhoto, Shared.lastName, 48.dp)
                Text(
                    Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp).weight(1f)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = cs.outlineVariant)
            SheetRow({ Icon(AppIcons.Person, null, tint = cs.onSurfaceVariant) }, tr("Profile"), onProfile)
            SheetRow({ Icon(AppIcons.Bag, null, tint = cs.onSurfaceVariant) }, tr("Gifts I reserved"), onReserved)
            SheetRow({ Icon(AppIcons.Gift, null, tint = cs.onSurfaceVariant) }, tr("Received gifts"), onReceived)
            SheetRow({ Icon(AppIcons.Delete, null, tint = cs.onSurfaceVariant) }, tr("Bin"), onBin)
            SheetRow({ Icon(AppIcons.Settings, null, tint = cs.onSurfaceVariant) }, tr("App settings"), onSettings)
            if (version.isNotEmpty()) Text(
                tr("Version %s", version), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp)
            )
        }
    }
}
