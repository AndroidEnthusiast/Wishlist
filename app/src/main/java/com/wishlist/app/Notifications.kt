package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToLong

/** The red of the "something new" dot (menu button and the Notifications item). */
val NewDot = Color(0xFFE53935)

/**
 * One price change in Notifications: which wish ([wishId], [title], [image]), on which list ([listKey]: "m:<id>" for
 * your own, "s:<code>" for a group wishlist), and by how much in percent ([pct]: below zero is cheaper).
 */
data class PriceNote(
    val wishId: Long, val listKey: String, val title: String, val image: String, val pct: Double, val time: Long, val read: Boolean,
    /** "price" (a drop or rise) or "target" (your target price was reached: [amount] in [currency]). */
    val kind: String = "price", val amount: Double = 0.0, val currency: String = ""
)

/**
 * The Notifications page in the menu (since 2.67): every price drop and rise the price checks found, newest first,
 * with a red dot while there are new ones. A drop is only kept when "Price drops" is on in App settings, a rise only
 * when "Price surges" is on; switching one off also hides those already kept. At most 100 are kept.
 */
object Notifications {
    private const val KEY = "notes"
    var all by mutableStateOf(listOf<PriceNote>())
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        all = try {
            val a = JSONArray(prefs(c).getString(KEY, null) ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                PriceNote(
                    o.getLong("id"), o.optString("list"), o.optString("title"), o.optString("image"),
                    o.optDouble("pct", 0.0), o.optLong("time"), o.optBoolean("read"),
                    o.optString("kind", "price"), o.optDouble("amount", 0.0), o.optString("cur", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(c: Context) {
        val a = JSONArray()
        all.forEach {
            a.put(
                JSONObject().put("id", it.wishId).put("list", it.listKey).put("title", it.title).put("image", it.image)
                    .put("pct", it.pct).put("time", it.time).put("read", it.read)
                    .put("kind", it.kind).put("amount", it.amount).put("cur", it.currency)
            )
        }
        prefs(c).edit().putString(KEY, a.toString()).apply()
    }

    /** What the page shows: drops and rises as switched on in App settings. */
    val shown: List<PriceNote>
        get() = all.filter { it.kind == "target" || if (it.pct < 0) Repo.notifyDrops else Repo.notifyRises }

    /** True while there is something new to see (the red dots). */
    val unread: Boolean get() = shown.any { !it.read }

    /** A price check found a new price for [w] on list [listKey] (it was [was]). */
    @Synchronized
    fun priceChanged(c: Context, listKey: String, w: Wish, was: Double, now: Double) {
        if (was <= 0.0 || abs(now - was) < 0.005) return
        if (now < was && !Repo.notifyDrops) return
        if (now > was && !Repo.notifyRises) return
        load(c)
        val pct = (now - was) / was * 100
        // The newest change of a wish on a list replaces an older one, so the page doesn't fill up with one wish.
        val rest = all.filter { !(it.wishId == w.id && it.listKey == listKey && it.kind == "price") }
        all = (listOf(PriceNote(w.id, listKey, w.title, w.image, pct, System.currentTimeMillis(), false)) + rest).take(100)
        save(c)
    }

    /** Your target price for [w] was reached ([target]). */
    @Synchronized
    fun targetReached(c: Context, listKey: String, w: Wish, target: Double) {
        load(c)
        val rest = all.filter { !(it.wishId == w.id && it.kind == "target") }
        all = (listOf(PriceNote(w.id, listKey, w.title, w.image, 0.0, System.currentTimeMillis(), false, "target", target, w.currency)) + rest).take(100)
        save(c)
    }

    @Synchronized
    fun markAllRead(c: Context) {
        if (all.none { !it.read }) return
        all = all.map { it.copy(read = true) }
        save(c)
    }
}

/** The Notifications page: a small picture, the name, "decreased in price" or "increased in price", and the percentage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(snack: SnackbarHostState, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val notes = Notifications.shown
    // Opening the page counts as seeing them: the red dots go.
    LaunchedEffect(Unit) { Notifications.markAllRead(ctx) }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Notifications"), fontWeight = look.titleWeight) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        if (notes.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr("No notifications yet. When a wish gets cheaper or more expensive, it shows up here."),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.kind + "_" + it.listKey + "_" + it.wishId }) { n ->
                val id = n.wishId
                val down = n.pct < 0
                val target = n.kind == "target"
                Row(
                    Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                        // A reached target opens the wish's price history; a price change opens its list.
                        .clickable { if (target) HistoryNav.open = n.wishId else onOpen(n.listKey) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mat = RoundedCornerShape(10.dp)
                    Box(Modifier.size(44.dp).lookMat(look, mat, look.pictureBg(id, false)), contentAlignment = Alignment.Center) {
                        if (n.image.isNotBlank()) {
                            AsyncImage(
                                model = n.image, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(3.dp)
                            )
                        } else Text("🎁", fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(
                            n.title, style = MaterialTheme.typography.titleSmall, color = look.cardInk(id),
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (target) tr("reached your target price") else if (down) tr("decreased in price") else tr("increased in price"),
                            style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
                        )
                    }
                    if (target) {
                        Text(
                            money(n.amount, n.currency), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = targetGold(look.dark)
                        )
                    } else {
                        val pct = abs(n.pct).roundToLong().coerceAtLeast(1)
                        Text(
                            (if (down) "−" else "+") + pct + "%",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = if (down) look.drop else look.rise
                        )
                    }
                }
            }
        }
    }
}

/** The small red dot for something new. */
@Composable
fun RedDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(NewDot))
}
