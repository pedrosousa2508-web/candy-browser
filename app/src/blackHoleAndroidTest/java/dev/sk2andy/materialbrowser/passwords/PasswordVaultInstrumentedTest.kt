package dev.sk2andy.materialbrowser.passwords

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyInfo
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.passwords.BlackHoleNativeVaultStore
import dev.sk2andy.materialbrowser.data.passwords.VaultCredential
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.regex.Pattern
import javax.crypto.SecretKeyFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordVaultInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val directory = File(context.noBackupFilesDir, "black_hole_native_vault")
    private val isolated = File(context.noBackupFilesDir, "invented-native-store-test")
    private var ownsDirectory = false

    @Before fun requireDisposableEmptyEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator required" }
        check(!directory.exists() && !isolated.exists()) { "Never alter existing native credentials" }
        ownsDirectory = true
    }
    @After fun removeOnlyOwnedInventedFixtures() {
        if (ownsDirectory) { directory.deleteRecursively(); isolated.deleteRecursively() }
    }

    @Test fun nativeCrudSearchSelectionAndStrongDeleteAll() {
        ActivityScenario.launch(PasswordVaultActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertFalse(activity.packageManager.getActivityInfo(activity.componentName, 0).exported)
            }
            click("vault_unlock"); authenticate(); waitTag("vault_empty")
            add("https://alpha.invented.example/login", "fake-alpha", "invented-first")
            add("https://beta.invented.example", "fake-beta", "invented-second")
            field("vault_search", "fake-alpha")
            assertTrue(device.hasObject(By.text("https://alpha.invented.example/login")))
            assertTrue(device.wait(Until.gone(By.text("https://beta.invented.example")), TIMEOUT))
            field("vault_search", "")
            waitText("https://beta.invented.example")
            // Read/edit requires another real OS credential confirmation.
            firstTag("vault_view_").click(); authenticate(); waitTag("vault_password")
            click("vault_show")
            field("vault_site", "https://edited.invented.example")
            field("vault_username", "fake-edited")
            field("vault_password", "invented-edited")
            click("vault_save"); authenticate(); waitText("https://edited.invented.example")
            firstTag("vault_delete_[a-f0-9]{8}-").click(); click("vault_confirm_delete"); authenticate()
            assertTrue(device.wait(Until.gone(By.text("https://edited.invented.example")), TIMEOUT))
            add("https://gamma.invented.example", "fake-gamma", "invented-third")
            device.findObjects(By.res(Pattern.compile("vault_select_.*"))).map { it.resourceName }.forEach(::click)
            click("vault_delete_selected"); click("vault_confirm_delete"); authenticate(); waitTag("vault_empty")
            add("https://all.invented.example", "fake-all", "invented-all")
            click("vault_delete_all")
            assertFalse(requireNotNull(device.wait(Until.findObject(By.res("vault_confirm_all")), TIMEOUT)).isEnabled)
            field("vault_confirmation", "APAGAR TODAS")
            click("vault_confirm_all"); authenticate(); waitTag("vault_empty")
        }
    }

    @Test fun encryptedPersistenceCorruptionAndUnavailableKeyPreserveData() {
        ActivityScenario.launch(PasswordVaultActivity::class.java).use {
            click("vault_unlock"); authenticate(); waitTag("vault_empty")
            check(isolated.mkdirs())
            val wrapper = object : ContextWrapper(context) { override fun getNoBackupFilesDir(): File = isolated }
            val store = BlackHoleNativeVaultStore(wrapper)
            val entry = VaultCredential(UUID.randomUUID().toString(), "https://fake.invented.example", "fake-only", "invented,secret")
            store.saveCredential(entry, isNew = true)
            val file = File(isolated, "black_hole_native_vault/vault.bhv1")
            val firstBytes = file.readBytes()
            assertFalse(String(firstBytes, Charsets.ISO_8859_1).contains("invented,secret"))
            assertEquals(entry.password, BlackHoleNativeVaultStore(wrapper).credential(entry.id).password)
            store.saveCredential(entry, isNew = false)
            assertFalse(firstBytes.contentEquals(file.readBytes()))
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(BlackHoleNativeVaultStore.KEY_ALIAS, null) as javax.crypto.SecretKey
            assertNull(key.encoded)
            val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
            assertEquals(256, info.keySize)
            assertTrue(info.isUserAuthenticationRequired)
            assertEquals(30, info.userAuthenticationValidityDurationSeconds)
            val corrupted = file.readBytes().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            file.writeBytes(corrupted)
            assertTrue(runCatching { store.summaries() }.isFailure)
            assertTrue(runCatching { store.saveCredential(entry, isNew = false) }.isFailure)
            assertTrue(corrupted.contentEquals(file.readBytes()))
            file.writeBytes(firstBytes)
            // Only the key created on this disposable, guarded emulator is removed.
            keyStore.deleteEntry(BlackHoleNativeVaultStore.KEY_ALIAS)
            assertTrue(runCatching { store.summaries() }.isFailure)
            assertTrue(runCatching { store.saveCredential(entry, isNew = false) }.isFailure)
            assertTrue(firstBytes.contentEquals(file.readBytes()))
        }
    }

    @Test fun authenticationCancellationBackgroundRecreationAndInactivityLock() {
        ActivityScenario.launch(PasswordVaultActivity::class.java).use { scenario ->
            click("vault_unlock")
            pinField()
            device.pressBack()
            if (!device.wait(Until.gone(PIN_ENTRY), 1_500)) device.pressBack()
            waitTag("vault_locked")
            assertFalse(device.hasObject(By.res("vault_add")))
            click("vault_unlock"); authenticate(); waitTag("vault_empty")
            scenario.recreate(); waitTag("vault_locked")
            click("vault_unlock"); authenticate(); waitTag("vault_empty")
            device.pressHome()
            waitTagGone("vault_empty")
            context.startActivity(Intent(context, PasswordVaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitTag("vault_locked")
            click("vault_unlock"); authenticate(); waitTag("vault_empty")
            add("https://expiry.invented.example", "fake-expiry", "invented-expiry")
            assertTrue("Absolute/inactivity lock must occur", device.wait(Until.hasObject(By.res("vault_locked")), 35_000))
            assertFalse(device.hasObject(By.res("vault_search")))
            assertTrue("OS Keystore authorization must also expire", runCatching { BlackHoleNativeVaultStore(context).summaries() }.isFailure)
        }
    }

    @Test fun vaultCannotCaptureNormalOrPrivateBrowserStateOrChangeCsvPrototype() {
        val preferencesName = "invented-native-browser-" + UUID.randomUUID()
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val sessions = BrowserSessionStore(preferences)
        val normal = BrowserTab(id = "invented-normal", lastAccessedAt = 1L, url = "https://normal.invented.example")
        val privateTab = BrowserTab(id = "invented-private", lastAccessedAt = 2L,
            url = "https://private.invented.example", isIncognito = true)
        assertTrue(sessions.saveTabsImmediately(listOf(normal, privateTab), privateTab.id))
        assertEquals(listOf("invented-normal"), sessions.loadTabs(3L).first.map { it.id })
        val before = preferences.all.toMap()
        // Explicit manual records are the only vault input; normal/private pages are not captured.
        val cpv = File(context.noBackupFilesDir, "candy_password_vault_v1.bin")
        check(!cpv.exists()) { "Refuse to touch existing CSV prototype" }
        val inventedMarker = "CPV1-invented-preservation-marker".toByteArray()
        cpv.writeBytes(inventedMarker)
        try {
            ActivityScenario.launch(PasswordVaultActivity::class.java).use {
                click("vault_unlock"); authenticate(); waitTag("vault_empty")
                add("https://manual.invented.example", "fake-user", "invented-not-a-tab")
                assertTrue(cpv.readBytes().contentEquals(inventedMarker))
                assertFalse(device.hasObject(By.text("CPV1-invented-preservation-marker")))
                assertEquals(before, preferences.all)
                assertEquals(listOf("invented-normal"), sessions.loadTabs(3L).first.map { it.id })
                val saved = BlackHoleNativeVaultStore(context).summaries()
                assertEquals(1, saved.size)
                assertEquals("https://manual.invented.example", saved.single().site)
            }
        } finally { cpv.delete(); context.deleteSharedPreferences(preferencesName) }
    }

    private fun add(site: String, user: String, password: String) {
        click("vault_add"); authenticate(); waitTag("vault_site")
        field("vault_site", site); field("vault_username", user); field("vault_password", password)
        click("vault_save"); authenticate(); waitText(site)
    }
    private fun field(tag: String, value: String) {
        val node = requireNotNull(device.wait(Until.findObject(By.res(tag).enabled(true)), TIMEOUT))
        // Compose decoration semantics may wrap the actual editable accessibility node.
        val editable = if (node.className == "android.widget.EditText") node else
            node.findObject(By.clazz("android.widget.EditText")) ?: node
        editable.text = value
        instrumentation.waitForIdleSync()
        device.waitForIdle(500)
    }
    private fun click(tag: String) {
        device.waitForIdle(500)
        requireNotNull(device.wait(Until.findObject(By.res(tag).enabled(true)), TIMEOUT)) { "Missing test control $tag" }.click()
    }
    private fun firstTag(prefix: String) = requireNotNull(device.wait(Until.findObject(By.res(Pattern.compile("$prefix.*")).enabled(true)), TIMEOUT))
    private fun waitTag(tag: String) = assertTrue(device.wait(Until.hasObject(By.res(tag)), TIMEOUT))
    private fun waitTagGone(tag: String) = assertTrue(device.wait(Until.gone(By.res(tag)), TIMEOUT))
    private fun waitText(text: String) = assertTrue(device.wait(Until.hasObject(By.text(text)), TIMEOUT))
    private fun pinField(): androidx.test.uiautomator.UiObject2 {
        var node = device.wait(Until.findObject(PIN_ENTRY), TIMEOUT)
        if (node == null) {
            requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i).*use (pin|screen lock|password).*"))), TIMEOUT)).click()
            node = device.wait(Until.findObject(PIN_ENTRY), TIMEOUT)
        }
        return requireNotNull(node) { "Real Android authentication prompt required" }
    }
    private fun authenticate() { pinField().text = "246810"; device.pressEnter() }

    companion object {
        private const val TIMEOUT = 15_000L
        private val PIN_ENTRY = By.res(Pattern.compile(".*:id/(password_entry|lockPassword)"))
    }
}
