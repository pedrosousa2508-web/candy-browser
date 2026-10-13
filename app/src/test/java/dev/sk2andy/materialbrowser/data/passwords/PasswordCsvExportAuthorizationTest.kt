package dev.sk2andy.materialbrowser.data.passwords

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvExportAuthorizationTest {
    @Test
    fun `authorization can be consumed exactly once`() {
        val authorization = PasswordCsvExportAuthorization(100)
        assertTrue(authorization.consume(101))
        assertFalse(authorization.consume(102))
    }

    @Test
    fun `expired and backwards-clock grants fail closed and cannot be retried`() {
        for (time in listOf(99L, 30_101L)) {
            val authorization = PasswordCsvExportAuthorization(100)
            assertFalse(authorization.consume(time))
            assertFalse(authorization.consume(101))
        }
    }
}
