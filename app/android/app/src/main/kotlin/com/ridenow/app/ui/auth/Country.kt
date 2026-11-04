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
        Country("AU", "Australia", "+61", "### ### ###", 9, 9),
        Country("BD", "Bangladesh", "+880", "#### ######", 10, 10),
        Country("BR", "Brazil", "+55", "## #####-####", 10, 11),
        Country("CA", "Canada", "+1", "(###) ### ####", 10, 10),
        Country("FR", "France", "+33", "# ## ## ## ##", 9, 9),
        Country("DE", "Germany", "+49", "### ########", 10, 11),
        Country("IN", "India", "+91", "##### #####", 10, 10),
        Country("IT", "Italy", "+39", "### ### ####", 9, 10),
        Country("JP", "Japan", "+81", "## #### ####", 10, 10),
        Country("MX", "Mexico", "+52", "## #### ####", 10, 10),
        Country("NL", "Netherlands", "+31", "# ########", 9, 9),
        Country("NZ", "New Zealand", "+64", "## ### ####", 8, 10),
        Country("PK", "Pakistan", "+92", "### #######", 10, 10),
        Country("SG", "Singapore", "+65", "#### ####", 8, 8),
        Country("ZA", "South Africa", "+27", "## ### ####", 9, 9),
        Country("ES", "Spain", "+34", "### ## ## ##", 9, 9),
        Country("LK", "Sri Lanka", "+94", "## ### ####", 9, 9),
        Country("AE", "United Arab Emirates", "+971", "## ### ####", 9, 9),
        Country("GB", "United Kingdom", "+44", "#### ######", 10, 10),
        Country("US", "United States", "+1", "(###) ### ####", 10, 10),
    )
    val default = byIso("US")
    val suggested = listOf(byIso("US"), byIso("GB"), byIso("IN"))
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
