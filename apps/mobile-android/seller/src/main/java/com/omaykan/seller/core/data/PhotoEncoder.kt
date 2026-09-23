package com.omaykan.seller.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A picked photo, made small enough to send and shaped the way the server
 * takes it.
 *
 * A phone camera's photo is 4000 pixels across and several megabytes — over
 * the server's 3MB limit before base64 adds a third. A shop photo is shown in
 * a circle and on a directory card, so 1280 on the long edge is more than it
 * will ever be drawn at, and a JPEG of that is a few hundred kilobytes.
 *
 * On Android 9 and up ImageDecoder turns the photo upright from its EXIF
 * orientation. On 8, the two versions this app still supports below that,
 * BitmapFactory does not, and a portrait photo taken sideways arrives
 * sideways — rare enough on a shop photo not to pull in a library for.
 */
@Singleton
class PhotoEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** `data:image/jpeg;base64,…`. Throws IOException for anything unreadable. */
    suspend fun jpegDataUrl(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decode(uri) ?: throw IOException("That photo could not be read.")
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = scaleFor(info.size.width, info.size.height)
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).roundToInt()),
                    max(1, (info.size.height * scale).roundToInt()),
                )
                // A hardware bitmap cannot be compressed back out.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Halve while decoding, which is cheap, then scale the rest of the way.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val scale = scaleFor(decoded.width, decoded.height)
        return if (scale >= 1f) {
            decoded
        } else {
            Bitmap.createScaledBitmap(
                decoded,
                max(1, (decoded.width * scale).roundToInt()),
                max(1, (decoded.height * scale).roundToInt()),
                true,
            )
        }
    }

    private fun scaleFor(width: Int, height: Int): Float = min(1f, MAX_EDGE.toFloat() / max(width, height))

    private companion object {
        const val MAX_EDGE = 1280
        const val QUALITY = 85
    }
}
