package com.wishlist.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/*
 * Every menu is a bottom sheet (owner, 21 November 2026: "bottom sheet everywhere"; before, a chat's, a group's and a
 * gift exchange's ⋮ and the pick-one lists were small drop-downs at the top right). The app's DropdownMenu keeps its
 * name, so every menu follows the rule by itself: rows in one group, an icon in its tile when the row has one, a tick
 * on the chosen option of a pick-one list, red for leaving and removing. No ✕: swipe down or tap outside
 * (docs/STYLE.md "Sheets and menus").
 */

/** A menu: shown as a bottom sheet while [expanded]. Its rows are [DropdownMenuItem]s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(scrollState)) {
            val look = LocalLook.current
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                    .clip(if (look.design == "quiet") Squircle(18.dp) else androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(if (look.design == "quiet") look.groupFill() else androidx.compose.ui.graphics.Color.Transparent)
            ) { content() }
        }
    }
}

/**
 * One row of a menu: [leadingIcon] in its tile, the words, and a tick when [selected] (a pick-one list). [danger]: it
 * leaves or removes (red). Same parameters as Material's, so every menu in the app uses this one.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    selected: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val ink = if (danger) cs.error else cs.onSurface
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (selected) this.selected = true }
            .padding(horizontal = 14.dp, vertical = 6.dp).alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier.size(32.dp).clip(Squircle(9.dp)).background(if (danger) cs.error.copy(alpha = 0.14f) else cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides (if (danger) cs.error else cs.onPrimaryContainer)) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                }
            }
        }
        Box(Modifier.weight(1f).padding(start = if (leadingIcon != null) 14.dp else 4.dp)) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = ink, textAlign = TextAlign.Start)) { text() }
            }
        }
        if (selected) Icon(AppIcons.Check, tr("Chosen"), tint = cs.primary, modifier = Modifier.size(22.dp))
        else if (trailingIcon != null) trailingIcon()
    }
}
