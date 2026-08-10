# Changelog

All notable changes to KabelWacht are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Per-release notes shown
in F-Droid live in `fastlane/metadata/android/en-US/changelogs/`.

## [Unreleased]

### Added
- New tunnels (blank or imported) are prefilled with a free auto-generated
  name (`wg-tunnel-1`, `wg-tunnel-2`, …).

### Changed
- Importing a configuration (QR or file) that exactly matches a stored tunnel
  now says so instead of offering a second import.

## [6] — versionCode 6

### Added
- The tunnel editor now shows structured **Interface** and **Peer** sections
  (typed fields, masked keys, add/remove peers, one-tap key generation); the
  full wg-quick text remains available in a collapsed "Raw configuration"
  expander, kept in two-way sync.
- Export a tunnel to a standard wg-quick `.conf` file, gated by device
  authentication (the file contains the private key), with guidance that
  enrolling another device freshly beats transferring credentials.
- Phone screenshots in the F-Droid listing (and, small, on the repository's
  landing page).

### Fixed
- The editor title no longer stays "New tunnel" when editing an existing one.

## [5] — versionCode 5

### Changed
- **Signing keys rotated**: both the APK signing key and the repository index
  key are now EC P-256 identities derived from single seed secrets
  (`scripts/derive-signing-key.py`); public certificates are pinned in
  `signing/`. One-time: uninstall the old app, remove and re-add the
  repository (new fingerprint), reinstall.

## [4] — versionCode 4

### Changed
- Build toolchain upgraded to AGP 9.2.0 / Gradle 9.6.1 (built-in Kotlin);
  reproducibility re-verified. Repository icon added to the F-Droid repo entry.
  No functional changes.

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
