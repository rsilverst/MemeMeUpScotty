package com.rsilverst.mememeupscotty.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.rsilverst.mememeupscotty.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ImageUtils"

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
            Log.w(TAG, "saveBitmapToGallery failed", e)
            Result.failure(e)
        }
    }
}

// Streams a content:// URI (e.g. from the system photo picker) into a
// cache file that the canvas can read like any other File. Coil reads
// from the file path, so the original picker URI permission is not
// needed beyond the copy itself.
suspend fun copyUriToCache(context: Context, uri: Uri, cacheDir: File): Result<File> {
    val appContext = context.applicationContext
    return withContext(Dispatchers.IO) {
        try {
            val file = File.createTempFile("gallery_meme_", ".img", cacheDir)
            appContext.contentResolver.openInputStream(uri).use { input ->
                input ?: throw Exception("Failed to open picked image")
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Result.success(file)
        } catch (e: Exception) {
            Log.w(TAG, "copyUriToCache failed", e)
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
        Log.w(TAG, "shareBitmap failed", e)
        Result.failure(e)
    }
}
