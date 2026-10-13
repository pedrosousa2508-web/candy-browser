# Black Hole V4.1 branding provenance

The nine original 512×512 PNG artworks were recovered without modification from the branding
archive in `.github/workflows/black-hole-beta-v4-interface.yml` and
`.github/workflows/black-hole-beta-v4-1-assinatura-teste.yml`, at repository commit
`b83b9d57cc65758321901a5cb2c3f3453cf65287`. Both archives contain byte-identical artwork.
`scripts/test_generate_black_hole_branding.py` records and checks each original SHA-256.
The adaptive-icon background/monochrome design and violet/cyan color tokens use the same historical
implementation, reviewed separately from its CSV/password and whole-checkout patch scripts.

The artwork is committed directly; no archive decoding or Base64 patch runs in CI. Resource names
and activity aliases remain compatible with the Candy base. The MPL-2.0 license, upstream author
credits, source links and third-party notices in the repository and application are preserved.
This experimental fork does not replace the upstream attribution with a claim of original authorship.

Resource overlays only apply to `blackHoleTest`. Regenerate the text/adaptive-icon XML overlays
with `python3 scripts/generate_black_hole_branding.py`; original PNGs are retained as inputs.
