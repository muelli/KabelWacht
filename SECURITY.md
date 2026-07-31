<!--
SPDX-FileCopyrightText: 2026 Tobias Mueller and KabelWacht contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Security Policy

KabelWacht is a VPN client that handles WireGuard private keys, so we take
security reports seriously.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report privately through GitHub's
[private vulnerability reporting](https://github.com/muelli/KabelWacht/security/advisories/new)
(Security → Advisories → *Report a vulnerability*).

If you prefer email, contact the maintainer at the address on their
[GitHub profile](https://github.com/muelli) and put `KabelWacht security` in the
subject.

Please include:

- affected version (`versionCode`/`versionName`) and device/Android version,
- a description of the issue and its impact,
- steps to reproduce or a proof of concept, if available.

## What to expect

- We aim to acknowledge a report within **7 days**.
- We will keep you updated on our assessment and a fix timeline, and credit you in
  the release notes unless you prefer to stay anonymous.
- Please give us a reasonable window to release a fix before public disclosure
  (coordinated disclosure).

## Scope

This policy covers the KabelWacht app in this repository. Vulnerabilities in the
bundled upstream components — [wireguard-go](https://git.zx2c4.com/wireguard-go/),
[wireguard-tools](https://git.zx2c4.com/wireguard-tools/), and the
[WireGuard tunnel library](https://git.zx2c4.com/wireguard-android/) — should be
reported to the WireGuard project via <security@wireguard.com>.
