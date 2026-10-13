package dev.sk2andy.materialbrowser.data.passwords

import java.net.URI

/** Deliberately independent of browser profiles, tabs, CSV CPV1 and network services. */
internal class VaultCredential(val id: String, val site: String, val username: String, val password: String) {
    fun summary() = VaultSummary(id, site, username)
    override fun toString() = "VaultCredential(redacted)"
}

internal class VaultSummary(val id: String, val site: String, val username: String) {
    override fun toString() = "VaultSummary(redacted)"
}

internal enum class VaultField { Site, Username, Password }

internal object PasswordVaultRules {
    const val MAX_RECORDS = 5_000
    const val SESSION_MILLIS = 30_000L

    fun editField(entry: VaultCredential, field: VaultField, value: String): VaultCredential =
        VaultCredential(entry.id,
            if (field == VaultField.Site) value.take(4_096) else entry.site,
            if (field == VaultField.Username) value.take(1_024) else entry.username,
            if (field == VaultField.Password) value.take(4_096) else entry.password)

    fun validate(entry: VaultCredential) {
        require(entry.id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid identifier" }
        require(entry.site.length in 1..4_096 && entry.username.length <= 1_024 &&
            entry.password.length in 1..4_096) { "Invalid field length" }
        require(listOf(entry.site, entry.username, entry.password).none { '\u0000' in it }) { "Invalid field" }
        val uri = URI(entry.site)
        require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null) { "HTTP(S) site required" }
    }

    fun search(entries: List<VaultSummary>, query: String): List<VaultSummary> {
        val bounded = query.take(4_096).trim()
        return entries.filter { it.site.contains(bounded, ignoreCase = true) ||
            it.username.contains(bounded, ignoreCase = true) }
    }

    fun active(authenticatedAt: Long?, lastInteraction: Long, now: Long): Boolean =
        authenticatedAt != null && now >= authenticatedAt && now >= lastInteraction &&
            now - authenticatedAt < SESSION_MILLIS && now - lastInteraction < SESSION_MILLIS

    fun replace(entries: List<VaultCredential>, value: VaultCredential, isNew: Boolean): List<VaultCredential> {
        validate(value)
        if (isNew) {
            require(entries.size < MAX_RECORDS && entries.none { it.id == value.id })
            return entries + value
        }
        require(entries.count { it.id == value.id } == 1) { "Credential unavailable" }
        return entries.map { if (it.id == value.id) value else it }
    }

    fun remove(entries: List<VaultCredential>, ids: Set<String>): List<VaultCredential> {
        require(ids.isNotEmpty() && entries.map { it.id }.toSet().containsAll(ids))
        return entries.filterNot { it.id in ids }
    }
}
