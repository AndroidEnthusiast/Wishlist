package com.wishlist.app

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * The database on the phone (ROADMAP Stage 2, Room = Android's own SQLite library). 2.165 made the tables, 2.166
 * copied the data in as a rehearsal, and since 2.167 your lists and wishes are read from here (ListStore.kt); the
 * settings store is still written as the fallback until the owner's OK (S2.5).
 *
 * One row per thing instead of one big text per list: a wish is a row of `wishes` (a wish copied into another list is
 * a second row with the same id), so saving one wish never rewrites a whole list, and a save is all or nothing.
 */

/** One of your own wishlists. `photos` (the names of pictures already online) stays a JSON list: free-form. */
@Entity(tableName = "lists")
data class ListRow(
    @PrimaryKey val id: String,
    val name: String,
    val about: String,
    val code: String?,
    val token: String?,
    val photos: String,
    val position: Int
)

/** Every field of a [Wish] except its id: shared by [WishRow] and [BinRow]. */
data class WishCols(
    val url: String,
    val title: String,
    val desc: String,
    val image: String,
    val price: Double?,
    @ColumnInfo(name = "old_price") val oldPrice: Double?,
    val currency: String,
    val category: String,
    val got: Boolean,
    val star: Boolean,
    val note: String,
    val size: String,
    @ColumnInfo(name = "by_name") val by: String,
    val bin: Long,
    @ColumnInfo(name = "bin_by") val binBy: String,
    val checked: Long,
    val priv: Boolean,
    val qty: Int,
    val exact: String,
    val recv: Long
)

/** A wish on one of your lists, in its place on that list. Deleting a list deletes its wishes. */
@Entity(
    tableName = "wishes",
    primaryKeys = ["list_id", "id"],
    foreignKeys = [ForeignKey(entity = ListRow::class, parentColumns = ["id"], childColumns = ["list_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("list_id"), Index("id")]
)
data class WishRow(
    @ColumnInfo(name = "list_id") val listId: String,
    val id: Long,
    val position: Int,
    @Embedded val w: WishCols
)

/** A wish in the bin (Bin.kt), with the list it came from; that list may be gone already, so no link to it. */
@Entity(tableName = "bin", primaryKeys = ["list_id", "id"])
data class BinRow(
    @ColumnInfo(name = "list_id") val listId: String,
    val id: Long,
    val position: Int,
    @Embedded val w: WishCols
)

/** One category; the same set for all your own lists (owner, 2.95). */
@Entity(tableName = "categories")
data class CategoryRow(@PrimaryKey val name: String, val position: Int)

/** One point of a wish's price history (History.kt): when (seconds) and the price. */
@Entity(tableName = "price_points", primaryKeys = ["wish_id", "at"])
data class PricePointRow(@ColumnInfo(name = "wish_id") val wishId: Long, val at: Long, val price: Double)

/** A friend's list saved from a link or code (Repo.friends). */
@Entity(tableName = "friend_lists")
data class FriendListRow(@PrimaryKey val code: String, val name: String, val position: Int)

/** A line on the Notifications page (Notifications.kt, [PriceNote]). */
@Entity(tableName = "notifications")
data class NoteRow(
    @PrimaryKey(autoGenerate = true) val row: Long = 0,
    @ColumnInfo(name = "wish_id") val wishId: Long,
    @ColumnInfo(name = "list_key") val listKey: String,
    val title: String,
    val image: String,
    val pct: Double,
    val time: Long,
    val read: Boolean,
    val kind: String,
    val amount: Double,
    val currency: String,
    val position: Int
)

/** Small facts about the database itself, such as when the data was moved in and how much (S2.3). */
@Entity(tableName = "meta")
data class MetaRow(@PrimaryKey val key: String, val value: String)

fun Wish.cols() = WishCols(
    url, title, desc, image, price, oldPrice, currency, category, got, star, note, size, by, bin, binBy, checked, priv,
    qty, exact, recv
)

fun WishCols.toWish(id: Long) = Wish(
    id, url, title, desc, image, price, oldPrice, currency, category, got, star, note, size, by, bin, binBy, checked,
    priv, qty, exact, recv
)

fun PriceNote.toRow(position: Int) = NoteRow(
    wishId = wishId, listKey = listKey, title = title, image = image, pct = pct, time = time, read = read, kind = kind,
    amount = amount, currency = currency, position = position
)

fun NoteRow.toNote() = PriceNote(wishId, listKey, title, image, pct, time, read, kind, amount, currency)

@Dao
interface WishlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putLists(rows: List<ListRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putWishes(rows: List<WishRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putBin(rows: List<BinRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putCategories(rows: List<CategoryRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putPricePoints(rows: List<PricePointRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putFriendLists(rows: List<FriendListRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putNotes(rows: List<NoteRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putMeta(row: MetaRow)

    @Query("SELECT * FROM lists ORDER BY position") fun lists(): List<ListRow>
    @Query("SELECT * FROM wishes WHERE list_id = :listId ORDER BY position") fun wishes(listId: String): List<WishRow>
    @Query("SELECT * FROM bin ORDER BY position") fun bin(): List<BinRow>
    @Query("SELECT * FROM categories ORDER BY position") fun categories(): List<CategoryRow>
    @Query("SELECT * FROM price_points WHERE wish_id = :wishId ORDER BY at") fun pricePoints(wishId: Long): List<PricePointRow>
    @Query("SELECT * FROM price_points ORDER BY wish_id, at") fun allPricePoints(): List<PricePointRow>
    @Query("SELECT * FROM friend_lists ORDER BY position") fun friendLists(): List<FriendListRow>
    @Query("SELECT * FROM notifications ORDER BY position") fun notes(): List<NoteRow>
    @Query("SELECT value FROM meta WHERE `key` = :key") fun meta(key: String): String?

    @Query("SELECT COUNT(*) FROM lists") fun countLists(): Int
    @Query("SELECT COUNT(*) FROM wishes") fun countWishes(): Int
    @Query("SELECT COUNT(*) FROM bin") fun countBin(): Int
    @Query("SELECT COUNT(*) FROM price_points") fun countPricePoints(): Int

    @Query("DELETE FROM lists WHERE id = :id") fun deleteList(id: String)

    @Query("DELETE FROM wishes") fun clearWishes()
    @Query("DELETE FROM lists") fun clearLists()
    @Query("DELETE FROM bin") fun clearBin()
    @Query("DELETE FROM categories") fun clearCategories()
    @Query("DELETE FROM price_points") fun clearPricePoints()
    @Query("DELETE FROM friend_lists") fun clearFriendLists()
    @Query("DELETE FROM notifications") fun clearNotes()

    /** Empties every table except `meta` (inside the caller's transaction). */
    fun clearData() {
        clearWishes(); clearLists(); clearBin(); clearCategories(); clearPricePoints(); clearFriendLists(); clearNotes()
    }
}

@Database(
    entities = [
        ListRow::class, WishRow::class, BinRow::class, CategoryRow::class, PricePointRow::class,
        FriendListRow::class, NoteRow::class, MetaRow::class
    ],
    version = 1,
    exportSchema = true
)
abstract class WishlistDb : RoomDatabase() {
    abstract fun dao(): WishlistDao

    companion object {
        @Volatile private var db: WishlistDb? = null

        /** For the checks: a new pretend phone gets its own database file. */
        internal fun reset() { db?.close(); db = null }

        /** The app's one database, "wishlist.db" in the app's own folder. */
        fun get(c: Context): WishlistDb = db ?: synchronized(this) {
            db ?: Room.databaseBuilder(c.applicationContext, WishlistDb::class.java, "wishlist.db").build().also { db = it }
        }
    }
}
