package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * The state holder of the people screens (the rebuild, owner 19 November 2026; MAP "How a screen is built"): the
 * Friends tab, a friend's page, adding a friend and your Profile only draw; everything that changes your friends or
 * your profile goes through here. The data itself stays in Friends (people, codes, notes) and Shared (your name).
 */
@Stable
internal class PeopleModel(private val ctx: Context) {
    // ---- friends ----
    suspend fun answer(id: String, yes: Boolean) = Friends.answer(ctx, id, yes)
    /** Withdrawing your request and unfriending are the same on the server: the link between you is removed. */
    suspend fun withdraw(id: String) = Friends.remove(ctx, id)
    suspend fun unfriend(id: String) = Friends.remove(ctx, id)
    /** Your private note about a friend: on this phone only, saved as you type. */
    fun saveNote(id: String, text: String) = Friends.setNote(ctx, id, text)
    /** A friend request with their code: the answer ("sent", "friends", "already", "self", …) and their name. */
    suspend fun addFriend(code: String): Pair<String, String> = Friends.add(ctx, code)
    suspend fun newCode() = Friends.newCode(ctx)

    // ---- your profile ----
    /** Your name on this phone (friends see it, and it's "Added by" on wishlists you make with others)… */
    fun setName(name: String) = Shared.setAppName(ctx, name)
    /** …and online, so friends' copies follow. */
    suspend fun sendName() = Friends.nameChanged(ctx)
    suspend fun setBirthday(birthday: String) = Friends.setAbout(ctx, birthday, Friends.myBio)
    suspend fun setBio(bio: String) = Friends.setAbout(ctx, Friends.myBirthday, bio)
    suspend fun setPhoto(src: String, edit: HeroEdit): Boolean = Friends.setPhoto(ctx, src, edit)
    suspend fun removePhoto() = Friends.removePhoto(ctx)
}

/** The people screens' state holder, one per screen. */
@Composable
internal fun rememberPeople(): PeopleModel {
    val ctx = LocalContext.current
    return remember { PeopleModel(ctx) }
}
