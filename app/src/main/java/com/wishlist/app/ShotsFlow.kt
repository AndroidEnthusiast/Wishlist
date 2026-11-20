package com.wishlist.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Screenshot to wish (owner, 20 November 2026; docs/decisions/screenshots.md): the screens. What they show and do is
 * ShotsState (ShotsModel.kt). The photo editor and Add a wish are the app's own, with "2 of 5" and Skip added.
 */

/**
 * Pictures shared into the app become wishes. [onDone] gets the closing message (null: nothing to say) and the list to
 * open with "Open the list" (a route key "m:…" / "s:…", null when the wishes went to more than one list).
 */
@Composable
internal fun ShotsFlow(d: Deep.Shots, onDone: (String?, String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { ShotsState(ctx, d.pictures, d.link, d.more) }
    // Reopens the editor after "Keep going" (its own Back already let go of photos picked inside it).
    var reopen by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { s.load() }
    LaunchedEffect(s.step) {
        if (s.step != ShotStep.DONE) return@LaunchedEffect
        val msg = s.doneMessage() ?: if (s.shots.all { it.failed })
            (if (s.several) tr("Couldn’t open these pictures. Save them to your phone first, then share them again.")
            else tr("Couldn’t open that picture. Save it to your phone first, then share it again."))
        else null
        onDone(msg, s.lists.singleOrNull()?.first?.let { routeOfSaveKey(it) })
    }

    when (s.step) {
        ShotStep.LOADING -> if (s.several) PickSheet(s, loading = true, onClose = { s.stop() })
        ShotStep.PICK -> PickSheet(s, loading = false, onClose = { s.stop() })
        ShotStep.PHOTO -> s.current?.file?.let { file ->
            key(s.index, reopen) {
                HeroEditor(
                    file, HeroEdit(), wish = true,
                    onSave = { chosen, e -> scope.launch { s.photoEdited(file, chosen, e) } },
                    onCancel = { s.cancel() },
                    step = if (s.several) (s.index + 1) to s.queue.size else null,
                    onSkip = if (s.several) ({ s.skip() }) else null,
                    notice = if (s.index > 0) s.notice else null
                )
            }
        }
        ShotStep.FORM -> key(s.index) {
            WishDialog(
                null, if (s.several) "" else s.link.orEmpty(), chooseList = true, startTarget = s.target,
                initialImage = s.cut.orEmpty(),
                step = if (s.several) (s.index + 1) to s.queue.size else null,
                onSkip = if (s.several) ({ s.skip() }) else null,
                onCancelAsk = if (s.several) ({ s.askStop = true }) else null,
                onSaved = { name -> s.savedHere(name) },
                // Only one screenshot, cancelled: nothing is added (its photo goes).
                onClose = { if (s.step == ShotStep.FORM) s.stop() }
            )
        }
        ShotStep.DONE -> {}
    }

    if (s.askStop) AlertDialog(
        onDismissRequest = { s.askStop = false },
        title = { Text(tr("Stop adding?")) },
        text = {
            Text(
                if (s.added == 1) tr("1 wish added, %d left. The wishes you saved stay.", s.left)
                else tr("%d wishes added, %d left. The wishes you saved stay.", s.added, s.left)
            )
        },
        confirmButton = { TextButton(onClick = { s.stop() }) { Text(tr("Stop")) } },
        dismissButton = { TextButton(onClick = { s.askStop = false; reopen++ }) { Text(tr("Keep going")) } }
    )
}

/** "m:…" for one of your own lists, "s:…" for a shared one, from a "Save to" key. */
internal fun routeOfSaveKey(key: String): String = MyLists.idOfSaveKey(key)?.let { "m:$it" } ?: "s:$key"

/** Several screenshots: which ones, and which list. */
@Composable
private fun PickSheet(s: ShotsState, loading: Boolean, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var newList by remember { mutableStateOf(false) }
    val targets = saveTargets(Repo.mineIds.map { it to (Repo.mineNames[it] ?: "My wishlist") }, Shared.lists)
    FormSheetHost(onClose) {
        FormSheet(
            onDismissRequest = onClose,
            title = { Text(if (s.shots.size == 1) tr("1 screenshot") else tr("%d screenshots", s.shots.size)) },
            text = {
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                tr("Next, you crop and name each one. Every wish is saved as soon as you tap Save, so you can stop at any time."),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                            )
                            SaveToField(targets, s.target, { s.target = it }, Modifier.fillMaxWidth(), onNew = { newList = true })
                            if (s.more) Text(
                                tr("Only the first %d are shown. Share the others afterwards.", Deep.Shots.MAX),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                            if (!loading && s.usable.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("%d picked", s.pickedCount), style = MaterialTheme.typography.titleSmall, color = cs.onSurface, modifier = Modifier.weight(1f))
                                val all = s.pickedCount == s.usable.size
                                TextButton(onClick = { s.pickAll(!all) }) { Text(if (all) tr("Select none") else tr("Select all")) }
                            }
                        }
                    }
                    if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(tr("Getting your screenshots…"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.padding(start = 12.dp))
                        }
                    } else items(s.shots) { shot -> ShotTile(shot) { s.toggle(shot) } }
                }
            },
            confirmButton = {
                val n = s.pickedCount
                Button(onClick = { s.begin() }, enabled = !loading && n > 0, modifier = Modifier.fillMaxWidth()) {
                    Text(if (n == 1) tr("Add 1 wish") else tr("Add %d wishes", n))
                }
            }
        )
    }
    if (newList) NewListDialog(
        onCreated = { id -> newList = false; s.target = MyLists.saveKey(id, false) },
        onClose = { newList = false }
    )
}

/** One screenshot: tap to tick or untick; one that couldn't be read says so and can't be ticked. */
@Composable
private fun ShotTile(shot: Shot, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val file = shot.file
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(shape).background(cs.surfaceVariant)
            .then(
                if (shot.picked) Modifier.border(BorderStroke(3.dp, cs.primary), shape)
                // A picture that couldn't be opened: an outline, so its message reads as a tile.
                else if (file == null) Modifier.border(BorderStroke(1.dp, cs.outlineVariant), shape) else Modifier
            )
            .toggleable(shot.picked, enabled = file != null, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center
    ) {
        if (file == null) {
            Text(
                tr("Couldn’t open this picture"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)
            )
        } else {
            WishImage(file, Modifier.fillMaxSize(), ContentScale.Crop)
            // The tick, top right: filled when picked, an empty ring when not.
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                    .background(if (shot.picked) cs.primary else cs.surface.copy(alpha = 0.85f))
                    .border(2.dp, if (shot.picked) cs.primary else cs.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (shot.picked) Icon(AppIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
