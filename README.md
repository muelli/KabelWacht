# KabelWacht

A simple, fully **free-software** [WireGuard](https://www.wireguard.com/) VPN client
for Android. It manages tunnel profiles and connects — nothing more.

- **Create, list, edit and delete** WireGuard profiles.
- **Import** a configuration by **scanning a QR code** or opening a **`.conf` file**.
- **Connect/disconnect** with a per-tunnel switch (userspace backend, **no root**).
- **No trackers, no analytics, no ads, no Google Play Services.** The only network
  connection the app makes is your own WireGuard tunnel.
- Ready to build on **F-Droid** and in **GitHub CI**.

## Free-software dependencies

| Purpose | Library | License |
| --- | --- | --- |
| WireGuard backend + wg-quick parser | [`com.wireguard.android:tunnel`](https://git.zx2c4.com/wireguard-android/) | GPL-2.0 |
| QR code scanning | [`com.journeyapps:zxing-android-embedded`](https://github.com/journeyapps/zxing-android-embedded) | Apache-2.0 |
| UI | AndroidX Jetpack Compose (Material 3) | Apache-2.0 |

Because it links the GPL-2.0 WireGuard tunnel library, **KabelWacht is licensed
GPL-2.0-or-later** (see [`LICENSE`](LICENSE)).

## Building

Requirements: JDK 17 and the Android SDK (compile/target SDK 35, min SDK 29).

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew lint                   # Android lint
./gradlew assembleRelease        # minified release APK (unsigned)
```

The wrapper pins Gradle; `local.properties` (with `sdk.dir=...`) is created
automatically by Android Studio, or write it by hand. It is git-ignored.

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

Inclusion in the F-Droid catalogue is a separate step: submit a metadata recipe to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). A ready-to-adapt template lives at
[`fdroid/com.github.muelli.kabelwacht.yml`](fdroid/com.github.muelli.kabelwacht.yml)
(update the repository URLs first).

## Scope (v1)

One active tunnel at a time. Always-on VPN, per-app split tunnelling, and in-app key
generation are intentionally out of scope for the first version.

## Trademark

WireGuard is a registered trademark of Jason A. Donenfeld. This project is not
officially affiliated with or endorsed by the WireGuard project.
