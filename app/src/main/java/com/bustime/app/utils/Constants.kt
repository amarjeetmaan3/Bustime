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

    // Supabase (यूज़र से मिले रूट के लिए)। Supabase में Settings -> API से लेकर यहाँ भरो।
    // anon key सार्वजनिक रहती है, यही उसका काम है: यह सिर्फ़ नया रूट भेजने की इजाज़त देती है, पढ़ने की नहीं।
    const val SUPABASE_URL = "https://mdryxbvmqsphbfkvqtnf.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1kcnl4YnZtcXNwaGJma3ZxdG5mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE1NDA0NzEsImV4cCI6MjEwNzExNjQ3MX0.Hc577LoBVsLFQIZnJ7thhpCSuRBV5Odplj7uua6BWP0"

    // SharedPreferences
    const val PREFS_NAME = "BustimePrefs"
    const val KEY_LAST_SYNC_TIME = "last_sync_time"
}
