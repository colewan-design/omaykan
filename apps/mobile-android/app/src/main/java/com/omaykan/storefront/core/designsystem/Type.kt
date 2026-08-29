package com.omaykan.storefront.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The type scale from tokens.css, at 1rem = 16sp.
 *
 * The web stack starts with the platform UI font, so the Android answer to
 * `-apple-system, …, Roboto` is FontFamily.Default rather than a bundled face.
 * The sizes and weights are what carry the family resemblance.
 */

private val display = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 48.sp,
    lineHeight = 52.sp,
)

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
