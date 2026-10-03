package com.pranav.study.cet_study_sprint

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore

/** Android 10 scoped storage, matching the existing navigation screenshot workflow. */
internal fun saveUiProof(context: Context, bitmap: Bitmap, name: String) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StudySprintUi")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
}
