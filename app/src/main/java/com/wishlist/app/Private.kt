package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The eye with a line through it: "nobody else can see this". Google's Material icon "visibility_off" (outlined),
 * free to use under the Apache 2.0 licence. Drawn from its path, so no extra library or download is needed.
 */
val PrivateIcon: ImageVector by lazy {
    ImageVector.Builder("visibility_off", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes(
            "M12 6c3.79 0 7.17 2.13 8.82 5.5-.59 1.22-1.42 2.27-2.41 3.12l1.41 1.41c1.39-1.23 2.49-2.77 3.18-4.53" +
                "C21.27 7.11 17 4 12 4c-1.27 0-2.49.2-3.64.57l1.65 1.65C10.66 6.09 11.32 6 12 6zm-1.07 1.14L13 9.21" +
                "c.57.25 1.03.71 1.28 1.28l2.07 2.07c.08-.34.14-.7.14-1.07C16.5 9.01 14.48 7 12 7c-.37 0-.72.05-1.07.14z" +
                "M2.01 3.87l2.68 2.68C3.06 7.83 1.77 9.53 1 11.5 2.73 15.89 7 19 12 19c1.52 0 2.98-.29 4.32-.82l3.42 3.42" +
                " 1.41-1.41L3.42 2.45 2.01 3.87zm7.5 7.5l2.61 2.61c-.04.01-.08.02-.12.02-1.38 0-2.5-1.12-2.5-2.5" +
                " 0-.05.01-.08.01-.13zm-3.4-3.4l1.75 1.75c-.23.55-.36 1.15-.36 1.78 0 2.48 2.02 4.5 4.5 4.5.63 0" +
                " 1.23-.13 1.77-.36l.98.98c-.88.24-1.8.38-2.75.38-3.79 0-7.17-2.13-8.82-5.5.7-1.43 1.72-2.61 2.93-3.53z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}

/** The round badge in the top left corner of a private wish, in both views. */
@Composable
fun PrivateBadge(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    // A light shadow keeps it visible on a white product picture.
    Surface(color = MaterialTheme.colorScheme.background, shape = CircleShape, shadowElevation = 2.dp, modifier = modifier) {
        Icon(
            PrivateIcon, tr("Private wish"), tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(5.dp).size(size)
        )
    }
}

/** "Make private" / "Visible to friends" on a wish of My wishlist, with a short message about what it means. */
fun togglePrivate(c: Context, w: Wish) {
    val on = !w.priv
    Repo.setPrivate(c, w.id, on)
    toast(c, if (on) tr("Private: friends can’t see this wish any more.") else tr("Friends can see this wish again."))
}
