package com.example.quickchat.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID


class CloudinaryUploader(private val context: Context) {
    sealed class UploadResult {
        data class Success(
            val fileUrl: String,
            val thumbnailUrl: String? = null,
            val fileType: String,
            val fileName: String,
            val fileSize: Long
        ) : UploadResult()
        data class Error(val message: String) : UploadResult()
        data class Progress(val progress: Float) : UploadResult()
    }

    suspend fun uploadFile(uri: Uri): Flow<UploadResult> = callbackFlow {
        try {
            // Get file details
            val fileSize = context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
            val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val fileType = when {
                mimeType.startsWith("image/") -> "image"
                mimeType == "application/pdf" -> "pdf"
                mimeType.startsWith("audio/") -> "audio"
                mimeType.startsWith("video/") -> "video"
                else -> "file"
            }

            // Check file size limits (e.g., 10MB max)
            val maxSize = 10 * 1024 * 1024 // 10MB
            if (fileSize > maxSize) {
                trySend(UploadResult.Error("File too large. Max 10MB allowed."))
                close()
                return@callbackFlow
            }

            // Create temp file
            val tempFile = createTempFile(context, uri, fileName)

            // Upload to Cloudinary
            MediaManager.get().upload(tempFile.absolutePath)
                .option("resource_type", if (fileType == "image" || fileType == "video") "auto" else "raw")
                .option("public_id", "messages/${UUID.randomUUID()}")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {
                        // Optional: Send start event if needed
                    }

                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                        trySend(UploadResult.Progress(bytes.toFloat() / totalBytes.toFloat()))
                    }

                    override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                        val secureUrl = resultData["secure_url"] as? String
                        if (secureUrl != null) {
                            trySend(UploadResult.Success(
                                fileUrl = secureUrl,
                                fileType = fileType,
                                fileName = fileName,
                                fileSize = fileSize
                            ))
                        } else {
                            trySend(UploadResult.Error("Upload failed: No URL returned"))
                        }
                        close()
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        trySend(UploadResult.Error("Upload failed: ${error?.description}"))
                        close()
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                        trySend(UploadResult.Error("Upload failed and will retry: ${error?.description}"))
                    }
                }).dispatch()

            awaitClose { }
        } catch (e: Exception) {
            trySend(UploadResult.Error("Upload failed: ${e.localizedMessage}"))
            close()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }

    private suspend fun createTempFile(context: Context, uri: Uri, fileName: String): File {
        return withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        }
    }
}