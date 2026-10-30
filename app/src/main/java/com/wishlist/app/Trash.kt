package com.wishlist.app

/**
 * A wish that was just deleted and can still be brought back with Undo.
 * [index] is where it stood in the list. [listCode] is the shared wishlist it was in, or null for My wishlist.
 */
class Trashed(val wish: Wish, val index: Int, val listCode: String?)
