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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * What the gift exchange screens share: messages for a missing server update or an impossible draw, a day as words, the theme's colours, the cover and the confetti. (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

/*
 * The screens of Gift exchange (2.129, see Exchange.kt): the list of your exchanges (side menu), a new exchange, the
 * join screen, an exchange's page (before the draw: people, rules and Draw; after it: your name with the reveal moment,
 * the anonymous chats, the organiser's draw check and Reveal), its settings, and the anonymous chat.
 */

/** Gift exchange needs supabase-exchange.sql: an error that says the server doesn't know its functions yet. */
fun gxMissing(e: Exception): Boolean = e.message.orEmpty().let { it.contains("404") || it.contains("PGRST202") }

/** "Anna would have nobody left to draw." / "Anna, Ben and Cas would only have 2 people left to draw between them." */
fun gxStuckText(s: GxStuck): String {
    val names = s.names.ifEmpty { listOf(tr("Someone")) }
    if (names.size == 1) return tr("%s would have nobody left to draw.", names[0])
    val joined = names.dropLast(1).joinToString(", ") + " " + tr("and") + " " + names.last()
    return if (s.left == 1) tr("%s would only have 1 person left to draw between them.", joined)
    else tr("%1\$s would only have %2\$d people left to draw between them.", joined, s.left)
}

internal fun gxDayText(d: LocalDate): String =
    d.dayOfMonth.toString() + " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK) + " " + d.year

// ---------- the drawn cover and the confetti ----------

internal fun gxColours(theme: String): List<Color> = when (theme) {
    "sint" -> listOf(Color(0xFFB71C1C), Color(0xFFE0A100))
    "bday" -> listOf(Color(0xFFE91E63), Color(0xFF7E57C2))
    "office" -> listOf(Color(0xFF1565C0), Color(0xFF00897B))
    "plain" -> listOf(Color(0xFF455A64), Color(0xFF7E57C2))
    else -> listOf(Color(0xFF1B5E20), Color(0xFFC62828))
}

/** The exchange's cover: the organiser's photo, or the theme drawn here (colours and a few of its emoji). */
@Composable
fun GxCover(theme: String, photo: String?, modifier: Modifier, title: String? = null) {
    Box(modifier.clip(RoundedCornerShape(24.dp))) {
        if (!photo.isNullOrEmpty()) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(gxColours(theme))))
            val e = GxThemes.emoji(theme)
            // A few of the theme's emoji, always in the same places, slightly turned.
            listOf(Triple(0.08f, 0.15f, -12f), Triple(0.78f, 0.1f, 14f), Triple(0.62f, 0.62f, -8f), Triple(0.2f, 0.66f, 10f), Triple(0.9f, 0.7f, -16f))
                .forEach { (fx, fy, r) ->
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        Text(
                            // Decoration only: not read aloud, and not text the layout checks should measure.
                            e, fontSize = 26.sp, modifier = Modifier.padding(start = maxWidth * fx, top = maxHeight * fy).rotate(r)
                                .clearAndSetSemantics {}
                        )
                    }
                }
        }
        if (title != null) Text(
            title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))).padding(16.dp)
        )
    }
}

/** Confetti falling over the screen for about two seconds each time [run] goes up. */
@Composable
fun Confetti(run: Int, modifier: Modifier = Modifier) {
    if (run == 0) return
    val p = remember(run) { Animatable(0f) }
    LaunchedEffect(run) { p.animateTo(1f, tween(2400, easing = LinearEasing)) }
    if (p.value >= 1f) return
    val colours = listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))
    Canvas(modifier.fillMaxSize()) {
        val rnd = kotlin.random.Random(run)
        repeat(90) { i ->
            val x = rnd.nextFloat() * size.width
            val speed = 0.7f + rnd.nextFloat() * 0.8f
            val start = -rnd.nextFloat() * size.height * 0.4f
            val y = start + p.value * speed * size.height * 1.4f
            val sway = kotlin.math.sin((p.value * 6f + i) * 1.3f) * 18f
            rotate(p.value * 540f * speed + i * 23f, Offset(x + sway, y)) {
                drawRect(colours[i % colours.size], Offset(x + sway - 6f, y - 9f), Size(12f, 18f), alpha = 1f - p.value * 0.5f)
            }
        }
    }
}

// ---------- the list of exchanges (side menu) ----------
