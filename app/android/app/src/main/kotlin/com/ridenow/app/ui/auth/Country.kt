package com.ridenow.app.ui.auth

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** [pattern] uses '#' for a digit, e.g. "(###) ### ####". [min]..[max] is the national number length. */
data class Country(val iso: String, val name: String, val dial: String, val pattern: String, val min: Int, val max: Int) {
    /** Flag emoji built from the ISO code, e.g. "LK" -> flag of Sri Lanka. */
    val flag: String
        get() = iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

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
        Country("AF", "Afghanistan", "+93", "## ### ####", 9, 9),
        Country("AL", "Albania", "+355", "## ### ####", 8, 9),
        Country("DZ", "Algeria", "+213", "### ## ## ##", 9, 9),
        Country("AD", "Andorra", "+376", "### ### ###", 6, 9),
        Country("AO", "Angola", "+244", "### ### ###", 9, 9),
        Country("AG", "Antigua and Barbuda", "+1268", "### ####", 7, 7),
        Country("AR", "Argentina", "+54", "### #### ####", 10, 11),
        Country("AM", "Armenia", "+374", "## ## ## ##", 8, 8),
        Country("AU", "Australia", "+61", "### ### ###", 9, 9),
        Country("AT", "Austria", "+43", "### ### #######", 10, 13),
        Country("AZ", "Azerbaijan", "+994", "## ### ## ##", 9, 9),
        Country("BS", "Bahamas", "+1242", "### ####", 7, 7),
        Country("BH", "Bahrain", "+973", "#### ####", 8, 8),
        Country("BD", "Bangladesh", "+880", "#### ######", 10, 10),
        Country("BB", "Barbados", "+1246", "### ####", 7, 7),
        Country("BY", "Belarus", "+375", "## ### ## ##", 9, 9),
        Country("BE", "Belgium", "+32", "### ## ## ##", 8, 9),
        Country("BZ", "Belize", "+501", "### ####", 7, 7),
        Country("BJ", "Benin", "+229", "## ## ## ##", 8, 8),
        Country("BT", "Bhutan", "+975", "## ### ###", 7, 8),
        Country("BO", "Bolivia", "+591", "#### ####", 8, 8),
        Country("BA", "Bosnia and Herzegovina", "+387", "## ### ###", 8, 8),
        Country("BW", "Botswana", "+267", "## ### ###", 7, 8),
        Country("BR", "Brazil", "+55", "## #####-####", 10, 11),
        Country("BN", "Brunei", "+673", "### ####", 7, 7),
        Country("BG", "Bulgaria", "+359", "## ### ####", 8, 9),
        Country("BF", "Burkina Faso", "+226", "## ## ## ##", 8, 8),
        Country("BI", "Burundi", "+257", "## ## ## ##", 8, 8),
        Country("CV", "Cabo Verde", "+238", "### ####", 7, 7),
        Country("KH", "Cambodia", "+855", "### ### ###", 8, 9),
        Country("CM", "Cameroon", "+237", "# ## ## ## ##", 8, 9),
        Country("CA", "Canada", "+1", "(###) ### ####", 10, 10),
        Country("CF", "Central African Republic", "+236", "## ## ## ##", 8, 8),
        Country("TD", "Chad", "+235", "## ## ## ##", 8, 8),
        Country("CL", "Chile", "+56", "# #### ####", 9, 9),
        Country("CN", "China", "+86", "### #### ####", 11, 11),
        Country("CO", "Colombia", "+57", "### ### ####", 10, 10),
        Country("KM", "Comoros", "+269", "### ####", 7, 7),
        Country("CG", "Congo", "+242", "## ### ####", 9, 9),
        Country("CR", "Costa Rica", "+506", "#### ####", 8, 8),
        Country("HR", "Croatia", "+385", "## ### ####", 8, 9),
        Country("CU", "Cuba", "+53", "# ### ####", 8, 8),
        Country("CY", "Cyprus", "+357", "## ### ###", 8, 8),
        Country("CZ", "Czechia", "+420", "### ### ###", 9, 9),
        Country("CI", "Côte d'Ivoire", "+225", "## ## ## ## ##", 10, 10),
        Country("DK", "Denmark", "+45", "## ## ## ##", 8, 8),
        Country("DJ", "Djibouti", "+253", "## ## ## ##", 8, 8),
        Country("DM", "Dominica", "+1767", "### ####", 7, 7),
        Country("DO", "Dominican Republic", "+1809", "### ####", 7, 7),
        Country("CD", "DR Congo", "+243", "## ### ####", 9, 9),
        Country("EC", "Ecuador", "+593", "## ### ####", 8, 9),
        Country("EG", "Egypt", "+20", "### ### ####", 9, 10),
        Country("SV", "El Salvador", "+503", "#### ####", 8, 8),
        Country("GQ", "Equatorial Guinea", "+240", "### ### ###", 9, 9),
        Country("ER", "Eritrea", "+291", "# ### ###", 7, 7),
        Country("EE", "Estonia", "+372", "#### ####", 7, 8),
        Country("SZ", "Eswatini", "+268", "## ## ####", 7, 8),
        Country("ET", "Ethiopia", "+251", "## ### ####", 9, 9),
        Country("FJ", "Fiji", "+679", "### ####", 7, 7),
        Country("FI", "Finland", "+358", "### ### ### ###", 6, 12),
        Country("FR", "France", "+33", "# ## ## ## ##", 9, 9),
        Country("GA", "Gabon", "+241", "## ## ## ##", 7, 8),
        Country("GM", "Gambia", "+220", "### ####", 7, 7),
        Country("GE", "Georgia", "+995", "### ## ## ##", 9, 9),
        Country("DE", "Germany", "+49", "### ########", 10, 11),
        Country("GH", "Ghana", "+233", "## ### ####", 9, 9),
        Country("GR", "Greece", "+30", "### ### ####", 10, 10),
        Country("GD", "Grenada", "+1473", "### ####", 7, 7),
        Country("GT", "Guatemala", "+502", "#### ####", 8, 8),
        Country("GN", "Guinea", "+224", "### ## ## ##", 8, 9),
        Country("GW", "Guinea-Bissau", "+245", "### ### ###", 7, 9),
        Country("GY", "Guyana", "+592", "### ####", 7, 7),
        Country("HT", "Haiti", "+509", "#### ####", 8, 8),
        Country("HN", "Honduras", "+504", "#### ####", 8, 8),
        Country("HU", "Hungary", "+36", "## ### ####", 8, 9),
        Country("IS", "Iceland", "+354", "### ####", 7, 7),
        Country("IN", "India", "+91", "##### #####", 10, 10),
        Country("ID", "Indonesia", "+62", "### ### ### ###", 9, 12),
        Country("IR", "Iran", "+98", "### ### ####", 10, 10),
        Country("IQ", "Iraq", "+964", "### ### ####", 10, 10),
        Country("IE", "Ireland", "+353", "## ### ####", 9, 9),
        Country("IL", "Israel", "+972", "## ### ####", 8, 9),
        Country("IT", "Italy", "+39", "### ### ####", 9, 10),
        Country("JM", "Jamaica", "+1876", "### ####", 7, 7),
        Country("JP", "Japan", "+81", "## #### ####", 10, 10),
        Country("JO", "Jordan", "+962", "# #### ####", 8, 9),
        Country("KZ", "Kazakhstan", "+7", "### ### ## ##", 10, 10),
        Country("KE", "Kenya", "+254", "### ### ###", 9, 9),
        Country("KI", "Kiribati", "+686", "#### ####", 5, 8),
        Country("XK", "Kosovo", "+383", "## ### ###", 8, 8),
        Country("KW", "Kuwait", "+965", "#### ####", 8, 8),
        Country("KG", "Kyrgyzstan", "+996", "### ### ###", 9, 9),
        Country("LA", "Laos", "+856", "## ## ### ###", 8, 10),
        Country("LV", "Latvia", "+371", "## ### ###", 8, 8),
        Country("LB", "Lebanon", "+961", "## ### ###", 7, 8),
        Country("LS", "Lesotho", "+266", "#### ####", 8, 8),
        Country("LR", "Liberia", "+231", "## ### ###", 7, 8),
        Country("LY", "Libya", "+218", "### ### ####", 9, 10),
        Country("LI", "Liechtenstein", "+423", "### ### ###", 7, 9),
        Country("LT", "Lithuania", "+370", "### #####", 8, 8),
        Country("LU", "Luxembourg", "+352", "### ### ### ##", 6, 11),
        Country("MG", "Madagascar", "+261", "## ## ### ##", 9, 9),
        Country("MW", "Malawi", "+265", "### ### ###", 9, 9),
        Country("MY", "Malaysia", "+60", "### ### ####", 9, 10),
        Country("MV", "Maldives", "+960", "### ####", 7, 7),
        Country("ML", "Mali", "+223", "## ## ## ##", 8, 8),
        Country("MT", "Malta", "+356", "#### ####", 8, 8),
        Country("MH", "Marshall Islands", "+692", "### ####", 7, 7),
        Country("MR", "Mauritania", "+222", "## ## ## ##", 8, 8),
        Country("MU", "Mauritius", "+230", "#### ####", 7, 8),
        Country("MX", "Mexico", "+52", "## #### ####", 10, 10),
        Country("FM", "Micronesia", "+691", "### ####", 7, 7),
        Country("MD", "Moldova", "+373", "## ### ###", 8, 8),
        Country("MC", "Monaco", "+377", "# ## ## ## ##", 8, 9),
        Country("MN", "Mongolia", "+976", "#### ####", 8, 8),
        Country("ME", "Montenegro", "+382", "### ### ###", 8, 9),
        Country("MA", "Morocco", "+212", "## ### ####", 9, 9),
        Country("MZ", "Mozambique", "+258", "## ### ####", 8, 9),
        Country("MM", "Myanmar", "+95", "### ### ####", 7, 10),
        Country("NA", "Namibia", "+264", "## ### ####", 8, 9),
        Country("NR", "Nauru", "+674", "### ####", 7, 7),
        Country("NP", "Nepal", "+977", "### ### ####", 8, 10),
        Country("NL", "Netherlands", "+31", "# ########", 9, 9),
        Country("NZ", "New Zealand", "+64", "### ### ####", 8, 10),
        Country("NI", "Nicaragua", "+505", "#### ####", 8, 8),
        Country("NE", "Niger", "+227", "## ## ## ##", 8, 8),
        Country("NG", "Nigeria", "+234", "### ### ####", 10, 10),
        Country("KP", "North Korea", "+850", "### ### ####", 8, 10),
        Country("MK", "North Macedonia", "+389", "## ### ###", 8, 8),
        Country("NO", "Norway", "+47", "### ## ###", 8, 8),
        Country("OM", "Oman", "+968", "#### ####", 8, 8),
        Country("PK", "Pakistan", "+92", "### #######", 10, 10),
        Country("PW", "Palau", "+680", "### ####", 7, 7),
        Country("PS", "Palestine", "+970", "### ### ###", 9, 9),
        Country("PA", "Panama", "+507", "#### ####", 7, 8),
        Country("PG", "Papua New Guinea", "+675", "#### ####", 7, 8),
        Country("PY", "Paraguay", "+595", "### ### ###", 9, 9),
        Country("PE", "Peru", "+51", "### ### ###", 9, 9),
        Country("PH", "Philippines", "+63", "### ### ####", 10, 10),
        Country("PL", "Poland", "+48", "### ### ###", 9, 9),
        Country("PT", "Portugal", "+351", "### ### ###", 9, 9),
        Country("QA", "Qatar", "+974", "#### ####", 8, 8),
        Country("RO", "Romania", "+40", "### ### ###", 9, 9),
        Country("RU", "Russia", "+7", "### ### ## ##", 10, 10),
        Country("RW", "Rwanda", "+250", "### ### ###", 9, 9),
        Country("KN", "Saint Kitts and Nevis", "+1869", "### ####", 7, 7),
        Country("LC", "Saint Lucia", "+1758", "### ####", 7, 7),
        Country("VC", "Saint Vincent and the Grenadines", "+1784", "### ####", 7, 7),
        Country("WS", "Samoa", "+685", "## #####", 5, 7),
        Country("SM", "San Marino", "+378", "### ### ####", 6, 10),
        Country("SA", "Saudi Arabia", "+966", "## ### ####", 9, 9),
        Country("SN", "Senegal", "+221", "## ### ## ##", 9, 9),
        Country("RS", "Serbia", "+381", "## ### ####", 8, 9),
        Country("SC", "Seychelles", "+248", "# ### ###", 7, 7),
        Country("SL", "Sierra Leone", "+232", "## ### ###", 8, 8),
        Country("SG", "Singapore", "+65", "#### ####", 8, 8),
        Country("SK", "Slovakia", "+421", "### ### ###", 9, 9),
        Country("SI", "Slovenia", "+386", "## ### ###", 8, 8),
        Country("SB", "Solomon Islands", "+677", "### ####", 5, 7),
        Country("SO", "Somalia", "+252", "## ### ###", 7, 8),
        Country("KR", "South Korea", "+82", "## #### ####", 9, 10),
        Country("ES", "Spain", "+34", "### ## ## ##", 9, 9),
        Country("LK", "Sri Lanka", "+94", "## ### ####", 9, 9),
        Country("SD", "Sudan", "+249", "## ### ####", 9, 9),
        Country("SR", "Suriname", "+597", "### ####", 6, 7),
        Country("SE", "Sweden", "+46", "### ### ### ####", 7, 13),
        Country("CH", "Switzerland", "+41", "## ### ## ##", 9, 9),
        Country("SY", "Syria", "+963", "## ### ####", 8, 9),
        Country("ST", "São Tomé and Príncipe", "+239", "### ####", 7, 7),
        Country("TW", "Taiwan", "+886", "### ### ###", 9, 9),
        Country("TJ", "Tajikistan", "+992", "## ### ####", 9, 9),
        Country("TZ", "Tanzania", "+255", "### ### ###", 9, 9),
        Country("TH", "Thailand", "+66", "## ### ####", 9, 9),
        Country("TL", "Timor-Leste", "+670", "### ## ###", 7, 8),
        Country("TG", "Togo", "+228", "## ## ## ##", 8, 8),
        Country("TO", "Tonga", "+676", "### ####", 5, 7),
        Country("TT", "Trinidad and Tobago", "+1868", "### ####", 7, 7),
        Country("TN", "Tunisia", "+216", "## ### ###", 8, 8),
        Country("TR", "Turkey", "+90", "### ### ## ##", 10, 10),
        Country("TM", "Turkmenistan", "+993", "## ### ###", 8, 8),
        Country("TV", "Tuvalu", "+688", "## #####", 5, 7),
        Country("UG", "Uganda", "+256", "### ### ###", 9, 9),
        Country("UA", "Ukraine", "+380", "## ### ####", 9, 9),
        Country("AE", "United Arab Emirates", "+971", "## ### ####", 9, 9),
        Country("GB", "United Kingdom", "+44", "#### ######", 10, 10),
        Country("US", "United States", "+1", "(###) ### ####", 10, 10),
        Country("UY", "Uruguay", "+598", "## ### ###", 8, 8),
        Country("UZ", "Uzbekistan", "+998", "## ### ## ##", 9, 9),
        Country("VU", "Vanuatu", "+678", "### ####", 5, 7),
        Country("VA", "Vatican City", "+39", "### ### ####", 10, 10),
        Country("VE", "Venezuela", "+58", "### ### ####", 10, 10),
        Country("VN", "Vietnam", "+84", "### ### ####", 9, 10),
        Country("YE", "Yemen", "+967", "### ### ###", 8, 9),
        Country("ZM", "Zambia", "+260", "## ### ####", 9, 9),
        Country("ZW", "Zimbabwe", "+263", "## ### ####", 9, 9),
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
