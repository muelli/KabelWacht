# Verifying a reproducible build

KabelWacht's release build is byte-for-byte reproducible: building the same tagged
source with the same toolchain yields an identical APK (apart from the signature).
This lets anyone confirm that a published, signed APK was really built from the
public source — the guarantee F-Droid's "Reproducible Builds" status is based on.

## Toolchain

- JDK 17 (Temurin)
- Android SDK, compile/target SDK 35
- **NDK 27.2.12479018** and **CMake 3.22.1**
- Gradle via the committed wrapper

## Steps

1. Get the published **signed** APK you want to verify — e.g. `kabelwacht-<code>.apk`
   from the [F-Droid repo](https://muelli.github.io/KabelWacht/) or the GitHub
   Release — and note its `versionCode`.

2. Check out the matching tag and its submodules:
   ```bash
   git clone --recursive https://github.com/muelli/KabelWacht
   cd KabelWacht
   git checkout v<code>
   git submodule update --init --recursive
   ```

3. Build the release APK from source:
   ```bash
   ./gradlew clean :app:assembleRelease
   # -> app/build/outputs/apk/release/app-release-unsigned.apk
   ```

4. Compare, ignoring the signature. The rebuilt APK is unsigned and the published
   one is signed, so compare the contents rather than raw bytes — for example with
   [`apksigcopier`](https://github.com/obfusk/apksigcopier):
   ```bash
   pip install apksigcopier
   apksigcopier compare kabelwacht-<code>.apk --unsigned app-release-unsigned.apk
   ```
   `apksigcopier compare` exits `0` when the two APKs are identical except for the
   signature — i.e. the published binary matches the source.

   Alternatively, strip the signatures from both and diff with
   [`diffoscope`](https://diffoscope.org/).

## CI

[`.github/workflows/reproducible.yml`](../.github/workflows/reproducible.yml) proves
reproducibility on every tag by building the release APK twice from two independent
checkouts and asserting the results are byte-identical. Each GitHub Release also
publishes a `SHA256SUMS` file for the release artifacts.
