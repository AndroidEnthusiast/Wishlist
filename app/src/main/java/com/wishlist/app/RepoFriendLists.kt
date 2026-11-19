package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Friends' wishlists you follow, and this phone's own id (a part of [Repo] since the data rebuild, owner 19 November 2026). */
internal object FriendLists {
    var friends by mutableStateOf(listOf<Friend>())
        internal set
    var deviceId: String = ""
        internal set
}

// ---- what you can do with it (callers keep writing Repo.…, as before the rebuild) ----

fun Repo.addFriend(c: Context, f: Friend): Unit = synchronized(this) { FriendLists.friends = friends.filter { it.code != f.code } + f; persist(c) }

fun Repo.removeFriend(c: Context, code: String): Unit = synchronized(this) { FriendLists.friends = friends.filter { it.code != code }; persist(c); FriendWatch.forget(c, code) }

fun Repo.renameFriend(c: Context, code: String, name: String): Unit = synchronized(this) {
    FriendLists.friends = friends.map { if (it.code == code) it.copy(name = name) else it }; persist(c)
}
