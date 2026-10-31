package com.wishlist.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** The "Are you sure?" question and the "Deleted ... Undo" message. Placed once, in the main screen. */
@Composable
fun DeleteHost(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val ask = DeleteFlow.asking
    if (ask != null) {
        AlertDialog(
            onDismissRequest = { DeleteFlow.cancel() },
            title = { Text(tr("Delete this wish?")) },
            text = {
                Text(
                    tr("“%1\$s” goes to the bin. You can restore it from there for %2\$d days.", shorten(ask.title), BIN_DAYS) +
                        if (Shared.active != null) " " + tr("Everyone on the list can see it in the bin.") else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { DeleteFlow.confirm(ctx) }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { DeleteFlow.cancel() }) { Text(tr("Cancel")) } }
        )
    }

    val t = DeleteFlow.undoable
    LaunchedEffect(t) {
        if (t == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val r = snack.showSnackbar(
            message = tr("Moved “%s” to the bin", shorten(t.wish.title)),
            actionLabel = tr("Undo"),
            duration = SnackbarDuration.Long
        )
        if (r == SnackbarResult.ActionPerformed) DeleteFlow.undo(ctx) else DeleteFlow.finish(ctx, t)
    }
}

private fun shorten(s: String): String = if (s.length <= 40) s else s.take(39).trimEnd() + "…"
