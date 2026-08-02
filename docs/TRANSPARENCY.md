# Binary transparency

KabelWacht's releases are **publicly logged and independently verifiable**. Three
mechanisms work together so that nobody — including the maintainer or the hosting
platform — can silently hand you a modified build:

1. **Reproducible builds** — the same tagged source produces a byte-identical APK,
   so anyone can regenerate and compare ([how to verify](REPRODUCIBLE.md)).
2. **Provenance attestations in a public transparency log** — CI records every
   released APK, checksum file, and the F-Droid repository's signed index in the
   [Sigstore](https://www.sigstore.dev/) transparency log (Rekor), bound to the
   exact GitHub Actions run, commit, and repository that produced it. An artifact
   that is not in the log was not built by our CI. Logging the *index* also makes
   split-view attacks (serving different users different repository contents)
   detectable.
3. **Append-only publication history** — the published repository lives on the
   [`fdroid-repo`](https://github.com/muelli/KabelWacht/tree/fdroid-repo) git
   branch; every publish is a commit, and the branch is protected against
   force-pushes and deletion. History cannot be quietly rewritten.

A scheduled [transparency monitor](../.github/workflows/transparency-monitor.yml)
re-checks the live repository daily and raises a public alarm on any mismatch.

## Verify an APK yourself

With the [GitHub CLI](https://cli.github.com/) (any authenticated account):

```bash
gh attestation verify kabelwacht-<versionCode>.apk --repo muelli/KabelWacht
```

This confirms the file's digest is in the transparency log and shows exactly which
workflow run and commit built it.

## Verify the live repository (what your F-Droid client sees)

```bash
scripts/verify-transparency.sh
```

This fetches the live index and latest APK from
`https://muelli.github.io/KabelWacht/fdroid/repo`, checks both against the
transparency log, compares the index with the append-only branch, and checks the
signing certificate. Run it from anywhere — the more independent vantage points,
the stronger the guarantee.

## Pin the signing certificate

All published APKs are signed by a certificate with this SHA-256 digest:

```
70dbd684ec0bce8d51d394fa7189c6584fd7bec290d5b447482eca8a64b601a9
```

Check it on any APK with `apksigner verify --print-certs <apk>`, or on-device
with a verifier app such as
[AppVerifier](https://github.com/soupslurpr/AppVerifier). The F-Droid client also
pins this automatically once the repository is added (the repository fingerprint
covers the index signing key; the certificate above covers the APKs).

## Threat model, honestly

| Attack | Caught by |
| --- | --- |
| Modified APK on the web server / CDN | attestation check (monitor or any user) |
| Split view: different index for different users | index attestation + append-only branch |
| Rewriting already-published history | branch protection + Rekor's append-only log |
| Signing key stolen and used outside CI | artifact exists but has no attestation |
| **Compromised CI producing a malicious build** | **not caught by transparency** — this is what reproducible builds are for: rebuild from source and compare |
| Malicious source code | none of the above — source review is the only defence |
