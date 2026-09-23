package com.omaykan.seller.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A picture from the server, with an icon standing in until — or instead of —
 * it arriving.
 *
 * The icon is drawn underneath and the image over it, rather than swapped on
 * Coil's load state: a product with no photo, a shop that never uploaded one
 * (the image endpoint 404s) and a phone with no signal all end up showing the
 * same calm placeholder, with no state to get wrong.
 */
@Composable
fun RemoteThumb(
    url: String?,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = TileShape,
    contentDescription: String? = null,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier
            .clip(shape)
            .background(SellerTheme.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            fallback,
            contentDescription = null,
            tint = SellerTheme.colors.onAccentSoft,
            modifier = Modifier.size(iconSize),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
