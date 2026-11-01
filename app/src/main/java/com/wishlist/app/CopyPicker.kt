package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Added on Thursday 01-12-26": when a wish was added (the day's name in the app's language). A wish's id is the moment
 * it was made, so that is the date (a copy on another list keeps the date of the original). Null for an id that
 * isn't a plausible moment.
 */
fun addedOn(w: Wish): String? {
    val t = w.id
    if (t < 1_400_000_000_000L || t > System.currentTimeMillis() + 86_400_000L) return null
    val locale = if (Lang.code == "nl") Locale("nl", "NL") else Locale.UK
    return tr("Added on %s", SimpleDateFormat("EEEE dd-MM-yy", locale).format(Date(t)))
}

/** The "Added on" line under a wish: small, quiet and in italics. */
@Composable
fun AddedOnText(w: Wish, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val s = addedOn(w) ?: return
    Text(s, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = color, modifier = modifier)
}

/** True when your other lists (all but [exceptId]) have any wish to copy. */
fun canCopyFromOthers(c: android.content.Context, exceptId: String?): Boolean =
    Repo.allMine(c).any { it.id != exceptId && it.wishes.isNotEmpty() }

/**
 * Choosing wishes from your other lists, to copy into a list ([targetId], or null for one that is being made).
 * Each of your other lists is a heading with its wishes and a tick box; group wishlists are not offered (they are
 * gift ideas for someone else). Wishes the target already has are left out.
 */
@Composable
fun CopyWishesDialog(targetId: String?, onDone: (List<Wish>) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val lists = remember { Repo.allMine(ctx) }
    val have = lists.firstOrNull { it.id == targetId }?.wishes?.map { it.id }?.toSet().orEmpty()
    val sources = lists.filter { it.id != targetId }
        .map { d -> d to d.wishes.filter { it.id !in have } }
        .filter { it.second.isNotEmpty() }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    val byId = sources.flatMap { it.second }.associateBy { it.id }
    fun toggle(id: Long) {
        picked = if (id in picked) picked - id else picked + id
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The title on the left, Cancel on the right.
                    Text(
                        tr("Add wishes from your other lists"), style = MaterialTheme.typography.titleMedium,
                        fontWeight = LocalLook.current.titleWeight, maxLines = 2,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    TextButton(onClick = onClose) { Text(tr("Cancel")) }
                }
                Text(
                    tr("Choose wishes from your other lists to copy here."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (sources.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(tr("Your other lists have no wishes to add yet."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                        sources.forEach { (d, wishes) ->
                            item(key = "h_" + d.id) {
                                Text(
                                    if (d.name == "My wishlist") tr(d.name) else d.name,
                                    style = MaterialTheme.typography.titleSmall, color = cs.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                                )
                            }
                            wishes.forEach { w ->
                                item(key = d.id + "_" + w.id) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable { toggle(w.id) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = w.id in picked, onCheckedChange = { toggle(w.id) })
                                        Box(
                                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                                .lookMat(LocalLook.current, RoundedCornerShape(10.dp), LocalLook.current.pictureBg(w.id, false)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (w.image.isNotBlank()) {
                                                AsyncImage(
                                                    model = w.image, contentDescription = null, contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize().padding(3.dp)
                                                )
                                            } else Text("🎁", fontSize = 20.sp)
                                        }
                                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                            Text(
                                                w.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis
                                            )
                                            w.price?.let {
                                                Text(money(it, w.currency), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { onDone(picked.mapNotNull { byId[it] }) }, enabled = picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(if (picked.size == 1) tr("Add 1 wish") else tr("Add %d wishes", picked.size))
                }
            }
        }
    }
}
