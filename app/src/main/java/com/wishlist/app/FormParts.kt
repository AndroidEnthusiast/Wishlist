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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.focusProperties
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

/** The category of a wish as one drop-down field: no category, the categories of the list, "New category" and "Edit categories". */
@Composable
fun CategoryField(
    selected: String, cats: List<String>, onPick: (String) -> Unit, onNew: () -> Unit, onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected.ifEmpty { tr("No category") }, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(tr("Category")) },
            trailingIcon = { Icon(AppIcons.ArrowDropDown, null) },
            // A picker, not a text box: it never takes the focus (16 November 2026: it opened with a blue edge).
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }
        )
        // A text field would pop up the keyboard. This invisible layer opens the menu instead.
        Box(
            Modifier.matchParentSize()
                .clickable(role = Role.DropdownList, onClickLabel = tr("Choose a category")) { open = true }
        )
        // A bottom sheet, not a drop-down (owner, 16 November 2026: the list opened over the form; the list grows and
        // has actions, so it gets the room of a sheet).
        if (open) PickSheet(
            tr("Category"), listOf("" to tr("No category")) + cats.map { it to it }, selected,
            onPick = { open = false; onPick(it) },
            actions = listOf(
                Triple(AppIcons.Add, tr("New category")) { open = false; onNew() },
                // Add, rename and delete categories (2.110, owner): the only place, since List settings no longer has them.
                Triple(AppIcons.Edit, tr("Edit categories")) { open = false; onEdit() }
            ),
            onClose = { open = false }
        )
    }
}

/**
 * A bottom sheet to pick one of [options] (key to name), the chosen one ticked, with [actions] (icon, name, what it
 * does) in their own group below (owner, 16 November 2026: Category and Save to in the wish form). For lists that can
 * grow or have actions; a short fixed choice next to its button can stay a drop-down.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PickSheet(
    title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit,
    actions: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = emptyList(),
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding().padding(bottom = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            SheetGroup(options.map { (key, name) ->
                @Composable {
                    SheetRow(
                        { Icon(AppIcons.ListIcon, null, tint = cs.onSurfaceVariant) },
                        name, { onPick(key) }, chosen = key == selected
                    )
                }
            })
            if (actions.isNotEmpty()) SheetGroup(actions.map { (icon, name, go) ->
                @Composable { SheetRow({ Icon(icon, null, tint = cs.onSurfaceVariant) }, name, go) }
            })
        }
    }
}

/** The symbol of a currency code for the price field: € for EUR, $ for USD, £ for GBP. The code itself if it has none. */
fun currencySymbol(code: String): String = try {
    java.util.Currency.getInstance(code).getSymbol(java.util.Locale.US)
} catch (e: Exception) {
    code
}
