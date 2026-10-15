package com.bustime.app.utils

object Constants {
    // API और बैकएंड
    const val BASE_URL = "https://your-private-api.com/"
    
    // GitHub अपडेटर: यहाँ अपना GitHub username लिखो (रिपो का नाम Bustime है)
    const val GITHUB_OWNER = "amarjeetmaan3"
    const val GITHUB_REPO = "Bustime"

    // Supabase client: publishable keys belong in the apikey header, not Authorization: Bearer.
    const val SUPABASE_URL = "https://mdryxbvmqsphbfkvqtnf.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_1J6qNzpvPH-TTI-qxRU8wA_RVO7EpfM"

    // SharedPreferences
    const val PREFS_NAME = "BustimePrefs"
    const val KEY_LAST_SYNC_TIME = "last_sync_time"
}
