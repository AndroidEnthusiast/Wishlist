package com.wishlist.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * An exchange's dialogs (split out of ExchangeScreen.kt in the rebuild, owner 19 November 2026; MAP "How a screen is
 * built"): invite friends, change my wishlist, settings, leave, a rule, draw names (and without those who haven't
 * joined), reveal, add someone late, remove someone, and the anonymous chats. What is open is in [ExchangeScreenState];
 * what they change goes through [ExchangeModel] ([act] shows the result as a message).
 */
@Composable
internal fun ExchangeScreenDialogs(
    x: Exchange, gid: String, st: ExchangeScreenState, snack: SnackbarHostState,
    act: (suspend () -> String?) -> Unit, draw: (Boolean) -> Unit, onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ex = rememberExchange()
    if (st.inviting) GxInviteDialog(x, snack) { st.inviting = false }
    if (st.changing) {
        val ask = remember { GxShowAsk() }
        ListPickDialog(
            title = tr("Change my wishlist"), confirm = tr("Save"), current = Exchanges.myListId(ctx, x),
            onClose = { st.changing = false }, note = gxListNote
        ) { id, fresh ->
            if (!ask.ok(ctx, id)) return@ListPickDialog tr("Pick another wishlist, or make a new one.")
            ex.setList(x.id, id)
            if (fresh) try { ex.listToFriends(id) } catch (e: Exception) {}
            st.changing = false
            snack.toastSoon(tr("“%s” is in the exchange now", Repo.mineName(id)))
            null
        }
        ask.Host()
    }
    if (st.settings) ExchangeSettingsDialog(x, snack, onCancelled = { st.settings = false; onBack() }) { st.settings = false }
    if (st.leaving) GxLeaveDialog(x, snack, onLeft = { st.leaving = false; onBack() }) { st.leaving = false }
    if (st.addingRule) GxRuleDialog(x) { st.addingRule = false }
    if (st.drawAsk) AlertDialog(
        onDismissRequest = { st.drawAsk = false },
        title = { Text(tr("Draw names now?")) },
        text = { Text(tr("Everyone gets one name. Rules can’t change after this.")) },
        confirmButton = { TextButton(onClick = { st.drawAsk = false; draw(false) }) { Text(tr("Draw names")) } },
        dismissButton = { TextButton(onClick = { st.drawAsk = false }) { Text(tr("Cancel")) } }
    )
    st.pendingAsk?.let { names ->
        AlertDialog(
            onDismissRequest = { st.pendingAsk = null },
            title = { Text(if (names.size == 1) tr("%s hasn’t joined yet", names[0]) else tr("%d people haven’t joined yet", names.size)) },
            text = { Text(tr("Draw without them? They can still join later and get a name.")) },
            confirmButton = { TextButton(onClick = { st.pendingAsk = null; draw(true) }) { Text(tr("Draw without them")) } },
            dismissButton = { TextButton(onClick = { st.pendingAsk = null }) { Text(tr("Wait")) } }
        )
    }
    if (st.revealAsk) AlertDialog(
        onDismissRequest = { st.revealAsk = false },
        title = { Text(tr("Show everyone who had whom?")) },
        text = { Text(tr("Everyone in the exchange sees all the pairs.")) },
        confirmButton = {
            TextButton(onClick = {
                st.revealAsk = false
                act { if (ex.reveal(gid)) { st.confetti++; null } else tr("You can reveal after the exchange date.") }
            }) { Text(tr("Reveal")) }
        },
        dismissButton = { TextButton(onClick = { st.revealAsk = false }) { Text(tr("Cancel")) } }
    )
    st.adding?.let { m ->
        AlertDialog(
            onDismissRequest = { st.adding = null },
            title = { Text(tr("Add %s to the draw?", m.name.ifBlank { tr("them") })) },
            text = { Text(tr("Everyone has seen their name, so one person gets %s instead. They’ll be told.", m.name.ifBlank { tr("them") })) },
            confirmButton = { TextButton(onClick = { st.adding = null; act { ex.addLate(gid, m.id); null } }) { Text(tr("Add")) } },
            dismissButton = { TextButton(onClick = { st.adding = null }) { Text(tr("Cancel")) } }
        )
    }
    st.removing?.let { m ->
        AlertDialog(
            onDismissRequest = { st.removing = null },
            title = { Text(tr("Remove %s?", m.name.ifBlank { tr("them") })) },
            text = {
                Text(
                    if (x.drawn) tr("They are told. Only the person who drew them gets a new name.")
                    else tr("They are told. Their wishlist stays theirs.")
                )
            },
            confirmButton = {
                TextButton(danger = true, onClick = {
                    st.removing = null
                    act { ex.remove(gid, m.id); tr("%s was removed", m.name.ifBlank { tr("Someone") }) }
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(onClick = { st.removing = null }) { Text(tr("Cancel")) } }
        )
    }
    st.chatSide?.let { side -> GxChatScreen(x, side) { st.chatSide = null; scope.launch { try { Exchanges.refresh(ctx) } catch (e: Exception) {} } } }
}
