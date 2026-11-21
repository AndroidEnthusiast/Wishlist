package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * The Add / Edit wish form's state holder (the rebuild, owner 19 November 2026; MAP "How a screen is built"): what is
 * typed and chosen, reading a product link, the photo, the list it is saved to, and saving. The form (WishDialog in
 * WishForm.kt) only draws. [edit]: the wish being edited, or null for a new one.
 */
@Stable
internal class WishFormState(
    private val ctx: Context, private val edit: Wish?, private val chooseList: Boolean, private val fromShop: Boolean,
    private val places: Places, private val scope: CoroutineScope, initialUrl: String,
    // A screenshot's photo, already cut in the photo editor (Screenshot to wish, owner 20 November 2026).
    initialImage: String = ""
) {
    // Which wishlist was open when this dialog started. A wish shared in from the browser can be saved to another one.
    private val startList = Shared.active
    var url by mutableStateOf(edit?.url ?: initialUrl)
    var title by mutableStateOf(edit?.title ?: "")
    var price by mutableStateOf(edit?.price?.let { "%.2f".format(Locale.US, it) } ?: "")
    var desc by mutableStateOf(edit?.desc ?: "")
    var image by mutableStateOf(edit?.image ?: initialImage)
    var cur by mutableStateOf(edit?.currency ?: localCurrency())
    // A new wish starts without a category: you pick one yourself if you want (2.64).
    var cat by mutableStateOf(edit?.category ?: "")
    var note by mutableStateOf(edit?.note ?: "")
    var sizeSel by mutableStateOf(edit?.size ?: "")
    // How many (owner, 2.128). The exact-or-similar value is kept as it was (no longer shown or chosen, 16 November 2026).
    var qty by mutableStateOf(edit?.qty ?: 1)
    var exact by mutableStateOf(edit?.exact ?: "")
    var star by mutableStateOf(edit?.star ?: false)
    // "Private wish": only on this phone, friends never see it (My wishlist only). Off unless chosen.
    var priv by mutableStateOf(edit?.priv ?: false)
    var busy by mutableStateOf(false)
    // The name and photo are in, the hidden browser is still looking for the price (owner, 21 November 2026).
    var lookingForPrice by mutableStateOf(false)
    // Save was tapped while the page was still being read: it saves once the reading is done (owner, 21 November 2026).
    var saveWaiting by mutableStateOf(false)
    private var reading: Job? = null
    // The price field when the quick read filled the form: another price there later was typed while waiting.
    private var priceBefore = ""
    var err by mutableStateOf("")
    // True after Save was tapped without a name: the name field turns red with a short message.
    var nameErr by mutableStateOf(false)
    var newCatDlg by mutableStateOf(false)
    var editCats by mutableStateOf(false)
    // A picture picked in this dialog but not saved yet. It is deleted again if you cancel or replace it.
    var picked by mutableStateOf("")
    // "+ New wishlist" from the Save to menu: the usual form, then this wish goes there (owner, 7 November 2026).
    var newListDlg by mutableStateOf(false)
    // Take photo / Choose photo, then a free crop (owner, 7 November 2026: "so the picture is perfect").
    var photoMenu by mutableStateOf(false)
    var cropping by mutableStateOf<String?>(null)
    // The price as the page gave it (2.178): another price at Save was typed by the person, and the first price check
    // leaves a typed price alone (PriceWatch).
    private var readPrice by mutableStateOf<String?>(null)

    /** A photo taken or chosen (its copy on the phone, null when it couldn't be used): the crop comes next. */
    fun imported(p: String?) {
        if (p == null) err = tr("Couldn’t use that picture.") else { cropping = p; err = "" }
    }

    /** Cancel: a picture picked here goes, and the wishlist that was open before is open again. */
    fun leave() {
        reading?.cancel()
        if (picked.isNotEmpty()) ImageStore.delete(ctx, picked)
        // Cancelled after choosing another wishlist: go back to the one that was open.
        if (Shared.active != startList) {
            if (startList == null) Shared.close() else Shared.open(ctx, startList)
        }
    }

    /** What the first, quick read found (name, photo) while the price is still being looked for. */
    fun useFirst(m: Meta) {
        title = m.title; cur = m.currency
        if (!ImageStore.isLocal(image)) image = if (m.image.isNotEmpty() || edit == null) m.image else image
        priceBefore = price
        lookingForPrice = true
    }

    /**
     * Fills the form from what was read from a page (a link, or the shop page on screen in Find a gift). [shown]: what
     * the quick read already put in the form; whatever was typed over it since is kept.
     */
    fun useMeta(m: Meta?, shown: Meta? = null) {
        lookingForPrice = false
        if (shown != null && m != null && !m.blocked) {
            title = keepTyped(title, shown.title, m.title)
            if (!ImageStore.isLocal(image)) image = keepTyped(image, shown.image, m.image)
            // A price typed while waiting stays; the currency goes with the price.
            if (price == priceBefore && (m.price != null || edit == null)) {
                cur = m.currency
                price = m.price?.let { "%.2f".format(Locale.US, it) }.orEmpty(); readPrice = price
            }
            if (m.price == null && price.isBlank()) err = tr("No price found on that page. Add it to track price drops.")
            return
        }
        if (m == null) {
            err = tr("Couldn’t read that page. Fill in the details yourself.")
        } else if (m.blocked) {
            err = tr("Couldn’t read this shop’s page. Fill in the name and price yourself.")
        } else {
            // The shop's sales text no longer fills Description (owner, 2.137): it stays free for your own words.
            title = m.title; cur = m.currency
            // A picture you chose yourself is kept. Otherwise the image comes from the link.
            if (!ImageStore.isLocal(image)) image = if (m.image.isNotEmpty() || edit == null) m.image else image
            if (m.price != null || edit == null) { price = m.price?.let { "%.2f".format(Locale.US, it) }.orEmpty(); readPrice = price }
            if (m.price == null) err = tr("No price found on that page. Add it to track price drops.")
        }
    }

    /** Reads the product page of link [raw] and fills the form. */
    fun go(raw: String) {
        val u = cleanUrl(raw)
        if (u == null) { err = tr("Enter a full product link"); return }
        url = u; busy = true; err = ""; lookingForPrice = false
        reading?.cancel()
        reading = scope.launch {
            var shown: Meta? = null
            val m = fetchMeta(u, ctx, quick = true, onFirst = { shown = it; useFirst(it) })
            useMeta(m, shown)
            busy = false
        }
    }

    // A wish shared in from the browser goes where the last one went (My wishlist, private, or a shared list).
    fun pickTarget(key: String) {
        val mineId = MyLists.idOfSaveKey(key)
        if (mineId != null) {
            Shared.close()
            Repo.openMine(ctx, mineId)
        } else if (Shared.lists.any { it.code == key }) {
            Shared.open(ctx, key); priv = false
        }
        // Every wishlist has its own categories; none is picked for you.
        cat = ""
    }

    /** When the form opens: the list to save to, and the page of a shared link read (or what was read already). */
    fun start(startTarget: String?, initialUrl: String, initialMeta: Meta?) {
        if (chooseList && edit == null) pickTarget(startTarget ?: Repo.lastSaveTo)
        if (edit == null && initialUrl.isNotEmpty()) {
            if (initialMeta != null) { url = initialUrl; useMeta(initialMeta) } else go(initialUrl)
        }
    }

    /** ✕ on the photo: a picture picked here goes at once. */
    fun removePhoto() {
        if (picked.isNotEmpty() && picked == image) { ImageStore.delete(ctx, picked); picked = "" }
        image = ""
    }

    /** The photo editor's result ([chosen] from the photo [src], edited as [e]) becomes the wish's photo. */
    fun photoEdited(src: String, chosen: String, e: HeroEdit) {
        cropping = null
        scope.launch {
            val out = renderWishPhoto(ctx, chosen, e) ?: chosen
            if (out != chosen) ImageStore.delete(ctx, chosen)
            if (src != chosen && src != out) ImageStore.delete(ctx, src)
            if (picked.isNotEmpty() && picked != out) ImageStore.delete(ctx, picked)
            picked = out; image = out
        }
    }

    /** The photo editor's back arrow: the new photo goes (the wish keeps the picture it had). */
    fun dropCrop(src: String) { cropping = null; ImageStore.delete(ctx, src) }

    /** Saving the wish (the Save button). False without a name: every wish needs one (owner, 20 November 2026). */
    fun save(): Boolean {
        if (title.isBlank()) { nameErr = true; return false }
        val p = price.replace(',', '.').toDoubleOrNull()
        val u = cleanUrl(url) ?: url.trim()
        // A picture that was replaced or removed in this dialog is no longer needed.
        if (edit != null && edit.image != image) ImageStore.delete(ctx, edit.image)
        picked = ""
        // Your own wishlists only: collab wishlists share one price between everyone's phones.
        val typedPrice = p != null && Shared.active == null && u.isNotBlank() && price != readPrice &&
            (edit == null || !PriceWatch.same(p, edit.price))
        if (edit == null) {
            val newId = System.currentTimeMillis()
            if (typedPrice && p != null) PriceWatch.noteTyped(ctx, newId, u, p)
            Repo.add(
                ctx,
                Wish(
                    newId, u, title.trim(), desc, image, p, null, cur, cat,
                    star = star && Shared.active == null, note = note.trim(), size = sizeSel, by = Repo.authorName,
                    priv = priv && Shared.active == null, qty = qty, exact = exact
                )
            )
            if (chooseList) Repo.setLastSaveTo(ctx, Shared.active ?: MyLists.saveKey(Repo.openMine, false))
            if (fromShop) places.shopSaved(Repo.listName)
        } else {
            if (typedPrice && p != null) PriceWatch.noteTyped(ctx, edit.id, u, p)
            Repo.replace(
                ctx,
                edit.copy(
                    url = u, title = title.trim(), desc = desc, image = image, price = p,
                    oldPrice = if (p == edit.price) edit.oldPrice else null,
                    currency = cur, category = cat, star = star && Shared.active == null, note = note.trim(), size = sizeSel,
                    priv = priv && Shared.active == null, qty = qty, exact = exact
                ),
                base = edit
            )
        }
        return true
    }
}

/**
 * A field filled twice from a page (the quick read, then the hidden browser): the second value replaces the first
 * only when nobody typed over it ([current] is still what was [shown], or empty) and the second has something.
 */
internal fun keepTyped(current: String, shown: String, found: String): String =
    if ((current == shown || current.isBlank()) && found.isNotBlank()) found else current
