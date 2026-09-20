package com.omaykan.storefront

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omaykan.storefront.core.designsystem.MountainMark
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Wordmark

/**
 * The splash, in Compose rather than in the window theme.
 *
 * The platform's own splash slot masks its icon to a circle, which is the wrong
 * shape for a wordmark. So the launch theme paints only the forest ground (see
 * values/colors.xml) and this draws the mark and the name on the identical
 * colour, which reads as one continuous screen rather than a handover.
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
        modifier = modifier
            .fillMaxSize()
            .background(OmaykanTheme.colors.forest),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            MountainMark(Modifier.size(width = 132.dp, height = 90.dp))
            Wordmark(fontSize = 34.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}
