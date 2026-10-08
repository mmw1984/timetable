package com.timetable.wear.ui.theme

import androidx.compose.ui.graphics.Color

object SubjectColors {

    private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Color>()

    fun colorFor(subject: String): Color {
        val code = subject.trim().substringBefore(' ').substringBefore('\t').uppercase()
            .ifEmpty { return DefaultColor }
        // Distinct subjects are few; memoize to avoid Regex + Color allocs on every recomposition.
        colorCache[code]?.let { return it }
        val color = when (code) {
            "ENG" -> Color(0xFF8AB4F8)
            "CHIN" -> Color(0xFFF28B82)
            "MACO" -> Color(0xFF81C995)
            "PHY" -> Color(0xFFD2A8FF)
            "ICT" -> Color(0xFF78D9EC)
            "CS" -> Color(0xFF80CBC4)
            "PE" -> Color(0xFFFFB74D)
            "C&L", "CL" -> Color(0xFFFFE082)
            "CEP" -> Color(0xFFF48FB1)
            "M2", "MATH" -> Color(0xFFA5D6A7)
            "BIO" -> Color(0xFFAED581)
            "CHEM" -> Color(0xFFCE93D8)
            "CHIS", "HIST", "ECON", "BAFS", "VA", "HMSC", "CLIT" -> Color(0xFFB39DDB)
            else -> DefaultColor
        }
        if (colorCache.size < 64) colorCache[code] = color
        return color
    }

    private val DefaultColor = Color(0xFF9AA0A6)

    fun containerColorFor(subject: String, isCurrent: Boolean): Color? {
        if (isCurrent) return null
        return null
    }
}
