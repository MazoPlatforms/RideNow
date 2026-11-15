package com.ridenow.driver.ui.support

import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideIcons

/** A help topic and its questions. Questions and answers are string resources so they can be translated. */
class HelpTopic(val icon: List<String>, val title: Int, val sub: Int, val faqs: List<Pair<Int, Int>>)

object HelpContent {
    val topics = listOf(
        HelpTopic(RideIcons.Book, R.string.sc_start, R.string.sc_start_sub, listOf(R.string.q_go_online to R.string.a_go_online, R.string.q_accept to R.string.a_accept, R.string.q_first to R.string.a_first)),
        HelpTopic(RideIcons.Car, R.string.sc_vehicle, R.string.sc_vehicle_sub, listOf(R.string.q_docs to R.string.a_docs, R.string.q_expiry to R.string.a_expiry, R.string.q_change_vehicle to R.string.a_change_vehicle)),
        HelpTopic(RideIcons.Wallet, R.string.sc_earn, R.string.sc_earn_sub, listOf(R.string.q_withdraw to R.string.a_withdraw, R.string.q_payout_late to R.string.a_payout_late, R.string.q_cash to R.string.a_cash)),
        HelpTopic(RideIcons.Route, R.string.sc_rides, R.string.sc_rides_sub, listOf(R.string.q_cancel to R.string.a_cancel, R.string.q_nav to R.string.a_nav, R.string.q_wrong_pin to R.string.a_wrong_pin)),
        HelpTopic(RideIcons.Person, R.string.sc_account, R.string.sc_account_sub, listOf(R.string.q_edit_profile to R.string.a_edit_profile, R.string.q_verify to R.string.a_verify, R.string.q_phone to R.string.a_phone)),
        HelpTopic(RideIcons.Alert, R.string.sc_safety, R.string.sc_safety_sub, listOf(R.string.q_emergency to R.string.a_emergency, R.string.q_report to R.string.a_report, R.string.q_share to R.string.a_share)),
    )
    /** Popular topics on Help & Safety map onto these topics. */
    val booking = 3; val payments = 2; val account = 4; val maps = 3; val appIssues = 0
}
