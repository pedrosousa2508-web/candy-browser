package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconOptionTest {
    @Test
    fun `Black Hole exposes nine choices using stable launcher aliases`() {
        assertEquals(
            listOf(
                "LauncherIconClassic", "LauncherIconCookie", "LauncherIconCottonCandy",
                "LauncherIconLemon", "LauncherIconDonut", "LauncherIconCookieMaxed",
                "LauncherIconCottonCandyFreeform", "LauncherIconLemonMaxed", "LauncherIconDonutMaxed",
            ),
            AppIconOption.availableOptions(blackHole = true).map { it.aliasName },
        )
    }

    @Test
    fun `Candy retains all ten existing choices including Jelly Beans`() {
        val options = AppIconOption.availableOptions(blackHole = false)
        assertEquals(10, options.size)
        assertTrue(AppIconOption.JellyBeans in options)
    }
}
