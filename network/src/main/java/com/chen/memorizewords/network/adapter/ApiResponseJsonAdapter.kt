package com.chen.memorizewords.network.adapter

import com.chen.memorizewords.network.model.ApiResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

class ApiResponseJsonAdapter<T>(
    private val dataAdapter: JsonAdapter<T>
) : JsonAdapter<ApiResponse<T>>() {

    override fun fromJson(reader: JsonReader): ApiResponse<T>? {
        var code = 0
        var message = ""
        var data: T? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "code" -> code = reader.nextInt()
                "message" -> message = reader.nextString()
                "data" -> {
                    // 👇 只兜 data
                    data = try {
                        dataAdapter.fromJson(reader)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        reader.skipValue()
                        null
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ApiResponse(
            code = code,
            message = message,
            data = data
        )
    }

    override fun toJson(writer: JsonWriter, value: ApiResponse<T>?) {
        throw UnsupportedOperationException("Serialization not supported")
    }
}
