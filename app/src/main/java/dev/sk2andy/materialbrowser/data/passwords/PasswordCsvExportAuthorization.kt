package dev.sk2andy.materialbrowser.data.passwords

/** One-use UI authorization, not a cryptographic lock. Construct only after Android authentication. */
internal class PasswordCsvExportAuthorization(private val issuedAtMillis: Long) {
    private var consumed = false

    @Synchronized
    fun consume(nowMillis: Long): Boolean {
        if (consumed) return false
        consumed = true
        return nowMillis >= issuedAtMillis && nowMillis - issuedAtMillis <= VALID_FOR_MILLIS
    }

    override fun toString(): String = "PasswordCsvExportAuthorization(<redacted>)"

    private companion object {
        const val VALID_FOR_MILLIS = 30_000L
    }
}
