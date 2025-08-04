package com.example.quickchat.data.model


data class CloudinaryUploadResponse(
    val asset_id: String?,
    val public_id: String?,
    val version: Long?,
    val version_id: String?,
    val signature: String?,
    val width: Int?,
    val height: Int?,
    val format: String?,
    val resource_type: String?,
    val created_at: String?,
    val tags: List<String>?,
    val bytes: Long?,
    val type: String?,
    val etag: String?,
    val placeholder: Boolean?,
    val url: String?,
    val secure_url: String?
)
