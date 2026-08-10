# Submitting KabelWacht to the official F-Droid catalogue

The ready-to-submit recipe lives at
[`fdroid/com.github.muelli.kabelwacht.yml`](../fdroid/com.github.muelli.kabelwacht.yml).
Inclusion happens via a merge request to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata); only the maintainer can do
this (it is their GitLab account and their submission).

## Steps

1. Fork <https://gitlab.com/fdroid/fdroiddata> on GitLab and clone it
   (`--depth=1` is fine).
2. Create a branch named after the application id:

   ```bash
   git checkout -b com.github.muelli.kabelwacht
   ```

3. Copy the recipe in, stripping this repo's leading comment block:

   ```bash
   sed '/^#/d;/./,$!d' /path/to/KabelWacht/fdroid/com.github.muelli.kabelwacht.yml \
     > metadata/com.github.muelli.kabelwacht.yml
   ```

4. Optional but recommended checks inside the fdroiddata checkout
   (`pip install fdroidserver`):

   ```bash
   fdroid readmeta
   fdroid lint com.github.muelli.kabelwacht
   ```

5. Commit (convention: `New App: KabelWacht`), push to the fork, and open a
   merge request against `fdroid/fdroiddata` `master`. Fill in the MR
   template's checklist; the fdroiddata CI will attempt a full build.

## What reviewers will see (and why it should sail through)

- **License/deps**: AGPL-3.0-or-later, no proprietary dependencies, no
  trackers; `fdroid scanner` already gates every publish upstream.
- **From-source build**: WireGuard is built from pinned git submodules;
  `submodules: true` + `ndk:` cover it. The one thing worth knowing (already
  noted in `MaintainerNotes`): upstream's libwg-go Makefile downloads a
  pinned, SHA-256-verified Go toolchain from go.dev during the build.
- **Listing content**: descriptions, icon, changelogs and screenshots are
  taken automatically from `fastlane/` in this repository.
- **Reproducible publishing**: `Binaries:` + `AllowedAPKSigningKeys:` ask
  F-Droid to verify their build against the developer-signed release APK and
  publish the developer-signed APK — so f-droid.org installs stay
  update-compatible with this repo's own F-Droid repository.

## If the reproducible verification fights back

F-Droid's build environment differs from our CI (JDK build, toolchain
packaging); if their rebuild does not byte-match and review stalls, the
pragmatic fallback is to drop the `Binaries:` and `AllowedAPKSigningKeys:`
lines from the MR. F-Droid then signs with its own key — the app still ships,
but f-droid.org installs cannot cross-update with our own repo's APKs (users
pick one source). The lines can be re-added later once reproducibility against
their infrastructure is confirmed.

## After the MR is merged

- Each new `v<versionCode>` tag is picked up automatically
  (`AutoUpdateMode: Version v%c`, `UpdateCheckMode: Tags`) — no further MRs
  needed for updates.
- Keep the signing seeds safe: `AllowedAPKSigningKeys` pins the current APK
  certificate; a key change would need an fdroiddata update and breaks
  update paths.
