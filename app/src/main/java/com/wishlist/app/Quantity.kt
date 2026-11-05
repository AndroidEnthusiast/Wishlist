package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

/*
 * Quantity (owner, 2.128): a wish can be wanted more than once ("2×", the wish's qty). Givers reserve how many they'll
 * buy ("How many will you buy?"); the label says "1 of 2 reserved" until all are taken, then "Reserved"; your own
 * part shows as "You reserved 1". Older apps reserve all that is still free (supabase-setup.sql, wl_reserve_item).
 */

/** How many of a wish this phone reserved ([my]), how many all givers reserved ([n]), and whether none is left. */
data class ResCount(val my: Int, val n: Int, val full: Boolean)

object ResCounts {
    private val map = mutableStateMapOf<String, ResCount>()

    fun put(code: String, counts: Map<Long, ResCount>) {
        map.keys.filter { it.startsWith("$code:") }.forEach { map.remove(it) }
        counts.forEach { (id, c) -> map["$code:$id"] = c }
    }

    fun of(code: String, id: Long): ResCount? = map["$code:$id"]

    /** How many of [w] this phone may still reserve (its own part counts as free for itself). */
    fun free(code: String, w: Wish): Int {
        val c = of(code, w.id) ?: return w.qty
        return (w.qty - (c.n - c.my)).coerceAtLeast(0)
    }
}

/**
 * The label of a reserved wish for givers: "You reserved 1", "1 of 2 reserved" or "Reserved" for a 2× wish, else
 * "Reserved by you" / "Reserved". Null when it is free.
 */
fun reserveLabel(code: String, w: Wish, r: Boolean?): String? {
    val c = ResCounts.of(code, w.id)
    if (w.qty > 1 && c != null) {
        if (c.my > 0) return tr("You reserved %d", c.my)
        return if (c.full) tr("Reserved") else tr("%1\$d of %2\$d reserved", c.n, w.qty)
    }
    return when (r) {
        true -> tr("Reserved by you")
        false -> tr("Reserved")
        else -> null
    }
}

/** "How many will you buy?": 1 up to [max] (what's still free), [current] chosen already. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReserveCountDialog(max: Int, current: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    var n by remember { mutableStateOf(current.coerceIn(1, maxOf(1, max))) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("How many will you buy?")) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..maxOf(1, max)).forEach { k ->
                        FilterChip(selected = k == n, onClick = { n = k }, label = { Text(k.toString()) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = max > 0, onClick = { onPick(n) }) { Text(tr("Reserve")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** A small heading in the wish form ("Wish", "Details", "Visibility"; owner, 2.128). */
@Composable
fun FormHeading(text: String) {
    Text(
        text, style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)
    )
}

/** "How many" in the wish form: − 1 +, from 1 to 99 (1 = not shown to givers). */
@Composable
fun QuantityField(qty: Int, onChange: (Int) -> Unit) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(tr("How many"), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, modifier = androidx.compose.ui.Modifier.weight(1f))
        androidx.compose.material3.IconButton(onClick = { onChange((qty - 1).coerceAtLeast(1)) }, enabled = qty > 1) {
            Text("−", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = if (qty > 1) cs.primary else cs.outline)
        }
        Text(qty.toString(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp))
        androidx.compose.material3.IconButton(onClick = { onChange((qty + 1).coerceAtMost(99)) }, enabled = qty < 99) {
            Text("+", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = cs.primary)
        }
    }
}

/** "Exactly this" / "Similar is fine": one choice, or none (tap the chosen one again). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExactField(value: String, onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = value == "exact", onClick = { onChange(if (value == "exact") "" else "exact") }, label = { Text(tr("Exactly this")) })
        FilterChip(selected = value == "similar", onClick = { onChange(if (value == "similar") "" else "similar") }, label = { Text(tr("Similar is fine")) })
    }
}
