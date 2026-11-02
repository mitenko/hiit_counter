# Rename to RepRhythm (spec revision 45)

**Status:** requested 2026-11-02. Amends every earlier spec where it names the app "REPKIT".

## 1. Why

Said aloud, "REPKIT" sounds too much like an offensive phrase. The app launched on Play on 2026-10-27 and has only a handful of installs, so the name changes now, while it costs little.

## 2. The name

- **RepRhythm**, in camel case, wherever the app is named: the launcher label and app settings (`app_name`), the entry list's top-bar title, "About RepRhythm", the welcome dialog, the Pro row ("RepRhythm Pro", "RepRhythm Pro ✓") and the ⓘ texts that mention the app.
- It is a brand and stays in Latin letters in Spanish, Simplified Chinese and Hindi. Those strings change through the translation data and generator, as usual.

## 3. What does not change

The applicationId and Kotlin packages (`com.mitenko.repkit`), the `repkit_pro` product id, the repo, `hiit.db`, the DataStore files, the `Hiit*` class names and the Firebase project. None of them is shown to users, and changing the applicationId or the product id would break installs and purchases.

Earlier specs keep "REPKIT" as history; read it as RepRhythm.

## 4. Outside the app

The Play listing, the payments profile's public name, AdMob (the app name and the consent message), the privacy policy and the website are renamed by the user, separately from this change.

## 5. Testing

`EntryListScreenTest` checks the new title, welcome title, Pro row texts and the "About RepRhythm" button.
