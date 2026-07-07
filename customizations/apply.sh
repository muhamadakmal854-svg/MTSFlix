#!/bin/bash
# ==============================================================
#  MTSFlix Customization Script v2.0.2 (Fix Foreground Icon Compile)
#  Patches CloudStream 3 → MTSFlix
#  13 steps including: branding, Firebase, provider URL,
#  device verification, auto-update, AndroidManifest patches
# ==============================================================
set -e

MTSFLIX_DIR="${MTSFLIX_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
CS_DIR="${CS_DIR:-$(pwd)/cloudstream}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-muhamadakmal854-svg/MTSFlix}"
PROVIDER_GITHUB="${PROVIDER_GITHUB:-muhamadakmal854-svg/Provider}"

MTS_PROVIDER_URL="https://cdn.jsdelivr.net/gh/${PROVIDER_GITHUB}@builds/repo.json"
LICENSE_URL="https://raw.githubusercontent.com/${GITHUB_REPOSITORY}/main/licenses.json"
VERSION_URL="https://raw.githubusercontent.com/${GITHUB_REPOSITORY}/main/version.json"

echo ""
echo "======================================================"
echo "    MTSFlix Customization Script v2.0"
echo "======================================================"
echo ""
echo "  App Repo    : $GITHUB_REPOSITORY"
echo "  Provider    : $PROVIDER_GITHUB"
echo "  Provider URL: $MTS_PROVIDER_URL"
echo "  License URL : $LICENSE_URL"
echo "  Version URL : $VERSION_URL"
echo ""

[ ! -d "$CS_DIR" ] && { echo "ERROR: CloudStream dir not found: $CS_DIR"; exit 1; }

# --- 1. Change applicationId -----------------------------------------------
echo "[1/13] Changing applicationId to com.mts.mtsflix..."
APP_BUILD="$CS_DIR/app/build.gradle.kts"
if [ -f "$APP_BUILD" ]; then
  sed -i 's|applicationId = "com\.lagradost\.cloudstream3"|applicationId = "com.mts.mtsflix"|g' "$APP_BUILD"
  echo "  OK: applicationId = com.mts.mtsflix"
fi

# --- 2. Change App Name ----------------------------------------------------
echo "[2/13] Setting app name to MTSFlix..."
STRINGS_FILE="$CS_DIR/app/src/main/res/values/strings.xml"
if [ -f "$STRINGS_FILE" ]; then
  sed -i 's|<string name="app_name">[^<]*</string>|<string name="app_name">MTSFlix</string>|g' "$STRINGS_FILE"
  echo "  OK: app_name = MTSFlix"
else
  mkdir -p "$CS_DIR/app/src/main/res/values"
  cat > "$CS_DIR/app/src/main/res/values/strings_mtsflix.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">MTSFlix</string>
    <string name="app_description">Tonton movie, series &amp; anime tanpa had</string>
</resources>
EOF
  echo "  OK: strings_mtsflix.xml created"
fi

# --- 3. Add google-services classpath to root build ------------------------
echo "[3/13] Patching root build.gradle for Firebase..."
ROOT_BUILD="$CS_DIR/build.gradle.kts"
if [ -f "$ROOT_BUILD" ] && ! grep -q "google-services" "$ROOT_BUILD"; then
  sed -i '/com\.android\.tools\.build:gradle/a\        classpath("com.google.gms:google-services:4.4.2")' "$ROOT_BUILD" 2>/dev/null || true
  echo "  OK: google-services classpath added"
fi

# --- 4. Add google-services plugin to app build ----------------------------
echo "[4/13] Adding google-services plugin to app/build.gradle.kts..."
if [ -f "$APP_BUILD" ] && ! grep -q "google-services" "$APP_BUILD"; then
  python3 - << 'PYEOF'
import re, os
path = os.environ.get('CS_DIR','cloudstream') + '/app/build.gradle.kts'
try:
    c = open(path).read()
    if 'google-services' not in c:
        c = re.sub(r'(alias\(libs\.plugins\.kotlin\.serialization\))',
                   r'\1\n    id("com.google.gms.google-services") version "4.4.2"', c, count=1)
        open(path,'w').write(c)
        print('  OK: google-services plugin added')
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
fi

# --- 5. Add Firebase + WorkManager dependencies ----------------------------
echo "[5/13] Adding Firebase & WorkManager dependencies..."
if [ -f "$APP_BUILD" ] && ! grep -q "firebase-bom" "$APP_BUILD"; then
  python3 - << 'PYEOF'
import re, os
path = os.environ.get('CS_DIR','cloudstream') + '/app/build.gradle.kts'
deps = """
    // MTSFlix: Firebase, Auth, WorkManager
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // MTSFlix end
"""
try:
    c = open(path).read()
    if 'firebase-bom' not in c:
        c = c.replace('dependencies {', 'dependencies {\n' + deps)
        c += "\n// MTSFlix: Avoid duplicate protobuf classes\nconfigurations.all {\n    exclude(group = \"com.google.firebase\", module = \"protolite-well-known-types\")\n}\n"
        open(path,'w').write(c)
        print('  OK: Firebase deps and exclusions added')
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
fi

# --- 6. Update libs.versions.toml ------------------------------------------
echo "[6/13] Updating version catalog..."
TOML="$CS_DIR/gradle/libs.versions.toml"
if [ -f "$TOML" ] && ! grep -q "google-services" "$TOML"; then
  python3 - << 'PYEOF'
import re, os
path = os.environ.get('CS_DIR','cloudstream') + '/gradle/libs.versions.toml'
try:
    c = open(path).read()
    if 'googleServices' not in c:
        c = re.sub(r'(\[versions\]\n)', r'\1googleServices = "4.4.2"\n', c, count=1)
    if 'google-services' not in c:
        c = re.sub(r'(\[plugins\]\n)',
            r'\1google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }\n',
            c, count=1)
    open(path,'w').write(c)
    print('  OK: libs.versions.toml updated')
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
fi

# --- 7. Generate BuildUrls.kt with hardcoded URLs --------------------------
echo "[7/13] Generating BuildUrls.kt with hardcoded URLs..."
TARGET_LIC="$CS_DIR/app/src/main/java/com/mts/mtsflix/license"
mkdir -p "$TARGET_LIC"
cat > "$TARGET_LIC/BuildUrls.kt" << KTEOF
// AUTO-GENERATED by MTSFlix build script — DO NOT EDIT
package com.mts.mtsflix.license

/** MTS Provider repository URL — hardcoded, auto-syncs on app start */
const val MTS_PROVIDER_URL = "${MTS_PROVIDER_URL}"

/** MTSFlix device license database URL */
const val MTS_LICENSE_URL = "${LICENSE_URL}"

/** MTSFlix version check URL for in-app auto-update */
const val MTS_VERSION_URL = "${VERSION_URL}"
KTEOF
echo "  OK: BuildUrls.kt generated"
echo "      Provider : $MTS_PROVIDER_URL"
echo "      License  : $LICENSE_URL"
echo "      Version  : $VERSION_URL"

# --- 8. Copy Custom MTSFlix Source Files -----------------------------------
echo "[8/13] Copying custom MTSFlix source files..."
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

# --- 8b. Copy Custom Branding Assets (Logo & Banner) ------------------------
echo "Copying custom branding assets..."
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

# --- 9. Patch AndroidManifest: LicenseCheckActivity as LAUNCHER ------------
echo "[9/13] Setting LicenseCheckActivity as LAUNCHER..."
MANIFEST="$CS_DIR/app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
  python3 - << 'PYEOF'
import re, os

path = os.environ.get('CS_DIR','cloudstream') + '/app/src/main/AndroidManifest.xml'
try:
    c = open(path, encoding='utf-8').read()

    # 1. Strip ALL existing intent-filters containing LAUNCHER (including LEANBACK_LAUNCHER)
    pattern = re.compile(r'<intent-filter[\s\S]*?>[\s\S]*?android\.intent\.category\.(?:LEANBACK_)?LAUNCHER[\s\S]*?</intent-filter>')
    c, count = pattern.subn('', c)
    print(f"  Removed {count} original launcher intent filters")

    # 2. If LicenseCheckActivity is already present (just in case), remove it first to avoid duplicates
    c = re.sub(r'\s*<!-- MTSFlix: Device Verification \(LAUNCHER\) -->[\s\S]*?</activity>', '', c)

    # 3. Add LicenseCheckActivity as the sole LAUNCHER before </application>
    activity = '''
        <!-- MTSFlix: Device Verification (LAUNCHER) -->
        <activity
            android:name="com.mts.mtsflix.license.LicenseCheckActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>'''
    c = c.replace('</application>', activity + '\n    </application>')
    open(path,'w',encoding='utf-8').write(c)
    print('  OK: LicenseCheckActivity set as sole LAUNCHER')
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
fi

# --- 10. Patch AndroidManifest: REQUEST_INSTALL_PACKAGES + FileProvider ----
echo "[10/13] Adding REQUEST_INSTALL_PACKAGES + FileProvider for auto-update..."
if [ -f "$MANIFEST" ]; then
  python3 - << 'PYEOF'
import re, os

path = os.environ.get('CS_DIR','cloudstream') + '/app/src/main/AndroidManifest.xml'
try:
    c = open(path, encoding='utf-8').read()
    changed = False

    # Add REQUEST_INSTALL_PACKAGES permission if missing
    if 'REQUEST_INSTALL_PACKAGES' not in c:
        c = re.sub(
            r'(<uses-permission)',
            '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n    \g<1>',
            c, count=1
        )
        changed = True
        print('  OK: REQUEST_INSTALL_PACKAGES permission added')

    # Add FileProvider if missing
    if 'fileprovider' not in c.lower():
        provider = """
        <!-- MTSFlix: FileProvider for APK install -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_provider_paths" />
        </provider>"""
        c = c.replace('</application>', provider + '\n    </application>')
        changed = True
        print('  OK: FileProvider added')

    if changed:
        open(path,'w',encoding='utf-8').write(c)
    else:
        print('  INFO: Permissions already present')

except Exception as e:
    print(f'  WARN: {e}')
PYEOF

  # Create file_provider_paths.xml
  RESDIR="$CS_DIR/app/src/main/res/xml"
  mkdir -p "$RESDIR"
  if [ ! -f "$RESDIR/file_provider_paths.xml" ]; then
    cat > "$RESDIR/file_provider_paths.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<!-- MTSFlix: FileProvider paths for APK auto-update -->
<paths>
    <external-files-path name="apk_download" path="Download/" />
    <external-path name="external_download" path="Download/" />
</paths>
EOF
    echo "  OK: file_provider_paths.xml created"
  fi
fi

# --- 11. Firebase config check --------------------------------------------
echo "[11/13] Firebase config check & patching..."
if [ -f "$CS_DIR/app/google-services.json" ]; then
  echo "  OK: google-services.json present"
  python3 - << 'PYEOF'
import json, os
path = os.environ.get('CS_DIR','cloudstream') + '/app/google-services.json'
try:
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    clients = data.get('client', [])
    if clients:
        existing = {c.get('client_info', {}).get('android_client_info', {}).get('package_name') for c in clients}
        base_client = clients[0]
        for pkg in ['com.mts.mtsflix.prerelease', 'com.mts.mtsflix.nodata']:
            if pkg not in existing:
                new_c = json.loads(json.dumps(base_client))
                new_c['client_info']['android_client_info']['package_name'] = pkg
                app_id = new_c['client_info'].get('mobilesdk_app_id', '')
                if app_id:
                    new_c['client_info']['mobilesdk_app_id'] = app_id + '.' + pkg.split('.')[-1]
                clients.append(new_c)
                print(f"  Added client package: {pkg}")
        data['client'] = clients
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2)
        print("  OK: patched google-services.json successfully")
except Exception as e:
    print(f"  WARN: Failed to patch google-services.json: {e}")
PYEOF
  PROJ=$(python3 -c "import json; d=json.load(open('$CS_DIR/app/google-services.json')); print(d.get('project_info',{}).get('project_id','?'))" 2>/dev/null || echo "?")
  echo "  Project: $PROJ"
else
  echo "  WARN: google-services.json missing"
  echo "  INFO: Add GOOGLE_SERVICES_JSON to GitHub Secrets"
fi

# --- 12. Search for CloudStream's repo preference key ---------------------
echo "[12/13] Checking CloudStream extension repo config..."
python3 - << PYEOF
import os

cs_dir = os.environ.get('CS_DIR','cloudstream')
provider_url = os.environ.get('MTS_PROVIDER_URL','')

found = []
for root, dirs, files in os.walk(cs_dir + '/app/src'):
    dirs[:] = [d for d in dirs if d not in ['build','generated']]
    for fname in files:
        if fname.endswith('.kt'):
            fpath = os.path.join(root,fname)
            try:
                c = open(fpath,encoding='utf-8').read()
                if any(k in c for k in ['PluginRepositories','ExtensionRepos','plugin_repos','EXTENSION_URL']):
                    found.append(fname)
            except: pass

if found:
    print(f'  Found CS repo keys in: {", ".join(found)}')
    print(f'  DefaultRepoSetup.kt will inject: {provider_url}')
else:
    print(f'  INFO: DefaultRepoSetup.kt will inject URL at runtime')
    print(f'  URL: {provider_url}')
PYEOF

# --- 13. Summary ----------------------------------------------------------
echo ""
echo "======================================================"
echo "    MTSFlix Customization Complete!"
echo "======================================================"
echo ""
echo "  Package   : com.mts.mtsflix"
echo "  App Name  : MTSFlix"
echo "  Provider  : $MTS_PROVIDER_URL"
echo "  License   : $LICENSE_URL"
echo "  AutoUpdate: $VERSION_URL"
echo "  Launcher  : LicenseCheckActivity"
FIREBASE_STATUS="Pending (add GOOGLE_SERVICES_JSON secret)"
[ -f "$CS_DIR/app/google-services.json" ] && FIREBASE_STATUS="Ready"
echo "  Firebase  : $FIREBASE_STATUS"
echo ""
