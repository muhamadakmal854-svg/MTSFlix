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

# --- 4. Patch RepositoryManager.kt for legacy MD5 verification fallback ---
echo "[4/4] Patching RepositoryManager.kt to add legacy MD5 hash validation fallback..."
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
        
    if changed:
        open(repo_mgr_path, 'w', encoding='utf-8').write(content)
        print("  OK: RepositoryManager.kt patched successfully")
    else:
        print("  INFO: RepositoryManager.kt already patched or targets not found")
PYEOF

# --- 5. Patch MainActivity.kt for permanent repo & auto-download plugins ---
echo "[5/5] Patching MainActivity.kt for permanent repo & auto-download plugins..."
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

    # Inject the permanent repo and auto-download logic right after super.onCreate(savedInstanceState)
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
        print("  OK: Permanent repo and auto-loader injected into MainActivity")

    if changed:
        open(main_path, 'w', encoding='utf-8').write(content)
        print("  OK: MainActivity.kt patched successfully")
    else:
        print("  INFO: MainActivity.kt already patched or target not found")
PYEOF

echo "======================================================"
echo "    MTSFlix Customization Complete! (Fresh Minimal)"
echo "======================================================"
