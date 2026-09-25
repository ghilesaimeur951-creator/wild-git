#!/usr/bin/env bash
set -euo pipefail

mkdir -p ui-captures
adb shell wm size 360x800
adb shell wm density 160
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.example.sportchrono android.permission.POST_NOTIFICATIONS
adb shell am start -n com.example.sportchrono/.MainActivity
sleep 5

capture() {
  adb exec-out screencap -p > "ui-captures/$1.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb exec-out cat /sdcard/window.xml > "ui-captures/$1.xml"
}
tap_text() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb exec-out cat /sdcard/window.xml > ui-captures/current.xml
  coords=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
label = sys.argv[1]
nodes = ET.parse('ui-captures/current.xml').iter('node')
for node in nodes:
    if node.get('text') == label:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit('Commande introuvable : ' + label)
PY
)
  adb shell input tap $coords
  sleep 2
}

capture 01-chronos
tap_text 'Séances'
capture 02-seances
tap_text 'Créer ou charger une séance'
capture 03-creation
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb exec-out cat /sdcard/window.xml > ui-captures/current.xml
coords=$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
for node in ET.parse('ui-captures/current.xml').iter('node'):
    if node.get('class') == 'android.widget.EditText':
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    raise SystemExit('Champ de saisie introuvable')
PY
)
adb shell input tap $coords
sleep 2
capture 04-clavier
adb shell input keyevent 4
tap_text '+ Ajouter un exercice'
capture 05-catalogue
