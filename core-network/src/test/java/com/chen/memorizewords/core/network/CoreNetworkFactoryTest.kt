package com.chen.memorizewords.core.network

import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.ApiResponseAdapterFactory
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreNetworkFactoryTest {
    @Test
    fun `createMoshi registers extra JsonAdapter factories`() {
        val moshi = CoreNetworkFactory.createMoshi(
            extraAdapters = listOf(ApiResponseAdapterFactory())
        )
        val responseType = Types.newParameterizedType(ApiResponse::class.java, String::class.java)

        val response = moshi.adapter<ApiResponse<String>>(responseType).fromJson(
            """{"code":200,"message":"ok","data":"synced"}"""
        )

        assertEquals(200, response?.code)
        assertEquals("ok", response?.message)
        assertEquals("synced", response?.data)
    }
}
