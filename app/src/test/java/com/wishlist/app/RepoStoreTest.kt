package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** The app itself (Repo) saves into the database only, since 2.171 (ROADMAP S2.5). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class RepoStoreTest {
    @Test fun aWishAddedInTheAppIsInTheDatabase() {
        val c = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        val teapot = Wish(1, "https://shop.example/1", "Teapot", "", "", 10.0, null, "EUR", "Home")
        prefs.edit().clear().putString("wishes", JSONArray().put(wishToJson(teapot)).toString()).putString("cats", "Home").commit()
        ListStore.reset(); WishlistDb.reset()
        Repo.load(c)
        val oldStore = prefs.getString("wishes", null)
        Repo.addMine(c, teapot.copy(id = 2, title = "Lamp"))
        ListStore.flush()
        val inDb = java.util.concurrent.Executors.newSingleThreadExecutor().submit(java.util.concurrent.Callable {
            WishlistDb.get(c).dao().wishes(MAIN_LIST).map { it.w.title }
        }).get()
        assertEquals(setOf("Teapot", "Lamp"), inDb.toSet())
        // The settings store's old copy is untouched.
        assertEquals(oldStore, prefs.getString("wishes", null))
        // A new start of the app reads the database.
        ListStore.reset(); ListStore.start(c)
        assertEquals(setOf("Teapot", "Lamp"), ListStore.get(MAIN_LIST)!!.wishes.map { it.title }.toSet())
    }
}
