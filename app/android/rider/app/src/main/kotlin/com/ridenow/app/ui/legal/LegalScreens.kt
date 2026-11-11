package com.ridenow.app.ui.legal

import com.ridenow.app.ui.components.localizedLabel

import androidx.compose.ui.res.stringResource

import com.ridenow.app.BuildConfig
import com.ridenow.app.R
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.LogoMark
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Legal and About: app card plus links to each document. */
@Composable
fun LegalScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val textLegalAbout = stringResource(R.string.text_legal_about)
    val textAppInformationLegalDocumentsAndPolicies = stringResource(R.string.text_app_information_legal_documents_and_policies)
    val textAppName = stringResource(R.string.app_name)
    val textVersionValueValue = stringResource(R.string.text_version_value_value)
    val textYourEverydayRideCompanion = stringResource(R.string.text_your_everyday_ride_companion)
    val textLegal = stringResource(R.string.text_legal)
    val textTermsOfService = stringResource(R.string.text_terms_of_service)
    val textReadOurTermsAndConditions = stringResource(R.string.text_read_our_terms_and_conditions)
    val textPrivacyPolicy = stringResource(R.string.text_privacy_policy)
    val textLearnHowWeHandleYourData = stringResource(R.string.text_learn_how_we_handle_your_data)
    val textCookiePolicy = stringResource(R.string.text_cookie_policy)
    val textHowWeUseCookies = stringResource(R.string.text_how_we_use_cookies)
    val textDataProtection = stringResource(R.string.text_data_protection)
    val textYourDataRightsAndSecurity = stringResource(R.string.text_your_data_rights_and_security)
    val textAbout = stringResource(R.string.text_about)
    val textMenuAbout = stringResource(R.string.menu_about)
    val textAppInformationAndFeatures = stringResource(R.string.text_app_information_and_features)
    val textOpenSourceLicenses = stringResource(R.string.text_open_source_licenses)
    val textThirdPartyLibrariesAndLicenses = stringResource(R.string.text_third_party_libraries_and_licenses)

    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textLegalAbout, onBack, subtitle = textAppInformationLegalDocumentsAndPolicies)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen("About RideNow") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF14181F) else androidx.compose.ui.graphics.Color.White).border(1.dp, c.border, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { LogoMark(40.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(textAppName, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(textVersionValueValue.format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = c.muted, fontSize = 12.sp)
                    Text(textYourEverydayRideCompanion, color = c.muted, fontSize = 12.sp)
                }
                RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
            }
            SectionLabel(textLegal)
            GroupCard {
                NavRow(RideIcons.Doc, textTermsOfService, { onOpen("Terms of Service") }, subtitle = textReadOurTermsAndConditions); RowDivider()
                NavRow(RideIcons.Shield, textPrivacyPolicy, { onOpen("Privacy Policy") }, subtitle = textLearnHowWeHandleYourData); RowDivider()
                NavRow(RideIcons.Info, textCookiePolicy, { onOpen("Cookie Policy") }, subtitle = textHowWeUseCookies); RowDivider()
                NavRow(RideIcons.Shield, textDataProtection, { onOpen("Data Protection") }, subtitle = textYourDataRightsAndSecurity)
            }
            SectionLabel(textAbout)
            GroupCard {
                NavRow(RideIcons.Info, textMenuAbout, { onOpen("About RideNow") }, subtitle = textAppInformationAndFeatures); RowDivider()
                NavRow(RideIcons.Star, textOpenSourceLicenses, { onOpen("Open Source Licenses") }, subtitle = textThirdPartyLibrariesAndLicenses)
            }
            Spacer(Modifier.height(16.dp))
        }
        // Low-opacity "from Sifric" watermark at the bottom of the screen.
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(if (c.isDark) R.drawable.ridenow_from_sifric_dark else R.drawable.ridenow_from_sifric_light),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(110.dp).alpha(0.22f),
            )
        }
    }
}



/** Simple reader for the legal documents. The text is placeholder copy. */
@Composable
fun DocScreen(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val textUsingRidenow = stringResource(R.string.text_using_ridenow)
    val textYouAgreeToUseRidenowLawfullyAndToGiveAccurateAccountDetails = stringResource(R.string.text_you_agree_to_use_ridenow_lawfully_and_to_give_accurate_account_details)
    val textRidesAndPayments = stringResource(R.string.text_rides_and_payments)
    val textFaresAreEstimatesRidesAreCurrentlyPaidInCashToTheDriver = stringResource(R.string.text_fares_are_estimates_rides_are_currently_paid_in_cash_to_the_driver)
    val textCancellations = stringResource(R.string.text_cancellations)
    val textYouMayCancelARideAtAnyTimeBeforePickup = stringResource(R.string.text_you_may_cancel_a_ride_at_any_time_before_pickup)
    val textWhatWeCollect = stringResource(R.string.text_what_we_collect)
    val textYourNamePhoneNumberLocationDuringRidesAndTripHistory = stringResource(R.string.text_your_name_phone_number_location_during_rides_and_trip_history)
    val textHowWeUseIt = stringResource(R.string.text_how_we_use_it)
    val textToMatchYouWithDriversKeepTripsSafeAndImproveTheService = stringResource(R.string.text_to_match_you_with_drivers_keep_trips_safe_and_improve_the_service)
    val textYourChoices = stringResource(R.string.text_your_choices)
    val textYouCanDownloadOrDeleteYourDataFromPrivacySecurity = stringResource(R.string.text_you_can_download_or_delete_your_data_from_privacy_security)
    val textWhatAreCookies = stringResource(R.string.text_what_are_cookies)
    val textSmallFilesThatHelpAnAppOrWebsiteRememberYourPreferences = stringResource(R.string.text_small_files_that_help_an_app_or_website_remember_your_preferences)
    val textHowWeUseThem = stringResource(R.string.text_how_we_use_them)
    val textForSignInLanguageAndAppearanceSettings = stringResource(R.string.text_for_sign_in_language_and_appearance_settings)
    val textYourRights = stringResource(R.string.text_your_rights)
    val textAccessCorrectExportOrDeleteTheDataWeHoldAboutYou = stringResource(R.string.text_access_correct_export_or_delete_the_data_we_hold_about_you)
    val textSecurity = stringResource(R.string.text_security)
    val textDataIsEncryptedInTransitAndStoredWithRestrictedAccess = stringResource(R.string.text_data_is_encrypted_in_transit_and_stored_with_restricted_access)
    val textJetpackCompose = stringResource(R.string.text_jetpack_compose)
    val textApacheLicense20 = stringResource(R.string.text_apache_license_2_0)
    val textAndroidxLibraries = stringResource(R.string.text_androidx_libraries)
    val textKotlin = stringResource(R.string.text_kotlin)
    val textEverydayRides = stringResource(R.string.text_everyday_rides)
    val textRidenowHelpsYouBookScheduleAndShareRidesInAFewTaps = stringResource(R.string.text_ridenow_helps_you_book_schedule_and_share_rides_in_a_few_taps)
    val textVersion = stringResource(R.string.text_version)
    val textVersionValueValue2 = stringResource(R.string.text_version_value_value_2)
    val textComingSoon = stringResource(R.string.text_coming_soon)
    val textThisDocumentWillBeAvailableHere = stringResource(R.string.text_this_document_will_be_available_here)
    val textLastUpdated3Nov2026 = stringResource(R.string.text_last_updated_3_nov_2026)

    val Docs = mapOf(
        "Terms of Service" to listOf(textUsingRidenow to textYouAgreeToUseRidenowLawfullyAndToGiveAccurateAccountDetails, textRidesAndPayments to textFaresAreEstimatesRidesAreCurrentlyPaidInCashToTheDriver, textCancellations to textYouMayCancelARideAtAnyTimeBeforePickup),
        "Privacy Policy" to listOf(textWhatWeCollect to textYourNamePhoneNumberLocationDuringRidesAndTripHistory, textHowWeUseIt to textToMatchYouWithDriversKeepTripsSafeAndImproveTheService, textYourChoices to textYouCanDownloadOrDeleteYourDataFromPrivacySecurity),
        "Cookie Policy" to listOf(textWhatAreCookies to textSmallFilesThatHelpAnAppOrWebsiteRememberYourPreferences, textHowWeUseThem to textForSignInLanguageAndAppearanceSettings),
        "Data Protection" to listOf(textYourRights to textAccessCorrectExportOrDeleteTheDataWeHoldAboutYou, textSecurity to textDataIsEncryptedInTransitAndStoredWithRestrictedAccess),
        "Open Source Licenses" to listOf(textJetpackCompose to textApacheLicense20, textAndroidxLibraries to textApacheLicense20, textKotlin to textApacheLicense20),
        "About RideNow" to listOf(textEverydayRides to textRidenowHelpsYouBookScheduleAndShareRidesInAFewTaps, textVersion to textVersionValueValue2.format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)),
    )

    val c = RideTheme.colors
    val sections = Docs[title] ?: listOf(textComingSoon to textThisDocumentWillBeAvailableHere)
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(localizedLabel(title), onBack, subtitle = textLastUpdated3Nov2026)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            sections.forEachIndexed { i, (h, body) ->
                Text("${i + 1}. $h", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
                Text(body, color = c.muted, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LegalPreview() = RideNowTheme(darkTheme = true) { LegalScreen({}, {}) }
