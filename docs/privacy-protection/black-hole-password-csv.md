# Black Hole experimental password CSV

This is a recovery of the V4.1 demonstration, **only for fabricated credentials**. It is not
an audited password manager or a secure vault for real passwords. The preceding Black Hole
APK remained fast on the user's POCO; the new CSV recovery still needs its own phone test.

## Ownership

| Concern | Source |
| --- | --- |
| Bounded UTF-8 CSV parser/writer | `data/passwords/BlackHolePasswordCsv.kt` |
| Exact duplicates and other password versions | `data/passwords/PasswordCsvImportRules.kt` |
| Historical CPV1 encrypted storage | `data/passwords/BlackHolePasswordCsvStore.kt` |
| One-use, 30-second export authorization | `data/passwords/PasswordCsvExportAuthorization.kt` |
| Android Files and OS authentication | `src/blackHoleTest/.../passwords/PasswordCsvActivity.kt` |
| SAF-only URI validation | `data/passwords/PasswordCsvDocumentRules.kt` |
| Browser ZIP exclusion | `data/AppDataArchiveRules.kt` |
| JVM tests | `src/test/.../data/passwords`, `PasswordCsvArchiveExclusionTest` |
| Disposable-emulator tests and fake SAF provider | `src/blackHoleAndroidTest/.../passwords` |

## Flow and compatibility

Open Settings → Protection and data → Experimental password CSV. This entry and the activity
are available only in Black Hole. Candy variants have no CSV screen or manifest entry.
The browser engine, navigation, nine launcher aliases, release R8 and test signing are unchanged.
Storage is initialized lazily, and parsing/crypto/file I/O runs outside the UI thread.

Import uses `OpenDocument`, a plaintext warning, a numerical preview and explicit confirmation.
Both picker results must be registered SAF document URIs. File, web, resource and arbitrary
content URIs are rejected before any read, authentication or write to prevent app-private
plaintext destinations or an unintended source outside the document contract.
The report counts rows, invalid rows, Android-only records, inserted entries, exact duplicates,
different password versions and stored entries. A duplicate has the same literal URL, username
and password; changing only the title is also a duplicate. Different passwords for the same
URL/username remain separate entries. There is no overwrite, autofill, automatic page access,
sync or new animation. Android-only records are preserved without being used on web pages.

CSV supports UTF-8/BOM, quoted commas/quotes/newlines, CRLF/LF and the historical header aliases
(URL/website/site/URI/login URI/origin, username/user/login/email/user ID, password/pass/senha,
name/title/site name). Ambiguous aliases, malformed UTF-8 and structurally malformed CSV fail.
Invalid individual rows are skipped and counted. Password whitespace is never trimmed.
Limits: 20,000 data rows, 32 columns, 16,384 characters per cell, 12 MiB decoded characters,
4,096-character URLs/passwords, 1,024-character usernames, and a 16 MiB encrypted store.
Capacity errors preserve the existing encrypted dataset. Export preserves literal fields;
do not open untrusted CSV in spreadsheets, where fields may be interpreted as formulas.

Export first warns, uses `CreateDocument("text/csv")`, then requests `BiometricPrompt` with
strong biometrics or device credentials. A fresh, one-use grant is consumed before decrypting
or opening the destination. There is no pre-picker authorization to reuse. The grant and
pending picker state are not saved across recreation. Preview data is discarded when the
screen stops. A cancelled prompt writes no credentials. On a write failure/cancellation,
the selected partial document is deleted best-effort; failure to delete is reported explicitly.

## Security limitations

* CSV import sources and export destinations are **readable plaintext**. They are user-selected
  documents outside the app's storage protection. A provider can upload to cloud, keep previous
  versions or retain partial files; deletion cannot be guaranteed. The source is never deleted.
* Local persistence uses AES-256-GCM with fresh nonces and the original CPV1 AAD/format and
  historical filename/key alias. Ciphertext stays in `noBackupFilesDir`. The browser ZIP refuses
  this file and its atomic `.bak`/`.new` files on export and import. Android's backup/transfer
  rules do not include `no_backup`. There is no automatic migration or restore of this dataset.
* The Keystore key is installation-local, non-exportable, and **not bound to biometric use**.
  Authentication protects the UI export flow, not a cryptographic vault boundary. Root, a
  compromised process/device or instrumentation privileges are outside this prototype's protection.
* Passwords necessarily exist in heap strings during parsing, merging and serialization. Byte
  arrays are wiped where practical, but immutable strings cannot be reliably zeroed. No claim
  of memory-erasure guarantees, independent security audit or protection for real passwords.
* The screen/dialogs use `FLAG_SECURE`, are not exported, and show only warnings and counts.
  No credentials or raw errors enter UI, saved state, clipboard, logs or plaintext staging files.
  SAF URI permissions are transient; no persisted file grant or URI is stored by this feature.

## Verification

The current workflow builds both normal variants and the R8-optimized test APK. The emulator
job retains the existing three branding cases and adds three CSV cases with independent
completion checks. The test-only provider exercises real Android Files and document grants
with invented data. The fresh emulator receives fabricated PIN `246810` to test the actual OS
device-credential path. A test-only activity monitor adds Android's `EXTRA_INITIAL_URI` to
the outgoing picker intents so they open the invented provider directly; it never supplies
an activity result or substitutes Android's document grants. This avoids selecting the wrong
sidebar row while system providers are rediscovered/reordered during emulator boot. The suite
exercises cancellation and successful authorization. Hardware fingerprint
authentication still requires a phone test. The script refuses physical-device serials, and
instrumentation refuses non-emulator hardware or an existing prototype dataset.

```sh
./gradlew --no-daemon --max-workers=1 -Pcandy.androidTestBuildType=blackHoleTest \
  :app:assembleFullBlackHoleTest :app:assembleFullBlackHoleTestAndroidTest
ANDROID_SERIAL=emulator-5554 bash scripts/run_black_hole_instrumentation.sh
```

Only run this on a new, dedicated disposable emulator. It configures and removes a test lock PIN.
Instrumentation compatibility rules remain confined to that build; the downloadable ARM64 APK
uses the ordinary release optimization rules. CSV tests and the fake provider are absent from it.
No Actions step decodes or applies any historical Base64 patch.
