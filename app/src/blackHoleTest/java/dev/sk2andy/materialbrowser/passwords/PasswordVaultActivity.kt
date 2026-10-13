package dev.sk2andy.materialbrowser.passwords

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.passwords.BlackHoleNativeVaultStore
import dev.sk2andy.materialbrowser.data.passwords.PasswordVaultRules
import dev.sk2andy.materialbrowser.data.passwords.VaultCredential
import dev.sk2andy.materialbrowser.data.passwords.VaultSummary
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** No browser state, profile access, automatic capture, networking or CSV migration. */
class PasswordVaultActivity : AppCompatActivity() {
    private val store by lazy { BlackHoleNativeVaultStore(applicationContext) }
    private var entries by mutableStateOf<List<VaultSummary>>(emptyList())
    private var editor by mutableStateOf<VaultCredential?>(null)
    private var isNew by mutableStateOf(false)
    private var unlocked by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<Int?>(null)
    private var authenticatedAt: Long? = null
    private var lastInteraction = 0L
    private var generation = 0L
    private var authentication: BiometricPrompt? = null
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent {
            CandyTheme {
                PasswordVaultScreen(
                    unlocked = unlocked, busy = busy, entries = entries, editor = editor, message = message,
                    onUnlock = { authenticate { loadList() } },
                    onLock = ::lock,
                    onAdd = { authenticate { operation { entries = store.summaries(); isNew = true; editor = VaultCredential(UUID.randomUUID().toString(), "https://", "", "") } } },
                    onView = { id -> authenticate { operation { entries = store.summaries(); editor = store.credential(id); isNew = false } } },
                    onEdit = { editor = it },
                    onCancelEdit = { editor = null },
                    onSave = {
                        val selected = editor ?: return@PasswordVaultScreen
                        if (runCatching { PasswordVaultRules.validate(selected) }.isFailure) {
                            message = R.string.vault_invalid
                        } else {
                            val newEntry = isNew
                            authenticate { operation { store.saveCredential(selected, newEntry); editor = null; entries = store.summaries() } }
                        }
                    },
                    onDelete = { ids -> authenticate { operation { store.delete(ids); editor = null; entries = store.summaries() } } },
                    onDeleteAll = { confirmation ->
                        val ids = entries.map { it.id }.toSet()
                        authenticate { operation { store.deleteAll(ids, confirmation); editor = null; entries = store.summaries() } }
                    },
                    onClose = { finish() },
                )
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(1_000)
                    if (unlocked && !PasswordVaultRules.active(authenticatedAt, lastInteraction, SystemClock.elapsedRealtime())) lock()
                }
            }
        }
    }

    private fun lock() {
        generation++
        unlocked = false
        authenticatedAt = null
        entries = emptyList()
        editor = null
        message = null
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) lastInteraction = SystemClock.elapsedRealtime()
        return super.dispatchTouchEvent(event)
    }

    private fun authenticate(action: () -> Unit) {
        if (busy || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            lock()
            message = R.string.vault_auth_unavailable
            return
        }
        busy = true
        pendingAction = action
        authentication = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val confirmed = pendingAction ?: return
                pendingAction = null
                val confirmedAt = SystemClock.elapsedRealtime()
                lifecycleScope.launch {
                    lifecycle.withResumed {
                        if (SystemClock.elapsedRealtime() - confirmedAt >= PasswordVaultRules.SESSION_MILLIS) {
                            busy = false
                            lock()
                            return@withResumed
                        }
                        authenticatedAt = confirmedAt
                        lastInteraction = requireNotNull(authenticatedAt)
                        unlocked = true
                        busy = false
                        message = null
                        confirmed()
                    }
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                pendingAction = null
                busy = false
                lock()
                message = R.string.vault_auth_cancelled
            }
        })
        authentication?.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.vault_auth_title))
            .setSubtitle(getString(R.string.vault_auth_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build())
    }

    private fun loadList() = operation { entries = store.summaries() }

    /** IO returns a result for the current foreground epoch; never publishes a stale credential. */
    private fun operation(block: suspend VaultOperation.() -> Unit) {
        val epoch = generation
        busy = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { VaultOperation().apply { block() } }
                if (epoch == generation && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    PasswordVaultRules.active(authenticatedAt, lastInteraction, SystemClock.elapsedRealtime())) {
                    result.entries?.let { entries = it }
                    if (result.changeEditor) editor = result.editor
                    result.isNew?.let { isNew = it }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                lock()
                message = R.string.vault_failed
            } finally {
                busy = false
            }
        }
    }

    private class VaultOperation {
        var entries: List<VaultSummary>? = null
        var changeEditor = false
        var editor: VaultCredential? = null
            set(value) { field = value; changeEditor = true }
        var isNew: Boolean? = null
    }

    override fun onPause() {
        lock() // Also cover loss of foreground in multi-window mode.
        super.onPause()
    }

    override fun onStop() {
        // Credential confirmation may take us to Android's lock screen: data is cleared now,
        // and can reappear only after that pending OS authentication succeeds in the foreground.
        lock()
        super.onStop()
    }

    override fun onDestroy() {
        pendingAction = null
        authentication?.cancelAuthentication()
        lock()
        super.onDestroy()
    }
}
