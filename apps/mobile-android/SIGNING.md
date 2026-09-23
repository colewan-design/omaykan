# Signing identity — package names and fingerprints

What Google, and anything else that identifies this app, knows it by.

Android identifies an app by **package name + signing certificate** together,
not by either alone. Debug and release differ in both, so to Google they are two
different apps and each needs its own registration.

## The two builds

| | Debug | Release |
| --- | --- | --- |
| Package name | `com.omaykan.storefront.debug` | `com.omaykan.storefront` |
| SHA-1 | `96:D8:0A:AA:27:CF:9E:3C:18:1D:BC:85:A2:BB:90:CC:DE:BB:40:5E` | `F5:06:66:EA:BC:93:4D:BA:CB:9D:C1:4D:81:EF:CE:7B:0A:9C:F4:ED` |
| Keystore | `~/.android/debug.keystore` (alias `androiddebugkey`) | `C:\Users\ASUS\omaykan-mobile-keystore-backup\release.keystore` (alias `colepos`) |
| Certificate expires | 2053-10-02 | 2053-12-03 |

The `.debug` suffix is not a convention someone chose to be tidy — it is
`applicationIdSuffix = ".debug"` in `app/build.gradle.kts`, and it is what lets a
debug build sit on the same phone as a release one without either replacing the
other.

**These fingerprints are not secrets.** A signing certificate is public by
design: it ships inside every APK, and the Play Console prints it. The
*keystores* and their passwords are the secrets, and neither belongs in this
file or anywhere else in git.

## Regenerating them

If a keystore is ever replaced, these values change and every registration built
on them has to be updated.

```bash
# Debug — the password is literally "android", set by Android Studio
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android

# Release — password is in keystore.properties beside the keystore
keytool -list -v -keystore release.keystore -alias colepos
```

## What they are used for

**Google OAuth Android clients.** Sign in with Google needs one client per
package-name-and-fingerprint pair, so there are two:

| Client | Package name | SHA-1 |
| --- | --- | --- |
| omaykan android debug | `com.omaykan.storefront.debug` | the debug row above |
| omaykan android release | `com.omaykan.storefront` | the release row above |

An Android OAuth client has **no redirect URI field**. The package name and the
fingerprint are the whole identity — they are how Google decides the app asking
is the app you registered.

The Android client's id is **not** the value the backend checks. The audience of
the ID token the app sends is the **Web** client id, which is already
`GOOGLE_CLIENT_ID` on the backend and `VITE_GOOGLE_CLIENT_ID` on the web build.
The Android clients exist so Google can attest the app, not to name it to us.

## Two things that will bite

**The release key is not wired into the build.** `signingConfigs.release` reads a
`keystore.properties` next to `settings.gradle.kts` (or wherever
`KEYSTORE_PROPERTIES` points), and there is none — deliberately, so a fresh
checkout can still run `assembleDebug` and the tests. Until one is put there,
`assembleRelease` produces an **unsigned** APK, which will not install on a
phone, and the release fingerprint above is not yet in use by anything.

**Play App Signing replaces the release fingerprint.** If this app is ever
published through Google Play with Play App Signing on, Google re-signs it with
a key of their own, and the SHA-1 that identifies the installed app becomes
*theirs* — found under **Protected with Play → Play Store protection → Manage
Play app signing**. A third OAuth client is needed at that point, registered
against that fingerprint. The key in the backup folder then becomes the *upload*
key rather than the signing key, and is still irreplaceable: lose it and the
listing cannot be updated.

## Where the release key lives

`C:\Users\ASUS\omaykan-mobile-keystore-backup\` — rescued on 2026-08-26 from
`apps/mobile` before that folder was deleted, and **still the only copy**. It is
gitignored everywhere, which is why deleting that folder would have destroyed it
for good. Back it up somewhere that is not this laptop before anything ships.
