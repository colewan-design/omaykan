package com.omaykan.seller.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omaykan.seller.R

/*
 * Three voices, the same three :app uses.
 *
 * The platform sans carries everything a merchant reads to act on — names,
 * prices, counts, buttons, forms. Lora, a warm book serif, carries the few
 * lines that are there to be felt rather than acted on: the morning greeting,
 * the welcome screen's sign-off. Cinzel's inscriptional capitals set the
 * wordmark and nothing else.
 *
 * Both faces are bundled variable fonts (res/font, SIL Open Font License, from
 * Google Fonts) rather than downloadable ones: downloadable fonts go through
 * Google Play Services, and this app is built to run on phones without it.
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
    fontWeight = FontWeight.Medium,
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

val SellerTypography = Typography(
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
