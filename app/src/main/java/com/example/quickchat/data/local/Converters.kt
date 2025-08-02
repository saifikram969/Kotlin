package com.example.quickchat.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class Converters {
    private val gson = Gson()
    private val stringListType = (object : TypeToken<List<String>>() {}).type

    @TypeConverter
    fun stringListToJson(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> {
        return try {
            gson.fromJson<List<String>>(value ?: "[]", stringListType)
        } catch (e: Exception) {
            emptyList()
        }


    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun timestampToDate(value: Long?): Date? {
        return value?.let { Date(it) }
    }
}
