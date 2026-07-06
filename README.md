<div align="center">

# 🎬 MTSFlix

**Aplikasi Streaming Movie, Series & Anime untuk Komuniti MTS**

*Berasaskan CloudStream 3 · Firebase Sync · Verified Device Access*

[![Build](https://img.shields.io/github/actions/workflow/status/muhamadakmal854-svg/MTSFlix/build_release.yml?branch=main&label=Build%20APK&style=flat-square&color=success)](../../actions)
[![Release](https://img.shields.io/github/v/release/muhamadakmal854-svg/MTSFlix?label=Latest&style=flat-square&color=E50914)](../../releases/latest)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green?style=flat-square)](../../releases)

**[📥 Download APK](../../releases/latest)** · **[📋 Senarai Provider](https://github.com/muhamadakmal854-svg/Provider)** · **[💬 @muhamadakmal854](https://t.me/muhamadakmal854)**

</div>

---

## 🚀 Cara Install (Pengguna)

### Langkah 1 — Download APK
Pergi ke **[Releases](../../releases/latest)** dan download `MTSFlix-vX.X.X.apk`

### Langkah 2 — Install
Aktifkan *Sumber Tidak Diketahui* → Install APK

### Langkah 3 — Pengesahan Peranti
```
Buka MTSFlix → Skrin pengesahan muncul
→ Salin KOD PERANTI anda (MTSF-XXXX-XXXX-XXXX)
→ Hubungi admin: t.me/muhamadakmal854
→ Admin daftarkan → Tap "Cuba Semula" dalam app
→ Login Google (pilihan, untuk sync)
→ Selamat menonton! 🎬
```

### Cara Update APK
MTSFlix akan **auto-detect update** dan tawarkan download bila buka app. Tiada perlu uninstall.

---

## 📋 Cara Kerja

### Aliran Pengesahan Peranti

```
Buka App
   ↓
Jana Kod Unik Peranti (SHA-256 hardware fingerprint)
   ↓
Fetch licenses.json dari GitHub
   ↓
Kod AKTIF + BELUM TAMAT? ──YES──→ Masuk App → Login Google (pilihan)
                          ─ NO──→ Tunjuk Kod + Arahan Hubungi Admin
```

### Provider Auto-Sync (Tiada APK baru diperlukan)

```
Admin tambah/update provider di repo Provider
   ↓
GitHub Actions build .cs3 plugin
   ↓
plugins.json dikemaskini di branch builds
   ↓
Semua pengguna auto-dapat provider baru bila buka app 🔄
```

### APK Auto-Update

```
App buka → fetch version.json dari GitHub
   ↓
Versi baru ada? → Tunjuk dialog "Update Sekarang"
   ↓
Download APK via DownloadManager
   ↓
Install (sama signing key = data TIDAK hilang) ✅
```

---

## 👑 Panduan Admin — License Manager

### Perintah Penuh

```bash
# Tambah lesen baru
bash manage_license.sh add "Ahmad Ali" "ahmad@gmail.com" "MTSF-A1B2-C3D4-E5F6" "2026-12-31" "Samsung S24"

# Padam lesen secara kekal (perlu taip DELETE untuk sahkan)
bash manage_license.sh delete "MTSF-A1B2-C3D4-E5F6"

# Gantung lesen (data kekal, boleh unban)
bash manage_license.sh ban "MTSF-A1B2-C3D4-E5F6" "Melanggar syarat"

# Aktifkan semula lesen
bash manage_license.sh unban "MTSF-A1B2-C3D4-E5F6"

# Perbaharui tarikh tamat
bash manage_license.sh renew "MTSF-A1B2-C3D4-E5F6" "2027-12-31"

# Senarai semua lesen
bash manage_license.sh list
bash manage_license.sh list --active    # hanya aktif
bash manage_license.sh list --banned    # hanya banned
bash manage_license.sh list --expired   # hanya tamat

# Maklumat terperinci
bash manage_license.sh info "MTSF-A1B2-C3D4-E5F6"

# Simulasi semakan app
bash manage_license.sh check "MTSF-A1B2-C3D4-E5F6"

# Push ke GitHub (auto-push selepas setiap operasi, atau manual)
bash manage_license.sh push

# Statistik
bash manage_license.sh stats

# Eksport ke CSV
bash manage_license.sh export csv
```

### Keselamatan Data
- Data lama **TIDAK AKAN HILANG** apabila push ke GitHub
- Data hanya hilang apabila admin jalankan `delete` secara eksplisit
- `ban` menggantung akses tanpa memadam data (boleh `unban` kemudian)

---

## 🛠️ Setup Developer (Sekali Sahaja)

### 1. Clone MTSFlix repo
```bash
git clone https://github.com/muhamadakmal854-svg/MTSFlix.git
cd MTSFlix
```

### 2. Generate Keystore
```bash
bash generate_keystore.sh
# Salin output ke GitHub Secrets
```

### 3. Setup Firebase ([console.firebase.google.com](https://console.firebase.google.com/project/mtsflix-592e4))
- Add Android App → Package: `com.mts.mtsflix`
- Download `google-services.json` → encode base64

### 4. Tambah 5 GitHub Secrets
Lihat [SETUP_SECRETS.md](SETUP_SECRETS.md) untuk panduan lengkap.

| Secret | Sumber |
|--------|--------|
| `KEYSTORE_BASE64` | `generate_keystore.sh` |
| `KEYSTORE_PASSWORD` | `generate_keystore.sh` |
| `KEY_ALIAS` | `mtsflix` |
| `KEY_PASSWORD` | `generate_keystore.sh` |
| `GOOGLE_SERVICES_JSON` | Firebase Console |

### 5. Build APK Pertama
```bash
git tag v1.0.0
git push origin v1.0.0
# APK tersedia dalam ~15 minit di Releases
```

---

## 📁 Struktur Repo

```
MTSFlix/
├── .github/workflows/
│   └── build_release.yml      ← Build APK + Release + Update version.json
├── customizations/
│   ├── apply.sh               ← 13-langkah patch CloudStream → MTSFlix
│   └── strings_override.xml
├── custom_src/
│   ├── MTSFlixInit.kt         ← Initializer
│   ├── auth/GoogleSignInHelper.kt
│   ├── license/
│   │   ├── DeviceCodeManager.kt      ← Jana kod SHA-256
│   │   ├── LicenseVerifier.kt        ← Semak licenses.json
│   │   ├── LicenseCheckActivity.kt   ← Skrin pengesahan (LAUNCHER)
│   │   └── DefaultRepoSetup.kt       ← Inject provider URL
│   ├── notifications/EpisodeNotificationWorker.kt
│   ├── sync/FirestoreWatchlistSync.kt
│   └── update/AutoUpdateManager.kt   ← In-app APK auto-update
├── licenses.json              ← Database lesen (edit → push → serta-merta aktif)
├── version.json               ← Auto-update oleh GitHub Actions
├── manage_license.sh          ← CLI pengurusan lesen
├── generate_keystore.sh       ← Jana keystore signing
├── SETUP_SECRETS.md           ← Panduan setup secrets
├── .gitignore
└── README.md
```

---

## 📞 Hubungi Admin

| Platform | Pautan |
|----------|--------|
| **Telegram** | [@muhamadakmal854](https://t.me/muhamadakmal854) |
| **GitHub Issues** | [Buka Issue](../../issues/new) |

**Maklumat untuk daftar peranti:**
1. Kod peranti: `MTSF-XXXX-XXXX-XXXX` (dari skrin pengesahan)
2. Nama penuh
3. Email Google
4. Model peranti

---

<div align="center">

*🎬 MTSFlix · Berasaskan [CloudStream 3](https://github.com/recloudstream/cloudstream) (GPL-3.0)*

*Provider di repo [muhamadakmal854-svg/Provider](https://github.com/muhamadakmal854-svg/Provider)*

</div>
