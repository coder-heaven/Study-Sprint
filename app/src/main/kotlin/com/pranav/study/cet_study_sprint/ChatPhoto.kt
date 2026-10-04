package com.pranav.study.cet_study_sprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

internal data class ChatPhoto(val id: String, val dataUrl: String, val thumbnail: ByteArray)
internal object ChatPhotos {
    fun load(context: Context, uri: Uri): ChatPhoto {
        val source = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n == -1) break
                require(source.size() + n <= 12 * 1024 * 1024) { "Choose a photo smaller than 12 MB." }; source.write(buffer, 0, n) }
        } ?: error("This photo could not be opened.")
        val bytes = source.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo format could not be opened." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("This photo could not be decoded.")
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val transform = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            }
        }
        if (!transform.isIdentity) { val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true); if (rotated !== bitmap) bitmap.recycle(); bitmap = rotated }
        fun jpeg(image: Bitmap, quality: Int) = ByteArrayOutputStream().also { check(image.compress(Bitmap.CompressFormat.JPEG, quality, it)) }.toByteArray()
        var quality = 82; var encoded = jpeg(bitmap, quality)
        while (encoded.size > 250000 && quality > 42) { quality -= 10; encoded = jpeg(bitmap, quality) }
        if (encoded.size > 250000) { val smaller = Bitmap.createScaledBitmap(bitmap, maxOf(1, bitmap.width / 2), maxOf(1, bitmap.height / 2), true); bitmap.recycle(); bitmap = smaller; encoded = jpeg(bitmap, 65) }
        require(encoded.size <= 250000) { "Choose a smaller photo." }
        val scale = 128f / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true)
        val preview = jpeg(thumb, 70); if (thumb !== bitmap) thumb.recycle(); bitmap.recycle()
        // Re-encoding strips EXIF location/device metadata. Only explicit selections are sent.
        return ChatPhoto(UUID.randomUUID().toString(), "data:image/jpeg;base64," + Base64.encodeToString(encoded, Base64.NO_WRAP), preview)
    }
}
