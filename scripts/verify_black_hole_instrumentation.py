#!/usr/bin/env python3
"""Validate actual AndroidJUnitRunner completion records; reject empty or partial runs."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

CLASS = "dev.sk2andy.materialbrowser.data.BlackHoleBrandingInstrumentedTest"
EXPECTED = {
    "identityAndAttributionResolveInEnglishAndPortuguese",
    "eachOfNineIconsEnablesExactlyOneLauncherAndPersistsSelection",
    "legacyJellyBeansAliasRemainsLaunchableAndCanSwitchToNeon",
}
CSV_CLASS = "dev.sk2andy.materialbrowser.passwords.PasswordCsvInstrumentedTest"
CSV_EXPECTED = {
    "directCsvActionsReuseWarningsWithoutRestoringTransfers",
    "encryptedStorePreservesDuplicatesAndFailsClosedOnCorruption",
    "safImportAndAuthenticatedExportRoundTrip",
    "sensitiveScreenIsPrivateSecureAndRecreationDoesNotRestoreCredentials",
}

VAULT_CLASS = "dev.sk2andy.materialbrowser.passwords.PasswordVaultInstrumentedTest"
VAULT_EXPECTED = {
    "nativeCrudSearchSelectionAndStrongDeleteAll",
    "encryptedPersistenceCorruptionAndUnavailableKeyPreserveData",
    "authenticationCancellationBackgroundRecreationAndInactivityLock",
    "vaultCannotCaptureNormalOrPrivateBrowserStateOrChangeCsvPrototype",
}


def completed_tests(text, class_name=CLASS, expected=EXPECTED):
    records = []
    current = {}
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            current[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.partition(":")[2].strip())
            if code == 0 and "test" in current:
                records.append((current.get("class"), current["test"]))
            elif code < 0:
                raise ValueError(f"Instrumentation reported failure/skip code {code}: {current}")
            current = {}
    if "INSTRUMENTATION_CODE: -1" not in text:
        raise ValueError("Instrumentation did not finish normally")
    if len(records) != len(expected) or {name for _, name in records} != expected:
        raise ValueError(f"Expected all {len(expected)} tests, got {records}")
    if any(name != class_name for name, _ in records):
        raise ValueError("Unexpected instrumentation class")
    return records


def main(log, report, profile="branding"):
    class_name, expected = {"branding": (CLASS, EXPECTED), "csv": (CSV_CLASS, CSV_EXPECTED), "vault": (VAULT_CLASS, VAULT_EXPECTED)}[profile]
    records = completed_tests(Path(log).read_text(), class_name, expected)
    root = ET.Element("testsuite", name=class_name, tests=str(len(expected)), failures="0", errors="0", skipped="0")
    for class_name, test_name in records:
        ET.SubElement(root, "testcase", classname=class_name, name=test_name)
    path = Path(report)
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root)
    path.write_text(ET.tostring(root, encoding="unicode") + "\n")
    print(f"All {len(expected)} {profile} instrumentation tests completed successfully.")


if __name__ == "__main__":
    main(*sys.argv[1:])
