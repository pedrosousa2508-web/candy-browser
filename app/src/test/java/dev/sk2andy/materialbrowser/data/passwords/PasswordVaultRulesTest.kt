package dev.sk2andy.materialbrowser.data.passwords

import dev.sk2andy.materialbrowser.data.AppDataArchiveRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordVaultRulesTest {
    private val first = VaultCredential("00000000-0000-0000-0000-000000000001", "https://invented.example/login", "fake-user", "invented-one")
    private val second = VaultCredential("00000000-0000-0000-0000-000000000002", "https://invented.example/login", "fake-user", "invented-two")

    @Test fun `manual CRUD preserves distinct passwords and stable identities`() {
        val entries = PasswordVaultRules.replace(listOf(first), second, isNew = true)
        assertEquals(2, entries.size)
        val edited = VaultCredential(first.id, "https://other.example", "other-user", "invented-three")
        val updated = PasswordVaultRules.replace(entries, edited, isNew = false)
        assertEquals("invented-three", updated.first().password)
        assertEquals("invented-two", updated.last().password)
        assertEquals(second.id, PasswordVaultRules.remove(updated, setOf(first.id)).single().id)
        assertTrue(PasswordVaultRules.remove(updated, setOf(first.id, second.id)).isEmpty())
        assertTrue(runCatching { PasswordVaultRules.replace(entries, first, isNew = true) }.isFailure)
        assertTrue(runCatching { PasswordVaultRules.remove(entries, setOf("missing")) }.isFailure)
    }
    @Test fun `search matches site domain and username without looking at passwords`() {
        val entries = listOf(first.summary(), second.summary())
        assertEquals(2, PasswordVaultRules.search(entries, "INVENTED.EXAMPLE").size)
        assertEquals(2, PasswordVaultRules.search(entries, "fake-user").size)
        assertTrue(PasswordVaultRules.search(entries, "invented-one").isEmpty())
    }
    @Test fun `lock rejects expired absent and backwards sessions`() {
        assertFalse(PasswordVaultRules.active(null, 0, 10))
        assertTrue(PasswordVaultRules.active(100, 100, 29_999))
        assertFalse(PasswordVaultRules.active(100, 100, 30_100))
        assertFalse(PasswordVaultRules.active(100, 100, 99))
        assertFalse(PasswordVaultRules.active(100, 30_099, 30_100))
    }
    @Test fun `input and debug representations cannot expose password`() {
        assertFalse(first.toString().contains(first.password))
        assertFalse(first.summary().toString().contains(first.username))
        for (site in listOf("javascript:alert(1)", "https://fake:password@invented.example", "file:///tmp/fake")) {
            assertTrue(runCatching { PasswordVaultRules.validate(VaultCredential(first.id, site, "fake", "invented")) }.isFailure)
        }
    }
    @Test fun `new vault and atomic files cannot enter general exports or imports`() {
        for (path in listOf("no_backup/black_hole_native_vault", "no_backup/black_hole_native_vault/vault.bhv1", "no_backup/black_hole_native_vault/vault.bhv1.bak")) {
            assertFalse(AppDataArchiveRules.shouldExportRelativePath(path))
            assertNull(AppDataArchiveRules.dataRelativePath("data/$path", false))
        }
        assertTrue(AppDataArchiveRules.shouldExportRelativePath("no_backup/unrelated"))
    }
}
