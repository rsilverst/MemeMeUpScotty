package com.rsilverst.mememeupscotty.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.rsilverst.mememeupscotty.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Result<Unit> {
    val appContext = context.applicationContext
    return withContext(Dispatchers.IO) {
        try {
            val filename = "meme_${System.currentTimeMillis()}.png"
            var fos: java.io.OutputStream? = null
            var imageUri: Uri? = null
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MemeMeUpScotty")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = appContext.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                imageUri = resolver.insert(collection, contentValues)
            } else {
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            }

            if (imageUri == null) {
                throw Exception("Failed to insert MediaStore record")
            }

            fos = resolver.openOutputStream(imageUri)
            if (fos == null) {
                throw Exception("Failed to open output stream")
            }

            fos.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

suspend fun shareBitmap(context: Context, bitmap: Bitmap): Result<Unit> {
    val appContext = context.applicationContext
    return try {
        val uri = withContext(Dispatchers.IO) {
            val cachePath = File(appContext.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_meme_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            FileProvider.getUriForFile(
                appContext,
                "com.rsilverst.mememeupscotty.fileprovider",
                file
            )
        }

        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = context.getString(R.string.share_chooser_title)
            context.startActivity(Intent.createChooser(intent, title))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}

/**
 * Generates a high-fidelity meme bitmap by drawing the top and bottom text overlay
 * directly onto the source high-resolution background bitmap.
 * This bypasses device screen and UI-rendering resolution limits.
 */
fun generateHighResMeme(baseBitmap: Bitmap, topText: String, bottomText: String): Bitmap {
    val highRes = try {
        baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
    } catch (_: OutOfMemoryError) {
        try {
            // Fallback 1: Try copying with RGB_565 which uses half the memory per pixel (2 bytes vs 4 bytes)
            baseBitmap.copy(Bitmap.Config.RGB_565, true)
        } catch (_: OutOfMemoryError) {
            try {
                // Fallback 2: Try scaling the bitmap down to half-size to reduce memory footprint
                val newWidth = (baseBitmap.width / 2).coerceAtLeast(1)
                val newHeight = (baseBitmap.height / 2).coerceAtLeast(1)
                val scaled = baseBitmap.scale(newWidth, newHeight)
                try {
                    scaled.copy(Bitmap.Config.ARGB_8888, true)
                } catch (_: OutOfMemoryError) {
                    scaled.copy(Bitmap.Config.RGB_565, true)
                }
            } catch (e4: OutOfMemoryError) {
                // Fallback 3: Throw a descriptive exception that the VM can catch to inform the user
                throw RuntimeException("Failed to generate high-resolution meme due to insufficient memory.", e4)
            }
        }
    }
    val canvas = android.graphics.Canvas(highRes)
    val paint = android.graphics.Paint().apply {
        textSize = highRes.width * 0.08f // responsive size relative to source resolution
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    // 1. Draw Top Text Outline
    paint.color = android.graphics.Color.BLACK
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = highRes.width * 0.015f
    canvas.drawText(topText, highRes.width / 2f, highRes.height * 0.12f, paint)
    
    // 2. Draw Top Text Fill
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawText(topText, highRes.width / 2f, highRes.height * 0.12f, paint)
    
    // 3. Draw Bottom Text Outline
    paint.color = android.graphics.Color.BLACK
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = highRes.width * 0.015f
    canvas.drawText(bottomText, highRes.width / 2f, highRes.height * 0.90f, paint)
    
    // 4. Draw Bottom Text Fill
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawText(bottomText, highRes.width / 2f, highRes.height * 0.90f, paint)
    
    return highRes
}
