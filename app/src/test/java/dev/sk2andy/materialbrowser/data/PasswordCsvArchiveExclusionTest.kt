package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvArchiveExclusionTest {
    @Test
    fun `prototype ciphertext and atomic recovery files never enter browser archives`() {
        for (suffix in listOf("", ".bak", ".new")) {
            val path = "no_backup/candy_password_vault_v1.bin$suffix"
            assertFalse(AppDataArchiveRules.shouldExportRelativePath(path))
            assertNull(AppDataArchiveRules.dataRelativePath("data/$path", isDirectory = false))
        }
        assertTrue(AppDataArchiveRules.shouldExportRelativePath("files/candy_filter_rules.json"))
    }
}
