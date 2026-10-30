package com.wishlist.app

/**
 * Online sharing uses a free Supabase project.
 * Paste your Project URL and your publishable (or anon) key between the quotes.
 * These two values are meant to be public, so it is fine to keep them in the app.
 */
object Config {
    const val SUPABASE_URL = "https://aspnmvmsvwbezdltykgx.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_2RpbWlDJBB3yV2TO-h68xA_i-gXfd_t"

    /** Friends open this page to see your list. It is the web page in the repo, served by GitHub Pages. */
    const val SHARE_URL = "https://androidenthusiast.github.io/Wishlist/"

    /** The Supabase storage bucket for pictures you add yourself (created by supabase-photos.sql). */
    const val PHOTO_BUCKET = "wish-photos"

    val enabled: Boolean
        get() = SUPABASE_URL.isNotBlank() && SUPABASE_KEY.isNotBlank()
}
