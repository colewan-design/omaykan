# Baseline profiles

Not three more apps. Three test modules, one per app, whose only job is to
produce one file each:

```
baselineprofile/          → :app      app/src/main/generated/baselineProfiles/baseline-prof.txt
baselineprofile-seller/   → :seller   seller/src/main/generated/baselineProfiles/baseline-prof.txt
baselineprofile-rider/    → :rider    rider/src/main/generated/baselineProfiles/baseline-prof.txt
```

That file is a list of classes and methods. It ships inside the APK, and on
install ART compiles what it names ahead of time instead of interpreting it and
JIT-ing on the way. Compose is why it is worth having: the startup path is a
great deal of library code that is cold on first run, on every device that has
just installed, every time.

One each rather than one shared, because a profile is a list of methods in one
APK and cannot be pointed at another.

It does nothing for a debug build. Debug is compiled differently and the
profile is not consulted; measure this on `release` or not at all.

## Regenerating

```
./gradlew :app:generateBaselineProfile
./gradlew :seller:generateBaselineProfile
./gradlew :rider:generateBaselineProfile
```

Gradle downloads an emulator, boots it, installs a non-minified release build
of the app, drives it through the journey, reads ART's record of what ran,
keeps the part that was stable across repeated runs, and writes the result into
the path above. Twenty minutes the first time, most of it the emulator image;
a few minutes per app after that, since all three share one AVD.

Then **commit the file it changed.** A release built on CI has no emulator and
does not generate anything — it packages what is in the repository. An
uncommitted profile is simply no profile.

Nothing runs this for you. `assembleRelease` does not, and should not: booting
an emulator inside a release build would turn two minutes into twenty.

## When it is worth rerunning

The profile is a list of method names, so it degrades by drifting out of date
rather than by breaking. Stale entries are ignored; code it never heard of is
merely uncompiled. Worth rerunning after:

- a change to what an app does on startup — the launch path, the nav graph,
  dependency-injection wiring;
- a Compose, AGP or Kotlin upgrade, which moves library internals wholesale;
- a new screen early in the session, if you also add it to that app's journey.

Not worth rerunning for an ordinary feature commit.

## What each journey covers

Every journey has a `startup` test kept separate from the rest. That one also
feeds the **startup profile** — a smaller, more urgent list that dex layout is
ordered around — and mixing a scroll into it would dilute that with code no
launch ever reaches.

| | |
|---|---|
| `:app` | The market, a scroll, and all five tabs of the shell. |
| `:seller` | The signed-out landing and the sign-in form. |
| `:rider` | The three onboarding pages, paged through rather than skipped, and the sign-in form. |

**The seller and rider profiles stop at the sign-in form**, because the orders
list and the job board are behind tokens this build has no way to get. That is
a smaller profile, not a pointless one: what it covers is the part every launch
pays for and nobody can skip — process start, the Hilt graph, the theme,
session restore, and Compose coming up cold. The rider's onboarding is worth
more than its three screens suggest, since paging it compiles the scrolling and
animation machinery the board reuses.

If a throwaway shop and rider account are ever wired into CI, signing in is
where the rest of both would come from.

None of the three asserts anything, and none can fail a build on behaviour.
Every step waits for what it wants and moves on without it, because a release
build talks to the live API and CI may have no useful answer from it. A dead
network yields a smaller profile, not a red build.

## The emulator

`profileEmulator`, defined in each module: a Pixel 6 on API 34, **AOSP** rather
than Google APIs, because reading ART's profile back out requires root and the
Play-flavoured images are not rootable. The same name in all three, so they
share one AVD and one download.

`useConnectedDevices` is off, so a phone plugged into your laptop is never
used. A profile captured on an unrooted daily driver is silently a worse
profile — the capture degrades rather than failing — and it would land in the
repository looking exactly like a good one.

## Two repairs in the root build file

Both live in `../build.gradle.kts`, applied to every subproject, because
neither has anything to do with what these apps are. Recording a profile needs
a release build that has not been through R8 — a profile is a list of method
names, and names R8 has rewritten are names ART will never see — so the plugin
copies `release` into `nonMinifiedRelease`. Both repairs are to that copy:

- **Signing.** It inherits `release`'s signing config, which is null on a
  checkout without the keystore, and an unsigned APK cannot be installed. The
  synthesised variants are debug-signed so generating a profile works on the
  same checkout that can only `assembleDebug`. Neither ships.
- **A KGP defect.** KGP hands the copy's Kotlin source set the inherited
  `src/release/kotlin` directory as an unresolved Gradle `Provider`. KSP reads
  it back, gets `provider(?)`, resolves it against the project directory, and
  dies on Windows where `?` cannot be in a filename. The root build file
  substitutes the real path. It is self-clearing — a no-op once KGP resolves
  the provider before handing it over.
