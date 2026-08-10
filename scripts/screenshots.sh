#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Tobias Mueller and KabelWacht contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Take the fastlane phone screenshots on a headless emulator, fully automated:
#
#   1. builds the debug APK with x86_64 native libs,
#   2. boots the "shots" AVD headless (creates it first if missing),
#   3. installs the app and stages a demo tunnel in its private storage,
#   4. puts the status bar into demo mode (10:00, full battery, clean),
#   5. captures: tunnel list, tunnel editor, add menu,
#   6. writes them to fastlane/metadata/android/en-US/images/phoneScreenshots/.
#
# Usage:
#   scripts/screenshots.sh                 # shoot into the fastlane directory
#   scripts/screenshots.sh --out DIR       # shoot somewhere else (e.g. a preview)
#   scripts/screenshots.sh --keep-running  # leave the emulator up afterwards
#
# Requirements: ANDROID_HOME (or ~/Android/Sdk) with platform-tools, a current
# emulator, and the "system-images;android-35;default;x86_64" image; JDK 17 on
# JAVA_HOME or PATH; python3. First-time cost is the image download (~1.7 GB);
# after that a run takes a few minutes.
set -euo pipefail

cd "$(dirname "$0")/.."

AVD_NAME="${AVD_NAME:-shots}"
SYSTEM_IMAGE="system-images;android-35;default;x86_64"
APP_ID="com.github.muelli.kabelwacht"
OUT_DIR="fastlane/metadata/android/en-US/images/phoneScreenshots"
KEEP_RUNNING=0
while [ $# -gt 0 ]; do
  case "$1" in
    --out) OUT_DIR="$2"; shift 2 ;;
    --keep-running) KEEP_RUNNING=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_HOME
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
[ -x "$ADB" ] || { echo "adb not found at $ADB" >&2; exit 1; }
[ -x "$EMULATOR" ] || { echo "emulator not found at $EMULATOR" >&2; exit 1; }

# Gradle needs a JDK (javac), not just a JRE. If JAVA_HOME is unset or stale,
# find one in the usual places.
if [ ! -x "${JAVA_HOME:-/nonexistent}/bin/javac" ]; then
  JAVA_HOME="$(dirname "$(dirname "$(ls "$HOME"/.local/share/jdk-*/bin/javac \
    /usr/lib/jvm/*/bin/javac 2>/dev/null | head -1)")")"
  [ -x "${JAVA_HOME:-/nonexistent}/bin/javac" ] || {
    echo "No JDK found. Install one, e.g. Temurin 17 into ~/.local/share/," >&2
    echo "or set JAVA_HOME to a JDK (a plain JRE is not enough)." >&2
    exit 1
  }
  export JAVA_HOME
fi

say() { printf '==> %s\n' "$*"; }

say "Building debug APK (x86_64)"
./gradlew :app:assembleDebug -PtunnelAbis=x86_64 -q

if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
  say "Creating AVD $AVD_NAME (pixel_6, $SYSTEM_IMAGE)"
  echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d pixel_6
fi

STARTED_EMULATOR=0
cleanup() {
  if [ "$STARTED_EMULATOR" = 1 ] && [ "$KEEP_RUNNING" = 0 ]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if ! "$ADB" get-state >/dev/null 2>&1; then
  say "Booting $AVD_NAME headless"
  "$EMULATOR" -avd "$AVD_NAME" -no-window -gpu swiftshader_indirect \
    -no-audio -no-boot-anim -no-snapshot >/dev/null 2>&1 &
  STARTED_EMULATOR=1
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 5
  done
  # A freshly booted emulator is still busy (SystemUI can throw an ANR); let it
  # settle and clear the keyguard before driving the UI.
  "$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  sleep 8
fi
say "Device ready: Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r')"

say "Installing the app"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null

say "Staging demo tunnel"
"$ADB" shell "run-as $APP_ID mkdir -p files/tunnels"
"$ADB" shell "run-as $APP_ID sh -c 'cat > files/tunnels/vpn.example.conf'" <<'EOF'
[Interface]
PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
Address = 10.8.0.2/32
DNS = 9.9.9.9

[Peer]
PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
Endpoint = vpn.example.org:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
EOF

say "Setting demo-mode status bar"
"$ADB" shell settings put global sysui_demo_allowed 1
demo() { "$ADB" shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
demo -e command enter
demo -e command clock -e hhmm 1000
demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4
demo -e command notifications -e visible false

# Center coordinates ("x y") of the first UI element whose dump attribute
# matches, or empty if none. Dismisses a "System UI isn't responding" ANR if it
# is covering the screen.
element_coords() { # element_coords <attr> <value>
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 0
  "$ADB" shell cat /sdcard/ui.xml | ATTR="$1" VALUE="$2" python3 -c "
import os, re, sys
attr, value = os.environ['ATTR'], os.environ['VALUE']
pattern = attr + '=\"' + re.escape(value) + r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"'
m = re.search(pattern, sys.stdin.read())
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
"
}

# Wait (up to ~30s) for an element to appear; dismisses SystemUI ANRs on the way.
wait_for_element() { # wait_for_element <attr> <value>
  local coords i
  for i in $(seq 1 15); do
    coords="$(element_coords "$1" "$2")"
    [ -n "$coords" ] && { printf '%s' "$coords"; return 0; }
    # If an ANR dialog is up, wait it out.
    if [ -n "$(element_coords text "Wait")" ]; then
      "$ADB" shell input tap $(element_coords text "Wait") >/dev/null 2>&1 || true
    fi
    sleep 2
  done
  echo "timed out waiting for $1=$2" >&2
  return 1
}

tap_element() { # tap_element <attr> <value>
  # shellcheck disable=SC2046 — coords is 'x y'
  "$ADB" shell input tap $(wait_for_element "$1" "$2")
}

shot() { "$ADB" exec-out screencap -p > "$1"; say "captured $1"; }

mkdir -p "$OUT_DIR"
say "Launching the app"
"$ADB" shell am force-stop "$APP_ID"
"$ADB" shell am start -n "$APP_ID/.MainActivity" >/dev/null
wait_for_element text "vpn.example" >/dev/null   # list drawn with the demo tunnel
sleep 1
shot "$OUT_DIR/1.png"                            # tunnel list

tap_element text "vpn.example"
wait_for_element text "Interface" >/dev/null     # editor drawn
sleep 1
shot "$OUT_DIR/2.png"                            # structured editor

"$ADB" shell input keyevent BACK
wait_for_element content-desc "Add tunnel" >/dev/null
tap_element content-desc "Add tunnel"
wait_for_element text "Scan QR code" >/dev/null  # menu open
sleep 1
shot "$OUT_DIR/3.png"                            # add menu

demo -e command exit
say "Done: $(ls "$OUT_DIR" | tr '\n' ' ')"
