package com.wishlist.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
                    tr("“%1\$s” goes to the bin. After %2\$d days it’s gone for good.", shorten(ask.title), BIN_DAYS) +
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
        val r = snack.showToast(
            message = tr("Wish moved to the bin"),
            actionLabel = tr("Undo"),
            duration = SnackbarDuration.Short
        )
        if (r == SnackbarResult.ActionPerformed) DeleteFlow.undo(ctx) else DeleteFlow.finish(t)
    }
    val many = DeleteFlow.undoableMany
    LaunchedEffect(many) {
        if (many == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val r = snack.showToast(
            message = if (many.size == 1) tr("Wish moved to the bin") else tr("%d wishes moved to the bin", many.size),
            actionLabel = tr("Undo"),
            duration = SnackbarDuration.Short
        )
        if (r == SnackbarResult.ActionPerformed) DeleteFlow.undoMany(ctx) else DeleteFlow.finishMany(many)
    }
}

private fun shorten(s: String): String = if (s.length <= 40) s else s.take(39).trimEnd() + "…"
