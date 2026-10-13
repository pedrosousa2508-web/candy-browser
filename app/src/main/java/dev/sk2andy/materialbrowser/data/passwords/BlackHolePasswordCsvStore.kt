package dev.sk2andy.materialbrowser.data.passwords

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** V4.1-compatible encrypted local prototype. No autofill, network, logs or plaintext disk cache. */
internal class BlackHolePasswordCsvStore(context: Context) {
    private val target = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val magic = byteArrayOf(0x43, 0x50, 0x56, 0x31) // Historical CPV1 format.

    fun import(preview: PasswordCsvPreview): PasswordImportResult = synchronized(LOCK) {
        val merged = PasswordCsvImportRules.merge(load(), preview.entries)
        if (merged.report.inserted > 0) save(merged.entries)
        merged.report
    }

    fun exportCsv(authorization: PasswordCsvExportAuthorization, nowMillis: () -> Long, openOutput: () -> OutputStream): Int =
        synchronized(LOCK) {
            check(authorization.consume(nowMillis())) { "Fresh export authentication required" }
            val entries = load()
            require(entries.isNotEmpty()) { "Prototype has no entries" }
            openOutput().use { BlackHolePasswordCsv.write(entries, it) }
            entries.size
        }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        check(create) { "Prototype key unavailable" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun load(): List<ImportedPassword> {
        val bytes = try {
            target.openRead().use { it.readNBytes(MAX_FILE_BYTES + 1) }
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        require(bytes.size in 33..MAX_FILE_BYTES) { "Invalid prototype file" }
        require(bytes.copyOfRange(0, 4).contentEquals(magic)) { "Unsupported prototype format" }
        val ivLength = bytes[4].toInt() and 0xff
        require(ivLength == 12) { "Invalid prototype nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(5, 17)))
        cipher.updateAAD(magic)
        val plain = cipher.doFinal(bytes.copyOfRange(17, bytes.size))
        try {
            val json = JSONArray(String(plain, StandardCharsets.UTF_8))
            require(json.length() <= BlackHolePasswordCsv.MAX_RECORDS) { "Prototype capacity exceeded" }
            return (0 until json.length()).map { index ->
                val row = json.getJSONObject(index)
                ImportedPassword(row.getString("name"), row.getString("url"),
                    row.getString("username"), row.getString("password"))
            }
        } finally {
            plain.fill(0)
        }
    }

    private fun save(entries: List<ImportedPassword>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().put("name", entry.name).put("url", entry.url)
                .put("username", entry.username).put("password", entry.password))
        }
        val plain = json.toString().toByteArray(StandardCharsets.UTF_8)
        val encrypted = try {
            require(plain.size <= MAX_FILE_BYTES - 33) { "Prototype file capacity exceeded" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            cipher.updateAAD(magic)
            val iv = cipher.iv
            check(iv.size == 12) { "Unexpected GCM nonce size" }
            ByteArrayOutputStream().apply {
                write(magic)
                write(iv.size)
                write(iv)
                write(cipher.doFinal(plain))
            }.toByteArray()
        } finally {
            plain.fill(0)
        }
        try {
            val stream = target.startWrite()
            try {
                stream.write(encrypted)
                target.finishWrite(stream)
            } catch (failure: Exception) {
                target.failWrite(stream)
                throw failure
            }
        } finally {
            encrypted.fill(0)
        }
    }

    internal companion object {
        // Preserve V4.1 names; never restore/migrate this file through the browser's data ZIP.
        const val FILE_NAME = "candy_password_vault_v1.bin"
        const val KEY_ALIAS = "candy_password_vault_local_v1"
        private const val MAX_FILE_BYTES = 16 * 1024 * 1024
        private val LOCK = Any()
    }
}
