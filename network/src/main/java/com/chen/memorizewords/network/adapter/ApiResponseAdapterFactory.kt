package com.chen.memorizewords.network.adapter

import com.chen.memorizewords.network.model.ApiResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class ApiResponseAdapterFactory : JsonAdapter.Factory {

    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {

        val rawType = Types.getRawType(type)
        if (rawType != ApiResponse::class.java) return null

        // 拿到 T
        val t = (type as ParameterizedType).actualTypeArguments[0]
        val rawDataType = Types.getRawType(t)

        val dataAdapter = if (rawDataType == Unit::class.java) {
            UnitJsonAdapter() as JsonAdapter<Any>
        } else {
            moshi.adapter<Any>(t)
        }

        @Suppress("UNCHECKED_CAST")
        return ApiResponseJsonAdapter(dataAdapter) as JsonAdapter<*>
    }
}
