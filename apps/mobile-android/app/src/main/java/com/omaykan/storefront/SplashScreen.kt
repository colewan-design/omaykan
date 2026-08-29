package com.omaykan.storefront

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * The splash, in Compose rather than in the window theme.
 *
 * The platform's own splash slot masks its icon to a circle, which is the wrong
 * shape for a wordmark — it would crop "omaykan" down to the middle of itself.
 * So the launch theme paints only the brand ground (see values-v31/themes.xml)
 * and this draws the wordmark on top of the identical colour, which reads as
 * one continuous screen rather than a handover between two.
 *
 * It fades in rather than appearing: on a fast device the whole splash is over
 * in a few hundred milliseconds, and a logo that pops is a flicker.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "splash-fade",
    )

    LaunchedEffect(Unit) { shown = true }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_wordmark),
            contentDescription = "Omaykan",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .padding(horizontal = 32.dp)
                .alpha(alpha),
        )
    }
}
