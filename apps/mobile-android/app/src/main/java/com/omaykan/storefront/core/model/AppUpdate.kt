package com.omaykan.storefront.core.model

/**
 * A published build newer than the one running.
 *
 * Only ever constructed when there is genuinely something to offer, so a screen
 * holding one of these never has to ask whether it should say anything.
 *
 * This exists because the app is distributed as a direct APK: there is no Play
 * listing to notice a new build on the shopper's behalf, so if the app does not
 * ask, nobody ever finds out. See mobile-plan.md §9.
 */
data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    /** Where the APK is. Absent means published but not downloadable yet. */
    val apkUrl: String?,
    val notes: String?,
) {
    val installable: Boolean get() = !apkUrl.isNullOrBlank()
}
