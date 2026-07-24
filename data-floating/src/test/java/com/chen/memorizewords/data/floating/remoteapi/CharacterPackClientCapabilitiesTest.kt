package com.chen.memorizewords.data.floating.remoteapi

import kotlin.test.Test
import kotlin.test.assertEquals
import retrofit2.http.Query

class CharacterPackClientCapabilitiesTest {
    @Test
    fun `client advertises only the KTX2 renderer`() {
        assertEquals("2", CharacterPackClientCapabilities.MANIFEST_SCHEMAS)
        assertEquals(
            "ktx2_paged_v2",
            CharacterPackClientCapabilities.RENDERERS
        )
    }

    @Test
    fun `catalog and resolve requests carry both capability queries`() {
        assertEquals(
            EXPECTED_QUERY_NAMES,
            queryNames("getCharacterPacks")
        )
        assertEquals(
            EXPECTED_QUERY_NAMES,
            queryNames("resolveAppliedCharacterPack")
        )
    }

    private fun queryNames(methodName: String): List<String> {
        val method = CharacterPackApiService::class.java.getDeclaredMethod(
            methodName,
            String::class.java,
            String::class.java
        )
        return method.parameterAnnotations.map { annotations ->
            annotations.filterIsInstance<Query>().single().value
        }
    }

    private companion object {
        val EXPECTED_QUERY_NAMES = listOf(
            "supportedManifestSchemas",
            "supportedRenderers"
        )
    }
}
