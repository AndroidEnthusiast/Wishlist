package com.wishlist.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Text with the words between underscores in italics: "Shows people you _really_ like this". */
fun italics(s: String): AnnotatedString = buildAnnotatedString {
    s.split('_').forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part) } else append(part)
    }
}

/** The category of a wish as one drop-down field: no category, the categories of the list, and "New category". */
@Composable
fun CategoryField(selected: String, cats: List<String>, onPick: (String) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected.ifEmpty { tr("No category") }, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(tr("Category")) },
            trailingIcon = { Icon(AppIcons.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth()
        )
        // A text field would pop up the keyboard. This invisible layer opens the menu instead.
        Box(
            Modifier.matchParentSize()
                .clickable(role = Role.DropdownList, onClickLabel = tr("Choose a category")) { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr("No category"), fontWeight = if (selected.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                onClick = { open = false; onPick("") }
            )
            cats.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c, fontWeight = if (c == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onPick(c) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(tr("New category")) },
                leadingIcon = { Icon(AppIcons.Add, null) },
                onClick = { open = false; onNew() }
            )
        }
    }
}

/**
 * "Other information": description, size and the note for givers, folded away under one heading so the switches
 * of the form stay in sight.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ProductInfo(content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Opening it scrolls the form down at once until all of it is in sight: the fields appear at full size (fading in)
    // in the same moment, so the scroll starts with the tap instead of after a fold-out.
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            withFrameNanos { } // the opened part is laid out by then (at full height: it only fades in)
            reveal.bringIntoView()
            // Once more after the fade, in case the dialog changed its height in between (2.105: the owner had to
            // scroll down by hand to see the fields).
            kotlinx.coroutines.delay(260)
            reveal.bringIntoView()
        }
    }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "productInfoArrow")
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceVariant, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(reveal)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    // No ripple (the bar flashing distracted): only the arrow turns to show it opens or closes.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Button, onClickLabel = if (open) tr("Close") else tr("Open")
                    ) { open = !open }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Other information"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr("Description, size, notes"),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Icon(AppIcons.ArrowDropDown, null, modifier = Modifier.rotate(turn))
            }
            // Opens at full height at once and fades in (2.105: unfolding meant the scroll only reached the heading,
            // because the fields were still 0 high when it scrolled); folds back while fading out.
            AnimatedVisibility(
                open,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
            ) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

/** The symbol of a currency code for the price field: € for EUR, $ for USD, £ for GBP. The code itself if it has none. */
fun currencySymbol(code: String): String = try {
    java.util.Currency.getInstance(code).getSymbol(java.util.Locale.US)
} catch (e: Exception) {
    code
}
