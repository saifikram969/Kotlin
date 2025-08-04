/*
package com.example.quickchat.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.quickchat.apiservice.CloudinaryApiService
import com.example.quickchat.data.model.CloudinaryUploadResponse
import com.example.quickchat.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException

class CloudinaryRepository(
    private val apiService: CloudinaryApiService,
    private val context: Context
) {
    companion object {
        private const val TAG = "CloudinaryRepository"
        private const val UPLOAD_PRESET = "chat_unsigned_upload"
        private const val API_KEY = "461143212564624"
    }

    suspend fun uploadImage(uri: Uri): CloudinaryUploadResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val file = FileUtils.getFileFromUri(context, uri) ?: run {
                    Log.e(TAG, "Could not create file from URI")
                    return@withContext null
                }

                if (!file.exists()) {
                    Log.e(TAG, "File does not exist: ${file.absolutePath}")
                    return@withContext null
                }

                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val uploadPreset = UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())
                val apiKey = API_KEY.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.uploadImage(
                    file = body,
                    uploadPreset = uploadPreset,
                    apiKey = apiKey
                )

                processResponse(response).also {
                    // Clean up the temporary file
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete temporary file: ${file.absolutePath}")
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "IO Error during upload: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during upload: ${e.message}", e)
            null
        }
    }

    private fun processResponse(response: Response<CloudinaryUploadResponse>): CloudinaryUploadResponse? {
        return if (response.isSuccessful) {
            response.body()?.also {
                Log.d(TAG, "Upload successful: ${it.url}")
            }
        } else {
            val errorBody = response.errorBody()?.string() ?: "No error body"
            Log.e(TAG, "Upload failed with code ${response.code()}: $errorBody")
            null
        }
    }
}*/