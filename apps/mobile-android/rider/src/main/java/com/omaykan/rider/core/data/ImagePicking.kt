package com.omaykan.rider.core.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Turning a photo-picker `Uri` into bytes we are willing to send.
 *
 * Extracted from RegisterViewModel when the account screen grew a second
 * uploader. Both screens hand the same server the same kind of file under the
 * same 8MB rule, and the three ways reading one can go wrong — too big,
 * unreadable, a provider that reports no size at all — are worth answering
 * identically in both places.
 *
 * Nothing here touches a permission. The platform photo picker hands back a
 * grant for exactly the file the rider chose, which is why neither screen asks
 * for storage access — see RegisterScreen's note on `PickVisualMedia`.
 */

/** 8MB, matching the server's `max:8192` on every image route. */
const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024

/**
 * What came back from the picker.
 *
 * A sealed result rather than a nullable upload, because "too big" and
 * "unreadable" need different sentences: the first is a file the rider can
 * choose differently, the second is usually a grant that has gone away and the
 * answer is to pick it again.
 */
sealed interface PickedImage {
    data class Ok(val upload: DocumentUpload) : PickedImage

    /** @param megabytes already formatted, so the caller can name the number. */
    data class TooBig(val megabytes: String) : PickedImage

    data object Unreadable : PickedImage
}

/**
 * Read the file behind [uri], or say why not.
 *
 * The size is checked twice on purpose. Plenty of content providers report no
 * size at all through `OpenableColumns`, and one that does not would otherwise
 * walk a 40MB file straight past the first check and into memory.
 */
fun Context.readPickedImage(uri: Uri, fallbackName: String = "photo.jpg"): PickedImage = try {
    val resolver = contentResolver
    val size = resolver.sizeOf(uri)

    when {
        size != null && size > MAX_UPLOAD_BYTES -> PickedImage.TooBig(size.asMegabytes())

        else -> {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }

            when {
                bytes == null -> PickedImage.Unreadable

                bytes.size > MAX_UPLOAD_BYTES -> PickedImage.TooBig(bytes.size.toLong().asMegabytes())

                else -> PickedImage.Ok(
                    DocumentUpload(
                        bytes = bytes,
                        // The server validates the decoded image and accepts
                        // jpeg, png and webp; a provider that reports nothing
                        // gets jpeg, which is what a phone camera produces.
                        mimeType = resolver.getType(uri) ?: "image/jpeg",
                        fileName = resolver.nameOf(uri) ?: fallbackName,
                    ),
                )
            }
        }
    }
} catch (e: Exception) {
    // A revoked grant, a provider that has gone away, a file on an unmounted
    // SD card. All of them mean the same thing to the rider: pick it again.
    PickedImage.Unreadable
}

private fun Long.asMegabytes(): String = String.format("%.1f", this / 1_048_576.0)

private fun ContentResolver.sizeOf(uri: Uri): Long? =
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
            cursor.getLong(column)
        } else {
            null
        }
    }

/**
 * The display name the provider reports, never used as a path.
 *
 * It reaches the server as the multipart filename and the server throws it
 * away — the upload controllers name the file by a random id precisely because
 * an uploader-supplied name is attacker-chosen text. It is here so the rider
 * can see which file they picked.
 */
private fun ContentResolver.nameOf(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
    }
