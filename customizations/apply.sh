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
echo "[1/11] Changing applicationId to com.mts.mtsflix..."
APP_BUILD="$CS_DIR/app/build.gradle.kts"
if [ -f "$APP_BUILD" ]; then
  sed -i 's|applicationId = "com\.lagradost\.cloudstream3"|applicationId = "com.mts.mtsflix"|g' "$APP_BUILD"
  echo "  OK: applicationId = com.mts.mtsflix"
fi

# --- 2. Change App Name ----------------------------------------------------
echo "[2/11] Setting app name to MTSFlix in all localized resources..."
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

# --- 3. Copy Custom Assets (Logo, Banner) ----------------------------------
echo "[3/11] Copying custom logo and banner (rebranding all icons/drawables)..."
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

# --- 4. Patch RepositoryManager.kt for legacy MD5 verification fallback ---
echo "[4/11] Patching RepositoryManager.kt to add legacy MD5 hash validation fallback & ensure permanent MTS Repo..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
repo_mgr_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt'

if not os.path.exists(repo_mgr_path):
    print("  WARN: RepositoryManager.kt not found, skipping patch")
else:
    print("  Patching RepositoryManager.kt...")
    content = open(repo_mgr_path, encoding='utf-8').read()
    changed = False
    
    # 1. Inject md5 helper function below sha256 function
    sha256_end = '        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }\n    }'
    md5_func = '''        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    @androidx.annotation.WorkerThread
    fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }'''
    
    if sha256_end in content and 'fun md5(' not in content:
        content = content.replace(sha256_end, md5_func)
        changed = True
        print("  OK: md5 helper function injected")
        
    # 2. Inject verification fallback check
    verify_target = '''            if (expectedFileHash != null) {
                val downloadHash = sha256(tempFile)
                if (expectedFileHash != downloadHash) {
                    tempFile.delete()
                    throw IllegalStateException("Extension hash mismatch when validating \'${file.name}\'! Expected: \'$expectedFileHash\', got: \'$downloadHash\'.")
                }
            }'''
            
    verify_replacement = '''            if (expectedFileHash != null) {
                val downloadHash = sha256(tempFile)
                if (expectedFileHash != downloadHash) {
                    val md5Hash = md5(tempFile)
                    if (expectedFileHash != md5Hash) {
                        tempFile.delete()
                        throw IllegalStateException("Extension hash mismatch when validating \'${file.name}\'! Expected: \'$expectedFileHash\', got: \'$downloadHash\' or \'$md5Hash\'.")
                    }
                }
            }'''
            
    if verify_target in content:
        content = content.replace(verify_target, verify_replacement)
        changed = True
        print("  OK: md5 verification check injected")

    # 3. Intercept getRepositories() to make sure MTS Repo is ALWAYS returned
    get_repos_target = '''    fun getRepositories(): Array<RepositoryData> {
        return getKey(REPOSITORIES_KEY) ?: emptyArray()
    }'''
    
    get_repos_replacement = '''    fun getRepositories(): Array<RepositoryData> {
        val repoUrl = "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json"
        val repoName = "MTS Repo"
        val list = getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
        if (list.none { it.url == repoUrl }) {
            val newRepo = RepositoryData(null, repoName, repoUrl)
            return list + newRepo
        }
        return list
    }'''
    
    if get_repos_target in content:
        content = content.replace(get_repos_target, get_repos_replacement)
        changed = True
        print("  OK: getRepositories intercepted to enforce MTS Repo")

    # 4. Intercept removeRepository() to prevent deletion of MTS Repo
    remove_repo_target = '''    suspend fun removeRepository(context: Context, repository: RepositoryData) {
        val extensionsDir = File(context.filesDir, ONLINE_PLUGINS_FOLDER)'''
        
    remove_repo_replacement = '''    suspend fun removeRepository(context: Context, repository: RepositoryData) {
        if (repository.url == "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json") return
        val extensionsDir = File(context.filesDir, ONLINE_PLUGINS_FOLDER)'''
        
    if remove_repo_target in content:
        content = content.replace(remove_repo_target, remove_repo_replacement)
        changed = True
        print("  OK: removeRepository intercepted to protect MTS Repo")
        
    if changed:
        open(repo_mgr_path, 'w', encoding='utf-8').write(content)
        print("  OK: RepositoryManager.kt patched successfully")
    else:
        print("  INFO: RepositoryManager.kt already patched or targets not found")
PYEOF

# --- 5. Patch MainActivity.kt for permanent repo & auto-download plugins ---
echo "[5/11] Patching MainActivity.kt for permanent repo & auto-download plugins..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
main_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt'

if not os.path.exists(main_path):
    print("  WARN: MainActivity.kt not found, skipping patch")
else:
    print("  Patching MainActivity.kt...")
    content = open(main_path, encoding='utf-8').read()
    changed = False

    # 1. Inject the permanent repo and auto-download logic right after super.onCreate(savedInstanceState)
    oncreate_target = 'super.onCreate(savedInstanceState)'
    oncreate_marker = '// MTSFlix: Permanent repo & Auto-download plugins'
    
    if oncreate_marker not in content and oncreate_target in content:
        bypass_code = '''super.onCreate(savedInstanceState)
        // MTSFlix: Permanent repo & Auto-download plugins
        try {
            val repoUrl = "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json"
            val repoName = "MTS Repo"
            val key = "REPOSITORIES_KEY"
            val currentRepos = getKey<Array<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData>>(key) ?: emptyArray()
            
            // Add repo if not present
            if (currentRepos.none { it.url == repoUrl }) {
                val newRepo = com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(null, repoName, repoUrl)
                setKey(key, currentRepos + newRepo)
                Log.i("MTSFlix", "Added permanent repo: $repoUrl")
            }
            
            // Auto download and load/install all plugins in the background
            com.lagradost.cloudstream3.utils.Coroutines.ioSafe {
                try {
                    val repo = com.lagradost.cloudstream3.plugins.RepositoryManager.parseRepository(repoUrl)
                    if (repo != null) {
                        val plugins = com.lagradost.cloudstream3.plugins.RepositoryManager.getRepoPlugins(
                            com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(null, repoName, repoUrl)
                        )
                        if (plugins != null) {
                            for (pluginWrapper in plugins) {
                                val sitePlugin = pluginWrapper.plugin
                                Log.i("MTSFlix", "Auto downloading/updating plugin: ${sitePlugin.name}")
                                com.lagradost.cloudstream3.plugins.PluginManager.downloadPlugin(
                                    this@MainActivity,
                                    sitePlugin.url,
                                    sitePlugin.fileHash,
                                    sitePlugin.internalName,
                                    repoUrl,
                                    true // loadPlugin
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("MTSFlix", "Error auto loading plugins: " + t.message, t)
                }
            }
        } catch (e: Exception) {
            Log.e("MTSFlix", "Repo setup error: " + e.message)
        }'''
        content = content.replace(oncreate_target, bypass_code, 1)
        changed = True
        print("  OK: Permanent repo and auto-loader injected into MainActivity.onCreate()")

    # 2. Inject permanent repo check into onNewIntent
    onnewintent_target = 'override fun onNewIntent(intent: Intent) {'
    onnewintent_marker = '// MTSFlix: Ensure permanent repo before handling deep link'
    
    if onnewintent_marker not in content and onnewintent_target in content:
        onnewintent_code = '''override fun onNewIntent(intent: Intent) {
        // MTSFlix: Ensure permanent repo before handling deep link
        try {
            val repoUrl = "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json"
            val repoName = "MTS Repo"
            val key = "REPOSITORIES_KEY"
            val currentRepos = getKey<Array<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData>>(key) ?: emptyArray()
            if (currentRepos.none { it.url == repoUrl }) {
                val newRepo = com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(null, repoName, repoUrl)
                setKey(key, currentRepos + newRepo)
            }
        } catch (e: Exception) {}'''
        content = content.replace(onnewintent_target, onnewintent_code, 1)
        changed = True
        print("  OK: Permanent repo check injected into MainActivity.onNewIntent()")

    if changed:
        open(main_path, 'w', encoding='utf-8').write(content)
        print("  OK: MainActivity.kt patched successfully")
    else:
        print("  INFO: MainActivity.kt already patched or target not found")
PYEOF

# --- 6. Patch SettingsFragment.kt to hide Extensions menu option ----------
echo "[6/11] Patching SettingsFragment.kt to hide Extensions option..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
settings_frag_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsFragment.kt'

if not os.path.exists(settings_frag_path):
    print("  WARN: SettingsFragment.kt not found, skipping patch")
else:
    print("  Patching SettingsFragment.kt...")
    content = open(settings_frag_path, encoding='utf-8').read()
    
    # Hide the settingsExtensions view in onBindingCreated
    target = 'binding.apply {'
    replacement = 'binding.apply {\n            settingsExtensions.visibility = View.GONE'
    
    if target in content and 'settingsExtensions.visibility' not in content:
        content = content.replace(target, replacement)
        open(settings_frag_path, 'w', encoding='utf-8').write(content)
        print("  OK: Hided Extensions option in SettingsFragment")
    else:
        print("  INFO: SettingsFragment.kt already patched or target not found")
PYEOF
# --- 7. Patch strings and settings_general.xml ----------------------------
echo "[7/11] Patching donottranslate-strings.xml and settings_general.xml..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')

# 1. Translate legal notice text
donottranslate_path = cs_dir + '/app/src/main/res/values/donottranslate-strings.xml'
if os.path.exists(donottranslate_path):
    print("  Patching donottranslate-strings.xml...")
    content = open(donottranslate_path, encoding='utf-8').read()
    
    malay_notice = """Sebarang isu undang-undang mengenai kandungan dalam aplikasi ini perlulah dirujuk kepada hos fail dan penyedia kandungan sebenar kerana kami tidak mempunyai sebarang kaitan dengan mereka.

        Sekiranya berlaku pelanggaran hak cipta, sila hubungi terus pihak yang bertanggungjawab atau laman web penstriman berkenaan.

        Aplikasi ini adalah untuk kegunaan pendidikan dan peribadi sahaja.

        MTSFlix tidak mengehos sebarang kandungan dalam aplikasi ini, dan tidak mempunyai kawalan ke atas media yang dimasukkan atau dikeluarkan.
        MTSFlix berfungsi seperti mana-mana enjin carian lain, seperti Google. MTSFlix tidak mengehos, memuat naik atau menguruskan sebarang video, filem atau kandungan. Ia hanya merangkak, mengumpul dan memaparkan pautan dalam antara muka yang mudah dan mesra pengguna.

        Ia hanya mengikis laman web pihak ketiga yang boleh diakses secara umum melalui mana-mana pelayar web biasa. Adalah menjadi tanggungjawab pengguna untuk mengelakkan sebarang tindakan yang boleh melanggar undang-undang di kawasan tempatan anda. Gunakan MTSFlix atas risiko anda sendiri."""
        
    pattern = r'(<string name="legal_notice_text">)(.*?)(</string>)'
    
    # Let's replace the content between <string name="legal_notice_text"> and </string>
    def repl_func(match):
        return match.group(1) + malay_notice + match.group(3)
        
    new_content = re.sub(pattern, repl_func, content, flags=re.DOTALL)
    if new_content != content:
        open(donottranslate_path, 'w', encoding='utf-8').write(new_content)
        print("    OK: Translated legal_notice_text to Bahasa Melayu and changed CloudStream to MTSFlix")

# 2. Remove benene and links category from settings_general.xml
xml_path = cs_dir + '/app/src/main/res/xml/settings_general.xml'
if os.path.exists(xml_path):
    print("  Patching settings_general.xml...")
    content = open(xml_path, encoding='utf-8').read()
    
    # Remove benene count preference
    benene_pattern = r'\s*<Preference\s+android:icon="@drawable/benene".*?/>'
    content = re.sub(benene_pattern, '', content, flags=re.DOTALL)
    
    # Remove pref_category_links category
    links_pattern = r'\s*<PreferenceCategory android:title="@string/pref_category_links">.*?</PreferenceCategory>'
    content = re.sub(links_pattern, '', content, flags=re.DOTALL)
    
    open(xml_path, 'w', encoding='utf-8').write(content)
    print("    OK: Removed benene count and links category from settings_general.xml")
PYEOF

# --- 8. Patch InAppUpdater.kt for custom update repository -----------------
echo "[8/11] Patching InAppUpdater.kt for custom update repository..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
updater_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/utils/InAppUpdater.kt'

if not os.path.exists(updater_path):
    print("  WARN: InAppUpdater.kt not found, skipping patch")
else:
    print("  Patching InAppUpdater.kt...")
    content = open(updater_path, encoding='utf-8').read()
    changed = False
    
    # 1. Replace GITHUB_USER_NAME and GITHUB_REPO
    user_target = 'private const val GITHUB_USER_NAME = "recloudstream"'
    user_repl = 'private const val GITHUB_USER_NAME = "muhamadakmal854-svg"'
    if user_target in content:
        content = content.replace(user_target, user_repl)
        changed = True
        print("    OK: Changed GITHUB_USER_NAME to muhamadakmal854-svg")
        
    repo_target = 'private const val GITHUB_REPO = "cloudstream"'
    repo_repl = 'private const val GITHUB_REPO = "MTSFlix"'
    if repo_target in content:
        content = content.replace(repo_target, repo_repl)
        changed = True
        print("    OK: Changed GITHUB_REPO to MTSFlix")
        
    # 2. Change update file name to MTSFlix
    name_target = 'val appUpdateName = "CloudStream"'
    name_repl = 'val appUpdateName = "MTSFlix"'
    if name_target in content:
        content = content.replace(name_target, name_repl)
        changed = True
        print("    OK: Changed appUpdateName to MTSFlix")
        
    if changed:
        open(updater_path, 'w', encoding='utf-8').write(content)
        print("    OK: InAppUpdater.kt patched successfully")
    else:
        print("    INFO: InAppUpdater.kt already patched or targets not found")
PYEOF

# --- 9. Generate BuildUrls.kt with hardcoded URLs --------------------------
echo "[9/11] Generating BuildUrls.kt with hardcoded URLs..."
TARGET_LIC="$CS_DIR/app/src/main/java/com/mts/mtsflix/license"
mkdir -p "$TARGET_LIC"
cat > "$TARGET_LIC/BuildUrls.kt" << KTEOF
// AUTO-GENERATED by MTSFlix build script — DO NOT EDIT
package com.mts.mtsflix.license

/** MTSFlix device license database URL */
const val MTS_LICENSE_URL = "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json"
KTEOF
echo "  OK: BuildUrls.kt generated"

# --- 10. Copy Custom MTSFlix Source Files ----------------------------------
echo "[10/11] Copying custom MTSFlix source files..."
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

# --- 11. Patch AndroidManifest: LicenseCheckActivity as LAUNCHER -----------
echo "[11/11] Setting LicenseCheckActivity as LAUNCHER..."
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
