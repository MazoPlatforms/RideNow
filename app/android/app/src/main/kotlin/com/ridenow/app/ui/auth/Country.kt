package com.ridenow.app.ui.auth

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** [pattern] uses '#' for a digit, e.g. "(###) ### ####". [min]..[max] is the national number length. */
data class Country(val iso: String, val name: String, val dial: String, val pattern: String, val min: Int, val max: Int) {
    /** Placeholder shown in the empty field. */
    val example: String
        get() {
            val sample = "5550123456789"
            var i = 0
            return pattern.map { if (it == '#') sample[i++ % sample.length] else it }.joinToString("")
        }
}

object Countries {
    val all = listOf(
        Country("LK", "Sri Lanka", "+94", "## ### ####", 9, 9),
    )
    val default = byIso("LK")
    val suggested = listOf(byIso("LK"))
    fun byIso(iso: String) = all.first { it.iso == iso }
}

/** Formats raw digits with the country's pattern while keeping the cursor in the right place. */
class PhoneTransformation(private val pattern: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val sb = StringBuilder()
        val digitPos = ArrayList<Int>()
        var d = 0
        for (ch in pattern) {
            if (d >= digits.length) break
            if (ch == '#') { digitPos.add(sb.length); sb.append(digits[d]); d++ } else sb.append(ch)
        }
        val formatted = sb.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 0 -> digitPos.firstOrNull() ?: 0
                offset >= digitPos.size -> formatted.length
                else -> digitPos[offset - 1] + 1
            }
            override fun transformedToOriginal(offset: Int): Int = digitPos.count { it < offset }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
