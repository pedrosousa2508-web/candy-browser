package dev.sk2andy.materialbrowser.data.passwords

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordCsvImportRulesTest {
    private fun entry(password: String = "fake-password", username: String = "fake-user", name: String = "Fake") =
        ImportedPassword(name, "https://example.org", username, password)

    @Test
    fun `deduplicates existing and incoming entries while preserving different password versions`() {
        val existing = listOf(entry())
        val merged = PasswordCsvImportRules.merge(existing,
            listOf(entry(name = "Renamed"), entry("other-fake"), entry("other-fake"), entry(username = "other-user")))
        assertEquals(PasswordImportResult(2, 2, 1, 3), merged.report)
        assertEquals("fake-password", merged.entries[0].password)
        assertEquals("other-fake", merged.entries[1].password)
        assertEquals(1, existing.size)
    }

    @Test
    fun `counts a second version within the same import as a conflict`() {
        val report = PasswordCsvImportRules.merge(emptyList(), listOf(entry(), entry("other-fake"))).report
        assertEquals(PasswordImportResult(2, 0, 1, 2), report)
    }

    @Test
    fun `a full store still accepts an exact duplicate without growing`() {
        val report = PasswordCsvImportRules.merge(List(20_000) { entry(username = "fake-$it") },
            listOf(entry(username = "fake-0"))).report
        assertEquals(PasswordImportResult(0, 1, 0, 20_000), report)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects growth beyond capacity before committing`() {
        PasswordCsvImportRules.merge(List(20_000) { entry(username = "fake-$it") }, listOf(entry()))
    }
}
