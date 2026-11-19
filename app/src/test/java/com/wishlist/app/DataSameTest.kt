package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of the data rebuild (owner, 19 November 2026: "saving and loading give back exactly the same
 * lists"; `docs/decisions/roadmap.md`, "The data rebuild").
 *
 * A pretend phone that comes from an old version (everything still in the settings store) starts the app, then goes
 * through every change Repo can make to your own lists, the bin, copies, categories, friends' lists and the settings,
 * and starts again. After each step everything the app holds AND everything saved (the settings store and the
 * database) is written down. The result must match `src/test/resources/data-same.txt` word for word: that file was
 * recorded with Data.kt as it was before the rebuild (2.255). Moments in time become T1, T2, … (same moment, same name).
 *
 * When a change to how data is saved is MEANT (a new feature), delete the file, run this check once to record it again
 * (it then fails with "recorded"), and look at the difference in git before committing it.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class DataSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/data-same.txt")
    private val out = StringBuilder()
    private val start = System.currentTimeMillis()

    private fun w(id: Long, title: String, cat: String, price: Double? = 10.0) =
        Wish(id, "https://shop.example/p/$id", title, "about $title", "", price, null, "EUR", cat)

    /** A start of the app on this pretend phone. */
    private fun startApp() {
        ListStore.reset(); WishlistDb.reset()
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
    }

    /** Moments are different at every run: each becomes T1, T2, … in the order they first appear. */
    private fun timesNamed(s: String): String {
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(s) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    private fun wishText(x: Wish) = wishToJson(x).let { o -> o.keys().asSequence().sorted().joinToString(",") { "$it=${o.get(it)}" } }

    private fun note(step: String) {
        ListStore.flush()
        out.append("## ").append(step).append('\n')
        val r = Repo
        out.append("open=${r.openMine} ids=${r.mineIds} names=${r.mineNames.toSortedMap()} name=${r.myName} about=${r.myAbout}\n")
        out.append("cats=${r.myCats} code=${r.shareCode} token=${r.ownerToken} photos=${r.uploadedPhotos.sorted()}\n")
        out.append("look=${r.design}/${r.theme}/${r.palette}/${r.colours} font=${r.font} view=${r.view} sort=${r.sort}\n")
        out.append("instant=${r.instantDelete} drops=${r.notifyDrops} rises=${r.notifyRises} interval=${r.intervalHours} last=${r.lastCheck} saveTo=${r.lastSaveTo}\n")
        out.append("friends=${r.friends} device=${r.deviceId} recent=${r.recent.toSortedMap()}\n")
        out.append("hero=${r.hero} edit=${r.heroEdit} cut=${r.heroCut}\n")
        for (m in r.allMine(c)) {
            out.append("list ${m.id} '${m.name}' about='${m.about}' cats=${m.cats} code=${m.code} photos=${m.photos.sorted()}\n")
            m.wishes.forEach { out.append("  wish ").append(wishText(it)).append('\n') }
            m.wishes.forEach { x -> out.append("  copies ${x.id}=${r.hasCopies(x.id)} lists=${r.listsWith(c, x.id)} original=${r.originalOf(c, x.id)}\n") }
        }
        r.myBin.forEach { out.append("bin ${r.ownerOf(it.id)} '${r.binListName(it.id)}' gone=${r.deletedListOf(it.id)} ").append(wishText(it)).append('\n') }
        out.append("sync=${r.syncWishes().map { it.id }}\n")
        // Everything in the settings store, sorted.
        prefs.all.toSortedMap().forEach { (k, v) ->
            val shown = if (v is Set<*>) v.map { it.toString() }.sorted().toString() else v.toString()
            out.append("pref ").append(k).append('=').append(shown).append('\n')
        }
        // Everything in the database, as the app reads it at a start.
        val db = java.util.concurrent.Executors.newSingleThreadExecutor().submit(java.util.concurrent.Callable {
            ListStore.read(WishlistDb.get(c).dao())
        }).get()
        db.lists.forEach { m ->
            out.append("db list ${m.id} '${m.name}' about='${m.about}' cats=${m.cats} code=${m.code} token=${m.token} photos=${m.photos.sorted()}\n")
            m.wishes.forEach { out.append("  db wish ").append(wishText(it)).append('\n') }
        }
        db.bin.forEach { (l, x) -> out.append("db bin $l ").append(wishText(x)).append('\n') }
        out.append("db cats=${db.categories} friends=${db.friends}\n")
        db.history.toSortedMap().forEach { (id, pts) -> out.append("db history $id ${pts.map { "${it.p}" }}\n") }
    }

    /** Different moments for every step, so two steps never share a moment by chance. */
    private fun tick() = Thread.sleep(5)

    @Test fun savingAndLoadingGiveBackExactlyTheSame() {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        // A phone from an old version: everything in the settings store, the old look, two lists, a bin, copies.
        val e = prefs.edit()
        e.putString("device", "device-1")
            .putString("wishes", org.json.JSONArray().put(wishToJson(w(1, "Teapot", "Home"))).put(wishToJson(w(2, "Lamp ABC123", "Tech").copy(priv = true, star = true, note = "blue", qty = 2, exact = "similar"))).toString())
            .putString("cats", "Tech\nHome")
            .putString("theme", "dark").putString("palette", "lavender").putString("style", "classic")
            .putString("font", "google_sans").putString("view", "list").putString("sort", "low")
            .putString("about", "Sizes: M").putInt("interval", 3).putString("name", "Mine")
            .putBoolean("instant_delete", true).putBoolean("notify_rises", true).putLong("lastCheck", 1234L)
            .putString("last_save_to", "private")
            .putString("mine_ids", "[\"main\",\"L2\"]")
            .putString("bin", org.json.JSONArray().put(wishToJson(w(9, "Old mug", "Home").copy(bin = 999L))).toString())
            .putString("bin_owner", "{\"9\":\"L2\"}")
            .putString("friends", "[{\"code\":\"FRIENDAA\",\"name\":\"Sam\"}]")
            .putString("recent", "{\"m:L2\":5000}")
            .putString("mine_copies", "{\"L2\":[1]}")
            .putString("hero", "")
        MyLists.write(e, MineData("L2", "Garden", "for the garden", listOf("Garden"), listOf(w(1, "Teapot", "Garden"), w(3, "Spade", "Garden", null)), null, null, emptySet()))
        e.commit()

        startApp(); note("first start of the new version")
        Repo.addMine(c, w(4, "Book", "Books")); tick()
        // A new second: two prices of one wish in the same second are one row in the database (its key is the wish and
        // the second), so the record would depend on how fast the computer is.
        Thread.sleep(1000 - System.currentTimeMillis() % 1000 + 20)
        Repo.replace(c, Repo.myWishes.first { it.id == 1L }.copy(title = "Blue teapot", price = 12.5)); note("added and changed (copy follows)")
        Repo.addCat(c, "Garden"); Repo.renameCat(c, "Home", "House"); Repo.addCat(c, "all"); Repo.addCat(c, "tech")
        note("categories")
        tick(); val b = Repo.createMine(c, "Birthday", "  my day  ")
        Repo.copyInto(c, b, listOf(w(4, "Book", "Books"))); note("new list with a copy")
        tick(); val t = Repo.trash(c, 4L)!!; note("original in the bin takes its copy")
        Repo.untrash(c, t); note("undo brings both back")
        tick(); Repo.trash(c, 2L); note("private wish in the bin")
        Repo.restoreMine(c, 2L); Repo.setPrivate(c, 2L, false); note("restored and no longer private")
        tick(); Repo.receive(c, "L2", setOf(3L)); note("received on a list that isn't open")
        Repo.unreceive(c, 3L); note("received gift back")
        Repo.changeEverywhere(c, 4L) { it.copy(star = true) }
        tick(); Repo.receiveEverywhere(c, 4L); note("I have it on every list")
        tick(); Repo.moveMine(c, MAIN_LIST, b, setOf(2L)); note("moved")
        Repo.openMine(c, "L2"); note("another list open")
        Repo.renameMine(c, MAIN_LIST, "Main list"); Repo.renameMine(c, "L2", "Garden!"); Repo.setAbout(c, "dig")
        Repo.setListName(c, "  "); note("renamed")
        tick(); Repo.touchRecent(c, "m:$b"); tick(); Repo.touchRecent(c, "s:SHARED11"); Repo.forgetRecent(c, "m:L2")
        Repo.setLook(c, "chaos", "light", "teal"); Repo.setFont(c, "google_sans"); Repo.setInstantDelete(c, false)
        Repo.setNotifyDrops(c, false); Repo.setNotifyRises(c, false); Repo.setView(c, "grid"); Repo.setSort(c, "az")
        Repo.setInterval(c, 12); Repo.setLastCheck(c, 777L); Repo.setLastSaveTo(c, "mine")
        Repo.addFriend(c, Friend("FRIENDBB", "Alex")); Repo.renameFriend(c, "FRIENDAA", "Sammy"); Repo.removeFriend(c, "FRIENDBB")
        note("settings and friends")
        tick(); Repo.deleteMine(c, MAIN_LIST); note("the original list deleted: the next takes its place")
        Repo.restoreMineTo(c, 1L, Repo.mineIds.first()); note("restored to another list")
        Repo.removeCat(c, "Books"); Repo.dropFromBin(c, Repo.myBin.map { it.id }.toSet()); note("category removed, bin emptied")
        Repo.cleanTitlesOnce(c); note("names cleaned once")
        Repo.setShareFor(c, b, "LINKCODE", "tok"); Repo.setUploadedFor(c, b, setOf("a.jpg")); note("a link for a list that isn't open")
        startApp(); note("started again: everything the same")

        val now = timesNamed(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
