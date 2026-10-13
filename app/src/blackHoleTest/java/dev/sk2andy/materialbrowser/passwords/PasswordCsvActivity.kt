package dev.sk2andy.materialbrowser.passwords

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.passwords.BlackHolePasswordCsv
import dev.sk2andy.materialbrowser.data.passwords.BlackHolePasswordCsvStore
import dev.sk2andy.materialbrowser.data.passwords.PasswordCsvDocumentRules
import dev.sk2andy.materialbrowser.data.passwords.PasswordCsvExportAuthorization
import dev.sk2andy.materialbrowser.data.passwords.PasswordCsvPreview
import java.io.FilterOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Isolated experimental screen. Never renders credentials, persists URI grants, or saves a preview. */
class PasswordCsvActivity : AppCompatActivity() {
    private enum class Picker { Import, Export }

    private val store by lazy { BlackHolePasswordCsvStore(applicationContext) }
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private lateinit var status: TextView
    private var generation = 0L
    private var pendingPicker: Picker? = null
    private var preview: PasswordCsvPreview? = null
    private var previewDialog: android.app.AlertDialog? = null
    private var authentication: BiometricPrompt? = null

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (pendingPicker != Picker.Import) return@registerForActivityResult
        pendingPicker = null
        when {
            uri == null -> reset(R.string.csv_demo_cancelled)
            !PasswordCsvDocumentRules.isSafDocument(this, uri) -> reset(R.string.csv_demo_failed)
            else -> readImport(uri, generation)
        }
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (pendingPicker != Picker.Export) return@registerForActivityResult
        pendingPicker = null
        when {
            uri == null -> reset(R.string.csv_demo_cancelled)
            !PasswordCsvDocumentRules.isSafDocument(this, uri) -> reset(R.string.csv_demo_failed)
            else -> authenticateExport(uri, generation)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        column.addView(TextView(this).apply { setText(R.string.csv_demo_title); textSize = 22f })
        column.addView(TextView(this).apply { setText(R.string.csv_demo_warning); textSize = 16f })
        importButton = Button(this).apply {
            id = R.id.csv_import
            setText(R.string.csv_demo_import)
            setOnClickListener { warnBeforePicker(Picker.Import) }
        }
        exportButton = Button(this).apply {
            id = R.id.csv_export
            setText(R.string.csv_demo_export)
            setOnClickListener { warnBeforePicker(Picker.Export) }
        }
        status = TextView(this).apply { id = R.id.csv_status; isSaveEnabled = false }
        column.addView(importButton)
        column.addView(exportButton)
        column.addView(status)
        column.addView(Button(this).apply {
            setText(android.R.string.cancel)
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun reset(message: Int? = null) {
        generation++
        pendingPicker = null
        preview = null
        importButton.isEnabled = true
        exportButton.isEnabled = true
        if (message != null) status.setText(message)
    }

    private fun current(operation: Long): Boolean = operation == generation && !isFinishing && !isDestroyed

    private fun warnBeforePicker(picker: Picker) {
        generation++
        importButton.isEnabled = false
        exportButton.isEnabled = false
        android.app.AlertDialog.Builder(this)
            .setTitle(if (picker == Picker.Import) R.string.csv_demo_import else R.string.csv_demo_export)
            .setMessage(if (picker == Picker.Import) R.string.csv_demo_import_warning else R.string.csv_demo_export_warning)
            .setNegativeButton(android.R.string.cancel) { _, _ -> reset(R.string.csv_demo_cancelled) }
            .setOnCancelListener { reset(R.string.csv_demo_cancelled) }
            .setPositiveButton(R.string.csv_demo_continue) { _, _ ->
                pendingPicker = picker
                try {
                    if (picker == Picker.Import) importPicker.launch(arrayOf("*/*"))
                    else exportPicker.launch("black-hole-test-passwords.csv")
                } catch (_: Exception) {
                    reset(R.string.csv_demo_failed)
                }
            }
            .show().window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun readImport(uri: Uri, operation: Long) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use(BlackHolePasswordCsv::parse)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            if (!current(operation)) return@launch
            if (parsed == null || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                reset(R.string.csv_demo_failed)
                return@launch
            }
            preview = parsed
            previewDialog = android.app.AlertDialog.Builder(this@PasswordCsvActivity)
                .setTitle(R.string.csv_demo_import)
                .setMessage(getString(R.string.csv_demo_preview, parsed.totalRows, parsed.entries.size,
                    parsed.invalidRows, parsed.appOnlyRows))
                .setNegativeButton(android.R.string.cancel) { _, _ -> reset(R.string.csv_demo_cancelled) }
                .setOnCancelListener { reset(R.string.csv_demo_cancelled) }
                .setPositiveButton(R.string.csv_demo_import) { _, _ -> commitImport(operation) }
                .show().also { it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }

    private fun commitImport(operation: Long) {
        val selected = preview ?: return
        preview = null
        previewDialog = null
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                try {
                    store.import(selected)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            if (!current(operation)) return@launch
            if (report == null) reset(R.string.csv_demo_failed) else {
                status.text = getString(R.string.csv_demo_report, report.inserted, report.exactDuplicates,
                    report.conflictingVersions, report.totalStored)
                reset()
            }
        }
    }

    private fun authenticateExport(uri: Uri, operation: Long) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            reset(R.string.csv_demo_auth_unavailable)
            return
        }
        var used = false
        authentication = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (used) return
                used = true
                if (!current(operation) || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                val authorization = PasswordCsvExportAuthorization(SystemClock.elapsedRealtime())
                writeExport(uri, operation, authorization)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                used = true
                if (current(operation)) reset(R.string.csv_demo_cancelled)
            }
        })
        authentication?.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.csv_demo_auth_title))
            .setSubtitle(getString(R.string.csv_demo_auth_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build())
    }

    private fun writeExport(uri: Uri, operation: Long, authorization: PasswordCsvExportAuthorization) {
        lifecycleScope.launch {
            var cleanupFailed = false
            val count = withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                try {
                    store.exportCsv(authorization, SystemClock::elapsedRealtime) {
                        val output = contentResolver.openOutputStream(uri, "wt") ?: error("No writable destination")
                        object : FilterOutputStream(output) {
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                job.ensureActive()
                                out.write(bytes, offset, length)
                            }
                            override fun write(value: Int) {
                                job.ensureActive()
                                out.write(value)
                            }
                        }
                    }
                } catch (failure: Exception) {
                    cleanupFailed = !runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                        .getOrDefault(false)
                    if (failure is CancellationException) throw failure
                    null
                }
            }
            if (!current(operation)) return@launch
            if (count == null) reset(if (cleanupFailed) R.string.csv_demo_cleanup_failed else R.string.csv_demo_failed)
            else {
                status.text = getString(R.string.csv_demo_exported, count)
                reset()
            }
        }
    }

    override fun onStop() {
        // A preview cannot outlive this visible screen, enter saved state, or become a stale import.
        if (preview != null) {
            preview = null
            previewDialog?.dismiss()
            previewDialog = null
            reset(R.string.csv_demo_cancelled)
        }
        super.onStop()
    }

    override fun onDestroy() {
        generation++
        preview = null
        previewDialog?.dismiss()
        authentication?.cancelAuthentication()
        super.onDestroy()
    }
}
