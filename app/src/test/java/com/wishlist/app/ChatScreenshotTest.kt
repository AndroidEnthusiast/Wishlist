package com.wishlist.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import java.util.Calendar

/**
 * Pictures of Messages with pretend chats (added for the rebuild, 19 November 2026, so the before/after comparison
 * covers them): the conversations, a chat with text, a wish, a reply, reactions, an invitation and a photo, its ⋮
 * menu, the long-press menu, Delete, Send a wish, and a chat with someone who is no longer a friend; light and dark.
 * Fixed dates, so the times on screen never change. Pictures in app/build/outputs/roborazzi/chat_*.png; only with
 * -Pwl.screens (the class name has "Screenshot").
 */
class ChatScreenshotTest : NavigationBase() {
    private fun settle() = repeat(8) { compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.guard("build/outputs/roborazzi/chat_$name.png"); compose.onRoot().captureRoboImage("build/outputs/roborazzi/chat_$name.png") }

    private fun at(day: Int, h: Int, m: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, day, h, m) }.timeInMillis
    private fun text(id: Long, mine: Boolean, s: String, t: Long, read: Boolean = true, react: String = "", theirs: String = "") =
        ChatMessage(id, mine, ChatContent(true, "text", s), t, read, react, theirs)

    private fun pretend() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit()
            .putString("pf_id", "me1").putString("pf_token", "tok").commit()
        val msgs = listOf(
            text(1, false, "Hi! What would you like for your birthday?", at(5, 10, 2)),
            text(2, true, "Ooh, let me send you something", at(5, 10, 4)),
            ChatMessage(3, true, ChatContent(true, "wish", "", "Cosy wool blanket in forest green", 49.95, "EUR", "", "https://example.com/blanket"), at(5, 10, 5), true),
            ChatMessage(4, false, ChatContent(true, "text", "Love it, reserved!", replyTo = 3, replyText = "Cosy wool blanket in forest green", replyBy = "me1"), at(5, 10, 20), true, "❤️", "🎉"),
            ChatMessage(5, false, ChatContent(true, "group", "g1", "Family", url = "https://example.com/g#LINK"), at(6, 9, 0), true),
            ChatMessage(6, false, ChatContent(false), at(6, 9, 1), true),
            text(7, true, "Thanks! See you Saturday", at(6, 9, 30), read = false)
        )
        val convs = listOf(
            Conversation("p2", "Magdalena", 0L, "", true, 2, msgs.last().copy(mine = false)),
            Conversation("p3", "Jorai with a very long name indeed", 0L, "", true, 0, text(9, true, "Okay!", at(4, 18, 45))),
            Conversation("p4", "Noor", 0L, "", false, 0, null)
        )
        compose.runOnUiThread {
            Friends.load(app)
            Friends.state = FriendsState("C1", listOf(Person("p2", "Magdalena", 0L), Person("p3", "Jorai with a very long name indeed", 0L)), emptyList(), emptyList())
            Chat.conversations = convs
            Chat.opened["p2"] = msgs
            Chat.opened["p4"] = listOf(text(8, false, "Bye!", at(3, 12, 0)))
        }
    }

    private fun open(peer: String) {
        compose.runOnUiThread { compose.activity.handle(android.content.Intent(compose.activity, MainActivity::class.java).putExtra(MainActivity.OPEN_CHAT, peer)) }
        settle()
    }

    private fun list() { settle(); tap("Messages"); settle() }

    @Test fun chatScreens() {
        compose.mainClock.autoAdvance = false
        pretend()
        list(); shot("list")
        open("p2"); shot("chat")
        open("p4"); shot("former")
    }

    @Test fun chatScreensDark() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.setLook(app, Repo.design, "dark", Repo.palette) }
        compose.mainClock.autoAdvance = false
        pretend()
        list(); shot("list_dark")
        open("p2"); shot("chat_dark")
    }

    @Test fun chatMenus() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("p2")
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performClick()
        shot("menu")
    }

    @Test fun chatLongPress() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("p2")
        compose.onAllNodes(hasText("Thanks! See you Saturday")).onFirst().performTouchInput { longClick() }
        shot("longpress")
        compose.onAllNodes(hasText("Delete") and hasClickAction()).onFirst().performClick()
        shot("delete")
    }

    @Test fun chatSendWish() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("p2")
        compose.onAllNodes(hasClickAction() and androidx.compose.ui.test.SemanticsMatcher("send a wish") {
            (if (androidx.compose.ui.semantics.SemanticsActions.OnClick in it.config) it.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label else null) == "Send a wish"
        }).onFirst().performClick()
        shot("wish")
    }

    // "Chat about this wish" (owner, 20 November 2026): the wish above the text box, and a sent message about a wish.
    @Test fun chatAboutAWish() {
        compose.mainClock.autoAdvance = false
        pretend()
        val about = ChatWish("Blue teapot, large", 39.0, "EUR", "", "https://example.com/teapot", "FRIEND01", 42L)
        compose.runOnUiThread {
            Chat.opened["p2"] = Chat.opened["p2"].orEmpty() +
                ChatMessage(10, false, ChatContent(true, "text", "Is it the large one you want?", about = about), at(6, 10, 0), true)
            compose.activity.deep = Deep.Chat("p2", about)
        }
        settle(); settle()
        shot("about")
    }
}
