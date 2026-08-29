package com.omaykan.storefront.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Product and shop photography.
 *
 * A missing photo is the normal case, not an error: most sari-sari stock has
 * never been photographed, and the shelf is still worth browsing. So a missing
 * or failed image leaves a quiet mark rather than a broken-image glyph, and it
 * occupies exactly the space the photo would, so a grid does not reflow as
 * images land.
 *
 * No ground of its own. A photo shot on white sat in a grey square that read as
 * a border the card did not have; anywhere a tile is genuinely wanted, the
 * caller paints it.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { Placeholder() },
                error = { Placeholder() },
            )
        }
    }
}

@Composable
private fun Placeholder() {
    Icon(
        imageVector = Icons.Filled.Image,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        tint = OmaykanTheme.colors.textTertiary,
    )
}
