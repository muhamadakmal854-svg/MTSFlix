#!/bin/bash
# ==============================================================
#  MTSFlix Customization Script v3.0 (Fresh Minimal Rebuild)
#  Patches CloudStream 3 → MTSFlix
# ==============================================================
set -e

MTSFLIX_DIR="${MTSFLIX_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
CS_DIR="${CS_DIR:-$(pwd)/cloudstream}"

[ ! -d "$CS_DIR" ] && { echo "ERROR: CloudStream dir not found: $CS_DIR"; exit 1; }

# --- 1. Change applicationId -----------------------------------------------
echo "[1/3] Changing applicationId to com.mts.mtsflix..."
APP_BUILD="$CS_DIR/app/build.gradle.kts"
if [ -f "$APP_BUILD" ]; then
  sed -i 's|applicationId = "com\.lagradost\.cloudstream3"|applicationId = "com.mts.mtsflix"|g' "$APP_BUILD"
  echo "  OK: applicationId = com.mts.mtsflix"
fi

# --- 2. Change App Name ----------------------------------------------------
echo "[2/3] Setting app name to MTSFlix in all localized resources..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
res_dir = cs_dir + '/app/src/main/res'
if not os.path.exists(res_dir):
    print("  WARN: res directory not found")
else:
    count = 0
    for root, dirs, files in os.walk(res_dir):
        for f in files:
            if f == 'strings.xml':
                path = os.path.join(root, f)
                try:
                    c = open(path, encoding='utf-8').read()
                    pattern = r'<string name="app_name">[^<]*</string>'
                    new_c = re.sub(pattern, '<string name="app_name">MTSFlix</string>', c)
                    if new_c != c:
                        open(path, 'w', encoding='utf-8').write(new_c)
                        count += 1
                except Exception as e:
                    print(f"  Error patching {path}: {e}")
    print(f"  OK: Patched {count} strings.xml files to MTSFlix")
PYEOF

# --- 3. Copy Custom Branding Assets (Logo & Banner) ------------------------
echo "[3/3] Copying custom branding assets..."
if [ -f "$MTSFLIX_DIR/logo.png" ]; then
  # Delete only the default Cloudstream ic_launcher and ic_launcher_round launcher files
  find "$CS_DIR/app/src/main/res" -name "ic_launcher.png" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher.webp" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher.xml" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_round.png" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_round.webp" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_round.xml" -delete
  
  # Copy logo.png to standard mipmap folders
  for dir in mipmap-mdpi mipmap-hdpi mipmap-xhdpi mipmap-xxhdpi mipmap-xxxhdpi; do
    mkdir -p "$CS_DIR/app/src/main/res/$dir"
    cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/$dir/ic_launcher.png"
    cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/$dir/ic_launcher_round.png"
  done
  echo "  OK: app logo.png copied to mipmap resource folders"
else
  echo "  WARN: logo.png not found at root"
fi

if [ -f "$MTSFLIX_DIR/banner.png" ]; then
  find "$CS_DIR/app/src/main/res" -name "ic_banner*" -delete
  mkdir -p "$CS_DIR/app/src/main/res/mipmap-xhdpi"
  cp "$MTSFLIX_DIR/banner.png" "$CS_DIR/app/src/main/res/mipmap-xhdpi/ic_banner.png"
  echo "  OK: banner.png copied as ic_banner.png"
else
  echo "  WARN: banner.png not found at root"
fi

echo "======================================================"
echo "    MTSFlix Customization Complete! (Fresh Minimal)"
echo "======================================================"
