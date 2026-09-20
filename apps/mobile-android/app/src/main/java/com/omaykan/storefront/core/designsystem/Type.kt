package com.omaykan.storefront.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omaykan.storefront.R

/*
 * Three voices, the way the reference uses them.
 *
 * The platform sans carries everything a shopper reads to act on — names,
 * prices, buttons, forms. Lora, a warm book serif, carries the storytelling:
 * aisle names on photographs, the Stories page, the welcome copy. Cinzel's
 * inscriptional capitals set the wordmark and nothing else.
 *
 * Both faces are bundled variable fonts (res/font, SIL Open Font License, from
 * Google Fonts) rather than downloadable ones: downloadable fonts go through
 * Google Play Services, and this app is built to run on phones without it.
 * Each Font entry below pins the variable `wght` axis to its weight.
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
 * its default instance, which drew every weight of both faces as Regular.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Spaced capitals — the wordmark's companion lines and small section labels. */
val SpacedCaps = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.22.em,
)

private val display = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    lineHeight = 50.sp,
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

val OmaykanTypography = Typography(
    displayLarge = display,
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
