package com.chen.memorizewords.core.network.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiResponseParserTest {

    @Test
    fun `problem details are preserved for conflict rate limit and service errors`() {
        listOf(409, 429, 503).forEach { status ->
            val result = ApiResponseParser.httpFailure(
                code = status,
                message = "HTTP fallback",
                errorBody = problemJson(status),
                encodedPath = "/api/practice/shadowing/evaluate"
            )

            val failure = assertIs<NetworkResult.Failure.HttpError>(result)
            assertEquals(status, failure.code)
            assertEquals("BUSINESS_$status", failure.businessCode)
            assertEquals("Server detail $status", failure.message)
            assertEquals(42L, failure.retryAfterSeconds)
            assertEquals(1_783_699_200_000L, failure.resetAtMs)
            assertEquals(1_783_695_600_000L, failure.serverTimeMs)
            assertEquals("trace-$status", failure.traceId)
        }
    }

    @Test
    fun `public endpoint unauthorized keeps problem details as http error`() {
        val result = ApiResponseParser.httpFailure(
            code = 401,
            message = "Unauthorized",
            errorBody = problemJson(401),
            encodedPath = "/api/auth/register"
        )

        val failure = assertIs<NetworkResult.Failure.HttpError>(result)
        assertEquals("BUSINESS_401", failure.businessCode)
        assertEquals("Server detail 401", failure.message)
    }

    @Test
    fun `protected endpoint unauthorized still triggers session handling`() {
        val result = ApiResponseParser.httpFailure(
            code = 401,
            message = "Unauthorized",
            errorBody = problemJson(401),
            encodedPath = "/api/me"
        )

        val failure = assertIs<NetworkResult.Failure.Unauthorized>(result)
        assertEquals("Server detail 401", failure.message)
        assertEquals("/api/me", failure.path)
    }

    private fun problemJson(status: Int): String = """
        {
          "type": "urn:memorize-words:problem:business-$status",
          "title": "Business $status",
          "status": $status,
          "detail": "Server detail $status",
          "code": "BUSINESS_$status",
          "retryAfterSeconds": 42,
          "resetAtMs": 1783699200000,
          "serverTimeMs": 1783695600000,
          "traceId": "trace-$status"
        }
    """.trimIndent()
}
