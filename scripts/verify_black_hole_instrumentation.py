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


def completed_tests(text):
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
    if len(records) != 3 or {name for _, name in records} != EXPECTED:
        raise ValueError(f"Expected all three branding tests, got {records}")
    if any(name != CLASS for name, _ in records):
        raise ValueError("Unexpected instrumentation class")
    return records


def main(log, report):
    records = completed_tests(Path(log).read_text())
    root = ET.Element("testsuite", name=CLASS, tests="3", failures="0", errors="0", skipped="0")
    for class_name, test_name in records:
        ET.SubElement(root, "testcase", classname=class_name, name=test_name)
    path = Path(report)
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root)
    path.write_text(ET.tostring(root, encoding="unicode") + "\n")
    print("All three branding instrumentation tests completed successfully.")


if __name__ == "__main__":
    main(*sys.argv[1:])
