# Changelog

All notable changes to KabelWacht are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Per-release notes shown
in F-Droid live in `fastlane/metadata/android/en-US/changelogs/`.

## [Unreleased]

### Added
- REUSE compliance, `SECURITY.md`, Gradle dependency verification, community-health
  docs, a privacy policy, and CI supply-chain hardening (SHA-pinned actions,
  Dependabot).

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
