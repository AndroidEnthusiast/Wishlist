package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Every empty or error page (owner, 21 November 2026: "icon, line, one button"; docs/STYLE.md "Empty and error
 * states"): a soft [icon], one kind line saying what happened ([text]), and one button for the next step ([action]:
 * Try again, Add a wish, Back). Never a line alone. The button is as wide as its words, like every screen button.
 */
@Composable
fun EmptyState(
    icon: ImageVector, text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier,
    // One of the app's objects at rest instead of the icon, where one fits (owner, 24 November 2026; Objects.kt).
    art: (@Composable () -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            if (art != null) art() else Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(36.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(action) }
    }
}
