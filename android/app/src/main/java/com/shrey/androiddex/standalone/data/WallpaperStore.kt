package com.shrey.androiddex.standalone.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WallpaperStore {
    fun file(context: Context): File = File(context.filesDir, "wallpaper.jpg")

    /** Copies only the selected image, bounds decode memory, and strips source metadata. */
    suspend fun import(context: Context, uri: Uri): Unit = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("The selected image could not be opened")
        require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) { "Image dimensions are invalid or too large" }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("The selected image could not be decoded")
        try {
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            val oriented = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val target = file(context)
            val temporary = File(context.filesDir, "wallpaper.tmp")
            try {
                temporary.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "Could not save wallpaper" } }
                if (!temporary.renameTo(target)) { temporary.copyTo(target, overwrite = true); temporary.delete() }
            } finally { temporary.delete() }
            if (oriented !== bitmap) oriented.recycle()
        } finally { bitmap.recycle() }
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(context)
            if (!target.exists()) return@runCatching null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, options)
            var sample = 1
            while (options.outWidth / sample > 2048 || options.outHeight / sample > 2048) sample *= 2
            BitmapFactory.decodeFile(target.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
