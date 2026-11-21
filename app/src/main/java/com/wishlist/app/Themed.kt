package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold as M3Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll

/*
 * The rest of the app's own parts (owner, 13 November 2026: "a theme must change absolutely every element of the app":
 * until 2.199 these were plain Android in every style). Text fields, menus, tick boxes, choice circles, chips, spinners,
 * progress bars, divider lines, sliders, the bottom panels, the message bar and the page behind every screen take the
 * chosen style's lines, shapes, shadows and colours. Screens use these, never Material's own (docs/STYLE.md); Quiet
 * keeps Material's calm look. Buttons, switches, dialogs and the top bar are in Styled.kt.
 * Split by kind in the rebuild (owner, 19 November 2026): text fields (ThemedFields.kt), menus, tick boxes, chips,
 * spinners and sliders (ThemedControls.kt), bottom panels, the message bar and form sheets (ThemedSheets.kt).
 */

/** The page behind a screen: the style's background colour and its drawing ([lookBackdrop]). */
@Composable
fun Modifier.lookPage(): Modifier {
    val look = LocalLook.current
    return background(look.scheme.background).lookBackdrop(look)
}

/**
 * Material's Scaffold with the style's page behind it ([lookPage]): every screen gets the style's background
 * drawing, not only Home. [containerColor] is the page's colour (normally the background).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val look = LocalLook.current
    // The large title of the top bar shrinks into the bar as the page scrolls (TopAppBar): Quiet since 2.210, every
    // style since 21 November 2026 (owner: "big title everywhere").
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scroll = remember(behavior) { behavior?.let { TopBarScroll(it) } }
    CompositionLocalProvider(LocalTopBarScroll provides scroll) {
        M3Scaffold(
            modifier.then(if (scroll != null) Modifier.nestedScroll(scroll.connection) else Modifier)
                .background(containerColor).then(if (containerColor == look.scheme.background) Modifier.lookBackdrop(look) else Modifier),
            topBar, bottomBar, snackbarHost, floatingActionButton, floatingActionButtonPosition,
            containerColor = Color.Transparent, contentColor = contentColor, contentWindowInsets = contentWindowInsets, content = content
        )
    }
}
