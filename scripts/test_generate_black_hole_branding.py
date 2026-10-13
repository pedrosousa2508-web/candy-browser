import hashlib
import unittest
import xml.etree.ElementTree as ET

import generate_black_hole_branding as branding


class BlackHoleBrandingTest(unittest.TestCase):
    def test_visible_copy_is_rebranded_without_changing_protocol_names(self):
        self.assertEqual("Open Black Hole Browser", branding.branded_value("open_app", "Open Candy Browser"))
        self.assertEqual("Black Hole uses Candy Rules v1", branding.branded_value("rules", "Candy uses Candy Rules v1"))
        self.assertEqual("Set up Candy Sync", branding.branded_value("sync", "Set up Candy Sync"))

    def test_attribution_and_license_copy_is_preserved(self):
        for key in ("about_imprint_body", "about_open_source_intro", "third_party_notices", "license_body"):
            self.assertEqual("Candy Browser MPL 2.0", branding.branded_value(key, "Candy Browser MPL 2.0"))

    def test_overlay_preserves_placeholders_and_span_markup(self):
        source = ET.fromstring('<resources><string name="open_app">Open Candy %1$s <b>now</b></string></resources>')
        result = branding.string_overlay(source, "values")
        self.assertEqual("Open Black Hole %1$s ", result[0].text)
        self.assertEqual("now", result[0][0].text)

    def test_portuguese_and_default_identity(self):
        source = ET.fromstring('<resources><string name="app_name">Candy Browser</string><string name="onboarding_welcome_title">Candy</string></resources>')
        for qualifier, title in (("values", "Welcome to Black Hole Browser"), ("values-pt", "Bem-vindo ao Black Hole Browser")):
            result = {item.get("name"): item.text for item in branding.string_overlay(source, qualifier)}
            self.assertEqual("Black Hole Browser", result["app_name"])
            self.assertEqual(title, result["onboarding_welcome_title"])

    def test_nine_original_artworks_and_legacy_alias_resource(self):
        root = branding.ROOT / "app/src/blackHoleTest/res"
        expected = {
            "classic": "1f53578e862c7fb5131280cb965bdff711369e2104de12771332193884619a7c",
            "neon": "2feb9f035b81b419120cb06c48dddebfaf2f4cf2ecce9c8ec1e74538b3177b57",
            "minimal": "0fadb2ee1041f957f24a9c7879a8a961d0f051239bf9b5ffa5bc7bdbba20cf10",
            "dark": "1fa7d26ee739cd5f38f89da341acaa4697fb1cf20e9c0b8ac67dcaaf92d97711",
            "cosmic": "879c11e407bfd104f49d8a79cc00f5a970272972627afd83f69466b16899818a",
            "luxury": "4ae13046d2fd01b6435276a4e2d2654729ad3332a67434bd6bbb5a20eaa8ac19",
            "blue": "404aae6646ce61cea683497292de3a01ca02d203236d3c704b0986cfdf11f29f",
            "redshift": "fdeacb8811d034e3400b69f726f3d2bebedeb657cfb59fda301f100fae8eac72",
            "solar_flare": "0f559cf7273609cfea6be62e59feafbc29ec0de163aea4619e3c21025fc16aa9",
        }
        for slug, digest in expected.items():
            self.assertEqual(digest, hashlib.sha256((root / f"drawable-nodpi/bh_icon_{slug}_art.png").read_bytes()).hexdigest())
        self.assertEqual(
            (root / "mipmap-anydpi-v26/ic_launcher.xml").read_bytes(),
            (root / "mipmap-anydpi-v26/ic_launcher_jelly_beans.xml").read_bytes(),
        )


if __name__ == "__main__":
    unittest.main()
