package com.omaykan.storefront.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omaykan.storefront.R
import com.omaykan.storefront.core.designsystem.CtaButton
import com.omaykan.storefront.core.designsystem.MountainMark
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.SerifFamily
import com.omaykan.storefront.core.designsystem.SpacedCaps
import com.omaykan.storefront.core.designsystem.Wordmark
import com.omaykan.storefront.core.designsystem.WovenBand

/**
 * The front door, shown once on a phone's first launch.
 *
 * Laid out to the reference: a photograph under a forest wash, the mark and
 * the name high up, what the app is for in one sentence, one button, and a
 * woven edge along the bottom. The words are this product's — shops near you,
 * at their own counter price — rather than the reference's, which were about
 * somebody else's.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OmaykanTheme.colors
    val forest = colors.forest

    Box(
        modifier
            .fillMaxSize()
            .background(forest),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_market),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    // Heavier than a photo wash usually is: a market stall is
                    // all colour, and the reference's hillside is all shadow.
                    Brush.verticalGradient(
                        0f to forest.copy(alpha = 0.82f),
                        0.4f to forest.copy(alpha = 0.6f),
                        0.62f to forest.copy(alpha = 0.76f),
                        1f to forest.copy(alpha = 0.97f),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))

            MountainMark(
                mountain = Color.Transparent,
                modifier = Modifier.size(width = 172.dp, height = 116.dp),
            )
            Wordmark(fontSize = 46.sp, modifier = Modifier.padding(top = 16.dp))
            Text(
                text = "YOUR NEIGHBOURHOOD SHOPS\nAT THEIR OWN COUNTER PRICES",
                style = SpacedCaps,
                color = colors.onForest,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.weight(1.4f))

            Text(
                text = "Groceries, coffee and meals from the shops near you — at the price on " +
                    "their own counter, brought to your door by a local rider.",
                style = TextStyle(fontFamily = SerifFamily, fontSize = 17.sp, lineHeight = 26.sp),
                color = colors.onForest,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp),
            )

            CtaButton(
                text = "Get Started",
                onClick = onGetStarted,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 30.dp)
                    .fillMaxWidth(),
            )

            // Local. Honest. Affordable. — the three things the app promises,
            // in the language most of its shoppers say them in.
            Text(
                text = "Lokal. Tapat. Abot-kaya.",
                style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                color = colors.onForest,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            WovenBand()
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .background(forest)
                    .navigationBarsPadding(),
            )
        }
    }
}
