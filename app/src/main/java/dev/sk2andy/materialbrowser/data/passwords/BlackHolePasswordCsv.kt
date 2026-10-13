package dev.sk2andy.materialbrowser.data.passwords

import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Sensitive values: never log or include in toString(). */
internal class ImportedPassword(
    val name: String,
    val url: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String = "ImportedPassword(<redacted>)"
}

internal class PasswordCsvPreview(
    val entries: List<ImportedPassword>,
    val totalRows: Int,
    val invalidRows: Int,
    val appOnlyRows: Int,
) {
    override fun toString(): String = "PasswordCsvPreview(total=$totalRows, invalid=$invalidRows, appOnly=$appOnlyRows)"
}

/**
 * Bounded RFC-4180 CSV importer for Google Password Manager and compatible exports.
 * This is an Android-local file reader: it performs no network operations.
 */
internal object BlackHolePasswordCsv {
    const val MAX_RECORDS = 20_000
    private const val MAX_COLUMNS = 32
    private const val MAX_CELL = 16_384
    private const val MAX_URL = 4_096
    private const val MAX_USERNAME = 1_024
    private const val MAX_PASSWORD = 4_096
    private const val MAX_CHARS = 12 * 1024 * 1024

    /** Direct to the user-selected stream; no plaintext staging file. Never trims passwords. */
    fun write(entries: List<ImportedPassword>, output: OutputStream) {
        require(entries.size <= MAX_RECORDS) { "Too many CSV records" }
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        writer.write("name,url,username,password\r\n")
        fun cell(value: String) {
            writer.write('"'.code)
            for (c in value) {
                if (c == '"') writer.write('"'.code)
                writer.write(c.code)
            }
            writer.write('"'.code)
        }
        for (entry in entries) {
            cell(entry.name)
            writer.write(','.code)
            cell(entry.url)
            writer.write(','.code)
            cell(entry.username)
            writer.write(','.code)
            cell(entry.password)
            writer.write("\r\n")
        }
        writer.flush()
    }

    fun parse(input: InputStream): PasswordCsvPreview {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val rows = PushbackReader(InputStreamReader(input, decoder), 2)
        var consumedChars = 0
        fun readChar(): Int {
            val value = rows.read()
            if (value != -1 && ++consumedChars > MAX_CHARS) {
                throw IllegalArgumentException("CSV is too large")
            }
            return value
        }
        fun unreadChar(value: Int) {
            if (value != -1) {
                rows.unread(value)
                consumedChars--
            }
        }

        fun nextRow(): List<String>? {
            val cells = ArrayList<String>()
            val cell = StringBuilder()
            var state = 0 // 0=start, 1=unquoted, 2=quoted, 3=after quote
            var seen = false
            fun addCell() {
                if (cells.size >= MAX_COLUMNS) throw IllegalArgumentException("Too many CSV columns")
                cells.add(cell.toString())
                cell.setLength(0)
                state = 0
            }
            while (true) {
                val c = readChar()
                if (c == -1) {
                    if (state == 2) throw IllegalArgumentException("Unterminated quoted CSV field")
                    if (!seen) return null
                    addCell()
                    return cells
                }
                if (c == '\uFEFF'.code && !seen && consumedChars == 1) continue
                seen = true
                if (state == 2) {
                    if (c == '"'.code) state = 3 else cell.append(c.toChar())
                } else if (state == 3 && c == '"'.code) {
                    cell.append('"')
                    state = 2
                } else if (c == ','.code) {
                    addCell()
                } else if (c == '\n'.code || c == '\r'.code) {
                    if (c == '\r'.code) {
                        val next = readChar()
                        if (next != '\n'.code) unreadChar(next)
                    }
                    addCell()
                    return cells
                } else if (state == 0 && c == '"'.code) {
                    state = 2
                } else if (state == 3) {
                    throw IllegalArgumentException("Unexpected text after closing CSV quote")
                } else if (c == '"'.code) {
                    throw IllegalArgumentException("Unquoted CSV quote")
                } else {
                    cell.append(c.toChar())
                    state = 1
                }
                if (cell.length > MAX_CELL) throw IllegalArgumentException("CSV field too long")
            }
        }

        val headers = nextRow() ?: throw IllegalArgumentException("CSV is empty")
        fun normalized(v: String) = v.trim().lowercase(Locale.ROOT)
            .replace(" ", "").replace("_", "").replace("-", "")
        val normalizedHeaders = headers.map(::normalized)
        fun col(vararg aliases: String): Int {
            val matches = normalizedHeaders.indices.filter { normalizedHeaders[it] in aliases }
            require(matches.size <= 1) { "Ambiguous CSV columns" }
            return matches.firstOrNull() ?: -1
        }
        val urlCol = col("url", "website", "site", "uri", "loginuri", "origin")
        val usernameCol = col("username", "user", "login", "email", "userid")
        val passwordCol = col("password", "pass", "senha")
        val nameCol = col("name", "title", "sitename")
        require(urlCol >= 0 && usernameCol >= 0 && passwordCol >= 0) {
            "CSV needs URL, username and password columns"
        }

        val entries = ArrayList<ImportedPassword>()
        var total = 0
        var invalid = 0
        var appOnly = 0
        while (true) {
            val row = nextRow() ?: break
            if (row.all(String::isBlank)) continue
            if (++total > MAX_RECORDS) throw IllegalArgumentException("Too many CSV records")
            fun field(index: Int) = row.getOrNull(index)?.trim().orEmpty()
            val url = field(urlCol)
            val username = field(usernameCol)
            val password = row.getOrNull(passwordCol).orEmpty() // Never trim passwords.
            val classification = classify(url)
            if (row.size != headers.size || classification == 0 ||
                url.length > MAX_URL || username.length > MAX_USERNAME ||
                password.isEmpty() || password.length > MAX_PASSWORD || '\u0000' in password ||
                '\u0000' in username || '\u0000' in field(nameCol)
            ) {
                invalid++
                continue
            }
            if (classification == 2) appOnly++
            entries.add(ImportedPassword(field(nameCol), url, username, password))
        }
        return PasswordCsvPreview(entries, total, invalid, appOnly)
    }

    /** 1 = web, 2 = Android app, 0 = invalid. Keep app-only entries, never fill them into web pages. */
    private fun classify(raw: String): Int = try {
        val uri = URI(raw)
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "https", "http" -> if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) 0 else 1
            "android" -> if (uri.schemeSpecificPart.isNullOrBlank()) 0 else 2
            else -> 0
        }
    } catch (_: java.net.URISyntaxException) {
        0
    }
}
