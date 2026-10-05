# REPKIT — Spanish, Simplified Chinese and Hindi (spec revision 25)

Status: requested by the user in chat (2026-10-03). These are first-pass translations; the user is having them reviewed by native speakers.

- **Languages:**
  - `values-es`: neutral Latin American Spanish, using "tú";
  - `values-zh-rCN`: Simplified Chinese, using "你";
  - `values-hi`: Hindi in Devanagari, using "आप", with gym words kept as people say them (रेप्स, सेट, स्ट्रीक).
- **Not translated:** "REPKIT" (`app_name` is `translatable="false"`) and "Pro".
- **Source of truth:** the review sheet (`\DEVMONSTER\ethor\claude_share\repkit-translations-review.csv`). The `values-xx/strings.xml` files are generated from it, so reviewer corrections go into the sheet and the files are regenerated.
- **Per-app language (Android 13+):** `res/xml/locales_config.xml` plus `android:localeConfig` lets a user pick REPKIT's language in Settings > Apps > REPKIT > Language without changing the phone's language.
- **Plurals:** Chinese uses `other` only; Spanish and Hindi use `one` and `other`.
- **Built on revision 24** (localisation prep): every user-facing string, date and voice cue already follows the locale.
