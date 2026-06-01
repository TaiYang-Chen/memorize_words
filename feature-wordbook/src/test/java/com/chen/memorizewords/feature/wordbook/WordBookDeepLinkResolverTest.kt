package com.chen.memorizewords.feature.wordbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordBookDeepLinkResolverTest {

    @Test
    fun `null data uses default launch`() {
        assertNull(WordBookDeepLinkResolver.resolve(null))
    }

    @Test
    fun `blank data uses default launch`() {
        assertNull(WordBookDeepLinkResolver.resolve("   "))
    }

    @Test
    fun `favorites deep link opens favorites as root`() {
        assertEquals(
            WordBookLaunchTarget(WordBookLaunchTarget.Destination.FAVORITES),
            WordBookDeepLinkResolver.resolve("myapp://favorites")
        )
    }

    @Test
    fun `my books deep link opens my books as root`() {
        assertEquals(
            WordBookLaunchTarget(WordBookLaunchTarget.Destination.MY_WORD_BOOKS),
            WordBookDeepLinkResolver.resolve("myapp://wordbook/my-books")
        )
    }

    @Test
    fun `shop deep link opens shop as root`() {
        assertEquals(
            WordBookLaunchTarget(WordBookLaunchTarget.Destination.SHOP),
            WordBookDeepLinkResolver.resolve("myapp://wordbook/shop")
        )
    }

    @Test
    fun `my books deep link keeps source query parameter`() {
        assertEquals(
            WordBookLaunchTarget(
                destination = WordBookLaunchTarget.Destination.MY_WORD_BOOKS,
                source = "profile"
            ),
            WordBookDeepLinkResolver.resolve("myapp://wordbook/my-books?source=profile")
        )
    }

    @Test
    fun `unknown deep link falls back to default launch`() {
        assertNull(WordBookDeepLinkResolver.resolve("myapp://wordbook/unknown"))
    }
}
