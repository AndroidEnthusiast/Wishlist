package com.wishlist.app

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Mark as owned" on a wish of your own (the tick on large cards, the ⋮ menu of two per row). Since 2.105 (owner) the
 * main screen then says "Wish marked as owned" (or "Wish marked as not owned") for 5 seconds, with Undo ([OwnedHost]).
 */
object OwnedFlow {
    /** The latest change that can still be undone: the wish and whether it became owned. */
    var changed by mutableStateOf<Pair<Long, Boolean>?>(null)
        private set

    fun toggle(c: Context, w: Wish) = set(c, w.id, !w.got)

    private fun set(c: Context, id: Long, on: Boolean) {
        val w = Repo.wishes.firstOrNull { it.id == id } ?: return
        if (w.got == on) return
        Repo.replace(c, w.copy(got = on))
        changed = id to on
    }

    fun undo(c: Context) {
        val (id, on) = changed ?: return
        changed = null
        val w = Repo.wishes.firstOrNull { it.id == id } ?: return
        if (w.got == on) Repo.replace(c, w.copy(got = !on))
    }

    fun done() {
        changed = null
    }
}

/**
 * "I have it" → "Move it to Received gifts" (owner, 12 November 2026): the wish (every copy of it) leaves your lists
 * for Received gifts; the main screen says "Moved to Received gifts" with Undo ([OwnedHost]).
 */
object ReceivedFlow {
    /** The wish that just moved, for Undo. */
    var moved by mutableStateOf<Long?>(null)
        private set

    /** A wish of the list that is open. */
    fun receive(c: Context, w: Wish) {
        if (Repo.hasCopies(w.id)) Repo.receiveEverywhere(c, w.id) else Repo.receive(c, Repo.openMine, setOf(w.id))
        moved = w.id
    }

    /** A wish from All your wishes (on one or more lists). */
    fun everywhere(c: Context, id: Long) {
        Repo.receiveEverywhere(c, id)
        moved = id
    }

    /** Back on its list (the original's list; copies don't come back). */
    fun undo(c: Context) {
        val id = moved ?: return
        moved = null
        Repo.unreceive(c, id)
    }

    fun done() {
        moved = null
    }
}

/** The message with Undo after a wish was marked as owned or not. Placed once, in the main screen, like PrivateHost. */
@Composable
fun OwnedHost(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val ch = OwnedFlow.changed
    LaunchedEffect(ch) {
        if (ch == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val timer = launch { delay(5000); snack.currentSnackbarData?.dismiss() }
        val r = snack.showToast(
            message = if (ch.second) tr("Wish marked as owned") else tr("Wish marked as not owned"),
            actionLabel = tr("Undo"),
            duration = SnackbarDuration.Indefinite
        )
        timer.cancel()
        if (r == SnackbarResult.ActionPerformed) OwnedFlow.undo(ctx) else if (OwnedFlow.changed === ch) OwnedFlow.done()
    }
    val rc = ReceivedFlow.moved
    LaunchedEffect(rc) {
        if (rc == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val r = snack.showToast(tr("Moved to Received gifts"), actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) ReceivedFlow.undo(ctx) else ReceivedFlow.done()
    }
}
