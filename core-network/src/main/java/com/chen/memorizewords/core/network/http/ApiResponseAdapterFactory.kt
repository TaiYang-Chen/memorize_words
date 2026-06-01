package com.chen.memorizewords.core.network.http

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

        val dataType = (type as ParameterizedType).actualTypeArguments[0]
        val rawDataType = Types.getRawType(dataType)
        val dataAdapter = if (rawDataType == Unit::class.java) {
            @Suppress("UNCHECKED_CAST")
            UnitJsonAdapter() as JsonAdapter<Any>
        } else {
            moshi.adapter<Any>(dataType)
        }

        @Suppress("UNCHECKED_CAST")
        return ApiResponseJsonAdapter(dataAdapter) as JsonAdapter<*>
    }
}
