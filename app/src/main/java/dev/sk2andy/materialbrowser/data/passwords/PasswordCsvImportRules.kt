package dev.sk2andy.materialbrowser.data.passwords

internal data class PasswordImportResult(
    val inserted: Int,
    val exactDuplicates: Int,
    val conflictingVersions: Int,
    val totalStored: Int,
)

internal class PasswordCsvMerge(val entries: List<ImportedPassword>, val report: PasswordImportResult) {
    override fun toString(): String = "PasswordCsvMerge(<redacted>)"
}

internal object PasswordCsvImportRules {
    private data class Identity(val url: String, val username: String) {
        override fun toString(): String = "Identity(<redacted>)"
    }

    private data class Fingerprint(val identity: Identity, val password: String) {
        override fun toString(): String = "Fingerprint(<redacted>)"
    }

    fun merge(existing: List<ImportedPassword>, incoming: List<ImportedPassword>): PasswordCsvMerge {
        require(existing.size <= BlackHolePasswordCsv.MAX_RECORDS) { "Prototype capacity exceeded" }
        val entries = existing.toMutableList()
        fun identity(entry: ImportedPassword) = Identity(entry.url, entry.username)
        fun fingerprint(entry: ImportedPassword) = Fingerprint(identity(entry), entry.password)
        val known = existing.mapTo(HashSet(), ::fingerprint)
        val identities = existing.mapTo(HashSet(), ::identity)
        var duplicates = 0
        var conflicts = 0
        var inserted = 0
        for (entry in incoming) {
            if (!known.add(fingerprint(entry))) {
                duplicates++
                continue
            }
            require(entries.size < BlackHolePasswordCsv.MAX_RECORDS) { "Prototype capacity exceeded" }
            if (!identities.add(identity(entry))) conflicts++
            entries += entry
            inserted++
        }
        return PasswordCsvMerge(entries, PasswordImportResult(inserted, duplicates, conflicts, entries.size))
    }
}
