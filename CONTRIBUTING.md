<!--
SPDX-FileCopyrightText: 2026 Tobias Mueller and KabelWacht contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Contributing to KabelWacht

Thanks for your interest in improving KabelWacht! Contributions of code, bug
reports, translations, and documentation are all welcome.

## Getting started

1. Clone with submodules — WireGuard is built from source:
   ```bash
   git clone --recursive https://github.com/muelli/KabelWacht
   ```
2. You need JDK 17, the Android SDK (compile/target 35), the **NDK 27.2.12479018**
   and **CMake 3.22.1**. See the [README](README.md) for details.
3. Build and test:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest lint
   # or emulate CI locally:
   scripts/ci-local.sh
   ```

## Ground rules

- **License.** By contributing, you agree that your contributions are licensed
  under **AGPL-3.0-or-later**, the license of this project.
- **REUSE.** Every file must carry licensing information. New source files should
  start with an SPDX header:
  ```
  // SPDX-License-Identifier: AGPL-3.0-or-later
  // Copyright (C) <year> <you>
  ```
  Run `reuse lint` to check. CI enforces it.
- **No proprietary dependencies.** KabelWacht is 100% free software and must stay
  buildable on F-Droid: no Google Play Services, no closed-source libraries, no
  trackers or analytics.
- **Reproducible builds.** Avoid anything that makes the release build
  non-deterministic. `.github/workflows/reproducible.yml` verifies it.
- **Style.** Follow the surrounding Kotlin style; keep changes focused.

## Pull requests

- Branch from `main`, keep commits logically separated (one concern per commit),
  and write clear commit messages.
- Make sure `assembleDebug`, `testDebugUnitTest`, `lint`, and `reuse lint` pass.
- Describe what and why in the PR; link any related issue.

## Reporting bugs and security issues

- Regular bugs: open a [GitHub issue](https://github.com/muelli/KabelWacht/issues)
  using the template.
- Security vulnerabilities: **do not** open a public issue — follow
  [`SECURITY.md`](SECURITY.md).

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By participating you
are expected to uphold it.
