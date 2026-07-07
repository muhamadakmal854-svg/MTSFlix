#!/bin/bash
# ==============================================================================
#            MTSFlix Automated Customization Script (Strict License Only)
# ==============================================================================
set -e

MTSFLIX_DIR="${MTSFLIX_DIR:-$(pwd)}"
CS_DIR="${CS_DIR:-$MTSFLIX_DIR/cloudstream}"

echo "======================================================"
echo "    MTSFlix Customization Script (Strict License)"
echo "    Root  : $MTSFLIX_DIR"
echo "    Target: $CS_DIR"
echo "======================================================"

# --- 1. Change applicationId -----------------------------------------------
echo "[1/6] Changing applicationId to com.mts.mtsflix..."
GRADLE_KTS="$CS_DIR/app/build.gradle.kts"
if [ -f "$GRADLE_KTS" ]; then
  python3 - << 'PYEOF'
import os
path = os.environ.get('CS_DIR','cloudstream') + '/app/build.gradle.kts'
try:
    c = open(path).read()
    c = c.replace('com.lagradost.cloudstream3', 'com.mts.mtsflix')
    open(path,'w').write(c)
    print('  OK: applicationId changed to com.mts.mtsflix')
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
else:
  echo "  WARN: app/build.gradle.kts not found"
fi

# --- 2. Set App Name to MTSFlix --------------------------------------------
echo "[2/6] Setting app name to MTSFlix in all localized resources..."
python3 - << 'PYEOF'
import os, xml.etree.ElementTree as ET
cs_dir = os.environ.get('CS_DIR','cloudstream')
count = 0
for root, dirs, files in os.walk(cs_dir + '/app/src/main/res'):
    if 'values' in os.path.basename(root) and 'strings.xml' in files:
        fpath = os.path.join(root, 'strings.xml')
        try:
            tree = ET.parse(fpath)
            root_el = tree.getroot()
            changed = False
            for string_el in root_el.findall('string'):
                if string_el.get('name') == 'app_name':
                    if string_el.text != 'MTSFlix':
                        string_el.text = 'MTSFlix'
                        changed = True
            if changed:
                tree.write(fpath, encoding='utf-8', xml_declaration=True)
                count += 1
        except Exception as e:
            pass
print(f"  OK: Patched {count} strings.xml files to MTSFlix")
PYEOF

# --- 3. Copy Custom Assets (Logo, Banner) ----------------------------------
echo "[3/6] Copying custom logo and banner (rebranding all icons/drawables)..."
if [ -f "$MTSFLIX_DIR/logo.png" ]; then
  # 1. Clean up and replace launcher icons & foreground
  find "$CS_DIR/app/src/main/res" -name "ic_launcher.png" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher.webp" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher.xml" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_round.png" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_round.webp" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_round.xml" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_foreground.xml" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_launcher_foreground.png" -delete
  
  for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    mkdir -p "$CS_DIR/app/src/main/res/mipmap-$density"
    cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/mipmap-$density/ic_launcher.png"
    cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/mipmap-$density/ic_launcher_round.png"
  done

  # 2. Clean up and replace all CloudStream logos and TV logos
  find "$CS_DIR/app/src/main/res" -name "ic_cloudstream*.xml" -delete
  find "$CS_DIR/app/src/main/res" -name "ic_cloudstream*.png" -delete
  
  # Copy logo as PNG to fallback drawables
  mkdir -p "$CS_DIR/app/src/main/res/drawable"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_launcher_foreground.png"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_cloudstream_monochrome.png"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_cloudstream_monochrome_big.png"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_cloudstreamlogotv.png"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_cloudstreamlogotv_2.png"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_cloudstreamlogotv_pre.png"
  cp "$MTSFLIX_DIR/logo.png" "$CS_DIR/app/src/main/res/drawable/ic_cloudstreamlogotv_pre_2.png"
  
  echo "  OK: logo.png replaced launcher icons, foregrounds, monochromes and TV logos"
else
  echo "  WARN: logo.png not found at root"
fi

if [ -f "$MTSFLIX_DIR/banner.png" ]; then
  # 3. Clean up and replace all banners
  find "$CS_DIR/app/src/main/res" -name "ic_banner*" -delete
  
  for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    mkdir -p "$CS_DIR/app/src/main/res/mipmap-$density"
    cp "$MTSFLIX_DIR/banner.png" "$CS_DIR/app/src/main/res/mipmap-$density/ic_banner.png"
  done
  echo "  OK: banner.png replaced launcher banners"
else
  echo "  WARN: banner.png not found at root"
fi

# --- 4. Generate BuildUrls.kt with hardcoded URLs --------------------------
echo "[4/6] Generating BuildUrls.kt with hardcoded URLs..."
TARGET_LIC="$CS_DIR/app/src/main/java/com/mts/mtsflix/license"
mkdir -p "$TARGET_LIC"
cat > "$TARGET_LIC/BuildUrls.kt" << KTEOF
// AUTO-GENERATED by MTSFlix build script — DO NOT EDIT
package com.mts.mtsflix.license

/** MTSFlix device license database URL */
const val MTS_LICENSE_URL = "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json"
KTEOF
echo "  OK: BuildUrls.kt generated"

# --- 5. Copy Custom MTSFlix Source Files ----------------------------------
echo "[5/6] Copying custom MTSFlix source files..."
CUSTOM_SRC="$MTSFLIX_DIR/custom_src"
TARGET_PKG="$CS_DIR/app/src/main/java/com/mts/mtsflix"
mkdir -p "$TARGET_PKG"

if [ -d "$CUSTOM_SRC" ]; then
  cp -r "$CUSTOM_SRC/"* "$TARGET_PKG/" 2>/dev/null || true
  COUNT=$(find "$TARGET_PKG" -name "*.kt" | wc -l)
  echo "  OK: $COUNT Kotlin files copied"
else
  echo "  WARN: custom_src not found"
fi

# --- 6. Patch AndroidManifest: LicenseCheckActivity as LAUNCHER -----------
echo "[6/6] Setting LicenseCheckActivity as LAUNCHER..."
MANIFEST="$CS_DIR/app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
  python3 - << 'PYEOF'
import re, os
path = os.environ.get('CS_DIR','cloudstream') + '/app/src/main/AndroidManifest.xml'
try:
    c = open(path, encoding='utf-8').read()
    if 'LicenseCheckActivity' in c:
        print('  INFO: LicenseCheckActivity already in manifest')
    else:
        # Remove MAIN/LAUNCHER intent-filters from existing activities
        c = re.sub(
            r'\s*<intent-filter>\s*<action android:name="android\.intent\.action\.MAIN"\s*/>\s*<category android:name="android\.intent\.category\.LAUNCHER"\s*/>\s*</intent-filter>',
            '', c
        )
        # Add LicenseCheckActivity as launcher before </application>
        activity = '''
        <!-- MTSFlix: Device Verification (LAUNCHER) -->
        <activity
            android:name="com.mts.mtsflix.license.LicenseCheckActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:theme="@style/AppTheme">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>'''
        c = c.replace('</application>', activity + '\n    </application>')
        open(path,'w',encoding='utf-8').write(c)
        print('  OK: LicenseCheckActivity set as LAUNCHER')
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
fi

echo "======================================================"
echo "    MTSFlix Customization Complete! (License Build)"
echo "======================================================"
