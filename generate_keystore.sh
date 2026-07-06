#!/bin/bash
# ============================================================
#  MTSFlix Keystore Generator
#  Jalankan SEKALI sahaja untuk generate signing keystore APK
# ============================================================
set -e

JKS="mtsflix-release.jks"
ALIAS="mtsflix"
PASS=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9@#%' | fold -w 24 | head -1 2>/dev/null || echo "MTSFlix2025SecureKey!")

echo ""
echo "╔════════════════════════════════════════════════╗"
echo "║        MTSFlix Keystore Generator             ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

# Check existing
if [ -f "$JKS" ]; then
  echo "AMARAN: Keystore sudah wujud!"
  echo "Jika anda generate semula, APK lama tidak boleh dikemaskini."
  read -p "Teruskan? (y/N): " C
  [ "$C" != "y" ] && { echo "Dibatalkan. Guna keystore sedia ada."; exit 0; }
  cp "$JKS" "$JKS.backup.$(date +%Y%m%d_%H%M%S)"
fi

command -v keytool &>/dev/null || { echo "ERROR: keytool tidak dijumpai. Pasang JDK 21."; exit 1; }

echo "Generating keystore..."
keytool -genkey -v \
  -keystore "$JKS" \
  -storetype JKS \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias "$ALIAS" \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=MTSFlix,OU=MTS,O=MTSFlix,L=Malaysia,S=Malaysia,C=MY"

KEYSTORE_B64=$(base64 -w 0 "$JKS" 2>/dev/null || base64 "$JKS")

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║    SALIN NILAI INI KE GITHUB SECRETS (MTSFlix repo)       ║"
echo "╠════════════════════════════════════════════════════════════╣"
echo ""
echo "Secret: KEYSTORE_BASE64"
echo "$KEYSTORE_B64"
echo ""
echo "Secret: KEYSTORE_PASSWORD"
echo "$PASS"
echo ""
echo "Secret: KEY_ALIAS"
echo "$ALIAS"
echo ""
echo "Secret: KEY_PASSWORD"
echo "$PASS"
echo ""

cat > keystore-secrets.txt << EOF
MTSFlix Keystore Secrets — $(date)

KEYSTORE_PASSWORD = $PASS
KEY_ALIAS = $ALIAS
KEY_PASSWORD = $PASS

KEYSTORE_BASE64 =
$KEYSTORE_B64
EOF

echo "Secrets disimpan: keystore-secrets.txt"
echo ""
echo "AMARAN:"
echo "  1. Simpan $JKS dan keystore-secrets.txt dengan selamat"
echo "  2. JANGAN commit ke GitHub (sudah ada dalam .gitignore)"
echo "  3. Jika keystore hilang, APK tidak boleh dikemaskini"
echo ""
echo "Tambah ke .gitignore (sudah termasuk secara automatik):"
echo "  echo '*.jks' >> .gitignore"
echo "  echo 'keystore-secrets.txt' >> .gitignore"
echo ""
echo "Selesai! Tambah 4 secrets ke GitHub dan APK akan auto-sign."
