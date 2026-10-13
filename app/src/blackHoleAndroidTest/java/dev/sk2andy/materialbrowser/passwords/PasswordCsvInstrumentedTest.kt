package dev.sk2andy.materialbrowser.passwords

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.StaleObjectException
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppDataArchiveRules
import dev.sk2andy.materialbrowser.data.passwords.BlackHolePasswordCsv
import dev.sk2andy.materialbrowser.data.passwords.BlackHolePasswordCsvStore
import dev.sk2andy.materialbrowser.data.passwords.PasswordCsvDocumentRules
import dev.sk2andy.materialbrowser.data.passwords.PasswordCsvExportAuthorization
import dev.sk2andy.materialbrowser.data.passwords.PasswordImportResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordCsvInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val isolated = File(context.noBackupFilesDir, "invented-csv-store-test")
    private var createdProductionFixture = false
    private var pickerMonitor: Instrumentation.ActivityMonitor? = null

    @Before
    fun requireDisposableEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator required" }
        check(listOf("", ".bak", ".new").none {
            File(context.noBackupFilesDir, BlackHolePasswordCsvStore.FILE_NAME + it).exists()
        }) {
            "Refuse to alter an existing prototype dataset"
        }
    }

    @After
    fun removeInventedFixtures() {
        pickerMonitor?.let(instrumentation::removeMonitor)
        isolated.deleteRecursively()
        if (createdProductionFixture) {
            for (suffix in listOf("", ".bak", ".new")) {
                File(context.noBackupFilesDir, BlackHolePasswordCsvStore.FILE_NAME + suffix).delete()
            }
        }
    }

    @Test
    fun encryptedStorePreservesDuplicatesAndFailsClosedOnCorruption() {
        assertFalse(PasswordCsvDocumentRules.isSafDocument(context, Uri.fromFile(File(context.filesDir, "invented.csv"))))
        assertFalse(PasswordCsvDocumentRules.isSafDocument(context, Uri.parse("https://example.org/invented.csv")))
        assertFalse(PasswordCsvDocumentRules.isSafDocument(context, Uri.parse("content://invalid.example/raw/invented")))
        check(isolated.mkdirs())
        val isolatedContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = isolated
        }
        val store = BlackHolePasswordCsvStore(isolatedContext)
        val preview = BlackHolePasswordCsv.parse(CsvTestDocumentsProvider.INVENTED_CSV.byteInputStream())
        assertEquals(PasswordImportResult(3, 1, 1, 3), store.import(preview))
        assertEquals(PasswordImportResult(0, 4, 0, 3), store.import(preview))
        val target = File(isolated, BlackHolePasswordCsvStore.FILE_NAME)
        val encrypted = target.readBytes()
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("fake-password-B"))
        assertFalse(AppDataArchiveRules.shouldExportRelativePath("no_backup/${target.name}"))

        var opened = false
        val expired = PasswordCsvExportAuthorization(0)
        assertTrue(runCatching { store.exportCsv(expired, { 30_001 }) {
            opened = true
            ByteArrayOutputStream()
        } }.isFailure)
        assertFalse(opened)

        val output = ByteArrayOutputStream()
        val authorization = PasswordCsvExportAuthorization(SystemClock.elapsedRealtime())
        assertEquals(3, store.exportCsv(authorization, SystemClock::elapsedRealtime) { output })
        val exported = BlackHolePasswordCsv.parse(output.toByteArray().inputStream())
        assertEquals(3, exported.entries.size)
        assertEquals(" fake,\"A\"  ", exported.entries[0].password)
        assertTrue(runCatching { store.exportCsv(authorization, SystemClock::elapsedRealtime) {
            error("A consumed grant must not open a destination")
        } }.isFailure)

        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        target.writeBytes(encrypted)
        assertTrue(runCatching { store.import(preview) }.isFailure)
        assertTrue(encrypted.contentEquals(target.readBytes()))
    }

    @Test
    fun safImportAndAuthenticatedExportRoundTrip() {
        createdProductionFixture = true
        pickerMonitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action in setOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT)) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildRootUri(CsvTestDocumentsProvider.AUTHORITY, "csv-tests"))
                }
                return null // Launch the real system picker, never stub its result or URI grants.
            }
        }.also(instrumentation::addMonitor)
        ActivityScenario.launch(PasswordCsvActivity::class.java).use {
            clickApp(R.id.csv_import)
            click(By.res("android", "button1"))
            waitForTestRoot()
            clickText("invented-input.csv")
            waitTextContains("Rows: 5")
            click(By.res("android", "button1"))
            waitStatus("Added: 3")
            waitStatus("Exact duplicates: 1")
            waitStatus("Other password versions preserved: 1")

            chooseExportDestination()
            waitDeviceCredential()
            device.pressBack()
            if (!device.wait(Until.gone(PIN_ENTRY), 1_500)) {
                device.pressBack() // Some PIN screens first dismiss the input keyboard.
            }
            waitStatus(context.getString(R.string.csv_demo_cancelled))
            val cancelled = DocumentsContract.buildDocumentUri(CsvTestDocumentsProvider.AUTHORITY, "export-1")
            assertEquals(0, context.contentResolver.openInputStream(cancelled)!!.use { it.readBytes().size })

            chooseExportDestination()
            val pin = waitDeviceCredential()
            pin.text = "246810" // Fabricated lock PIN configured only on the disposable CI emulator.
            device.pressEnter()
            waitStatus("Exported 3")
            val exportedUri = DocumentsContract.buildDocumentUri(CsvTestDocumentsProvider.AUTHORITY, "export-2")
            assertTrue(PasswordCsvDocumentRules.isSafDocument(context, exportedUri))
            val exported = context.contentResolver.openInputStream(exportedUri)!!.use(BlackHolePasswordCsv::parse)
            assertEquals(3, exported.entries.size)
            assertEquals(" fake,\"A\"  ", exported.entries[0].password)
            assertEquals(1, exported.appOnlyRows)
            assertTrue(DocumentsContract.deleteDocument(context.contentResolver, cancelled))
            assertTrue(DocumentsContract.deleteDocument(context.contentResolver, exportedUri))
        }
    }

    @Test
    fun sensitiveScreenIsPrivateSecureAndRecreationDoesNotRestoreCredentials() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, PasswordCsvActivity::class.java), 0)
        assertFalse(info.exported)
        ActivityScenario.launch(PasswordCsvActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertTrue(activity.getString(R.string.csv_demo_warning).contains("NOT a secure vault"))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertEquals("", activity.findViewById<android.widget.TextView>(R.id.csv_status).text.toString())
            }
        }
    }

    @Test
    fun directCsvActionsReuseWarningsWithoutRestoringTransfers() {
        for (action in listOf("blackhole.csv.IMPORT", "blackhole.csv.EXPORT")) {
            val intent = Intent(context, PasswordCsvActivity::class.java).setAction(action)
            ActivityScenario.launch<PasswordCsvActivity>(intent).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.res("android", "button1")), 10_000))
                // The existing confirmation opens before any document or authentication flow.
                click(By.res("android", "button2"))
                scenario.recreate()
                assertFalse(device.hasObject(By.res("android", "button1")))
                scenario.onActivity { assertEquals(action, it.intent.action) }
            }
        }
    }

    private fun chooseExportDestination() {
        clickApp(R.id.csv_export)
        click(By.res("android", "button1"))
        waitForTestRoot()
        // Android 35 DocumentsUI fragment_save.xml uses the framework button1 ID.
        click(By.res("android", "button1"))
    }

    private fun waitForTestRoot() = assertTrue("System picker must open the invented document root",
        device.wait(Until.hasObject(By.text(CsvTestDocumentsProvider.ROOT_TITLE)), TIMEOUT))

    private fun waitDeviceCredential(): UiObject2 {
        var pin = device.wait(Until.findObject(PIN_ENTRY), TIMEOUT)
        if (pin == null) {
            click(By.text(Pattern.compile("(?i).*use (pin|screen lock|password).*")))
            pin = device.wait(Until.findObject(PIN_ENTRY), TIMEOUT)
        }
        assertNotNull("Android device credential prompt must be shown", pin)
        return requireNotNull(pin)
    }

    private fun clickApp(id: Int) = click(By.res(context.resources.getResourceName(id)))
    private fun clickText(text: String) = click(By.text(text))
    private fun click(selector: androidx.test.uiautomator.BySelector) {
        repeat(3) { attempt ->
            device.waitForIdle(1_000)
            val control = device.wait(Until.findObject(selector), TIMEOUT)
            if (control == null) logControlIds()
            assertNotNull("Expected test control", control)
            try {
                requireNotNull(control).click()
                return
            } catch (stale: StaleObjectException) {
                if (attempt == 2) throw stale
            }
        }
    }

    private fun waitTextContains(text: String) =
        assertTrue("Expected numeric import preview", device.wait(Until.hasObject(By.textContains(text)), TIMEOUT))

    private fun waitStatus(text: String) = assertTrue("Expected transfer report",
        device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.csv_status)).textContains(text)), TIMEOUT))

    private fun logControlIds() {
        // Test diagnostics only. Never collect node text, descriptions, screenshots or credential values.
        var count = 0
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 12 || count++ >= 80) return
            println("CSV control: id=${node.viewIdResourceName}, class=${node.className}, " +
                "visible=${node.isVisibleToUser}, enabled=${node.isEnabled}, clickable=${node.isClickable}")
            for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow, 0)
    }

    private companion object {
        const val TIMEOUT = 15_000L
        // Android 35 BiometricPrompt embeds SystemUI's lockPassword; older fallback uses Settings.
        val PIN_ENTRY = By.res(Pattern.compile(".*:id/(password_entry|lockPassword)"))
    }
}
