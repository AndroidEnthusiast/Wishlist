package com.wishlist.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp

/**
 * The layout inspector (owner, 20 November 2026: "make absolutely sure mistakes like these aren't anywhere else";
 * docs/decisions/look.md, last section). After a screen settles, every text on it is measured; it reports:
 *  - text that is cut off (narrower than it needs, without "…"), or cut at the bottom;
 *  - a word or a number broken over two lines ("€33.1 / 9", "Wishe / s", "Decathlo / n");
 *  - two texts on top of each other ("Reserved by you" over "+78%");
 *  - text sticking out past the left or right edge of the screen;
 *  - two buttons with words side by side less than 6 dp apart (26 November 2026).
 * Links and addresses may break anywhere (they have no spaces), messages that float over everything (a snackbar) may
 * cover text, and text inside something that scrolls sideways may be off screen. It can't judge taste (balance,
 * spacing): pictures are still looked at.
 */
object LayoutGuard {
    private const val SLACK = 1.5f

    /** Every problem on screen, as "where: what". Empty when the screen is clean. */
    @Suppress("UNUSED_PARAMETER")
    fun problems(rules: SemanticsNodeInteractionsProvider): List<String> {
        val out = ArrayList<String>()
        for (root in roots()) {
            val texts = ArrayList<Pair<SemanticsNode, String>>()
            walk(root, false, false) { n, sideways, floating ->
                val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: return@walk
                if (text.isBlank() || n.size.width <= 0 || n.size.height <= 0) return@walk
                // A copy measured out of sight (to decide whether something fits) is not on the screen.
                if (!placed(n)) return@walk
                if (n.config.contains(SemanticsProperties.EditableText)) return@walk
                val r = layout(n)
                if (r != null) {
                    if (cut(r)) out += "“${short(text)}” is cut off"
                    broken(r)?.let { out += "“${short(text)}” breaks “$it” over two lines" }
                }
                val b = n.boundsInRoot
                if (!sideways && (b.left < -SLACK || b.right > root.size.width + SLACK)) out += "“${short(text)}” sticks out of the screen"
                if (!floating) texts += n to text
            }
            for (i in texts.indices) for (j in i + 1 until texts.size) {
                val (a, at) = texts[i]
                val (b, bt) = texts[j]
                if (related(a, b)) continue
                if (!a.boundsInRoot.overlaps(b.boundsInRoot)) continue
                // Compared inside their nearest shared parent, so a tilted card (Gen Z chaos) doesn't count its
                // slanted outline as an overlap.
                val (ba, bb) = inShared(a, b) ?: (a.boundsInRoot to b.boundsInRoot)
                // Where the letters are, not the empty line spacing around them (a big amount's tall line box touched
                // the word under it without any letter touching).
                val ra = ink(a, ba)
                val rb = ink(b, bb)
                val x = ra.intersect(rb)
                if (x.width > 2f && x.height > 2f) out += "“${short(at)}” overlaps “${short(bt)}”"
            }
            // Two buttons with words side by side need room between them (owner, 26 November 2026: "Share" and "Add
            // a friend" touched): at least 6 dp, or they read as one.
            val px = try { root.layoutInfo.density.density } catch (e: Exception) { 1f }
            val buttons = ArrayList<Pair<SemanticsNode, String>>()
            walk(root, false, false) { n, _, _ ->
                if (n.config.getOrNull(SemanticsProperties.Role) != androidx.compose.ui.semantics.Role.Button || !placed(n)) return@walk
                val label = words(n)
                if (label.isNotBlank()) buttons += n to label
            }
            for (i in buttons.indices) for (j in i + 1 until buttons.size) {
                val (a, al) = buttons[i]
                val (b, bl) = buttons[j]
                val ra = a.boundsInRoot
                val rb = b.boundsInRoot
                val sameRow = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top) > 0.6f * minOf(ra.height, rb.height)
                val gap = if (ra.left < rb.left) rb.left - ra.right else ra.left - rb.right
                if (sameRow && gap < 6f * px && gap > -ra.width / 2f) out += "the buttons “${short(al)}” and “${short(bl)}” touch"
            }
        }
        return out.distinct()
    }

    /** Fails with the list of problems (for the checks). [where]: which screen, for the message. */
    fun assertClean(rules: SemanticsNodeInteractionsProvider, where: String) {
        val p = problems(rules)
        if (p.isNotEmpty()) throw AssertionError("Layout problems on $where:\n - " + p.joinToString("\n - "))
    }

    /** Every window's Compose content (the screen, dialogs, sheets, menus), read as it is without waiting. */
    private fun roots(): List<SemanticsNode> {
        val views = try {
            val g = Class.forName("android.view.WindowManagerGlobal")
            val inst = g.getMethod("getInstance").invoke(null)
            val f = g.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST") (f.get(inst) as List<android.view.View>).toList()
        } catch (e: Exception) { emptyList() }
        val out = ArrayList<SemanticsNode>()
        fun find(v: android.view.View) {
            if (v is androidx.compose.ui.platform.ViewRootForTest) out += v.semanticsOwner.unmergedRootSemanticsNode
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))
        }
        views.filter { it.isShown }.forEach { find(it) }
        return out
    }

    /** The part of [box] (the node's box) where its letters are: from the cap height of the first line to just below
     *  the last line's baseline, between the lines' ends. The whole box when the text can't be measured. */
    private fun ink(n: SemanticsNode, box: Rect): Rect {
        val r = layout(n) ?: return box
        if (r.lineCount == 0 || r.size.height == 0) return box
        val fs = with(r.layoutInput.density) { r.layoutInput.style.fontSize.let { if (it.isSp) it.toPx() else 14.sp.toPx() } }
        val sy = box.height / r.size.height
        val sx = if (r.size.width > 0) box.width / r.size.width else 1f
        val top = (r.getLineBaseline(0) - fs * 0.72f).coerceAtLeast(0f)
        val bottom = (r.getLineBaseline(r.lineCount - 1) + fs * 0.22f).coerceAtMost(r.size.height.toFloat())
        val left = (0 until r.lineCount).minOf { r.getLineLeft(it) }.coerceAtLeast(0f)
        val right = (0 until r.lineCount).maxOf { r.getLineRight(it) }.coerceAtMost(r.size.width.toFloat())
        return Rect(box.left + left * sx, box.top + top * sy, box.left + right * sx, box.top + bottom * sy)
    }

    /** The words of a button: its own text or its children's. */
    private fun words(n: SemanticsNode): String {
        val own = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        return if (own.isNotBlank()) own else n.children.joinToString(" ") { words(it) }.trim()
    }

    private fun placed(n: SemanticsNode): Boolean = try { n.layoutInfo.isPlaced && n.layoutInfo.coordinates.isAttached } catch (e: Exception) { true }

    /** The two boxes in the coordinates of their nearest shared parent (null when that can't be worked out). */
    private fun inShared(a: SemanticsNode, b: SemanticsNode): Pair<Rect, Rect>? = try {
        val up = HashSet<Int>(); var p = a.parent; while (p != null) { up += p.id; p = p.parent }
        var c = b.parent; while (c != null && c.id !in up) c = c.parent
        val lca = c?.layoutInfo?.coordinates
        if (lca == null) null else lca.localBoundingBoxOf(a.layoutInfo.coordinates, false) to lca.localBoundingBoxOf(b.layoutInfo.coordinates, false)
    } catch (e: Exception) { null }

    private fun walk(n: SemanticsNode, sideways: Boolean, floating: Boolean, f: (SemanticsNode, Boolean, Boolean) -> Unit) {
        val side = sideways || n.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
        // A message that floats over the screen on purpose (a snackbar announces itself as a live region).
        val float = floating || n.config.contains(SemanticsProperties.LiveRegion)
        f(n, side, float)
        n.children.forEach { walk(it, side, float, f) }
    }

    private fun layout(n: SemanticsNode): TextLayoutResult? {
        val got = ArrayList<TextLayoutResult>()
        return try {
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(got)
            got.firstOrNull()
        } catch (e: Exception) { null }
    }

    /** Narrower or lower than the text needs, without "…" (Compose's own overflow flag is also set for text that fits). */
    private fun cut(r: TextLayoutResult): Boolean {
        val last = r.lineCount - 1
        if (last < 0 || r.isLineEllipsized(last)) return false
        val wide = r.lineCount == 1 && r.multiParagraph.intrinsics.maxIntrinsicWidth > r.size.width + SLACK
        val low = r.multiParagraph.height > r.size.height + SLACK
        return wide || low
    }

    /** The word or number broken over two lines, or null. Links and addresses (no spaces in them) may break. */
    private fun broken(r: TextLayoutResult): String? {
        val s = r.layoutInput.text.text
        for (i in 0 until r.lineCount - 1) {
            val end = r.getLineEnd(i)
            if (end <= 0 || end >= s.length) continue
            val a = s[end - 1]
            val b = s[end]
            if (!(a.isLetterOrDigit() || a == '.' || a == ',') || !b.isLetterOrDigit()) continue
            var from = end - 1
            while (from > 0 && !s[from - 1].isWhitespace()) from--
            var to = end
            while (to < s.length && !s[to].isWhitespace()) to++
            val word = s.substring(from, to)
            if (word.contains("://") || word.contains('/') || word.contains('@') || word.length > 24) continue
            return word
        }
        return null
    }

    /** One inside the other (a label inside its button, a text inside its card) is not an overlap. */
    private fun related(a: SemanticsNode, b: SemanticsNode): Boolean {
        fun above(x: SemanticsNode, y: SemanticsNode): Boolean { var p = y.parent; while (p != null) { if (p.id == x.id) return true; p = p.parent }; return false }
        return a.id == b.id || above(a, b) || above(b, a)
    }

    /** Two texts whose boxes touch only in their empty line spacing don't count; real ink overlap does. */
    private fun visibleOverlap(a: Rect, b: Rect, x: Rect): Boolean =
        x.height > minOf(a.height, b.height) * 0.25f || x.width > minOf(a.width, b.width) * 0.25f && x.height > 4f

    private fun short(s: String) = s.replace('\n', ' ').let { if (it.length > 40) it.take(38) + "…" else it }
}

/** Writes what the inspector found on [where] to build/layout-report.txt (the full sweep), and fails like assertClean. */
fun SemanticsNodeInteractionsProvider.guard(where: String) {
    val p = LayoutGuard.problems(this)
    if (p.isEmpty()) return
    java.io.File("build/layout-report.txt").appendText("== $where\n" + p.joinToString("") { " - $it\n" })
    if (System.getProperty("wl.guardReport").isNullOrEmpty()) throw AssertionError("Layout problems on $where:\n - " + p.joinToString("\n - "))
}
