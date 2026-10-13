package dev.sk2andy.materialbrowser.data.passwords

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Experimental independent vault. OS authentication-bound key; no CPV1 migration or browser data. */
internal class BlackHoleNativeVaultStore(context: Context) {
    private val target = AtomicFile(File(context.noBackupFilesDir, "black_hole_native_vault/$FILE_NAME"))
    private val magic = byteArrayOf(0x42, 0x48, 0x56, 0x31) // Separate BHV1 format.

    fun summaries(): List<VaultSummary> = synchronized(LOCK) { load().map { it.summary() } }

    fun credential(id: String): VaultCredential = synchronized(LOCK) { load().single { it.id == id } }

    fun saveCredential(value: VaultCredential, isNew: Boolean) = synchronized(LOCK) {
        val entries = load()
        save(PasswordVaultRules.replace(entries, value, isNew))
    }

    fun delete(ids: Set<String>) = synchronized(LOCK) { save(PasswordVaultRules.remove(load(), ids)) }

    fun deleteAll(expectedIds: Set<String>, confirmation: String) = synchronized(LOCK) {
        val entries = load()
        require(confirmation == "APAGAR TODAS" && expectedIds.isNotEmpty() &&
            entries.map { it.id }.toSet() == expectedIds) { "Fresh full confirmation required" }
        save(emptyList())
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        check(create && !target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) { "Vault key unavailable" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .build(),
        )
        return generator.generateKey()
    }

    private fun load(): List<VaultCredential> {
        val bytes = try {
            target.openRead().use { it.readNBytes(MAX_FILE_BYTES + 1) }
        } catch (_: FileNotFoundException) {
            check(!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) { "Vault unavailable" }
            return emptyList()
        }
        require(bytes.size in 33..MAX_FILE_BYTES) { "Invalid vault file" }
        require(bytes.copyOfRange(0, 4).contentEquals(magic)) { "Unsupported vault format" }
        val ivLength = bytes[4].toInt() and 0xff
        require(ivLength == 12) { "Invalid vault nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(5, 17)))
        cipher.updateAAD(magic)
        val plain = cipher.doFinal(bytes.copyOfRange(17, bytes.size))
        try {
            val json = JSONArray(String(plain, StandardCharsets.UTF_8))
            require(json.length() <= PasswordVaultRules.MAX_RECORDS) { "Vault capacity exceeded" }
            val entries = (0 until json.length()).map { index ->
                val row = json.getJSONObject(index)
                VaultCredential(row.getString("id"), row.getString("site"),
                    row.getString("username"), row.getString("password")).also(PasswordVaultRules::validate)
            }
            require(entries.map { it.id }.toSet().size == entries.size) { "Duplicate record identifier" }
            return entries
        } finally {
            plain.fill(0)
        }
    }

    private fun save(entries: List<VaultCredential>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().put("id", entry.id).put("site", entry.site)
                .put("username", entry.username).put("password", entry.password))
        }
        val plain = json.toString().toByteArray(StandardCharsets.UTF_8)
        val encrypted = try {
            require(plain.size <= MAX_FILE_BYTES - 33) { "Vault file capacity exceeded" }
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
        // Distinct namespace: existing CPV1 ciphertext and key are never opened or migrated.
        const val FILE_NAME = "vault.bhv1"
        const val KEY_ALIAS = "black_hole_native_vault_v1_auth"
        private const val MAX_FILE_BYTES = 16 * 1024 * 1024
        private val LOCK = Any()
    }
}
