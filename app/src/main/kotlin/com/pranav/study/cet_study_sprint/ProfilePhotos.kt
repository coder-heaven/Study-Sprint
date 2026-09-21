package com.pranav.study.cet_study_sprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.graphics.ImageDecoder
import java.io.File

internal object ProfilePhotos {
    fun fromUri(context: Context, uri: Uri): String? = runCatching {
        val original = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = 320f / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        } ?: return null
        save(context, original)
    }.getOrNull()
    fun save(context: Context, original: Bitmap): String? = runCatching {
        val side = 320f / maxOf(original.width, original.height)
        val image = if (side < 1f) Bitmap.createScaledBitmap(original,
            (original.width * side).toInt().coerceAtLeast(1),
            (original.height * side).toInt().coerceAtLeast(1), true) else original
        val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val old = prefs.getString("profile_photo", null)
        prefs.edit().putString("profile_photo", file.absolutePath).apply()
        if (old != null && old != file.absolutePath) File(old).takeIf { it.parentFile == context.filesDir }?.delete()
        file.absolutePath
    }.getOrNull()
    fun remove(context: Context) {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val path = prefs.getString("profile_photo", null)
        prefs.edit().remove("profile_photo").apply()
        if (path != null) File(path).takeIf { it.parentFile == context.filesDir }?.delete()
    }
}
