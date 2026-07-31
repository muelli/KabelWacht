# KabelWacht

[![CI](https://github.com/muelli/KabelWacht/actions/workflows/ci.yml/badge.svg)](https://github.com/muelli/KabelWacht/actions/workflows/ci.yml)
[![Reproducible build](https://github.com/muelli/KabelWacht/actions/workflows/reproducible.yml/badge.svg)](https://github.com/muelli/KabelWacht/actions/workflows/reproducible.yml)
[![REUSE status](https://api.reuse.software/badge/github.com/muelli/KabelWacht)](https://api.reuse.software/info/github.com/muelli/KabelWacht)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSES/AGPL-3.0-or-later.txt)

A simple, fully **free-software** [WireGuard](https://www.wireguard.com/) VPN client
for Android. It manages tunnel profiles and connects — nothing more.

- **Create, list, edit and delete** WireGuard profiles.
- **Import** a configuration by **scanning a QR code** or opening a **`.conf` file**.
- **Connect/disconnect** with a per-tunnel switch (userspace backend, **no root**).
- **Always-on VPN** support: enable it from the shield action in the app (opens the
  system VPN settings) and Android reconnects your most recently used tunnel
  automatically — including after a reboot, and with the optional kill-switch.
- **No trackers, no analytics, no ads, no Google Play Services.** The only network
  connection the app makes is your own WireGuard tunnel — see the
  [privacy policy](docs/PRIVACY.md).
- Ready to build on **F-Droid** and in **GitHub CI**.

## Free-software dependencies

| Purpose | Component | License |
| --- | --- | --- |
| WireGuard tunnel library + wg-quick parser | [`com.wireguard.android:tunnel`](https://git.zx2c4.com/wireguard-android/) | Apache-2.0 |
| Userspace WireGuard (`libwg-go.so`) | [wireguard-go](https://git.zx2c4.com/wireguard-go/) | MIT |
| `wg` / `wg-quick` binaries (`libwg.so`, `libwg-quick.so`) | [wireguard-tools](https://git.zx2c4.com/wireguard-tools/) | GPL-2.0 |
| QR code scanning | [`com.journeyapps:zxing-android-embedded`](https://github.com/journeyapps/zxing-android-embedded) | Apache-2.0 |
| UI | AndroidX Jetpack Compose (Material 3) | Apache-2.0 |

**KabelWacht is licensed [AGPL-3.0-or-later](LICENSE).** The dependencies keep their
own (compatible) licenses — see [`NOTICE`](NOTICE). The `wg`/`wg-quick` binaries are
GPL-2.0 programs invoked as separate subprocesses (aggregation); AGPL-3.0 imposes no
copyleft on them and they impose none on the app.

## Building

Requirements: JDK 17, the Android SDK (compile/target SDK 35, min SDK 29), plus the
**NDK 27.2.12479018** and **CMake 3.22.1** (WireGuard is built from source, see below).
Clone with submodules:

```bash
git clone --recursive https://github.com/muelli/KabelWacht
# or, in an existing clone:
git submodule update --init --recursive
```

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew lint                   # Android lint
./gradlew assembleRelease        # minified release APK (unsigned)
```

The wrapper pins Gradle; `local.properties` (with `sdk.dir=...`) is created
automatically by Android Studio, or write it by hand. It is git-ignored.

## WireGuard is built from source

KabelWacht does **not** ship the prebuilt `com.wireguard.android:tunnel` AAR. The
[`:tunnel`](tunnel/build.gradle.kts) module compiles the WireGuard tunnel library —
including `libwg-go.so` (wireguard-go), `libwg.so` and `libwg-quick.so`
(wireguard-tools) — from the pinned [`third_party/wireguard-android`](third_party)
submodule via the NDK. Every binary in the APK is built here from source.

## Reproducible builds

The release build is **byte-for-byte reproducible**: the same source produces an
identical APK regardless of build path or time (Go's `-trimpath`/`-buildid=`,
`--build-id=none`, and `elf-cleaner` remove the usual sources of non-determinism).
[`.github/workflows/reproducible.yml`](.github/workflows/reproducible.yml) enforces
this on every tag by building twice from two independent checkouts and diffing the
results. Verify locally:

```bash
./gradlew clean assembleRelease && sha256sum app/build/outputs/apk/release/*-unsigned.apk
# clean and build again — the hash is identical
```

## Project layout

```
app/src/main/java/com/github/muelli/kabelwacht/
├── data/        ConfigStore (file-per-tunnel), TunnelRepository, TunnelProfile
├── vpn/         TunnelManager (wraps GoBackend), WgTunnel
├── ui/          Compose screens: list/, edit/, nav/, theme/
├── KabelWachtApp.kt   Application + manual DI container
└── MainActivity.kt
```

Profiles are stored as individual wg-quick `.conf` files in the app's private
storage; the tunnel name is the file name and the network interface name (so it must
be 1–15 chars of `A–Z a–z 0–9 _ = + . -`).

## Versioning and releasing

The version is a **single incrementing integer** (like an Android `versionCode`),
kept in [`version.properties`](version.properties). Gradle derives both `versionCode`
and `versionName` from it, so there is one number to bump.

To cut release _N_:

1. Set `versionCode=N` in `version.properties`.
2. Add release notes at `fastlane/metadata/android/en-US/changelogs/N.txt`.
3. Commit, then tag and push:

   ```bash
   git tag vN && git push origin vN
   ```

Pushing the `vN` tag triggers [`release.yml`](.github/workflows/release.yml), which:

- verifies the tag matches `version.properties` (fails fast on a mismatch),
- builds the release APK,
- publishes a GitHub Release whose notes come from the fastlane changelog
  `changelogs/N.txt` (falling back to auto-generated notes if it's missing).

Signing is optional via the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Without them the release APK is published unsigned
(F-Droid rebuilds and signs from source anyway).

## Continuous integration

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — on push/PR: lint, unit
  tests, and `assembleDebug`; uploads the debug APK and reports as artifacts.
- [`.github/workflows/release.yml`](.github/workflows/release.yml) — the release flow
  described above.

### Run CI locally before pushing

```bash
scripts/ci-local.sh            # act (Docker) if available, else Gradle directly
scripts/ci-local.sh --gradle   # force the fast, no-Docker Gradle path
scripts/ci-local.sh --act      # force full GitHub Actions emulation via act
```

The script runs the same tasks as the CI `build` job (`lint testDebugUnitTest
assembleDebug`). Full emulation uses [`act`](https://github.com/nektos/act) with the
settings in [`.actrc`](.actrc); install it with e.g. `brew install act` (needs
Docker).

## F-Droid

The repository is F-Droid-ready: FOSS-only dependencies, tagged releases, and
[fastlane metadata](fastlane/metadata/android/en-US/) for the listing.

Inclusion in the official F-Droid catalogue is a separate step: submit a metadata
recipe to [fdroiddata](https://gitlab.com/fdroid/fdroiddata). A ready-to-adapt
template lives at
[`fdroid/com.github.muelli.kabelwacht.yml`](fdroid/com.github.muelli.kabelwacht.yml)
(update the repository URLs first).

### Self-hosted F-Droid repository (auto-published)

You can also run your **own** F-Droid repository, hosted on GitHub Pages and updated
automatically on every `vN` tag by
[`.github/workflows/publish-fdroid.yml`](.github/workflows/publish-fdroid.yml). On a
tag it builds and signs the APK, runs `fdroid update` to (re)generate the signed
index, and pushes the result to a persistent `fdroid-repo` branch that Pages serves.

An F-Droid repo needs **two** signing keys, both stored as GitHub Actions secrets and
both kept stable forever:

| Key | Signs | Secrets |
| --- | --- | --- |
| App (APK) key | the APK — its identity for updates | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |
| Repo index key | the repo index — its fingerprint = repo identity | `FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASS`, `FDROID_KEY_PASS`, `FDROID_KEY_ALIAS` |

**One-time setup:**

1. Create the app signing key (if you don't have one) and set its four secrets.
2. Create the repo index key and set its four secrets:

   ```bash
   FDROID_KEYSTORE_PASS='…' FDROID_KEY_PASS='…' scripts/fdroid-init.sh
   ```

   The script prints the base64 keystore, the secret names to set, and the repo
   **fingerprint**.
3. Confirm `repo_url` in [`fdroid/config.yml`](fdroid/config.yml) matches your Pages
   URL (`https://<owner>.github.io/<repo>/fdroid/repo`). Serving under `/fdroid/repo`
   lets plain `https://` links open F-Droid directly.
4. Push a `vN` tag. The first run creates the `fdroid-repo` branch and tries to enable
   Pages automatically; if it can't, set **Settings → Pages → Source: branch
   `fdroid-repo` / (root)** once.

After that, publishing is fully automatic. Users add your repo once — the workflow
writes a landing page at `https://<owner>.github.io/<repo>/` with the URL,
fingerprint, and a tap-to-add link. If secrets are absent the workflow logs a notice
and skips, so it never breaks a tag push.

## Scope (v1)

One active tunnel at a time. Always-on VPN, per-app split tunnelling, and in-app key
generation are intentionally out of scope for the first version.

## Trademark

WireGuard is a registered trademark of Jason A. Donenfeld. This project is not
officially affiliated with or endorsed by the WireGuard project.
