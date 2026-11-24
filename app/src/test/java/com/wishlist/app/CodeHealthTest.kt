package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards so the code can only get cleaner, never messier (owner, 24 November 2026: "I want to focus on building
 * features and not tech debt"; docs/decisions/roadmap.md, "Debt that can't grow"). They read the app's code as text,
 * so they take about a second. Today's numbers are in app/guard-baseline.txt: they may go down, never up.
 *  1. Silent catches: `catch (e: Exception)` (or Throwable) whose body never uses `e`, so the error is thrown away
 *     unseen. New code catches a narrower error, or uses it (Crash.report(e), a message, a log). Per file.
 *  2. App-wide changing data: an `object` (or companion object) that keeps a `var` or a mutable collection. Android
 *     can close the app in the background and that data is gone (ROADMAP R9). Screen state belongs in the screen's
 *     state holder; app data in an existing data service (Repo, Shared, Friends, …). Only a new data service that
 *     saves its own data may join the baseline, with a comment saying so.
 * When a number goes down, lower it in the baseline too (the test says which), so the ratchet stays tight.
 */
class CodeHealthTest {
    private val dir = File("src/main/java/com/wishlist/app")
    private val baselineFile = File("guard-baseline.txt")

    private fun files() = dir.listFiles()!!.filter { it.name.endsWith(".kt") }.sortedBy { it.name }

    private fun baseline(kind: String): List<List<String>> = baselineFile.readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        .map { it.split(Regex("\\s+")) }.filter { it[0] == kind }

    @Test fun silentCatchesOnlyGoDown() {
        val was = baseline("catch").associate { it[1] to it[2].toInt() }
        val now = files().associate { it.name to KtScan.silentCatches(it.readText()) }.filterValues { it.isNotEmpty() }
        val grew = now.filter { (f, lines) -> lines.size > (was[f] ?: 0) }
            .map { (f, lines) -> "$f: ${lines.size} (was ${was[f] ?: 0}), at lines $lines" }
        assertTrue(
            "New silent catches: catch a narrower error, or use it (Crash.report(e), a message to the person). $grew",
            grew.isEmpty()
        )
        val shrank = was.filter { (f, n) -> (now[f]?.size ?: 0) < n }.map { (f, _) -> "catch $f ${now[f]?.size ?: 0}" }
        assertTrue("Fewer silent catches, well done: lower these in app/guard-baseline.txt (0 = remove the line): $shrank", shrank.isEmpty())
    }

    @Test fun appWideChangingDataOnlyGoesDown() {
        val was = baseline("state").map { it[1] }.toSet()
        val now = files().flatMap { KtScan.statefulObjects(it.readText(), it.name) }.toSet()
        val added = now - was
        assertTrue(
            "New object(s) keeping changing data app-wide, lost when Android closes the app: keep screen state in the " +
                "screen's state holder, app data in an existing data service (Repo, Shared, …). Only a new data service " +
                "that saves its own data may be added to app/guard-baseline.txt, with a comment why: $added",
            added.isEmpty()
        )
        val gone = was - now
        assertTrue("No longer keeping changing data, well done: take these off app/guard-baseline.txt: $gone", gone.isEmpty())
    }

    /** The guards must see what they are meant to see (and nothing else), or they protect nothing. */
    @Test fun theGuardsSeeRight() {
        val code = """
            fun a() { try { x() } catch (e: Exception) {} }
            fun b() = try { y() } catch (e: Exception) { null }
            fun c() { try { x() } catch (e: Exception) { Crash.report(e) } }
            fun d() { try { x() } catch (e: Throwable) {
                // e is used below
                show("Couldn't: ${'$'}{e.message}")
            } }
            fun f() { try { x() } catch (e: Exception) { show("Couldn't save, e.g. offline { ") } }
            fun g() { try { x() } catch (e: IOException) {} }
            fun h() { try { x() } catch (_: Exception) { if (a) { b() } } }
        """.trimIndent()
        assertEquals(listOf(1, 2, 8, 10), KtScan.silentCatches(code))

        val objects = """
            object Cache { private val map = mutableMapOf<String, Int>()
                fun get(k: String) = map[k] }
            internal object Helper { const val A = 1
                fun go() { var i = 0; i++ } }
            object Flags { var on by mutableStateOf(false) }
            object Text { val brace = "{"; val n = 2 }
            class Box { companion object { var count = 0 } }
            class Plain { companion object { const val MAX = 3 } }
            val anon = object : Runnable { var i = 0; override fun run() {} }
            object Outer { object Inner { val list = mutableStateListOf<Int>() } }
        """.trimIndent()
        assertEquals(listOf("Cache", "Flags", "Box.Companion", "Inner"), KtScan.statefulObjects(objects, "T.kt"))
    }
}

/** Reads Kotlin code as text for the guards: strings, characters and comments don't count. */
internal object KtScan {
    /**
     * [t] with comments blanked to spaces (newlines kept), and strings and characters too; with [keepTemplates] what
     * is inside a string's templates stays, so a message showing e.message still uses `e`, but the words "e.g." don't.
     */
    fun blank(t: String, keepTemplates: Boolean = false): String {
        val out = StringBuilder(t)
        var i = 0
        while (i < t.length) {
            when {
                t.startsWith("//", i) -> { val e = t.indexOf('\n', i).let { if (it < 0) t.length else it }; wipe(out, i, e); i = e }
                t.startsWith("/*", i) -> { val e = t.indexOf("*/", i + 2).let { if (it < 0) t.length else it + 2 }; wipe(out, i, e); i = e }
                t[i] == '"' -> i = endOfString(t, i, out, keepTemplates)
                t[i] == '\'' -> { val e = endOfChar(t, i); wipe(out, i, e); i = e }
                else -> i++
            }
        }
        return out.toString()
    }

    private fun wipe(out: StringBuilder, from: Int, to: Int) {
        for (k in from until minOf(to, out.length)) if (out[k] != '\n') out[k] = ' '
    }

    /** Where the string starting at [start] ends; its text is wiped in [out] (templates too, unless [keep]). */
    private fun endOfString(t: String, start: Int, out: StringBuilder? = null, keep: Boolean = false): Int {
        val raw = t.startsWith("\"\"\"", start)
        var j = start + if (raw) 3 else 1
        out?.let { wipe(it, start, j) }
        while (j < t.length) {
            val from = j
            when {
                raw && t.startsWith("\"\"\"", j) -> { j += 3; while (j < t.length && t[j] == '"') j++; out?.let { wipe(it, from, j) }; return j }
                !raw && t[j] == '\\' -> { j += 2; out?.let { wipe(it, from, j) } }
                !raw && t[j] == '"' -> { out?.let { wipe(it, j, j + 1) }; return j + 1 }
                !raw && t[j] == '\n' -> return j
                t.startsWith("\${", j) -> { j = endOfTemplate(t, j + 2); if (!keep) out?.let { wipe(it, from, j) } }
                t[j] == '$' && t.getOrNull(j + 1)?.isLetter() == true -> {
                    j++
                    while (j < t.length && (t[j].isLetterOrDigit() || t[j] == '_')) j++
                    if (!keep) out?.let { wipe(it, from, j) }
                }
                else -> { out?.let { wipe(it, j, j + 1) }; j++ }
            }
        }
        return t.length
    }

    private fun endOfTemplate(t: String, start: Int): Int {
        var depth = 1
        var j = start
        while (j < t.length) {
            when (t[j]) {
                '"' -> { j = endOfString(t, j); continue }
                '\'' -> { j = endOfChar(t, j); continue }
                '{' -> depth++
                '}' -> if (--depth == 0) return j + 1
            }
            j++
        }
        return t.length
    }

    private fun endOfChar(t: String, start: Int): Int {
        val e = t.indexOf('\'', start + if (t.getOrNull(start + 1) == '\\') 3 else 2)
        return if (e < 0 || e - start > 8) start + 1 else e + 1
    }

    /** The index of the `}` that closes the `{` at [open] in blanked code. */
    private fun close(b: String, open: Int): Int {
        var depth = 0
        for (j in open until b.length) {
            if (b[j] == '{') depth++
            if (b[j] == '}' && --depth == 0) return j
        }
        return b.length
    }

    private fun lineOf(t: String, at: Int) = t.substring(0, at).count { it == '\n' } + 1

    private val CATCH = Regex("""\bcatch\s*\(\s*(\w+)\s*:\s*(?:kotlin\.|java\.lang\.)?(Exception|Throwable)\s*\)\s*\{""")

    /** Lines of broad catches whose body never mentions the error. */
    fun silentCatches(t: String): List<Int> {
        val b = blank(t)
        val noComments = blank(t, keepTemplates = true)
        return CATCH.findAll(b).mapNotNull { m ->
            val open = m.range.last
            val body = noComments.substring(open + 1, close(b, open))
            val name = m.groupValues[1]
            val used = name != "_" && Regex("(?<![\\w.])${Regex.escape(name)}\\b").containsMatchIn(body)
            if (used) null else lineOf(t, m.range.first)
        }.toList()
    }

    private val OBJECT = Regex("""\b(?:(companion)\s+)?object\b[ \t]*(\w+)?[^{\n]*\{|\b(class|interface)\s+(\w+)""")
    private val MEMBER_VAR = Regex("""(?m)^\s*(?:@[\w.]+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|protected|lateinit|override)\s+)*var\s""")
    private val MEMBER_MUTABLE = Regex("""(?m)^\s*(?:@[\w.]+\s+)*(?:(?:private|internal|public|protected)\s+)*val\s.*=\s*(mutable\w*Of|HashMap|LinkedHashMap|HashSet|LinkedHashSet|ArrayList|ConcurrentHashMap|MutableStateFlow|MutableSharedFlow|Atomic\w+|SnapshotStateList|SnapshotStateMap)\b""")

    /** Names of objects (companions as Outer.Companion) that keep a var or a mutable collection as a member. */
    fun statefulObjects(t: String, file: String): List<String> {
        val b = blank(t)
        var lastClass = file.removeSuffix(".kt")
        return OBJECT.findAll(b).mapNotNull { m ->
            if (m.groupValues[3].isNotEmpty()) { lastClass = m.groupValues[4]; return@mapNotNull null }
            val companion = m.groupValues[1].isNotEmpty()
            val name = m.groupValues[2].ifEmpty { if (companion) "Companion" else return@mapNotNull null }
            val open = m.range.last
            val body = b.substring(open + 1, close(b, open))
            // Only the object's own members: what is inside its functions and lambdas doesn't count.
            val top = StringBuilder()
            var depth = 0
            for (c in body) {
                when {
                    c == '{' -> depth++
                    c == '}' -> depth--
                    depth == 0 || c == '\n' -> top.append(if (c == ';') '\n' else c)
                }
            }
            val s = top.toString()
            if (MEMBER_VAR.containsMatchIn(s) || MEMBER_MUTABLE.containsMatchIn(s)) {
                if (companion && m.groupValues[2].isEmpty()) "$lastClass.Companion" else name
            } else null
        }.toList()
    }
}
