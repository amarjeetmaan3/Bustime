package com.bustime.app.utils

object Constants {
    // API और बैकएंड
    const val BASE_URL = "https://your-private-api.com/"
    
    // AdMob टेस्ट IDs (बाद में Samsung Store वाले असली IDs से रिप्लेस होंगे)
    const val ADMOB_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val ADMOB_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"

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
