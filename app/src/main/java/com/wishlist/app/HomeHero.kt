package com.wishlist.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/*
 * The top of Home (split out of Hub.kt in the rebuild, owner 18 November 2026): the wordmark, and you with your
 * three numbers (Wishes, Wishlists, Total value) or, with Home summary off, in the middle as friends see you.
 */

/**
 * "Wishlist" at the top of Home, in the style's own way: Quiet bold and tight; chaos tilted with a hard pink shadow.
 */
@Composable
fun Wordmark() {
    val look = LocalLook.current
    val ink = MaterialTheme.colorScheme.onBackground
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = look.titleWeight)
    when (look.design) {
        "chaos" -> Text(
            "Wishlist", color = ink,
            style = style.copy(shadow = androidx.compose.ui.graphics.Shadow(look.blobs[0], Offset(6f, 6f), 0f)),
            modifier = Modifier.rotate(-3f)
        )
        // In the theme's colour (owner, 17 November 2026: the whole app takes the theme, the wordmark too).
        else -> Text("Wishlist", style = style, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * You at the top of Home (owner, 16 November 2026: "Home = your profile"), as friends see you: photo and name (tap for
 * Profile) and your "About me" line. No chips (owner, 17 November 2026: no sizes, counts, loved or birthday here).
 */
@Composable
internal fun HomeHero(onProfile: () -> Unit) {
    val ctx = LocalContext.current
    Friends.load(ctx)
    val name = Shared.lastName.ifBlank { tr("Add your name") }
    // App settings > Display > Home summary off: you in the middle, as friends see you (owner, 18 November 2026).
    if (!Show["home_summary"]) {
        PersonHero(Friends.myPhoto, name, Friends.myBirthday, Friends.myBio, onPhoto = onProfile)
        return
    }
    // Your photo on the left, three blocks on its right (owner, 18 November 2026): your own lists only.
    val places = LocalPlaces.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.mineIds) { Repo.allMine(ctx) }
    val all = remember(mine) { mine.flatMap { it.wishes }.filter { it.bin == 0L && it.recv == 0L }.distinctBy { it.id } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).clickable(onClickLabel = tr("Profile"), onClick = onProfile)) { Avatar(Friends.myPhoto, name, 88.dp) }
            // On a card in the style's own look (owner, 18 November 2026: "it looks too flat"; Gen Z chaos had a white one).
            Row(
                Modifier.weight(1f).padding(start = 12.dp).lookCard(look, 5L, look.cardShape(5L, small = true))
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val line = look.cardMuted(5L).copy(alpha = 0.3f)
                HomeStat(all.size.toString(), if (all.size == 1) tr("Wish") else tr("Wishes"), Modifier.weight(1f)) { places.homePage = "wishes" }
                Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(line))
                HomeStat(mine.size.toString(), if (mine.size == 1) tr("Wishlist") else tr("Wishlists"), Modifier.weight(1f)) { places.homePage = "lists" }
                Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(line))
                // Total value can't be tapped (owner).
                HomeStat(homeTotal(all), tr("Total value"), Modifier.weight(1.4f), null)
            }
        }
        // A person's big name in the title font, like a wishlist's name (owner, 21 November 2026).
        Text(
            name, style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight,
            color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onProfile)
        )
        if (Friends.myBio.isNotBlank()) Text(
            Friends.myBio.trim(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** One of the three blocks next to your photo: the number (never cut off: it shrinks, FitText) and its name. */
@Composable
private fun HomeStat(value: String, label: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val look = LocalLook.current
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The card's own ink (dark on the bright cards of Gen Z chaos).
        FitText(value, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = look.display), look.cardInk(5L))
        // With large phone text the word shrinks a little to fit, like the number (the review of 20 November 2026:
        // it crowded the next block; two lines broke words in half).
        FitText(label, MaterialTheme.typography.bodySmall, look.cardMuted(5L))
    }
}
