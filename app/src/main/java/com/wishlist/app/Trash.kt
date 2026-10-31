package com.wishlist.app

/**
 * A wish that was just moved to the bin and can be taken out again with Undo. [wish] is the wish as it is in the bin,
 * [index] is where it stood in the list. [listCode] is the shared wishlist it was in, or null for My wishlist.
 */
class Trashed(val wish: Wish, val index: Int, val listCode: String?)
