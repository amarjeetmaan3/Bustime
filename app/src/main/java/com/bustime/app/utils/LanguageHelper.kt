package com.bustime.app.utils

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

object LanguageHelper {
    /** अभी चुनी हुई भाषा: "en", "hi", "pa" (ऐप में चुनी हो तो वो, वरना फ़ोन की) */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val lang = if (!locales.isEmpty) locales[0]?.language else Locale.getDefault().language
        return lang?.takeIf { it.isNotEmpty() } ?: "en"
    }
}
