package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "Delete wish?" and the "Wish moved to the bin · Undo" message. Placed once, in the main screen. The question (owner,
 * 27 November 2026, with a preview): the wish's photo centred on top (several: up to three fanned), the title, the
 * wish's name, the tickbox "Turn on one-tap delete" (counts only with Delete), a red Delete and Cancel. No animation.
 */
@Composable
fun DeleteHost(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val ask = DeleteFlow.asking
    if (ask != null) DeleteQuestion(ask, onDelete = { oneTap -> DeleteFlow.confirm(ctx, oneTap) }) { DeleteFlow.cancel() }

    val t = DeleteFlow.undoable
    LaunchedEffect(t) {
        if (t == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val r = snack.showToast(message = binMessage(tr("Wish moved to the bin")), actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) DeleteFlow.undo(ctx) else DeleteFlow.finish(t)
    }
    val many = DeleteFlow.undoableMany
    LaunchedEffect(many) {
        if (many == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val r = snack.showToast(
            message = binMessage(if (many.size == 1) tr("Wish moved to the bin") else tr("%d wishes moved to the bin", many.size)),
            actionLabel = tr("Undo"),
            duration = SnackbarDuration.Short
        )
        if (r == SnackbarResult.ActionPerformed) DeleteFlow.undoMany(ctx) else DeleteFlow.finishMany(many)
    }
}

/** The message after a delete; right after the tickbox turned One-tap delete on, once, where to switch it off. */
private fun binMessage(moved: String): String {
    if (!DeleteFlow.justTurnedOn) return moved
    DeleteFlow.toldOneTap()
    return moved + "\n" + tr("One-tap delete is on: App settings")
}

/** The question itself ([onDelete] gets whether "Turn on one-tap delete" is ticked). */
@Composable
internal fun DeleteQuestion(ask: DeleteFlow.Ask, onDelete: (Boolean) -> Unit, onCancel: () -> Unit) {
    var oneTap by remember(ask) { mutableStateOf(false) }
    val ws = ask.wishes
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { DeletePhotos(ws) },
        title = { Text(if (ws.size == 1) tr("Delete wish?") else tr("Delete %d wishes?", ws.size)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(deleteNames(ws), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                // The whole row is the tap target (48 dp tall), the label says it lasts (owner chose the words).
                Row(
                    Modifier.heightIn(min = 48.dp).toggleable(oneTap, role = Role.Checkbox) { oneTap = it },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(checked = oneTap, onCheckedChange = null)
                    Text(tr("Turn on one-tap delete"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = { TextButton(danger = true, onClick = { onDelete(oneTap) }) { Text(tr("Delete")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancel")) } }
    )
}

/** The wish's photo, big and centred; several: up to three, fanned. A wish without one shows the gift placeholder. */
@Composable
private fun DeletePhotos(ws: List<Wish>) {
    if (ws.size == 1) { WishThumb(ws[0], 104.dp); return }
    val shown = ws.take(3)
    Box(Modifier.size(width = 84.dp + 32.dp * (shown.size - 1), height = 96.dp), contentAlignment = Alignment.CenterStart) {
        shown.forEachIndexed { i, w ->
            val turn = if (shown.size == 1) 0f else -8f + 16f * i / (shown.size - 1)
            Box(Modifier.offset(x = 32.dp * i).rotate(turn)) { WishThumb(w, 84.dp) }
        }
    }
}

/** "Teapot"; "Teapot and Mug"; "Teapot, Mug and 3 more". */
internal fun deleteNames(ws: List<Wish>): String = when (ws.size) {
    0 -> ""
    1 -> ws[0].label
    2 -> tr("%1\$s and %2\$s", ws[0].label, ws[1].label)
    else -> tr("%1\$s, %2\$s and %3\$d more", ws[0].label, ws[1].label, ws.size - 2)
}
