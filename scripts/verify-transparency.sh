#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Tobias Mueller and KabelWacht contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Verify the LIVE KabelWacht F-Droid repository against the public Sigstore
# transparency log and the append-only git history — i.e. check that what the
# web server is handing out right now is exactly what CI built and published
# for everyone. Anyone can run this from anywhere; the more independent places
# it runs, the stronger the guarantee.
#
# Checks:
#   1. The signed repo index (entry.jar) served live has a provenance
#      attestation in the Sigstore transparency log for this repository.
#   2. The latest APK served live has one too.
#   3. The live index byte-matches the fdroid-repo git branch (append-only).
#   4. (if apksigner is available) The APK's signing certificate matches the
#      pinned digest below.
#
# Requirements: curl, python3, gh (authenticated: `gh auth login` or GH_TOKEN).
set -euo pipefail

REPO_SLUG="${REPO_SLUG:-muelli/KabelWacht}"
BASE="${BASE:-https://muelli.github.io/KabelWacht/fdroid/repo}"
# SHA-256 of the APK signing certificate (see docs/TRANSPARENCY.md).
CERT_SHA256="${CERT_SHA256:-8f02760a5c600a686c7ff452521c7a4be9f8ced922cdc7f9fc639b068625c289}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fail=0
say() { printf '%s\n' "$*"; }

if ! gh attestation --help >/dev/null 2>&1; then
  say "ERROR: your gh CLI lacks the 'attestation' command (needs gh >= 2.49)."
  say "Install a current GitHub CLI from https://cli.github.com/ and retry."
  exit 2
fi

say "==> Fetching live index from $BASE"
curl -fsS "$BASE/index-v2.json" -o "$work/index-v2.json"
curl -fsS "$BASE/entry.jar" -o "$work/entry.jar"

APK_NAME="$(python3 - "$work/index-v2.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
best = None
for pkg in d["packages"].values():
    for v in pkg["versions"].values():
        if best is None or v["manifest"]["versionCode"] > best["manifest"]["versionCode"]:
            best = v
print(best["file"]["name"].lstrip("/"))
PY
)"
say "==> Latest APK per live index: $APK_NAME"
curl -fsS "$BASE/$APK_NAME" -o "$work/$APK_NAME"

say "==> 1/4 Verifying index attestation (Sigstore/Rekor)"
if gh attestation verify "$work/entry.jar" --repo "$REPO_SLUG" >/dev/null; then
  say "    OK: live entry.jar is attested by $REPO_SLUG CI"
else
  say "    FAIL: live entry.jar has NO valid attestation"; fail=1
fi

say "==> 2/4 Verifying APK attestation (Sigstore/Rekor)"
if gh attestation verify "$work/$APK_NAME" --repo "$REPO_SLUG" >/dev/null; then
  say "    OK: live $APK_NAME is attested by $REPO_SLUG CI"
else
  say "    FAIL: live $APK_NAME has NO valid attestation"; fail=1
fi

say "==> 3/4 Comparing live index against the append-only git branch"
curl -fsS -H "Accept: application/vnd.github.raw" \
  ${GH_TOKEN:+-H "Authorization: Bearer $GH_TOKEN"} \
  "https://api.github.com/repos/$REPO_SLUG/contents/fdroid/repo/entry.jar?ref=fdroid-repo" \
  -o "$work/entry.git.jar" || true
if [ -s "$work/entry.git.jar" ] && cmp -s "$work/entry.jar" "$work/entry.git.jar"; then
  say "    OK: live index byte-matches the fdroid-repo branch"
else
  say "    FAIL: live index differs from the fdroid-repo branch"; fail=1
fi

say "==> 4/4 Checking APK signing certificate"
APKSIGNER="$(command -v apksigner || ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$APKSIGNER" ]; then
  certs="$("$APKSIGNER" verify --print-certs "$work/$APK_NAME" 2>&1)" || {
    say "    FAIL: apksigner rejected the APK signature:"; say "$certs"; fail=1; certs=""; }
  if [ -n "$certs" ]; then
    # Tolerate format variations across apksigner versions: take the first
    # certificate SHA-256 digest line and extract the hex digest.
    got="$(printf '%s\n' "$certs" | grep -im1 'certificate SHA-256 digest' \
           | grep -oE '[0-9a-f]{64}' | head -1 || true)"
    if [ "$got" = "$CERT_SHA256" ]; then
      say "    OK: signing certificate matches the pinned digest"
    elif [ -z "$got" ]; then
      say "    FAIL: could not parse a certificate digest from apksigner output:"
      say "$certs"; fail=1
    else
      say "    FAIL: signing certificate is $got, expected $CERT_SHA256"; fail=1
    fi
  fi
else
  say "    SKIP: apksigner not found (install Android build-tools to check)"
fi

if [ "$fail" -ne 0 ]; then
  say ""; say "TRANSPARENCY CHECK FAILED — the published repository does not match"
  say "what CI built and logged. Do not install; please report this immediately:"
  say "https://github.com/$REPO_SLUG/security/advisories/new"
  exit 1
fi
say ""; say "All transparency checks passed."
