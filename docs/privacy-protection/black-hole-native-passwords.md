# Black Hole native password manager (experimental)

The initial native screen is intentionally Portuguese-only; its strings are explicitly non-translatable for this stage. Only `fullBlackHoleTest` includes the activity, Compose screen and Android store. No browser startup creates the store or key. Opening settings only supplies callbacks. The native manager is independent of normal/private browsing profiles and has no page capture, autofill, browser history/cookie access or network interface.

## Architecture and storage

`PasswordVaultRules` validates bounded HTTP(S) site, username and password fields, stable identifiers, explicit CRUD and search. Different passwords for the same user/site remain separate manually created records. Debug representations redact all fields.

`BlackHoleNativeVaultStore` uses AES-256-GCM with a fresh 12-byte nonce, 128-bit tag and authenticated BHV1 format header. Its Android Keystore key requires OS strong-biometric or device-credential authentication, with a 30-second authorization window. The application does not supply its own password/PIN. Keys are non-exportable; hardware-backed storage is device-dependent, not guaranteed. The separate alias is `black_hole_native_vault_v1_auth`. The file is `no_backup/black_hole_native_vault/vault.bhv1`. Android backups omit no-backup files; application data ZIP import/export rejects the entire vault directory and atomic files.

Writes are serialized and atomic. Failed authentication, invalid format, oversized data, corrupted GCM tag or missing key fail closed; they never replace existing ciphertext with an empty store or regenerate the key for existing data. Explicit delete operations save a new encrypted dataset after confirmation; delete-all also checks the exact expected identifier set and the phrase `APAGAR TODAS`. Uninstalling or resetting the device lock can make data unrecoverable. No recovery/migration/export endpoint is offered for this new store.

## Authentication and lifecycle

Unlock, manual add, read/edit, save, deletion and full deletion require fresh OS confirmation. The list retains only metadata; password strings are returned only for an authenticated editor. Pausing/backgrounding clears list/editor state, including when Android opens its credential activity. Only a successful pending OS authentication can re-open the foreground session. Recreation never restores credentials. The timeout coroutine runs only while the activity is resumed. Absolute session lifetime and inactivity are bounded to 30 seconds; users must unlock again. `FLAG_SECURE` covers the activity and inherited Compose dialogs. Autofill is disabled on the view hierarchy. No sensitive input uses saved instance state or persistent URI grants.

## Security limitations and review requirements

Use invented credentials only. This is not yet a reviewed vault for real passwords. Java/Kotlin strings, JSON parsing, Compose input and the OS keyboard cannot guarantee heap erasure; temporary UTF-8 byte arrays are cleared, but brief decrypted datasets and strings can remain until garbage collection. Keystore has a time-based 30-second authorization window, not per-operation cryptographic tokens. A compromised application process, rooted OS or malicious accessibility/keyboard can expose data; screenshot protection is not a defense against those attacks. The implementation lacks independent cryptographic review, rollback protection, crash/power-loss fault injection, device/biometric enrollment matrix and a verified recovery design. Files and keys are never silently deleted to recover from corruption. Explicit record deletion does not promise forensic erasure of prior ciphertext from flash storage.

## CSV integration plan and CPV1 assessment

The existing V4.1 CPV1 store keeps its format, filename and key unchanged. Its key is not OS-authentication-bound; UI export authorization is a one-use gate, not equivalent to cryptographic key protection. It must remain a fabricated-credential prototype. The app-data import/export CSV actions still operate only on CPV1. Native manager deletion does not affect CPV1, nor does CSV import populate the native manager.

After independent security review, a separate explicit migration could preview counts, authenticate both operations, retain the untouched CPV1 source and encrypted rollback snapshot, stage/verify a new vault, then request confirmation to commit. Failure must leave both original and destination intact. CSV import can then reuse the bounded parser and exact-duplicate rules (site/user/password), preserve different password versions, and return counts. Export must require fresh authentication before opening/decrypting and warn about readable passwords, spreadsheets and partial file cleanup. No migration is implemented or initiated in this task.

## Validation

JVM policy tests cover CRUD, search, multiple/full deletion, identifiers, time limits, redaction and archive exclusions. Dedicated emulator tests use only fabricated credentials and an invented OS PIN; they must cover real authentication, Compose operations, background/inactivity lock, ciphertext persistence, nonce changes, corruption and missing-key preservation. Existing branding and real SAF CSV tests remain mandatory. Instrumentation transcripts must contain every expected completed test; runner exit or zero tests never counts as success. API 35 emulator testing does not establish compatibility with every device or performance on the POCO.
