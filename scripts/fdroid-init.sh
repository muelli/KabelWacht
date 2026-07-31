#!/usr/bin/env bash
#
# One-time setup for the self-hosted F-Droid repository's INDEX signing key.
#
# This key identifies your repo forever (its fingerprint is what users trust), so
# generate it once, keep the keystore safe, and never change it. It is separate from
# the app (APK) signing key.
#
# Usage:
#   FDROID_KEYSTORE_PASS='...' FDROID_KEY_PASS='...' scripts/fdroid-init.sh
#
# Then add the printed values as GitHub Actions repository secrets:
#   FDROID_KEYSTORE_BASE64, FDROID_KEYSTORE_PASS, FDROID_KEY_PASS, FDROID_KEY_ALIAS
#
set -euo pipefail

KEYSTORE="${1:-kabelwacht-fdroid.p12}"
ALIAS="${FDROID_KEY_ALIAS:-fdroidrepo}"
: "${FDROID_KEYSTORE_PASS:?set FDROID_KEYSTORE_PASS in the environment}"
: "${FDROID_KEY_PASS:?set FDROID_KEY_PASS in the environment}"

if [[ -e "$KEYSTORE" ]]; then
  echo "Refusing to overwrite existing keystore: $KEYSTORE" >&2
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$FDROID_KEYSTORE_PASS" -keypass "$FDROID_KEY_PASS" \
  -dname "CN=KabelWacht F-Droid Repo"

echo
echo "================ GitHub Actions secrets to set ================"
echo "FDROID_KEY_ALIAS = $ALIAS"
echo "FDROID_KEYSTORE_PASS = (the value you just used)"
echo "FDROID_KEY_PASS = (the value you just used)"
echo "FDROID_KEYSTORE_BASE64 = (the base64 blob below, single line)"
echo "--------------------------------------------------------------"
base64 -w0 "$KEYSTORE"; echo
echo "--------------------------------------------------------------"
echo "Repo signing fingerprint (SHA-256, users verify this):"
keytool -list -v -keystore "$KEYSTORE" -storepass "$FDROID_KEYSTORE_PASS" -alias "$ALIAS" \
  | awk -F': ' '/SHA256:/{gsub(/:/,"",$2); print tolower($2); exit}'
echo "=============================================================="
echo "Keep $KEYSTORE backed up and OUT of git."
