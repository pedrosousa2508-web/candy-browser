import unittest
from verify_black_hole_instrumentation import completed_tests, CSV_CLASS, CSV_EXPECTED

CLASS = "dev.sk2andy.materialbrowser.data.BlackHoleBrandingInstrumentedTest"
NAMES = [
    "identityAndAttributionResolveInEnglishAndPortuguese",
    "eachOfNineIconsEnablesExactlyOneLauncherAndPersistsSelection",
    "legacyJellyBeansAliasRemainsLaunchableAndCanSwitchToNeon",
]


def transcript(names, code=0):
    return "\n".join(
        f"INSTRUMENTATION_STATUS: class={CLASS}\nINSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: {code}"
        for name in names
    ) + "\nINSTRUMENTATION_CODE: -1\n"


class InstrumentationReportTest(unittest.TestCase):
    def test_accepts_three_real_completion_records(self):
        self.assertEqual(3, len(completed_tests(transcript(NAMES))))

    def test_rejects_zero_tests_even_with_successful_instrumentation_exit(self):
        with self.assertRaises(ValueError):
            completed_tests("INSTRUMENTATION_CODE: -1\n")

    def test_rejects_incomplete_suite(self):
        with self.assertRaises(ValueError):
            completed_tests(transcript(NAMES[:2]))

    def test_rejects_failed_or_skipped_tests(self):
        for code in (-1, -2, -3, -4):
            with self.assertRaises(ValueError):
                completed_tests(transcript(NAMES, code))

    def test_rejects_aborted_instrumentation(self):
        with self.assertRaises(ValueError):
            completed_tests(transcript(NAMES).replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"))

    def test_csv_profile_requires_all_csv_cases_and_correct_class(self):
        text = transcript(sorted(CSV_EXPECTED)).replace(CLASS, CSV_CLASS)
        self.assertEqual(3, len(completed_tests(text, CSV_CLASS, CSV_EXPECTED)))
        for rejected in (text.replace(CSV_CLASS, CLASS), text.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: -2", 1)):
            with self.assertRaises(ValueError):
                completed_tests(rejected, CSV_CLASS, CSV_EXPECTED)


if __name__ == "__main__":
    unittest.main()
