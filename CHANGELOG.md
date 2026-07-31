# Changelog

All notable changes to KabelWacht are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Per-release notes shown
in F-Droid live in `fastlane/metadata/android/en-US/changelogs/`.

## [Unreleased]

## [3] — versionCode 3

### Added
- **Always-on VPN** support: Android can now (re)connect the most recently used
  tunnel automatically via the system always-on VPN feature, including after a
  reboot. A shield action opens the system VPN settings.
- App icon and full description in the F-Droid repository listing (editable icon
  source lives in `artwork/icon.svg`).
- Predictive back gesture.
- REUSE compliance, `SECURITY.md`, Gradle dependency verification, community-health
  docs, a privacy policy, and CI supply-chain hardening (SHA-pinned actions,
  Dependabot).

### Changed
- Kotlin updated to 2.4.10; CI made faster and lighter (single-ABI PR builds,
  Gradle configuration cache).

## [2] — versionCode 2

### Changed
- Relicensed to **AGPL-3.0-or-later**.
- WireGuard is now built **entirely from source** (wireguard-go + wireguard-tools)
  instead of a prebuilt library.
- The release build is **byte-for-byte reproducible**, enforced in CI.

## [1] — versionCode 1

### Added
- Initial release: create, list, edit and delete WireGuard tunnel profiles.
- Import configurations by QR code or `.conf` file.
- Connect/disconnect with a per-tunnel switch (userspace backend, no root).
