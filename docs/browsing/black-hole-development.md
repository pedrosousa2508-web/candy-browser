# Black Hole development build

The `black-hole-dev` branch provides an optimized, test-signed Android build based on the
Black Hole V4.1 experiment. Branding and original icon artwork now live in versioned
`app/src/blackHoleTest/res` overlays. Browser behavior and mandatory upstream attribution remain
unchanged; no CSV, vault or new autofill feature is included.

## Build contract

| Concern | Contract |
| --- | --- |
| Variant | `fullBlackHoleTest`; other distribution flavors are disabled for this build type |
| Optimization | Inherits `release` minification and `proguard-android-optimize.txt` plus existing keep rules |
| Debugging | `isDebuggable=false`; a debug signing key does not enable debug runtime behavior |
| Signing | Android debug signing configuration only; no production signing credentials needed |
| Package | `dev.sk2andy.materialbrowser.blackholedev`, independent from production and previous experimental installs |
| Label/version | `Black Hole Browser` / `-blackhole-test` suffix |
| Updates | GitHub application updates disabled |
| ABI | CI uses `-Pcandy.releaseAbi=arm64-v8a`, as in V4.1; omit locally for other supported devices |
| Production | Existing release/localRelease signing checks remain required; no `-x validateReleaseSigning` |

Use JDK 17 and Android SDK 37.1, following the root README. From the repository root:

```sh
./gradlew --no-daemon --max-workers=2 -Pcandy.releaseAbi=arm64-v8a \
  :app:testFullDebugUnitTest :app:testFossDebugUnitTest \
  :app:lintFullDebug :app:lintFossDebug \
  :app:assembleFullDebug :app:assembleFossDebug \
  :app:lintFullBlackHoleTest :app:assembleFullBlackHoleTest
```

APK: `app/build/outputs/apk/full/blackHoleTest/app-full-blackHoleTest.apk`.
R8 mapping: `app/build/outputs/mapping/fullBlackHoleTest/mapping.txt`.
The new workflow compiles checked-in source directly, without Base64 payloads, Python source
rewrites or signing-task exclusions. Existing V4.1 workflows remain as historical references.
It runs only on `black-hole-dev` and uploads short-lived test artifacts, without publishing releases.

## Validation and installation limits

The nine visible icon choices are Classic, Neon, Minimal, Dark, Cosmic, Luxury, Blue, Red Shift
and Solar Flare. Existing technical launcher aliases remain stable. The old Jelly Beans alias
resolves to Classic artwork as an upgrade compatibility alias and is not shown as a tenth choice;
selecting any of the nine choices disables all other aliases. No preferences or user databases
are migrated or cleared. Candy variants retain their ten original choices, resources and palettes.

BlackHoleTest uses the recovered violet/cyan palette for the Dynamic/Candy palette choices,
respecting the saved light/dark/AMOLED mode and preserving Neutral. It does not rewrite appearance
preferences. Its splash is static, with the Classic logo, name and `Browse Beyond` tagline.
Existing onboarding gestures and private-mode transitions remain intact. No continuous animation,
particles, bitmap processing or browser-session changes are introduced.

Resources can be refreshed explicitly with `python3 scripts/generate_black_hole_branding.py`.
This deterministic resource generator is not a source patch and does not decode archives or
modify Kotlin during builds. CI runs its five regression tests and checks that regeneration leaves
the checked-in overlays unchanged. Legal copy, `Candy Rules v1` protocol labels and references to
the separately distributed Candy Sync extension preserve their upstream identity.

The workflow also tests the optimized app on its own API 35 x86_64 emulator. It builds without the
ARM64-only flag for that job; the downloadable artifact is still produced by the ARM64 build job.
The instrumented suite verifies localized identity/credits, all nine aliases, persisted selection,
one enabled launcher and recovery from the compatibility alias. To run it on a dedicated device:

```sh
./gradlew --no-daemon --max-workers=1 -Pcandy.androidTestBuildType=blackHoleTest \
  :app:assembleFullBlackHoleTest :app:assembleFullBlackHoleTestAndroidTest
ANDROID_SERIAL=emulator-5554 bash scripts/run_black_hole_instrumentation.sh
```

The default instrumentation build type remains `debug`. All `adb` commands must explicitly use
the same serial. These emulator checks do not validate performance on a POCO phone.
The optimized instrumentation sources live in `app/src/blackHoleAndroidTest`, independently of
the existing Candy suite, whose fixtures depend on debug-only Activities. Those fixtures are not
added to the optimized app or disabled in the original debug suite.
The runner invokes AndroidJUnitRunner directly and preserves its raw output. The verification
script requires successful completion records for each of the three named tests and normal
instrumentation completion; zero-test, partial, skipped or failed runs fail CI. JUnit reports are
emitted only from those verified completion records. This avoids treating AGP's empty connected-test
report as readiness evidence.
The instrumentation APK's reflective JUnit/framework classes are preserved with test-only
ProGuard directives. Application minification/optimization remains enabled with the original
application rules. Raw instrumentation output and logcat are retained for process-crash diagnosis.
Run `38358157390` crashed before discovery because AndroidJUnitRunner could not resolve
`androidx.tracing.Trace`, a shared dependency eliminated by application R8. The explicit
`candy.androidTestBuildType=blackHoleTest` mode keeps that class and its entry points in the
instrumented app only. It also preserves the icon-selection APIs and specific Kotlin helper
methods called from the separate test APK, which application R8 otherwise inlines or removes.
R8 remains enabled; the downloadable ARM64 build does not load these instrumentation
compatibility rules.

The debug key is local to the machine/CI runner. APKs from different runners may not update an
existing install signed by another key. Do not replace a production app or delete its data to test.
The separate package does not migrate previous profiles or experimental vaults.
Use fabricated accounts only. Never publish this test-signed APK or use real passwords.

The current Gradle configuration exposes JVM unit tests for debug variants; this workflow runs
Full and FOSS debug unit tests. Those tests do not execute the installed R8-processed APK. Before claiming
performance parity, smoke-test that APK on a dedicated device and compare the V4.1 baseline using
the same engine, sites, tab count, settings and equivalent installation/cache state. Measure cold
and warm startup, frame timing, memory, tab restoration, background return, media and extensions.
Test private-mode isolation and keep all existing license/attribution notices. Enable performance
diagnostics only for measurement builds, not as a default optimization.
