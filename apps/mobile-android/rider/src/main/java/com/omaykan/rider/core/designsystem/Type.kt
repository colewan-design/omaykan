package com.omaykan.rider.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The type scale from tokens.css, at 1rem = 16sp — the same one :app and
 * :seller use.
 *
 * The web stack starts with the platform UI font, so the Android answer to
 * `-apple-system, …, Roboto` is FontFamily.Default rather than a bundled face.
 * The sizes and weights are what carry the family resemblance.
 */

private val title1 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

private val title2 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)

private val title3 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 25.sp,
)

private val headline = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 22.sp,
)

private val body = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 22.sp,
)

private val subhead = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)

private val caption = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val RiderTypography = Typography(
    displayLarge = title1,
    displayMedium = title1,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title2,
    titleLarge = title3,
    titleMedium = headline,
    titleSmall = headline,
    bodyLarge = body,
    bodyMedium = subhead,
    bodySmall = caption,
    labelLarge = headline,
    labelMedium = subhead,
    labelSmall = caption,
)

/**
 * The two styles the shared scale has no room for.
 *
 * Both exist for one screen — the board — and both are about a rider reading a
 * card at arm's length in daylight. The scale in tokens.css tops out at 28sp
 * for a page title, which is the right size for a title and too small for the
 * only number on a card that decides whether the job is worth taking.
 */
object RiderTextStyles {

    /**
     * What a job pays.
     *
     * The largest thing in the app, larger than any heading, because it is the
     * first thing read and often the only thing read. Tight line height so the
     * caption under it belongs to it rather than floating.
     */
    val money = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    )

    /** A stat's value, in the canopy strip: smaller than [money], same weight. */
    val statValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    )

    /**
     * The small capitals over a group of fields or under a stat.
     *
     * Uppercase and letter-spaced rather than merely small, so a label can sit
     * directly against the thing it labels without a rule between them.
     */
    val overline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    )
}
