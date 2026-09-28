#!/usr/bin/env bash
# Seed demo data and capture README screenshots on a connected device.
# Requires: debug APK installed, adb, a device/emulator.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/screenshots"
mkdir -p "$OUT"

adb shell cmd uimode night no >/dev/null || true
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null

adb shell am force-stop app.taskdav
adb shell am broadcast -n app.taskdav/.debug.DemoSeedReceiver -a app.taskdav.debug.SEED_DEMO
sleep 3

capture() {
  local tab="$1" file="$2"
  adb shell am start -W -n app.taskdav/.MainActivity --es app.taskdav.EXTRA_NAV_TAB "$tab" >/dev/null
  sleep 2.2
  adb exec-out screencap -p > "$OUT/$file"
  echo "wrote $file"
}

capture home 01-home.png
capture calendar 02-calendar.png
capture tasks 03-tasks.png
capture notes 04-notes.png
capture settings 05-settings.png

adb shell am start -W -n app.taskdav/.MainActivity --es app.taskdav.EXTRA_NAV_TAB settings >/dev/null
sleep 1.5
adb shell input tap 540 520
sleep 1.8
adb exec-out screencap -p > "$OUT/06-appearance.png"
echo "wrote 06-appearance.png"

adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
echo "Done → $OUT"
