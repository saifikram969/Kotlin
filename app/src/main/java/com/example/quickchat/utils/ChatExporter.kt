package com.example.quickchat.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.quickchat.data.model.ChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ChatExporter {
    // Use application context to get package name instead of BuildConfig
    private fun getAuthority(context: Context) = "${context.packageName}.fileprovider"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val exportDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // Configure JSON serializer
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val httpClient = OkHttpClient()

    private fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            getAuthority(context),
            file
        )
    }

    fun exportToTxt(context: Context, messages: List<ChatMessage>, roomId: String): Uri {
        return try {

            Log.d("ChatExporter", "Starting export process.: $messages")


            val exportFile = File(context.cacheDir, "chat_${roomId}_${exportDateFormat.format(Date())}.txt").apply {
                Log.d("ChatExporter", "External storage not available, falling back to internal")

                createNewFile()
            }

            FileWriter(exportFile).use { writer ->
                Log.d("ChatExporter", "File exists, attempting to delete")

                messages.forEach { message ->
                    writer.write("${dateFormat.format(Date(message.timestamp))} - ${message.senderId}: ${message.text}\n")
                    message.imageUrl?.let { url ->
                        writer.write("  [Image: $url]\n")
                    }
                }
            }
            getUriForFile(context, exportFile)
        } catch (e: Exception) {
            Log.e("ChatExporter", "Failed to export to TXT", e)
            throw IOException("Failed to export to TXT: ${e.message}")
        }
    }

    fun exportToJson(context: Context, messages: List<ChatMessage>, roomId: String): Uri {
        return try {
            val exportFile = File(context.cacheDir, "chat_${roomId}_${exportDateFormat.format(Date())}.json").apply {
                createNewFile()
            }
            Log.d("ChatExporter", "File exists, attempting to delete")

            FileWriter(exportFile).use { writer ->
                writer.write(json.encodeToString(messages))
            }
            getUriForFile(context, exportFile)
        } catch (e: Exception) {
            Log.e("ChatExporter", "Failed to export to JSON", e)
            throw IOException("Failed to export to JSON: ${e.message}")
        }
    }

    fun exportToZip(context: Context, messages: List<ChatMessage>, roomId: String): Uri {
        return try {
            // 1. Ensure directories exist
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: run {
                val internalDir = File(context.filesDir, "Documents").apply {
                    mkdirs()
                }
                internalDir
            }.also { dir ->
                if (!dir.exists() && !dir.mkdirs()) {
                    throw IOException("Could not create storage directory")
                }
            }

            // 2. Create unique filename
            val zipFile = File(storageDir, "chat_export_${roomId}_${System.currentTimeMillis()}.zip").apply {

                val zipFileName = "chat_export_${roomId}_${System.currentTimeMillis()}.zip"
                Log.d("ChatExporter", "File exists, attempting to delete")

                parentFile?.mkdirs() // Ensure parent directories exist
                if (exists() && !delete()) {
                    throw IOException("Could not delete existing file")
                }
            }

            // 3. Create ZIP file
            FileOutputStream(zipFile).use { fileOut ->
                Log.d("ChatExporter", "File exists, attempting to delete")

                ZipOutputStream(BufferedOutputStream(fileOut)).use { zipOut ->
                    // Add messages.json
                    File.createTempFile("messages", ".json", context.cacheDir).run {
                        try {
                            writeText(json.encodeToString(messages))
                            addFileToZip(zipOut, this, "messages.json")
                        } finally {
                            delete()
                        }
                    }

                    // Add media files
                    messages.forEachIndexed { index, message ->
                        message.imageUrl?.let { url ->
                            try {
                                downloadMediaFile(context, url, index)?.let { mediaFile ->
                                    try {
                                        if (mediaFile.exists()) {
                                            addFileToZip(zipOut, mediaFile, "media/${mediaFile.name}")
                                        }
                                    } finally {
                                        mediaFile.delete()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("ChatExporter", "Failed to add media: $url", e)
                            }
                        }
                    }
                }
            }

            // 4. Verify ZIP was created
            if (!zipFile.exists() || zipFile.length() == 0L) {
                throw IOException("ZIP file creation failed")
            }

            // 5. Return content URI
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                zipFile
            )
        } catch (e: Exception) {
            Log.e("ChatExporter", "ZIP export failed", e)
            throw IOException("Failed to create export: ${e.localizedMessage ?: "Unknown error"}")
        }
    }


    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            fis.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }

    private fun downloadMediaFile(context: Context, url: String, index: Int): File? {
        return try {
            val extension = url.substringAfterLast('.', "").takeIf { it.length in 1..4 } ?: "jpg"
            val file = File.createTempFile("media_$index", ".$extension", context.cacheDir)

            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun createShareIntent(context: Context, uri: Uri, type: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            setType(type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}