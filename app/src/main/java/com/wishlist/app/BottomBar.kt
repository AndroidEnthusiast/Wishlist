package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/*
 * The bar at the bottom (since 2.65): quick access to what matters most, next to the side menu.
 * Home (the dashboard), New list, Add wish (just a +), Prices (price history of all your wishes), Friends (coming soon).
 * It shows on the dashboard, on a list, on Prices and on Friends; not on settings screens or the bin.
 */

@Composable
fun AppBottomBar(
    current: String,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onAdd: () -> Unit,
    onPrices: () -> Unit,
    onFriends: () -> Unit
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = if (look.classic) cs.surfaceContainer else cs.surface,
        modifier = if (look.design == "neo") Modifier.drawWithContent {
            drawContent()
            drawRect(look.line, Offset.Zero, Size(size.width, 2.dp.toPx()))
        } else Modifier
    ) {
        NavigationBarItem(
            selected = current == "home", onClick = onHome,
            icon = { Icon(AppIcons.Home, null) }, label = { Text(tr("Home"), maxLines = 1) }
        )
        NavigationBarItem(
            selected = false, onClick = onCreate,
            icon = { NewListIcon() }, label = { Text(tr("New list"), maxLines = 1) }
        )
        // Add wish: only a + in a filled circle, no words.
        NavigationBarItem(
            selected = false, onClick = onAdd,
            icon = {
                Box(
                    Modifier.size(40.dp).clip(if (look.design == "neo") RoundedCornerShape(10.dp) else CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Add, tr("Add wish"), tint = cs.onPrimary, modifier = Modifier.size(26.dp))
                }
            }
        )
        NavigationBarItem(
            selected = current == "prices", onClick = onPrices,
            icon = { PriceIcon(LocalContentColor.current) }, label = { Text(tr("Prices"), maxLines = 1) }
        )
        NavigationBarItem(
            selected = current == "friends", onClick = onFriends,
            icon = { Icon(AppIcons.Person, null) }, label = { Text(tr("Friends"), maxLines = 1) }
        )
    }
}

/** A list with a small + on its corner. */
@Composable
private fun NewListIcon() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(24.dp)) {
        Icon(AppIcons.ListIcon, tr("New list"), modifier = Modifier.size(24.dp))
        Box(
            Modifier.align(Alignment.BottomEnd).offset(5.dp, 4.dp).size(13.dp).clip(CircleShape).background(cs.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Add, null, tint = cs.onPrimary, modifier = Modifier.size(11.dp))
        }
    }
}

/** A small line chart going down: price history. */
@Composable
fun PriceIcon(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        val p = Path().apply {
            moveTo(3f * u, 7f * u)
            lineTo(9f * u, 13f * u)
            lineTo(13f * u, 9f * u)
            lineTo(21f * u, 17f * u)
        }
        drawPath(p, tint, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The arrow head at the end.
        val a = Path().apply {
            moveTo(15.5f * u, 17f * u)
            lineTo(21f * u, 17f * u)
            lineTo(21f * u, 11.5f * u)
        }
        drawPath(a, tint, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(3f * u, 21f * u), Offset(21f * u, 21f * u), 1.2f * u, StrokeCap.Round)
    }
}

/** "New list": your own wishlist or a group wishlist (the same forms as the + buttons on the dashboard). */
@Composable
fun CreateChoiceDialog(onMine: () -> Unit, onGroup: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Create")) },
        text = {
            Column {
                Choice(tr("New wishlist"), tr("A wishlist of your own, to share with friends")) { onMine() }
                Choice(tr("New group wishlist"), tr("Collect gift ideas for someone together with other givers")) { onGroup() }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun Choice(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
