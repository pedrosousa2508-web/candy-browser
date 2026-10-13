#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Set the serial of the dedicated emulator}"
[[ "$ANDROID_SERIAL" == emulator-* ]] || { echo 'Disposable emulator required for invented CSV fixtures'; exit 1; }
csv_pin_configured=false
report_dir=app/build/outputs/androidTest-results/black-hole-direct
mkdir -p "$report_dir"
capture_logs() {
  status=$?
  adb -s "$ANDROID_SERIAL" logcat -d > "$report_dir/logcat.txt" 2>&1 || true
  if [[ "$csv_pin_configured" == true ]]; then
    adb -s "$ANDROID_SERIAL" shell locksettings clear --old 246810 >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap capture_logs EXIT
adb -s "$ANDROID_SERIAL" wait-for-device
adb -s "$ANDROID_SERIAL" logcat -c
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/full/blackHoleTest/app-full-blackHoleTest.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/full/blackHoleTest/app-full-blackHoleTest-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class dev.sk2andy.materialbrowser.data.BlackHoleBrandingInstrumentedTest \
  dev.sk2andy.materialbrowser.blackholedev.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$report_dir/instrumentation.txt"
python3 scripts/verify_black_hole_instrumentation.py \
  "$report_dir/instrumentation.txt" "$report_dir/TEST-branding.xml"

# Fabricated lock PIN, exclusively on this disposable emulator. No production secrets.
adb -s "$ANDROID_SERIAL" shell locksettings set-pin 246810
csv_pin_configured=true
adb -s "$ANDROID_SERIAL" shell settings put global stay_on_while_plugged_in 3
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class dev.sk2andy.materialbrowser.passwords.PasswordCsvInstrumentedTest \
  dev.sk2andy.materialbrowser.blackholedev.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$report_dir/csv-instrumentation.txt"
python3 scripts/verify_black_hole_instrumentation.py \
  "$report_dir/csv-instrumentation.txt" "$report_dir/TEST-csv.xml" csv
