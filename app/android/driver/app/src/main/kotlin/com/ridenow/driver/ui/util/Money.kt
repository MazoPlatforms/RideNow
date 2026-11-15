package com.ridenow.driver.ui.util

import java.text.NumberFormat
import java.util.Locale

/** Amounts are shown as "LKR 1,250", the same on every screen. */
object Money {
    const val CURRENCY = "LKR"
    private val fmt: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
    fun fmt(amount: Number): String = "$CURRENCY ${fmt.format(Math.round(amount.toDouble()))}"
    fun signed(amount: Int): String = (if (amount < 0) "- " else "+ ") + fmt(Math.abs(amount))
}
