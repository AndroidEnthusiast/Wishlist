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
 * Picking a day and month without a year (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): your birthday,
 * an occasion's date and a gift exchange's date.
 */

/**
 * Day and month (no year). One button shows the date ("24 December", or "Pick a date"); tapping it opens the picker
 * (owner, 2.130): first a grid of the days 1–31, then a grid of the 12 months, where months without that day are greyed
 * out. "Remove" clears it ([removable]). [onChange] gets "MM-DD" or "". Your birthday on Profile, an occasion's date
 * (Occasion.kt) and a gift exchange's date.
 */
@Composable
fun DayMonthPicker(value: String, removable: Boolean = true, onChange: (String) -> Unit) {
    val look = LocalLook.current
    val m = Regex("^(\\d{2})-(\\d{2})$").find(value)
    val month = m?.groupValues?.get(1)?.toInt() ?: 0
    val day = m?.groupValues?.get(2)?.toInt() ?: 0
    var open by remember { mutableStateOf(false) }
    fun monthName(n: Int) = java.time.Month.of(n).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (day > 0 && month > 0) "$day " + monthName(month) else tr("Pick a date"), style = MaterialTheme.typography.titleMedium,
            color = look.chipContent(false),
            modifier = Modifier.lookChip(look).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (removable && value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text(tr("Remove")) }
    }
    if (open) DayMonthDialog(day, month, onPick = { d, mo -> open = false; onChange("%02d-%02d".format(mo, d)) }, onClose = { open = false })
}

@Composable
internal fun PickCell(label: String, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (on) cs.primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, style = MaterialTheme.typography.titleSmall,
            color = when { on -> cs.onPrimary; enabled -> cs.onSurface; else -> cs.onSurface.copy(alpha = 0.3f) }
        )
    }
}

/** The picker itself: days 1–31, then the months (the ones without that day greyed out). */
@Composable
internal fun DayMonthDialog(startDay: Int, startMonth: Int, onPick: (Int, Int) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var day by remember { mutableIntStateOf(0) }
    fun monthShort(n: Int) = java.time.Month.of(n).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.UK)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (day == 0) tr("Pick a day") else tr("Pick a month")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (day == 0) {
                    (1..31).chunked(7).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { d -> PickCell(d.toString(), d == startDay, true, Modifier.weight(1f)) { day = d } }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    Text("$day …", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    (1..12).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { mo ->
                                // A month without this day (31 → February) can't be picked; 29 February can.
                                val ok = day <= java.time.Month.of(mo).length(true)
                                PickCell(monthShort(mo), mo == startMonth && day == startDay, ok, Modifier.weight(1f)) { onPick(day, mo) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (day != 0) TextButton(onClick = { day = 0 }) { Text(tr("Back")) }
            else TextButton(onClick = onClose) { Text(tr("Cancel")) }
        }
    )
}
