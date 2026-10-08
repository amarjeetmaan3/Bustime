package com.bustime.app.utils

import java.text.Normalizer

object TextNormalizer {
    private val NOT_TEXT = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    /** "Jaipur ", "jaipur", "JAIPUR." सब एक जैसे; हिंदी/पंजाबी की मात्राएँ सुरक्षित रहती हैं */
    fun normalize(input: String): String {
        val s = Normalizer.normalize(input, Normalizer.Form.NFKC)
            .lowercase()
            .replace("\u200c", "")
            .replace("\u200d", "")
        return NOT_TEXT.replace(s, " ").trim()
    }
}
