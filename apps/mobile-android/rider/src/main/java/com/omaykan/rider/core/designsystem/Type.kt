package com.omaykan.rider.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omaykan.rider.R

/*
 * Three voices, the same three :app and :seller use.
 *
 * The platform sans carries everything a rider reads to act on — fees,
 * addresses, names, buttons, forms. Lora, a warm book serif, carries the few
 * lines that are there to be felt rather than acted on: the greeting over the
 * mountains, the welcome screen's sign-off, the line under a rider's name.
 * Cinzel's inscriptional capitals set the wordmark and nothing else.
 *
 * Both faces are bundled variable fonts (res/font, SIL Open Font License, from
 * Google Fonts) rather than downloadable ones: downloadable fonts go through
 * Google Play Services, and this app is built for phones without it.
 */

val SerifFamily = FontFamily(
    variable(R.font.lora, FontWeight.Normal),
    variable(R.font.lora, FontWeight.Medium),
    variable(R.font.lora, FontWeight.SemiBold),
    variable(R.font.lora, FontWeight.Bold),
)

val WordmarkFamily = FontFamily(
    variable(R.font.cinzel, FontWeight.Normal),
    variable(R.font.cinzel, FontWeight.SemiBold),
    variable(R.font.cinzel, FontWeight.Bold),
)

/**
 * One weight of a variable font. The axis has to be set explicitly: the plain
 * `Font(resId, weight)` overload only labels the entry and leaves the file at
 * its default instance, which draws every weight as Regular.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Spaced capitals — the wordmark's companion lines. */
val SpacedCaps = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.22.em,
)

private val title1 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

private val title2 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)

private val title3 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 24.sp,
)

private val headline = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
)

private val body = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 23.sp,
)

private val subhead = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
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
    displaySmall = title1,
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
 * The styles the shared scale has no room for.
 *
 * All but the last are about a rider reading a number at arm's length in
 * daylight. The scale tops out at 28sp for a page title, which is the right
 * size for a title and too small for the only number on a card that decides
 * whether the job is worth taking.
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

    /** A figure in a row of figures: smaller than [money], same weight. */
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

    /** A line meant to be felt: the greeting, a quote, the sign-off. */
    val serifQuote = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )
}
