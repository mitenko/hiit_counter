# REPKIT — Release build (spec revision 31)

Status: approved by the user (2026-10-05): "turn on R8", and the user created the upload key themselves.

## 1. Signing
- Google Play App Signing holds the app signing key. REPKIT signs uploads with an **upload key**: `repkit-upload.jks`, alias `repkit-upload`, RSA 2048, kept outside the repo, with a copy off the PC.
- `app/build.gradle.kts` creates the `upload` signing config only when the user-level `~/.gradle/gradle.properties` has `repkitUploadStoreFile`, `repkitUploadStorePassword`, `repkitUploadKeyAlias` and `repkitUploadKeyPassword`. The release build type uses it when present.
- Without them (CI, the rgradle workers, which hold no credentials) `bundleRelease` / `assembleRelease` produce **unsigned** artifacts. They are signed on the developer PC afterwards (`jarsigner` for the .aab, `apksigner` for an .apk), so the key never leaves that PC.
- Never commit a keystore or password (`*.jks` and `*.keystore` are gitignored).

## 2. Shrinking
- Release builds run R8 with `isMinifyEnabled = true` and `isShrinkResources = true`, on `proguard-android-optimize.txt` plus `app/proguard-rules.pro`.
- `proguard-rules.pro` keeps:
  - source file names and line numbers, and custom exceptions, for Crashlytics;
  - the member names of the app's enums, which are stored as text (`entry.type`, `theme_mode`, …).
- The Crashlytics Gradle plugin uploads the R8 mapping file for each release build, so crash stack traces read as source.
- Debug builds are not shrunk.

## 3. Verifying a release build on a device without losing data
- The phone runs a debug-signed build with the user's data, and a Play-signed build can't be installed over it.
- To smoke-test R8, sign the release APK with the **debug** key and `adb install -r` it over the debug build: the signature matches, so the data stays.
- Then reinstall the debug build the same way. The schema is the same, so nothing migrates.
- Release builds aren't debuggable, so `run-as` backups don't work while one is installed. Back up before installing it.

## 4. Version
- `versionCode` 1, `versionName` 0.1.0 for the first internal test.
- Each upload to Play needs a higher `versionCode`.
