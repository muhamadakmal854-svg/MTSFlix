#!/bin/bash
# ==============================================================
#  MTSFlix Customization Script v3.2 (Fresh Minimal Rebuild)
#  Patches CloudStream 3 → MTSFlix
# ==============================================================
set -e

MTSFLIX_DIR="${MTSFLIX_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
CS_DIR="${CS_DIR:-$(pwd)/cloudstream}"

[ ! -d "$CS_DIR" ] && { echo "ERROR: CloudStream dir not found: $CS_DIR"; exit 1; }

# --- 1. Change applicationId and strip prerelease suffixes ----------------
echo "[1/12] Patching build.gradle.kts to set applicationId com.mts.mtsflix and remove suffixes..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
app_build = cs_dir + '/app/build.gradle.kts'
if os.path.exists(app_build):
    c = open(app_build, encoding='utf-8').read()
    # 1. Change applicationId
    c = re.sub(r'applicationId\s*=\s*"com\.lagradost\.cloudstream3"', 'applicationId = "com.mts.mtsflix"', c)
    # 2. Remove applicationIdSuffix = ".prerelease" and ".debug"
    c = re.sub(r'applicationIdSuffix\s*=\s*"\.(prerelease|debug)"', 'applicationIdSuffix = ""', c)
    # 3. Remove versionNameSuffix = "-PRE"
    c = re.sub(r'versionNameSuffix\s*=\s*"-PRE"', 'versionNameSuffix = ""', c)
    open(app_build, 'w', encoding='utf-8').write(c)
    print("  OK: build.gradle.kts patched with clean applicationId com.mts.mtsflix")
PYEOF

# --- 2. Change App Name ----------------------------------------------------
echo "[2/12] Setting app name to MTSFlix in all localized resources & flavor directories..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
res_dirs = [
    cs_dir + '/app/src/main/res',
    cs_dir + '/app/src/prerelease/res',
    cs_dir + '/app/src/stable/res'
]
count = 0
for res_dir in res_dirs:
    if os.path.exists(res_dir):
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
echo "[3/12] Copying custom logo and banner (rebranding all icons/drawables)..."
if [ -f "$MTSFLIX_DIR/logo.png" ]; then
  # Clean up existing CloudStream logos in all flavor resource dirs
  for target_res in "$CS_DIR/app/src/main/res" "$CS_DIR/app/src/prerelease/res" "$CS_DIR/app/src/stable/res"; do
    if [ -d "$target_res" ]; then
      find "$target_res" -name "ic_launcher*.png" -delete
      find "$target_res" -name "ic_launcher*.webp" -delete
      find "$target_res" -name "ic_launcher*.xml" -delete
      find "$target_res" -name "ic_cloudstream*.xml" -delete
      find "$target_res" -name "ic_cloudstream*.png" -delete

      for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
        mkdir -p "$target_res/mipmap-$density"
        cp "$MTSFLIX_DIR/logo.png" "$target_res/mipmap-$density/ic_launcher.png"
        cp "$MTSFLIX_DIR/logo.png" "$target_res/mipmap-$density/ic_launcher_round.png"
      done

      mkdir -p "$target_res/drawable"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_launcher_foreground.png"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_cloudstream_monochrome.png"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_cloudstream_monochrome_big.png"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_cloudstreamlogotv.png"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_cloudstreamlogotv_2.png"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_cloudstreamlogotv_pre.png"
      cp "$MTSFLIX_DIR/logo.png" "$target_res/drawable/ic_cloudstreamlogotv_pre_2.png"
    fi
  done
  echo "  OK: logo.png replaced launcher icons in all flavor directories"
else
  echo "  WARN: logo.png not found at root"
fi

if [ -f "$MTSFLIX_DIR/banner.png" ]; then
  for target_res in "$CS_DIR/app/src/main/res" "$CS_DIR/app/src/prerelease/res" "$CS_DIR/app/src/stable/res"; do
    if [ -d "$target_res" ]; then
      find "$target_res" -name "ic_banner*" -delete
      for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
        mkdir -p "$target_res/mipmap-$density"
        cp "$MTSFLIX_DIR/banner.png" "$target_res/mipmap-$density/ic_banner.png"
      done
    fi
  done
  echo "  OK: banner.png replaced launcher banners"
else
  echo "  WARN: banner.png not found at root"
fi

# --- 4. Patch RepositoryManager.kt for legacy MD5 verification fallback ---
echo "[4/12] Patching RepositoryManager.kt to add legacy MD5 hash validation fallback & ensure permanent MTS Repo..."
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

# --- 5. Patch MainActivity.kt for permanent repo & setup wizard bypass ---
echo "[5/12] Patching MainActivity.kt for permanent repo..."
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

    # 1. Inject the permanent repo logic right after super.onCreate(savedInstanceState)
    oncreate_target = 'super.onCreate(savedInstanceState)'
    oncreate_marker = '// MTSFlix: Permanent repo'
    
    if oncreate_marker not in content and oncreate_target in content:
        bypass_code = '''super.onCreate(savedInstanceState)
        // MTSFlix: Permanent repo
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
        } catch (e: Exception) {
            Log.e("MTSFlix", "Repo setup error: " + e.message)
        }'''
        content = content.replace(oncreate_target, bypass_code, 1)
        changed = True
        print("  OK: Permanent repo injected into MainActivity.onCreate()")

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

    # 3. Disable setup wizard navigation (language & extensions)
    pattern_setup = re.compile(r'try\s*\{\s*if\s*\(\s*getKey\(\s*HAS_DONE_SETUP_KEY[\s\S]*?logError\(e\)\s*\}')
    new_content, count = pattern_setup.subn('// MTSFlix: Setup wizard bypassed entirely\n        Log.i("MTSFlix", "Setup wizard bypassed entirely")', content)
    if count > 0:
        content = new_content
        changed = True
        print("  OK: Setup wizard navigation disabled")

    if changed:
        open(main_path, 'w', encoding='utf-8').write(content)
        print("  OK: MainActivity.kt patched successfully")
    else:
        print("  INFO: MainActivity.kt already patched or target not found")
PYEOF

# --- 6. Patch SettingsFragment.kt to ensure Extensions menu option is VISIBLE ---
echo "[6/12] Patching SettingsFragment.kt to ensure Extensions option is VISIBLE..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')
settings_frag_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsFragment.kt'

if not os.path.exists(settings_frag_path):
    print("  WARN: SettingsFragment.kt not found, skipping patch")
else:
    print("  Patching SettingsFragment.kt...")
    content = open(settings_frag_path, encoding='utf-8').read()
    
    # Ensure settingsExtensions is VISIBLE so users can browse & choose providers from MTS Repo
    target = 'settingsExtensions.visibility = View.GONE'
    replacement = 'settingsExtensions.visibility = View.VISIBLE'
    if target in content:
        content = content.replace(target, replacement)
        open(settings_frag_path, 'w', encoding='utf-8').write(content)
        print("  OK: Ensured Extensions option is VISIBLE in SettingsFragment")
    else:
        print("  INFO: SettingsFragment.kt Extensions option is visible")
PYEOF

# --- 7. Patch strings and settings_general.xml ----------------------------
echo "[7/12] Patching donottranslate-strings.xml and settings_general.xml..."
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
echo "[8/12] Patching InAppUpdater.kt for custom update repository..."
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
echo "[9/12] Generating BuildUrls.kt with hardcoded URLs..."
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
echo "[10/12] Copying custom MTSFlix source files..."
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
echo "[11/12] Setting LicenseCheckActivity as LAUNCHER..."
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
        # Remove all existing MAIN/LAUNCHER/LEANBACK_LAUNCHER intent-filters
        pattern = re.compile(r'<intent-filter[\s\S]*?</intent-filter>')
        def repl(match):
            text = match.group(0)
            if 'android.intent.action.MAIN' in text and ('android.intent.category.LAUNCHER' in text or 'android.intent.category.LEANBACK_LAUNCHER' in text):
                return ''
            return text
        c = pattern.sub(repl, c)
        # Add LicenseCheckActivity as launcher before </application>
        activity = '''
        <!-- MTSFlix: Device Verification (LAUNCHER) -->
        <activity
            android:name="com.mts.mtsflix.license.LicenseCheckActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden"
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

# --- 12. Filter 18+/18x/NSFW providers automatically ----------------------
echo "[12/12] Patching automatic 18+/18x/NSFW provider filters..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')

# 1. Filter out 18+/NSFW plugins in PluginsViewModel.kt (Extensions download list)
pvm_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/settings/extensions/PluginsViewModel.kt'
if os.path.exists(pvm_path):
    c = open(pvm_path, encoding='utf-8').read()
    target = 'it.plugin.tvTypes?.contains(TvType.NSFW.name) != true || isAdult'
    replacement = '''val tvTypes = it.plugin.tvTypes ?: emptyList()
            val name = it.plugin.name.lowercase()
            val internalName = it.plugin.internalName.lowercase()
            val is18 = tvTypes.any { t -> t.equals(TvType.NSFW.name, ignoreCase = true) || t.contains("18") || t.lowercase().contains("adult") || t.lowercase().contains("nsfw") } ||
                       name.contains("18+") || name.contains("18x") || name.contains("adult") || name.contains("nsfw") || name.contains("porn") || name.contains("hentai") || name.contains("xxx") ||
                       internalName.contains("18+") || internalName.contains("18x") || internalName.contains("adult") || internalName.contains("nsfw") || internalName.contains("porn") || internalName.contains("hentai") || internalName.contains("xxx")
            !is18'''
    if target in c:
        c = c.replace(target, replacement)
        open(pvm_path, 'w', encoding='utf-8').write(c)
        print("  OK: Filtered 18+ plugins in PluginsViewModel.kt")

# 2. Filter out 18+/NSFW providers in PluginManager.kt (Plugin loading)
pm_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt'
if os.path.exists(pm_path):
    c = open(pm_path, encoding='utf-8').read()
    pm_target = '//Omit NSFW, if disabled'
    pm_replacement = '''// Omit 18+ / 18x / NSFW providers
            val siteNameLower = sitePlugin.name.lowercase()
            val siteInternalLower = sitePlugin.internalName.lowercase()
            val is18Plus = tvtypes.any { t -> t.equals(TvType.NSFW.name, ignoreCase = true) || t.contains("18") || t.lowercase().contains("adult") || t.lowercase().contains("nsfw") } ||
                           siteNameLower.contains("18+") || siteNameLower.contains("18x") || siteNameLower.contains("adult") || siteNameLower.contains("nsfw") || siteNameLower.contains("porn") || siteNameLower.contains("hentai") || siteNameLower.contains("xxx") ||
                           siteInternalLower.contains("18+") || siteInternalLower.contains("18x") || siteInternalLower.contains("adult") || siteInternalLower.contains("nsfw") || siteInternalLower.contains("porn") || siteInternalLower.contains("hentai") || siteInternalLower.contains("xxx")
            if (is18Plus) {
                Log.i(TAG, "Omit 18+ provider > ${sitePlugin.internalName}")
                return@mapNotNull null
            }
            //Omit NSFW, if disabled'''
    if pm_target in c and 'siteNameLower' not in c:
        c = c.replace(pm_target, pm_replacement)
        open(pm_path, 'w', encoding='utf-8').write(c)
        print("  OK: Filtered 18+ plugins in PluginManager.kt")

# 3. Filter out 18+/NSFW providers in APIHolder.kt (MainAPI.kt)
mainapi_path = cs_dir + '/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt'
if os.path.exists(mainapi_path):
    c = open(mainapi_path, encoding='utf-8').read()
    api_target = 'apis.withLock {\n            apis = apis + plugin\n        }'
    api_replacement = '''apis.withLock {
            val pName = plugin.name.lowercase()
            val is18 = plugin.supportedTypes.contains(TvType.NSFW) ||
                       pName.contains("18+") || pName.contains("18x") || pName.contains("nsfw") ||
                       pName.contains("adult") || pName.contains("porn") || pName.contains("hentai") || pName.contains("xxx")
            if (!is18) {
                apis = apis + plugin
            }
        }'''
    if api_target in c:
        c = c.replace(api_target, api_replacement)
        open(mainapi_path, 'w', encoding='utf-8').write(c)
        print("  OK: Filtered 18+ providers in APIHolder (MainAPI.kt)")

# 4. Disable NSFW category chip in HomeFragment.kt
hf_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt'
if os.path.exists(hf_path):
    c = open(hf_path, encoding='utf-8').read()
    hf_target = 'Pair(nsfw, listOf(TvType.NSFW)),'
    hf_replacement = '// Pair(nsfw, listOf(TvType.NSFW)),'
    if hf_target in c:
        c = c.replace(hf_target, hf_replacement)
        open(hf_path, 'w', encoding='utf-8').write(c)
        print("  OK: Disabled NSFW category chip in HomeFragment.kt")
PYEOF

echo "======================================================"
echo "    MTSFlix Customization Complete! (License Build)"
echo "======================================================"
