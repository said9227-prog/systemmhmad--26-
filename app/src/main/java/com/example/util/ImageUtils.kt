package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_DIMENSION = 1280
    private const val JPEG_QUALITY = 82

    suspend fun saveImageSafely(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(context.filesDir, "item_images").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(imagesDir, "item_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            
            // First, copy the content URI to a temporary file to avoid issues with reading the stream multiple times
            val tempFile = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.tmp")
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val success = processAndSaveBitmap(tempFile, targetFile, MAX_IMAGE_DIMENSION)
                if (success && targetFile.exists() && targetFile.length() > 0) {
                    Uri.fromFile(targetFile).toString()
                } else {
                    null
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in saveImageSafely, retrying with aggressive downscale", oom)
            System.gc()
            try {
                val imagesDir = File(context.filesDir, "item_images").apply {
                    if (!exists()) mkdirs()
                }
                val targetFile = File(imagesDir, "item_${System.currentTimeMillis()}_fallback.jpg")
                
                val tempFile = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.tmp")
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val success = processAndSaveBitmap(tempFile, targetFile, 640)
                    if (success && targetFile.exists()) {
                        Uri.fromFile(targetFile).toString()
                    } else null
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Fallback also failed", e)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image safely", e)
            null
        }
    }

    private fun processAndSaveBitmap(
        sourceFile: File,
        targetFile: File,
        maxDimension: Int
    ): Boolean {
        // Step 1: Read bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)
        
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) return false

        // Step 2: Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension, maxDimension)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        // Step 3: Decode downsampled bitmap
        var bitmap: Bitmap? = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            ?: return false

        // Step 4: Correct EXIF rotation if needed
        var rotationDegrees = 0
        try {
            val exif = ExifInterface(sourceFile.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation", e)
        }

        if (rotationDegrees != 0 && bitmap != null) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = rotatedBitmap
            }
        }

        // Step 5: Save compressed JPEG
        var outputStream: FileOutputStream? = null
        return try {
            outputStream = FileOutputStream(targetFile)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream) ?: false
        } finally {
            outputStream?.flush()
            outputStream?.close()
            bitmap?.recycle()
        }
    }

    private fun calculateInSampleSize(
        actualWidth: Int,
        actualHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (actualHeight > reqHeight || actualWidth > reqWidth) {
            val halfHeight = actualHeight / 2
            val halfWidth = actualWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun deleteImageFile(context: Context, imageUriString: String?) {
        if (imageUriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(imageUriString)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return)
                val imagesDir = File(context.filesDir, "item_images")
                if (file.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete image file: $imageUriString", e)
        }
    }
}
