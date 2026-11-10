package com.wishlist.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import java.io.File

/**
 * A phone with an older database gets the newest one without losing anything (ROADMAP "Data is sacred"; 2.178 is the
 * first version that changes the database). For every saved layout in app/schemas: a database is made exactly as that
 * version made it, filled with a list and a wish, then opened by today's app with its migrations.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class DbMigrationTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val schemas: File = listOf("schemas/com.wishlist.app.WishlistDb", "app/schemas/com.wishlist.app.WishlistDb").map(::File).first { it.isDirectory }

    private fun makeOld(version: Int, name: String) {
        val db = JSONObject(File(schemas, "$version.json").readText()).getJSONObject("database")
        val f = c.getDatabasePath(name)
        f.parentFile!!.mkdirs(); f.delete()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { s ->
            val es = db.getJSONArray("entities")
            for (i in 0 until es.length()) {
                val e = es.getJSONObject(i)
                s.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val ix = e.optJSONArray("indices") ?: continue
                for (k in 0 until ix.length()) s.execSQL(ix.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val q = db.getJSONArray("setupQueries")
            for (i in 0 until q.length()) s.execSQL(q.getString(i))
            s.execSQL("INSERT INTO lists (id, name, about, code, token, photos, position) VALUES ('main', 'My wishlist', '', NULL, NULL, '[]', 0)")
            s.execSQL(
                "INSERT INTO wishes (list_id, id, position, url, title, `desc`, image, price, currency, category, got, star, note, size, by_name, bin, bin_by, checked, priv, qty, exact, recv) " +
                    "VALUES ('main', 7, 0, 'https://shop.example/x', 'Teapot', '', '', 34.95, 'EUR', 'Home', 0, 1, '', '', '', 0, '', 0, 0, 1, '', 0)"
            )
            s.execSQL("INSERT INTO price_points (wish_id, at, price) VALUES (7, 100, 34.95)")
            s.version = version
        }
    }

    @Test fun everyOlderDatabaseOpensWithItsDataAndTheNewTables() {
        val newest = schemas.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        assertTrue("the newest layout is saved in app/schemas (build once so Room writes it)", newest >= 2)
        for (v in 1 until newest) {
            val name = "old-$v.db"
            makeOld(v, name)
            val db = Room.databaseBuilder(c, WishlistDb::class.java, name).addMigrations(*WishlistDb.MIGRATIONS).allowMainThreadQueries().build()
            try {
                val dao = db.dao()
                val w = dao.wishes("main").single()
                assertEquals("Teapot", w.w.title)
                assertEquals(34.95, w.w.price!!, 0.0)
                assertTrue(w.w.star)
                assertEquals(1, dao.countPricePoints())
                // The new table is there and works.
                assertNull(dao.watch(7))
                dao.putWatch(PriceWatchRow(7, "https://shop.example/x", 34.95, "EUR", 34.95, false, null, 1L, 0L))
                assertEquals(34.95, dao.watch(7)!!.read!!, 0.0)
            } finally {
                db.close()
            }
        }
    }
}
