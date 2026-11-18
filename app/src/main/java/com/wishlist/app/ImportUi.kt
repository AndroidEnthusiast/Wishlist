package com.wishlist.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * The Import sheet (owner, 18 November 2026; docs/decisions/import.md, logic in Import.kt): paste a list link or the
 * list as text → the check screen (tick boxes, where to) → done (with the warning that reservations don't come along).
 * One full-width form sheet whose content changes, so it never jumps.
 */

private enum class Step { PASTE, READING, NOTHING, CHECK, DONE }

/**
 * [start]: a link or text shared into the app (empty from the + menu). [onOpen]: opens one of your lists.
 */
@Composable
fun ImportSheet(start: String, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val go = LocalGo.current
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf(start) }
    var step by remember { mutableStateOf(Step.PASTE) }
    var tipFor by remember { mutableStateOf<String?>(null) }
    var read by remember { mutableStateOf<ImportRead?>(null) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var dups by remember { mutableStateOf(setOf<Int>()) }
    // Where to: null = a new list (named [newName]), else one of your own lists.
    var target by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var nameMissing by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(0) }
    var addedTo by remember { mutableStateOf("") }
    var browsing by remember { mutableStateOf<String?>(null) }
    var linkRead by remember { mutableStateOf("") }

    fun show(r: ImportRead) {
        read = r
        newName = r.name.ifBlank { Import.siteName(r.site).let { s -> if (s.isEmpty()) tr("Imported wishes") else tr("From %s", s) } }
        target = null
        dups = emptySet()
        picked = r.wishes.indices.toSet()
        step = Step.CHECK
    }

    fun readInput() {
        val t = input.trim()
        if (t.isEmpty()) return
        val link = Regex("https?://\\S+").find(t)?.value
        val oneLink = link != null && t.lines().count { it.isNotBlank() } <= 2 && Regex("https?://\\S+").findAll(t).count() == 1 &&
            (Import.isListLink(link) || t.replace(link, "").trim().split(Regex("\\s+")).size <= 8)
        if (!oneLink) {
            // Text from notes (or several links): each line a wish.
            val ws = Import.parseText(t)
            if (ws.isEmpty()) return
            show(ImportRead("", "", "", ws))
            return
        }
        linkRead = link!!
        step = Step.READING
        scope.launch {
            val r = try { ImportReaders.readHidden(ctx, link) } catch (e: Exception) { null }
            if (r == null || r.wishes.isEmpty()) {
                Import.reportNothing(r?.site ?: Import.siteKey(link), r?.version ?: 0)
                step = Step.NOTHING
            } else show(r)
        }
    }

    fun doImport() {
        val r = read ?: return
        val chosen = r.wishes.filterIndexed { i, _ -> i in picked }
        if (chosen.isEmpty()) return
        val listId = target ?: run {
            if (newName.isBlank()) { nameMissing = true; return }
            val id = Repo.createMine(ctx, newName, "")
            Import.occasionOf(r.date, newName)?.let { Occasions.setMine(ctx, id, it) }
            ListVisibility.startShared(ctx, id)
            id
        }
        added = Import.add(ctx, listId, chosen)
        addedTo = listId
        step = Step.DONE
    }

    FormSheetHost(onClose) {
        FormSheet(
            onDismissRequest = onClose,
            title = { Text(tr("Import")) },
            text = {
                when (step) {
                    Step.PASTE, Step.READING -> PastePart(input, { input = it }, step == Step.READING, tipFor) { tipFor = if (tipFor == it) null else it }
                    Step.NOTHING -> NothingPart(
                        site = Import.siteName(Import.siteKey(linkRead)),
                        onOpenPage = { browsing = linkRead },
                        onBack = { step = Step.PASTE }
                    )
                    Step.CHECK -> read?.let { r ->
                        // Duplicates depend on the list chosen: looked at again when it changes.
                        val existing = remember(target, Repo.mineVersion, Repo.myWishes) { target?.let { Repo.mine(ctx, it).wishes }.orEmpty() }
                        val d = remember(existing, r) { Import.duplicates(r.wishes, existing) }
                        // A wish already on the chosen list starts unticked; one that no longer is gets its tick back.
                        androidx.compose.runtime.LaunchedEffect(d) {
                            picked = picked - d + (dups - d).intersect(r.wishes.indices.toSet())
                            dups = d
                        }
                        CheckPart(
                            r, picked, d, target, newName, nameMissing,
                            onPick = { i -> picked = if (i in picked) picked - i else picked + i },
                            onAll = { all -> picked = if (all) r.wishes.indices.toSet() else emptySet() },
                            onTarget = { target = it; nameMissing = false },
                            onName = { newName = it.take(60); if (it.isNotBlank()) nameMissing = false }
                        )
                    }
                    Step.DONE -> DonePart(added, Repo.mineName(addedTo), Import.siteName(read?.site.orEmpty()), read?.wishes?.any { it.url.isNotBlank() } == true) {
                        onClose(); onOpen(addedTo)
                    }
                }
            },
            confirmButton = {
                when (step) {
                    Step.PASTE -> Button(onClick = { readInput() }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(tr("Continue")) }
                    Step.READING -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(tr("Reading the list…")) }
                    Step.NOTHING -> Button(onClick = { browsing = linkRead }, modifier = Modifier.fillMaxWidth()) { Text(tr("Open the page")) }
                    Step.CHECK -> Button(onClick = { doImport() }, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (picked.size == 1) tr("Import 1 wish") else tr("Import %d wishes", picked.size))
                    }
                    Step.DONE -> {
                        val site = Import.siteName(read?.site.orEmpty())
                        if (site.isNotEmpty()) Button(onClick = { NameGate.ensure { onClose(); go(Deep.ShareCard(addedTo)) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Send my new list to friends"))
                        } else Button(onClick = { onClose(); onOpen(addedTo) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Open the list")) }
                    }
                }
            }
        )
    }
    browsing?.let { url ->
        // The page in the app's own browser, with "Import this page" (owner: always a way through).
        ShopBrowser(
            Shop(Import.siteName(Import.siteKey(url)).ifEmpty { tr("List") }, url),
            onClose = { browsing = null },
            onFound = { _, _ -> },
            onImport = { r -> browsing = null; show(r) }
        )
    }
}

@Composable
internal fun PastePart(input: String, onInput: (String) -> Unit, reading: Boolean, tipFor: String?, onTip: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            tr("Paste the link to a wishlist, or the list itself from your notes (one wish per line). The list must be public."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        // The sites with each one's own icon, loaded live like Discover's shop tiles (no copied logos, Rule 7).
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(Import.SITES.size) { i ->
                val s = Import.SITES[i]
                Column(
                    Modifier.width(72.dp).clickable(onClickLabel = tr("How to find the link")) { onTip(s.name) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShopIcon(s, 52)
                    Text(
                        s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (tipFor == s.name) cs.primary else cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        tipFor?.let { Text(Import.tip(it), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface) }
        OutlinedTextField(
            input, { onInput(it.take(20000)) }, enabled = !reading,
            label = { Text(tr("Link or list")) },
            placeholder = { Text(tr("https://… or one wish per line")) },
            minLines = 2, maxLines = 8, modifier = Modifier.fillMaxWidth()
        )
        Text(
            tr("Also works with most shop pages. You choose what to keep before anything is added."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        if (reading) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(tr("This can take up to half a minute."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
internal fun NothingPart(site: String, onOpenPage: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("No wishes found yet"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        Text(
            if (site.isNotEmpty()) tr("%s didn’t show the wishes to the app. Is the list public? Check your connection too.", site)
            else tr("This page didn’t show any wishes to the app. Is the list public? Check your connection too."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
        )
        Text(
            tr("Open the page here, scroll until you see your wishes, then tap Import this page."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
        )
        Text(
            tr("Or copy your wishes as text and paste them instead (one per line)."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        OutlinedButton(onClick = onBack) { Text(tr("Paste something else")) }
    }
}

@Composable
internal fun CheckPart(
    r: ImportRead, picked: Set<Int>, dups: Set<Int>, target: String?, newName: String, nameMissing: Boolean,
    onPick: (Int) -> Unit, onAll: (Boolean) -> Unit, onTarget: (String?) -> Unit, onName: (String) -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val mine = remember(Repo.mineIds, Repo.mineVersion) { Repo.mineIds }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(tr("Add to"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.padding(bottom = 4.dp))
        }
        item {
            ChoiceRow(target == null, tr("New wishlist")) { onTarget(null) }
            if (target == null) OutlinedTextField(
                newName, onName, singleLine = true, label = { Text(tr("Wishlist name*")) }, isError = nameMissing,
                supportingText = if (nameMissing) ({ Text(tr("What is this wishlist called?")) }) else null,
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp), keyboardOptions = Capitalised
            )
        }
        items(mine.size) { i -> ChoiceRow(target == mine[i], Repo.mineName(mine[i])) { onTarget(mine[i]) } }
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.wishes.size == 1) tr("1 wish found") else tr("%d wishes found", r.wishes.size),
                    style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f)
                )
                val all = picked.size == r.wishes.size
                TextButton(onClick = { onAll(!all) }) { Text(if (all) tr("Select none") else tr("Select all")) }
            }
            Text(
                tr("Missing photos and prices fill in afterwards. It can take a minute before everything shows."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
            )
            if (r.wishes.size >= Import.MAX) Text(
                tr("Only the first %d wishes are shown.", Import.MAX),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        itemsIndexed(r.wishes) { i, f ->
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = tr("Tick or untick")) { onPick(i) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(i in picked, { onPick(i) })
                Box(Modifier.size(52.dp).lookSurface(LocalLook.current, Squircle(12.dp), cs.surfaceVariant, raised = false), contentAlignment = Alignment.Center) {
                    if (f.image.isNotBlank()) WishImage(f.image, Modifier.fillMaxSize(), ContentScale.Crop)
                    else PlaceholderIcon("gift", 24.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        f.title.ifBlank { tr("Name follows from the shop") }, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (f.title.isBlank()) cs.onSurfaceVariant else cs.onSurface
                    )
                    val line = listOfNotNull(
                        f.price?.let { money(it, f.currency) },
                        if (f.love) "♥ " + tr("Love this") else null,
                        f.url.takeIf { it.isNotBlank() }?.let { u -> try { java.net.URI(u).host?.removePrefix("www.") } catch (e: Exception) { null } }
                    ).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (i in dups) Text(tr("Already on this list"), style = MaterialTheme.typography.bodySmall, color = cs.primary)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
internal fun DonePart(added: Int, listName: String, site: String, hasLinks: Boolean, onOpenList: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "✓ " + (if (added == 1) tr("1 wish imported to %s", listName) else tr("%1\$d wishes imported to %2\$s", added, listName)),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface
        )
        if (hasLinks) Text(
            tr("Photos and prices fill in over the next minute. Price-drop alerts work for every wish with a shop link."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
        )
        if (site.isNotEmpty()) Text(
            tr("Gifts reserved on %s don’t come along. Send your friends your new list so nobody buys the same gift twice.", site),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        OutlinedButton(onClick = onOpenList) { Text(tr("Open the list")) }
    }
}

/** On Home while all your lists are still empty (owner: the moment it helps most). */
@Composable
fun ImportHomeCard(onImport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .lookCard(LocalLook.current, 7L, Squircle(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(tr("Already have a wishlist somewhere?"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        Text(
            tr("Bring it over from GoWish, Giftful, Amazon, bol.com, Etsy, Pinterest or your notes."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        OutlinedButton(onClick = onImport) { Text(tr("Import a list")) }
    }
}
