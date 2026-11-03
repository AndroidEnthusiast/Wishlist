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

    /** The newest app, rebuilt by GitHub on every change (the release "latest"). Also linked from the invite page. */
    const val APK_URL = "https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk"

    /** The Supabase storage bucket for pictures you add yourself (created by supabase-photos.sql). */
    const val PHOTO_BUCKET = "wish-photos"

    /*
     * Instant message notifications (Push.kt): the values of the Firebase project, from the Firebase console >
     * Project settings > General (Web API key, Project ID, Project number = sender ID) and "Your apps" > the Android
     * app (App ID). They are public, like the Supabase key. Empty: no push, the background check stays.
     */
    const val FCM_API_KEY = "AIzaSyBdXo4kptWlwzpPUYR-JEoGDE8t8rqYpqc"
    const val FCM_APP_ID = "1:173058587232:android:dd63c28f3e82f6308ce105"
    const val FCM_PROJECT_ID = "wishlist-a2cd4"
    const val FCM_SENDER_ID = "173058587232"

    val enabled: Boolean
        get() = SUPABASE_URL.isNotBlank() && SUPABASE_KEY.isNotBlank()
}
