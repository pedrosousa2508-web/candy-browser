#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Set the serial of the dedicated emulator}"
report_dir=app/build/outputs/androidTest-results/black-hole-direct
mkdir -p "$report_dir"
adb -s "$ANDROID_SERIAL" wait-for-device
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/full/blackHoleTest/app-full-blackHoleTest.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/full/blackHoleTest/app-full-blackHoleTest-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class dev.sk2andy.materialbrowser.data.BlackHoleBrandingInstrumentedTest \
  dev.sk2andy.materialbrowser.blackholedev.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$report_dir/instrumentation.txt"
python3 scripts/verify_black_hole_instrumentation.py \
  "$report_dir/instrumentation.txt" "$report_dir/TEST-branding.xml"
