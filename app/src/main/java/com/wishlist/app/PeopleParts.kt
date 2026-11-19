package com.wishlist.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * What every people screen uses (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): new wishes on a
 * friend's row, a person's round picture, your picture button, a person at the top of a page, small fact chips, a
 * birthday as words, and a section title.
 */

/**
 * "3 new wishes" on a friend's row (owner, 2.139): wishes they added that you haven't seen yet. What you've seen is
 * what was on their lists the last time you opened their page (kept on this phone). The first time a friend is counted,
 * everything counts as seen, so nobody starts with a pile of "new" wishes.
 */
object FriendNews {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    private fun prefs(c: android.content.Context) = c.applicationContext.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE)
    private fun ids(lists: Collection<RemoteList>) =
        lists.flatMap { it.wishes }.filter { it.bin == 0L && it.id != ABOUT_ITEM_ID && !it.got }.map { it.id.toString() }.toSet()

    suspend fun refresh(c: android.content.Context, friends: List<Person>) {
        val out = HashMap<String, Int>()
        for (f in friends) {
            val lists = f.lists.mapNotNull { l -> try { Friends.fetchList(c, f.id, l.code) } catch (e: Exception) { null } }
            if (lists.size < f.lists.size && f.lists.isNotEmpty()) { counts[f.id]?.let { out[f.id] = it }; continue }
            val now = ids(lists)
            val seen = prefs(c).getStringSet("fseen_" + f.id, null)
            if (seen == null) { prefs(c).edit().putStringSet("fseen_" + f.id, now).apply(); out[f.id] = 0 }
            else out[f.id] = (now - seen).size
        }
        counts = out
    }

    /** Their page was opened and their lists loaded: everything on them is seen now. */
    fun markSeen(c: android.content.Context, id: String, lists: Collection<RemoteList>) {
        prefs(c).edit().putStringSet("fseen_$id", ids(lists)).apply()
        counts = counts + (id to 0)
    }
}

/** A round picture of a person: their photo, or their first letter on the theme's colour. */
@Composable
fun Avatar(photo: String?, name: String, size: Dp) {
    val look = LocalLook.current
    if (!photo.isNullOrEmpty()) {
        AsyncImage(
            model = photo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(look.accent), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "🙂" }, color = look.onAccent,
                fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * The top right of Home, Messages, Price watch and Friends: a bell for Notifications, with a red dot when something is
 * new (owner, 2.122: before, news only showed in the side menu), and your profile picture, which opens your profile. The
 * bell is the style's own icon (filled, outlined, rounded or sharp), like the other icons.
 */
@Composable
fun ProfileButton(onProfile: () -> Unit) {
    val go = LocalGo.current
    val ctx = LocalContext.current
    Friends.load(ctx)
    IconButton(onClick = { go(Deep.Notes) }) {
        Box {
            Icon(AppIcons.Notifications, tr("Notifications"))
            if (Notifications.unread) RedDot(Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp))
        }
    }
    // Your picture opens the profile sheet (owner, 7 November 2026: no side menu): Profile, Gifts I reserved,
    // Received gifts, Bin, App settings.
    // Your picture fills the whole round button (owner, 15 November 2026: no ring around it), as big as the other
    // round buttons next to it.
    IconButton(onClick = { go(Deep.Account) }, round = false) {
        Box(Modifier.semantics { contentDescription = tr("Profile and settings") }) { Avatar(Friends.myPhoto, Shared.lastName, 40.dp) }
    }
}

/**
 * A person at the top of their page, the same on your Home and a friend's page (owner, 16 November 2026: "what you see
 * is what friends see"): the photo and name centred, then their "About me" as one short line of text (owner,
 * 17 November 2026: the chips for sizes, likes, counts and loved wishes felt messy and are gone). [birthdaySoon]: the
 * birthday shows only on a friend's page, and only in the 2 weeks before it. [below] under it (a button, a line).
 */
@Composable
internal fun PersonHero(
    photo: String?, name: String, birthday: String, bio: String, birthdaySoon: Boolean = false,
    onPhoto: (() -> Unit)? = null, below: @Composable () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).then(if (onPhoto != null) Modifier.clickable(onClickLabel = tr("Profile"), onClick = onPhoto) else Modifier)) {
            Avatar(photo, name, 112.dp)
        }
        Text(
            name, style = MaterialTheme.typography.headlineSmall, fontWeight = look.titleWeight, fontFamily = look.display,
            color = cs.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
                .then(if (onPhoto != null) Modifier.clickable(onClick = onPhoto) else Modifier).padding(horizontal = 4.dp)
        )
        if (bio.isNotBlank()) Text(
            bio.trim(), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).widthIn(max = 340.dp)
        )
        val days = Friends.daysUntil(birthday)
        if (birthdaySoon && days != null && days <= 14) AboutChip(
            "🎂 " + birthdayText(birthday) + " · " + (if (days == 0) tr("today") else tr("in %d days", days)),
            on = true, modifier = Modifier.padding(top = 12.dp)
        )
        below()
    }
}

/** A small chip of facts on a friend's page; [on] in your colour; with [onClick] it can be tapped. */
@Composable
internal fun AboutChip(text: String, on: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val look = LocalLook.current
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = look.chipContent(on), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.lookChip(look, on).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** "14 July" from "07-14", in the app's language. */
fun birthdayText(b: String): String {
    val m = Regex("^(\\d{2})-(\\d{2})$").find(b) ?: return ""
    val month = java.time.Month.of(m.groupValues[1].toInt())
    val locale = if (Lang.code == "nl") java.util.Locale("nl") else java.util.Locale.UK
    return m.groupValues[2].toInt().toString() + " " + month.getDisplayName(java.time.format.TextStyle.FULL, locale)
}

@Composable
internal fun ProfileTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp)
    )
}
