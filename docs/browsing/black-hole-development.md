# Black Hole development build

The `black-hole-dev` branch introduces an optimized, test-signed Android build based on the
Black Hole V4.1 experiment. This first step versions the build configuration; it does not yet
port the experiment's branding, UI or CSV prototype patches. Browser behavior and mandatory
upstream attribution remain unchanged.

## Build contract

| Concern | Contract |
| --- | --- |
| Variant | `fullBlackHoleTest`; other distribution flavors are disabled for this build type |
| Optimization | Inherits `release` minification and `proguard-android-optimize.txt` plus existing keep rules |
| Debugging | `isDebuggable=false`; a debug signing key does not enable debug runtime behavior |
| Signing | Android debug signing configuration only; no production signing credentials needed |
| Package | `dev.sk2andy.materialbrowser.blackholedev`, independent from production and previous experimental installs |
| Label/version | `Black Hole Dev (TESTE)` / `-blackhole-test` suffix |
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
