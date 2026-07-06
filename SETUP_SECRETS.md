# 📋 MTSFlix — Setup GitHub Secrets

## 🔑 Secrets untuk Repo MTSFlix

Pergi ke: **GitHub → MTSFlix repo → Settings → Secrets and variables → Actions → New repository secret**

### 1. Keystore Secrets (untuk sign APK)

Jalankan dalam Git Bash:
```bash
cd C:\Users\mts\Downloads\MTSFlix
bash generate_keystore.sh
```

Tambah 4 secrets dari output:

| Secret Name | Nilai |
|-------------|-------|
| `KEYSTORE_BASE64` | Output `generate_keystore.sh` |
| `KEYSTORE_PASSWORD` | Output `generate_keystore.sh` |
| `KEY_ALIAS` | `mtsflix` |
| `KEY_PASSWORD` | Output `generate_keystore.sh` |

### 2. Firebase Secret (untuk Google Sign-In + Sync)

**Step 1:** Pergi ke [Firebase Console → mtsflix-592e4](https://console.firebase.google.com/project/mtsflix-592e4)

**Step 2:** Project Settings → Add App → Android
- Package: `com.mts.mtsflix`
- Nickname: `MTSFlix`

**Step 3:** Download `google-services.json`

**Step 4:** Encode ke Base64 (Git Bash):
```bash
base64 -w 0 google-services.json
```

**Step 5:** Tambah sebagai:

| Secret Name | Nilai |
|-------------|-------|
| `GOOGLE_SERVICES_JSON` | Output base64 di atas |

### 3. Aktifkan Firebase Services

| Service | Cara |
|---------|------|
| **Authentication** | Authentication → Sign-in method → Google → Enable |
| **Firestore** | Firestore Database → Create database → Production → `asia-southeast1` |

### Firestore Security Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

---

## 🏗️ Setup Repo MTSFlix di GitHub

```bash
cd C:\Users\mts\Downloads\MTSFlix
git init
git add .
git commit -m "feat: MTSFlix v1.0 initial setup"
git branch -M main
git remote add origin https://github.com/muhamadakmal854-svg/MTSFlix.git
git push -u origin main
```

Kemudian buat branch `builds` (jika belum ada):
```bash
git checkout --orphan builds
git rm -rf .
git commit --allow-empty -m "Initial builds branch"
git push origin builds
git checkout main
```

---

## ✅ Senarai Semak

- [ ] `KEYSTORE_BASE64` — tambah ke MTSFlix GitHub Secrets
- [ ] `KEYSTORE_PASSWORD` — tambah ke MTSFlix GitHub Secrets
- [ ] `KEY_ALIAS` — tambah ke MTSFlix GitHub Secrets
- [ ] `KEY_PASSWORD` — tambah ke MTSFlix GitHub Secrets
- [ ] `GOOGLE_SERVICES_JSON` — tambah ke MTSFlix GitHub Secrets
- [ ] Google Sign-In aktif di Firebase
- [ ] Firestore database dibuat
- [ ] MTSFlix repo di-push ke GitHub
- [ ] Provider repo di-push ke GitHub (dengan branch `builds`)

---

## 🚀 Cara Build APK Pertama

Selepas semua secrets ditambah:

**Method 1: Tag**
```bash
git tag v1.0.0
git push origin v1.0.0
```

**Method 2: Manual Dispatch**
- GitHub → Actions → 🎬 MTSFlix Release Build → Run workflow
- Isi `version_name`: `1.0.0`
- Isi `release_notes`: `Versi pertama MTSFlix`
- Create Release: `true`

APK akan muncul di Releases dalam ~15-20 minit.
