#!/usr/bin/env python3
"""Generate checked-in BlackHoleTest resource overlays, never application source patches."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ICONS = (
    ("classic", "ic_launcher"), ("neon", "ic_launcher_cookie"),
    ("minimal", "ic_launcher_cotton_candy"), ("dark", "ic_launcher_lemon"),
    ("cosmic", "ic_launcher_donut"), ("luxury", "ic_launcher_cookie_maxed"),
    ("blue", "ic_launcher_cotton_candy_freeform"), ("redshift", "ic_launcher_lemon_maxed"),
    ("solar_flare", "ic_launcher_donut_maxed"),
)
LEGAL_KEYS = ("license", "licence", "copyright", "imprint", "legal", "notices", "uassets")
PROTOCOL_NAMES = ("Candy Rules v1", "Candy Sync")
COPY = {
    "app_name": "Black Hole Browser",
    "onboarding_welcome_title": "Welcome to Black Hole Browser",
    "onboarding_welcome_body": "Browse Beyond. Explore the web with gestures and privacy within reach.",
    "onboarding_welcome_card_title": "Navigate with three gestures",
    "onboarding_welcome_start": "Discover the gestures",
    "color_palette_dynamic": "Black Hole · Nebula",
    "color_palette_candy": "Black Hole · Nebula",
    "about_version": "Black Hole Browser version %1$s",
    "settings_startup_animation_subtitle": "Shows the Black Hole logo on regular app launches.",
}
PORTUGUESE_COPY = {
    "onboarding_welcome_title": "Bem-vindo ao Black Hole Browser",
    "onboarding_welcome_body": "Browse Beyond. Explore a web com gestos e privacidade ao seu alcance.",
    "onboarding_welcome_card_title": "Navegue com três gestos",
    "onboarding_welcome_start": "Explorar gestos",
    "about_version": "Black Hole Browser versão %1$s",
    "settings_startup_animation_subtitle": "Mostra o logo Black Hole ao abrir o aplicativo.",
}


def protected(key):
    return any(marker in key.lower() for marker in LEGAL_KEYS) or (
        key.startswith("about_") and key != "about_version"
    )


def branded_value(key, text):
    if protected(key):
        return text
    # Public protocol names and the separately distributed sync extension retain their identities.
    for index, name in enumerate(PROTOCOL_NAMES):
        text = text.replace(name, f"__BH_PROTOCOL_{index}__")
    text = text.replace("Candy Browser", "Black Hole Browser").replace("Candy", "Black Hole")
    for index, name in enumerate(PROTOCOL_NAMES):
        text = text.replace(f"__BH_PROTOCOL_{index}__", name)
    return text


def string_overlay(source, qualifier):
    values = ET.Element("resources")
    for element in source:
        key = element.get("name", "")
        if element.tag != "string" or protected(key):
            continue
        text = element.text or ""
        new = branded_value(key, text)
        # Retain nested spans and all formatting attributes verbatim; reviewed copies are plain text.
        if key in COPY:
            if qualifier == "values":
                new = COPY[key]
            elif qualifier.startswith("values-pt"):
                new = PORTUGUESE_COPY.get(key, COPY[key])
            elif key in ("app_name", "color_palette_dynamic", "color_palette_candy"):
                new = COPY[key]
        if new == text:
            continue
        clone = ET.fromstring(ET.tostring(element, encoding="unicode"))
        clone.text = new
        values.append(clone)
    if qualifier == "values":
        for key, text in (
            ("onboarding_brand_name", "Black Hole Browser"),
            ("onboarding_demo_url", "blackhole://gestures"),
        ):
            ET.SubElement(values, "string", {"name": key, "translatable": "false"}).text = text
    return values


def write_xml(path, root):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root, space="    ")
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + '\n')


def generate(root=ROOT):
    main = root / "app/src/main/res"
    output = root / "app/src/blackHoleTest/res"
    for file in sorted(main.glob("values*/strings.xml")):
        values = string_overlay(ET.parse(file).getroot(), file.parent.name)
        if len(values):
            write_xml(output / file.parent.name / "strings.xml", values)
    android = "http://schemas.android.com/apk/res/android"
    ET.register_namespace("android", android)
    for slug, resource in (*ICONS, ("classic", "ic_launcher_round"), ("classic", "ic_launcher_jelly_beans")):
        icon = ET.Element("adaptive-icon")
        for tag, drawable in (
            ("background", "bh_icon_background"),
            ("foreground", f"bh_icon_{slug}_art"),
            ("monochrome", "bh_icon_mono"),
        ):
            ET.SubElement(icon, tag, {f"{{{android}}}drawable": f"@drawable/{drawable}"})
        write_xml(output / "mipmap-anydpi-v26" / f"{resource}.xml", icon)
    (output / "drawable-nodpi/ic_launcher_foreground_art.png").write_bytes(
        (output / "drawable-nodpi/bh_icon_classic_art.png").read_bytes()
    )


if __name__ == "__main__":
    generate()
