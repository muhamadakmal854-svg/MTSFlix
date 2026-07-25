#!/bin/bash
# ==============================================================
#  MTSFlix Customization Script v4.0 (Zero Setup Screen & Direct KV Cloud Sync)
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
    c = re.sub(r'applicationId\s*=\s*"com\.lagradost\.cloudstream3"', 'applicationId = "com.mts.mtsflix"', c)
    c = re.sub(r'applicationIdSuffix\s*=\s*"\.(prerelease|debug)"', 'applicationIdSuffix = ""', c)
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

# --- 4. Patch RepositoryManager.kt & DataStoreHelper.kt --------------------
echo "[4/12] Patching RepositoryManager.kt & DataStoreHelper.kt for instant cloud sync..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')

# 1. RepositoryManager.kt
repo_mgr_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt'
if os.path.exists(repo_mgr_path):
    content = open(repo_mgr_path, encoding='utf-8').read()
    changed = False
    
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

    remove_repo_target = '''    suspend fun removeRepository(context: Context, repository: RepositoryData) {
        val extensionsDir = File(context.filesDir, ONLINE_PLUGINS_FOLDER)'''
        
    remove_repo_replacement = '''    suspend fun removeRepository(context: Context, repository: RepositoryData) {
        if (repository.url == "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json") return
        val extensionsDir = File(context.filesDir, ONLINE_PLUGINS_FOLDER)'''
        
    if remove_repo_target in content:
        content = content.replace(remove_repo_target, remove_repo_replacement)
        changed = True
        
    if changed:
        open(repo_mgr_path, 'w', encoding='utf-8').write(content)
        print("  OK: RepositoryManager.kt patched successfully")

# 2. DataStoreHelper.kt (Auto sync history to cloud on every video watch & resume position change)
dsh_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/utils/DataStoreHelper.kt'
if os.path.exists(dsh_path):
    c = open(dsh_path, encoding='utf-8').read()
    t_lw = 'isFromDownload\n            )\n        )'
    r_lw = '''isFromDownload
            )
        )
        try {
            context?.let { com.mts.mtsflix.cloud.MTSFlixCloudSync.saveWatchHistory(it) }
        } catch (e: Exception) {}'''
    if t_lw in c and 'saveWatchHistory' not in c:
        c = c.replace(t_lw, r_lw, 1)
    
    t_vp = 'setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))'
    r_vp = '''setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
        try {
            context?.let { com.mts.mtsflix.cloud.MTSFlixCloudSync.saveWatchHistory(it) }
        } catch (e: Exception) {}'''
    if t_vp in c:
        c = c.replace(t_vp, r_vp, 1)
        
    open(dsh_path, 'w', encoding='utf-8').write(c)
    print("  OK: DataStoreHelper.kt patched for instant cloud sync on playback")
PYEOF

# --- 5. Patch MainActivity.kt, SetupFragmentExtensions.kt & SetupFragmentLanguage.kt ---
echo "[5/12] Patching setup fragments to FORCE BYPASS Extensions screen..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')

# 1. SetupFragmentExtensions.kt
ext_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/setup/SetupFragmentExtensions.kt'
if os.path.exists(ext_path):
    c1 = open(ext_path, encoding='utf-8').read()
    t1 = r'override fun onBindingCreated\(binding: FragmentSetupExtensionsBinding\) \{'
    r1 = '''override fun onBindingCreated(binding: FragmentSetupExtensionsBinding) {
        safe {
            try {
                val repositories = RepositoryManager.getRepositories() + PREBUILT_REPOSITORIES
                for (repo in repositories) {
                    PluginsViewModel.downloadAll(activity, repo, null)
                }
            } catch (e: Exception) {}
            findNavController().navigate(R.id.navigation_home)
            return@safe
        }'''
    if 'return@safe' not in c1:
        c1 = re.sub(t1, r1, c1, count=1)
        open(ext_path, 'w', encoding='utf-8').write(c1)
        print("  OK: Patched SetupFragmentExtensions.kt")

# 2. SetupFragmentLanguage.kt
lang_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/setup/SetupFragmentLanguage.kt'
if os.path.exists(lang_path):
    c2 = open(lang_path, encoding='utf-8').read()
    t2 = r'override fun onBindingCreated\(binding: FragmentSetupLanguageBinding\) \{'
    r2 = '''override fun onBindingCreated(binding: FragmentSetupLanguageBinding) {
        safe {
            setKey(HAS_DONE_SETUP_KEY, true)
            findNavController().navigate(R.id.navigation_home)
            return@safe
        }'''
    if 'return@safe' not in c2:
        c2 = re.sub(t2, r2, c2, count=1)
        open(lang_path, 'w', encoding='utf-8').write(c2)
        print("  OK: Patched SetupFragmentLanguage.kt")

# 3. MainActivity.kt
main_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt'
if os.path.exists(main_path):
    c3 = open(main_path, encoding='utf-8').read()
    changed = False

    oncreate_target = 'super.onCreate(savedInstanceState)'
    if 'setKey(HAS_DONE_SETUP_KEY, true)' not in c3 and oncreate_target in c3:
        bypass_code = '''super.onCreate(savedInstanceState)
        setKey(HAS_DONE_SETUP_KEY, true)
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
        c3 = c3.replace(oncreate_target, bypass_code, 1)
        changed = True

    t3_1 = '''        try {
            if (getKey(HAS_DONE_SETUP_KEY, false) != true) {
                navController.navigate(R.id.navigation_setup_language)
                // If no plugins bring up extensions screen
            } else if (PluginManager.getPluginsOnline().isEmpty()
                && PluginManager.getPluginsLocal().isEmpty()
//                && PREBUILT_REPOSITORIES.isNotEmpty()
            ) {
                navController.navigate(
                    R.id.navigation_setup_extensions,
                    SetupFragmentExtensions.newInstance(false)
                )
            }
        } catch (e: Exception) {
            logError(e)
        }'''

    r3_1 = '''        try {
            setKey(HAS_DONE_SETUP_KEY, true)
            ioSafe {
                try {
                    val repos = com.lagradost.cloudstream3.plugins.RepositoryManager.getRepositories()
                    for (r in repos) {
                        com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel.downloadAll(this@MainActivity, r, null)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            logError(e)
        }'''

    if t3_1 in c3:
        c3 = c3.replace(t3_1, r3_1, 1)
        changed = True

    t3_2 = 'navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->'
    r3_2 = '''navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
            if (navDestination.id == R.id.navigation_setup_extensions ||
                navDestination.id == R.id.navigation_setup_language ||
                navDestination.id == R.id.navigation_setup_provider_languages ||
                navDestination.id == R.id.navigation_setup_media ||
                navDestination.id == R.id.navigation_setup_layout) {
                setKey(HAS_DONE_SETUP_KEY, true)
                try {
                    navController.navigate(R.id.navigation_home)
                } catch (e: Exception) {}
            }'''

    if t3_2 in c3 and 'navDestination.id == R.id.navigation_setup_extensions' not in c3:
        c3 = c3.replace(t3_2, r3_2, 1)
        changed = True

    if 'override fun onPause() {' not in c3:
        onpause_code = '''
    override fun onPause() {
        super.onPause()
        try {
            com.mts.mtsflix.cloud.MTSFlixCloudSync.saveWatchHistory(this)
        } catch (e: Exception) {}
    }
'''
        c3 = c3.replace('override fun onDestroy() {', onpause_code + '\n    override fun onDestroy() {')
        changed = True

    if 'runAutoUpdate()' in c3:
        c3 = c3.replace('runAutoUpdate()', 'runAutoUpdate(checkAutoUpdate = false)')
        changed = True

    if changed:
        open(main_path, 'w', encoding='utf-8').write(c3)
        print("  OK: MainActivity.kt patched successfully")
PYEOF

# --- 6. Patch SettingsFragment.kt ---
echo "[6/12] Patching SettingsFragment.kt to ensure Extensions option is VISIBLE..."
python3 - << 'PYEOF'
import os
cs_dir = os.environ.get('CS_DIR','cloudstream')
settings_frag_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsFragment.kt'

if os.path.exists(settings_frag_path):
    content = open(settings_frag_path, encoding='utf-8').read()
    target = 'settingsExtensions.visibility = View.GONE'
    replacement = 'settingsExtensions.visibility = View.VISIBLE'
    if target in content:
        content = content.replace(target, replacement)
        open(settings_frag_path, 'w', encoding='utf-8').write(content)
        print("  OK: Ensured Extensions option is VISIBLE in SettingsFragment")
PYEOF

# --- 7. Patch strings and settings_general.xml ----------------------------
echo "[7/12] Patching donottranslate-strings.xml and settings_general.xml..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')

donottranslate_path = cs_dir + '/app/src/main/res/values/donottranslate-strings.xml'
if os.path.exists(donottranslate_path):
    content = open(donottranslate_path, encoding='utf-8').read()
    malay_notice = """Sebarang isu undang-undang mengenai kandungan dalam aplikasi ini perlulah dirujuk kepada hos fail dan penyedia kandungan sebenar kerana kami tidak mempunyai sebarang kaitan dengan mereka.

        Sekiranya berlaku pelanggaran hak cipta, sila hubungi terus pihak yang bertanggungjawab atau laman web penstriman berkenaan.

        Aplikasi ini adalah untuk kegunaan pendidikan dan peribadi sahaja.

        MTSFlix tidak mengehos sebarang kandungan dalam aplikasi ini, dan tidak mempunyai kawalan ke atas media yang dimasukkan atau dikeluarkan.
        MTSFlix berfungsi seperti mana-mana enjin carian lain, seperti Google. MTSFlix tidak mengehos, memuat naik atau menguruskan sebarang video, filem atau kandungan. Ia hanya merangkak, mengumpul dan memaparkan pautan dalam antara muka yang mudah dan mesra pengguna.

        Ia hanya mengikis laman web pihak ketiga yang boleh diakses secara umum melalui mana-mana pelayar web biasa. Adalah menjadi tanggungjawab pengguna untuk mengelakkan sebarang tindakan yang boleh melanggar undang-undang di kawasan tempatan anda. Gunakan MTSFlix atas risiko anda sendiri."""
    pattern = r'(<string name="legal_notice_text">)(.*?)(</string>)'
    new_content = re.sub(pattern, lambda m: m.group(1) + malay_notice + m.group(3), content, flags=re.DOTALL)
    if new_content != content:
        open(donottranslate_path, 'w', encoding='utf-8').write(new_content)

xml_path = cs_dir + '/app/src/main/res/xml/settings_general.xml'
if os.path.exists(xml_path):
    content = open(xml_path, encoding='utf-8').read()
    content = re.sub(r'\s*<Preference\s+android:icon="@drawable/benene".*?/>', '', content, flags=re.DOTALL)
    content = re.sub(r'\s*<PreferenceCategory android:title="@string/pref_category_links">.*?</PreferenceCategory>', '', content, flags=re.DOTALL)
    open(xml_path, 'w', encoding='utf-8').write(content)
PYEOF

# --- 8. Patch InAppUpdater.kt ----------------------------------------------
echo "[8/12] Patching InAppUpdater.kt for custom update repository..."
python3 - << 'PYEOF'
import os
cs_dir = os.environ.get('CS_DIR','cloudstream')
updater_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/utils/InAppUpdater.kt'

if os.path.exists(updater_path):
    content = open(updater_path, encoding='utf-8').read()
    content = content.replace('private const val GITHUB_USER_NAME = "recloudstream"', 'private const val GITHUB_USER_NAME = "muhamadakmal854-svg"')
    content = content.replace('private const val GITHUB_REPO = "cloudstream"', 'private const val GITHUB_REPO = "MTSFlix"')
    content = content.replace('val appUpdateName = "CloudStream"', 'val appUpdateName = "MTSFlix"')
    open(updater_path, 'w', encoding='utf-8').write(content)
PYEOF

# --- 9. Generate BuildUrls.kt ----------------------------------------------
echo "[9/12] Generating BuildUrls.kt with hardcoded URLs..."
TARGET_LIC="$CS_DIR/app/src/main/java/com/mts/mtsflix/license"
mkdir -p "$TARGET_LIC"
cat > "$TARGET_LIC/BuildUrls.kt" << KTEOF
// AUTO-GENERATED by MTSFlix build script — DO NOT EDIT
package com.mts.mtsflix.license

const val MTS_LICENSE_URL = "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json"
KTEOF

# --- 10. Copy Custom MTSFlix Source Files ----------------------------------
echo "[10/12] Copying custom MTSFlix source files..."
CUSTOM_SRC="$MTSFLIX_DIR/custom_src"
TARGET_PKG="$CS_DIR/app/src/main/java/com/mts/mtsflix"
mkdir -p "$TARGET_PKG"

if [ -d "$CUSTOM_SRC" ]; then
  cp -r "$CUSTOM_SRC/"* "$TARGET_PKG/" 2>/dev/null || true
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
    if 'LicenseCheckActivity' not in c:
        pattern = re.compile(r'<intent-filter[\s\S]*?</intent-filter>')
        c = pattern.sub(lambda m: '' if 'android.intent.action.MAIN' in m.group(0) and ('android.intent.category.LAUNCHER' in m.group(0) or 'android.intent.category.LEANBACK_LAUNCHER' in m.group(0)) else m.group(0), c)
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
except Exception as e:
    print(f'  WARN: {e}')
PYEOF
fi

# --- 12. COMPREHENSIVE 18+ / 18x / HENTAI / VIVAMAX TRACE & BLOCK FILTER ---
echo "[12/12] Patching COMPREHENSIVE adult, Hentai & Vivamax item/row filters..."
python3 - << 'PYEOF'
import os, re
cs_dir = os.environ.get('CS_DIR','cloudstream')

# HomeViewModel.kt: Filter out all 18+ categories AND individual Hentai/Vivamax items
hvm_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt'
if os.path.exists(hvm_path):
    c = open(hvm_path, encoding='utf-8').read()
    target_row = 'home?.items?.forEach { list ->'
    replacement_row = '''home?.items?.forEach { list ->
                            val rowName = list.name.lowercase()
                            val is18Row = rowName.contains("18+") || rowName.contains("18x") || rowName.contains("18") ||
                                          rowName.contains("adult") || rowName.contains("nsfw") || rowName.contains("bokep") ||
                                          rowName.contains("hentai") || rowName.contains("porn") || rowName.contains("vivamax") ||
                                          rowName.contains("semi") || rowName.contains("jav") || rowName.contains("xxx") || rowName.contains("sex") ||
                                          rowName.contains("kelas bintang") || rowName.contains("kelasbintang") || rowName.contains("inem") || rowName.contains("bintang") ||
                                          rowName.contains("philiphines") || rowName.contains("philippines") || rowName.contains("philippine") || rowName.contains("tagalog 18")
                            if (is18Row) return@forEach
                            
                            val cleanItems = list.list.filter { item ->
                                val iName = item.name.lowercase()
                                val iUrl = item.url.lowercase()
                                val is18Item = iName.contains("18+") || iName.contains("18x") || iName.contains("bokep") ||
                                               iName.contains("hentai") || iName.contains("vivamax") || iName.contains("semi") ||
                                               iName.contains("inem") || iName.contains("kelas bintang") || iName.contains("kelasbintang") ||
                                               iName.contains("onaj") || iName.contains("someya") || iName.contains("jav") ||
                                               iName.contains("love wa gal") || iName.contains("gal kara") || iName.contains("hamehara") ||
                                               iName.contains("sekuhara") || iName.contains("enjo kouhai") || iName.contains("enjo") ||
                                               iName.contains("kouhai") || iName.contains("nuki nuki") || iName.contains("nuki") ||
                                               iName.contains("zupposism") || iName.contains("paihame") || iName.contains("hajimaru unmei") ||
                                               iName.contains("donselya") || iName.contains("kesong puti") || iName.contains("kesong") ||
                                               iName.contains("kulong") || iName.contains("virgin") || iName.contains("netorare") ||
                                               iName.contains("ntr") || iName.contains("taimanin") || iName.contains("chikan") ||
                                               iName.contains("oppai") || iName.contains("oyakodon") ||
                                               iUrl.contains("18+") || iUrl.contains("18x") || iUrl.contains("bokep") || iUrl.contains("hentai") ||
                                               iUrl.contains("vivamax") || iUrl.contains("semi") || iUrl.contains("inem") || iUrl.contains("kelasbintang") ||
                                               iUrl.contains("hamehara") || iUrl.contains("enjo") || iUrl.contains("nuki") || iUrl.contains("paihame") || iUrl.contains("donselya")
                                !is18Item
                            }
                            val list = list.copy(list = java.util.concurrent.CopyOnWriteArrayList(cleanItems))'''
    if target_row in c and 'is18Row' not in c:
        c = c.replace(target_row, replacement_row)
        open(hvm_path, 'w', encoding='utf-8').write(c)
        print("  OK: Patched HomeViewModel.kt to filter 18+ Hentai/Vivamax categories & items")

# PluginsViewModel.kt: Hide 18+ plugins in Extensions Download screen
pvm_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/settings/extensions/PluginsViewModel.kt'
if os.path.exists(pvm_path):
    c = open(pvm_path, encoding='utf-8').read()
    target = 'it.plugin.tvTypes?.contains(TvType.NSFW.name) != true || isAdult'
    replacement = '''val tvTypes = it.plugin.tvTypes ?: emptyList()
            val name = it.plugin.name.lowercase()
            val internalName = it.plugin.internalName.lowercase()
            val is18 = tvTypes.any { t -> t.equals(TvType.NSFW.name, ignoreCase = true) || t.contains("18") || t.lowercase().contains("adult") || t.lowercase().contains("nsfw") } ||
                       name.contains("18") || name.contains("adult") || name.contains("nsfw") || name.contains("porn") || name.contains("hentai") || name.contains("xxx") || name.contains("bokep") || name.contains("vivamax") || name.contains("semi") || name.contains("jav") || name.contains("kelasbintang") || name.contains("inem") ||
                       internalName.contains("18") || internalName.contains("adult") || internalName.contains("nsfw") || internalName.contains("porn") || internalName.contains("hentai") || internalName.contains("xxx") || internalName.contains("bokep") || internalName.contains("vivamax") || internalName.contains("semi") || internalName.contains("jav") || internalName.contains("kelasbintang") || internalName.contains("inem")
            !is18'''
    if target in c:
        c = c.replace(target, replacement)
        open(pvm_path, 'w', encoding='utf-8').write(c)
        print("  OK: Patched PluginsViewModel.kt to filter 18+ extensions")

# PluginManager.kt: Prevent loading 18+ plugins
pm_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt'
if os.path.exists(pm_path):
    c = open(pm_path, encoding='utf-8').read()
    pm_target = '//Omit NSFW, if disabled'
    pm_replacement = '''// Omit 18+ / 18x / NSFW / Vivamax / Bokep / Kelas Bintang providers
            val siteNameLower = sitePlugin.name.lowercase()
            val siteInternalLower = sitePlugin.internalName.lowercase()
            val is18Plus = tvtypes.any { t -> t.equals(TvType.NSFW.name, ignoreCase = true) || t.contains("18") || t.lowercase().contains("adult") || t.lowercase().contains("nsfw") } ||
                           siteNameLower.contains("18") || siteNameLower.contains("adult") || siteNameLower.contains("nsfw") || siteNameLower.contains("porn") || siteNameLower.contains("hentai") || siteNameLower.contains("xxx") || siteNameLower.contains("bokep") || siteNameLower.contains("vivamax") || siteNameLower.contains("semi") || siteNameLower.contains("jav") || siteNameLower.contains("kelasbintang") || siteNameLower.contains("inem") ||
                           siteInternalLower.contains("18") || siteInternalLower.contains("adult") || siteInternalLower.contains("nsfw") || siteInternalLower.contains("porn") || siteInternalLower.contains("hentai") || siteInternalLower.contains("xxx") || siteInternalLower.contains("bokep") || siteInternalLower.contains("vivamax") || siteInternalLower.contains("semi") || siteInternalLower.contains("jav") || siteInternalLower.contains("kelasbintang") || siteInternalLower.contains("inem")
            if (is18Plus) {
                Log.i(TAG, "Omit 18+ provider > ${sitePlugin.internalName}")
                return@mapNotNull null
            }
            //Omit NSFW, if disabled'''
    if pm_target in c and 'siteNameLower' not in c:
        c = c.replace(pm_target, pm_replacement)
        open(pm_path, 'w', encoding='utf-8').write(c)
        print("  OK: Patched PluginManager.kt to omit 18+ plugins")

# MainAPI.kt: Filter apis list
mainapi_path = cs_dir + '/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt'
if os.path.exists(mainapi_path):
    c = open(mainapi_path, encoding='utf-8').read()
    api_target = 'apis.withLock {\n            apis = apis + plugin\n        }'
    api_replacement = '''apis.withLock {
            val pName = plugin.name.lowercase()
            val is18 = plugin.supportedTypes.contains(TvType.NSFW) ||
                       pName.contains("18") || pName.contains("nsfw") || pName.contains("adult") ||
                       pName.contains("porn") || pName.contains("hentai") || pName.contains("xxx") ||
                       pName.contains("bokep") || pName.contains("vivamax") || pName.contains("semi") || pName.contains("jav") || pName.contains("kelasbintang") || pName.contains("inem")
            if (!is18) {
                apis = apis + plugin
            }
        }'''
    if api_target in c:
        c = c.replace(api_target, api_replacement)
        open(mainapi_path, 'w', encoding='utf-8').write(c)
        print("  OK: Filtered 18+ providers in MainAPI.kt")

# HomeFragment.kt: Comment out NSFW chip
hf_path = cs_dir + '/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt'
if os.path.exists(hf_path):
    c = open(hf_path, encoding='utf-8').read()
    c = c.replace('Pair(nsfw, listOf(TvType.NSFW)),', '// Pair(nsfw, listOf(TvType.NSFW)),')
    open(hf_path, 'w', encoding='utf-8').write(c)
    print("  OK: Disabled NSFW category chip in HomeFragment.kt")
PYEOF

echo "======================================================"
echo "    MTSFlix Customization Complete! (License Build)"
echo "======================================================"
