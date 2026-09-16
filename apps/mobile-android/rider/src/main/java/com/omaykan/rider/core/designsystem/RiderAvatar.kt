package com.omaykan.rider.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.omaykan.rider.BuildConfig

/**
 * A rider's face, or the letter that stands in for it.
 *
 * The app drew an initial in a coloured disc long before there were
 * photographs, and that is not a placeholder to be removed now there are — most
 * riders will never upload one, and a rider who does not want their face on a
 * stranger's phone is entitled to keep it that way. So [Avatar] stays the base
 * case and this wraps it.
 *
 * ## Why the URL is completed here
 *
 * The server sends a path — `/api/riders/{id}/avatar?v=…` — rather than an
 * absolute URL, so a debug build pointed at a laptop loads the photo from the
 * laptop rather than from production. Joining it to [BuildConfig.API_BASE_URL]
 * is the one line that has to know that, and it lives here rather than in the
 * three screens that draw a rider.
 *
 * The `?v=` on the end is a stamp over the stored file's path, so replacing a
 * photo changes the URL and Coil fetches the new one. Nothing has to reach into
 * the cache.
 *
 * ## Why the fallback is on both states, not just failure
 *
 * `loading` gets the letter too. A disc that is empty for the length of a
 * network round trip and then fills is worse on a job card than one that shows
 * the letter and quietly becomes a face — the card would jump, and on a slow
 * connection the rider's own header would flicker on every cold start.
 */
@Composable
fun RiderAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = RiderTheme.colors.canopyFill,
    content: Color = RiderTheme.colors.onCanopy,
) {
    val letter: @Composable () -> Unit = {
        Avatar(name = name, size = size, container = container, content = content)
    }

    if (photoUrl.isNullOrBlank()) {
        letter()
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(PillShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = photoUrl.absolute(),
            contentDescription = null,
            // Cropped rather than fitted: these are phone photographs of
            // people, in whatever aspect ratio the camera produced, and a disc
            // with letterboxing in it looks like a bug.
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(PillShape),
            loading = { letter() },
            error = { letter() },
        )
    }
}

/**
 * Join a server-relative path to the API host.
 *
 * Absolute URLs are passed through untouched, so a future server that starts
 * sending a CDN address needs no change here.
 */
private fun String.absolute(): String =
    if (startsWith("http://") || startsWith("https://")) {
        this
    } else {
        BuildConfig.API_BASE_URL.trimEnd('/') + "/" + trimStart('/')
    }
