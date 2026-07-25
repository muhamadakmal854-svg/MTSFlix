# 🎬 MTSFlix v1.0.7

<div align="center">

![MTSFlix Logo](https://img.shields.io/badge/MTS-FLIX-E50914?style=for-the-badge&labelColor=141414&color=E50914)
![Version](https://img.shields.io/badge/Versi-1.0.7-white?style=for-the-badge&labelColor=141414)
![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=141414)
![TV](https://img.shields.io/badge/Android%20TV-Supported-blue?style=for-the-badge&labelColor=141414)

**Aplikasi streaming video peribadi berasaskan CloudStream 3**
*Dengan sistem Cloud Sync & Multi-Device support*

[📥 Muat Turun APK](https://github.com/muhamadakmal854-svg/MTSFlix/releases/download/v1.0.7/MTSFlix-v1.0.7.apk) • [📊 Pantau Build](https://github.com/muhamadakmal854-svg/MTSFlix/actions) • [📺 TV Pairing](https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/MTSFlix@main/pair/index.html)

</div>

---

## 🆕 Apa Yang Baru — v1.0.7

### ☁️ Cloud Sync v6.0 — Cross-Device Auto Sync

Sejarah tontonan kini disimpan dan dipulihkan secara automatik melalui **GitHub Gist Cloud**.

| Ciri | Penerangan |
|---|---|
| 💾 **Auto Save** | Simpan ke cloud setiap kali tonton, skip, atau tukar status |
| 🔄 **Auto Restore** | Pulih semua data selepas install semula atau clear data |
| 📱 **Multi-Device** | Sync antara 5 device atau lebih dengan akaun Google yang sama |
| ⏱️ **Smart Timestamp** | Hanya restore jika data cloud lebih baru dari data tempatan |
| 🔇 **Senyap** | Semua sync berlaku di balik layar tanpa ganggu pengalaman menonton |

**Data yang disimpan:**
- ▶️ Continue Watching (posisi video)
- ✅ Watching / Completed / On-Hold / Dropped
- 📌 Plan to Watch
- ⭐ Favorites
- 📢 Subscribed

#### Aliran Cross-Device Sync

```
Phone A tonton episod 5
        ↓
Auto simpan ke GitHub Gist Cloud ✅

Phone B / C / D / E buka app
        ↓
Auto tarik dari cloud (setiap kali buka app)
        ↓
Toast: "✅ Sejarah tontonan dikemas kini dari cloud"
        ↓
Continue Watching dikemas kini ✅
```

---

### 👤 Sistem Profil — Multi-Profile Support

Pilih profil sebelum masuk ke app — seperti Netflix!

```
Install App → Verify Lesen → Login Google
        ↓
┌─────────────────────────────┐
│         MTSFlix             │
│      Pilih Profil           │
│  "Siapa yang menonton?"     │
│                             │
│  🔴 A  ahmad@gmail.com ●   │ ← Aktif
│  🔴 F  fatimah@gmail.com   │
│  🔴 S  siti@gmail.com      │
│                             │
│  [+ Tambah Profil Google]   │
└─────────────────────────────┘
        ↓
Restore sejarah profil tersebut
        ↓
Continue Watching profil itu ✅
```

**Ciri Profil:**
- Tambah & padam profil Google
- Setiap profil ada sejarah tontonan tersendiri
- Data di cloud kekal walaupun profil dipadam dari device

---

### 📺 Android TV / Google TV Support

Semua skrin kini boleh digunakan dengan **remote D-pad**.

| Skrin | TV Support |
|---|---|
| Verify Lesen | ✅ D-pad navigation + focus indicator |
| Google Sign-In | ✅ D-pad navigation + focus indicator |
| Pilih Profil | ✅ D-pad navigation + scale animation |
| QR Code Pairing | ✅ Scan dengan telefon, tanpa taip dengan remote |

**Focus Indicator:**
- 🔴 Border merah terang apabila butang/profil dipilih dengan remote
- 📏 Saiz sedikit membesar (scale) untuk pengesahan visual
- 📝 Subtitle "Gunakan ▲▼ untuk navigasi, OK untuk pilih" di TV

---

### 📺 QR Code TV Pairing — Log Masuk Tanpa Remote Keyboard

Log masuk Google di Android TV kini **semudah scan QR Code**!

#### Cara Guna:

**Langkah 1** — Di skrin TV, pilih:
> `Log Masuk Google` → `📺 Log Masuk via QR Code (Android TV)`

**Langkah 2** — Skrin TV akan papar:
```
┌─────────────────────────────────┐
│  ┌──────────┐  KOD PAIRING:     │
│  │ QR CODE  │  MTS-7K4P         │
│  └──────────┘                   │
│  Atau buka: cdn.jsdelivr.net/   │
│  gh/muhamadakmal854-svg/        │
│  MTSFlix@main/pair/             │
│  ⏳ Menunggu... 4:52            │
└─────────────────────────────────┘
```

**Langkah 3** — Telefon scan QR / buka URL:
```
┌─────────────────────────┐
│  MTSFlix Pairing TV     │
│  Kod: [MTS-7K4P] ←auto │
│  Gmail: ___@gmail.com   │
│  [✅ Sahkan & Log Masuk]│
└─────────────────────────┘
```

**Langkah 4** — TV auto log masuk dalam **5 saat** ✅

> **Pairing URL:** https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/MTSFlix@main/pair/index.html

---

### 🔒 Keselamatan & Privasi

| Aspek | Perlindungan |
|---|---|
| Extensions Menu | ❌ Disembunyikan (untuk elak kecurian URL) |
| Download Extension | ✅ Automatik di balik layar sahaja |
| Data Cloud | 🔐 Private GitHub Gist (hanya pemilik akaun yang boleh akses) |
| Pairing Code | ⏰ Tamat tempoh dalam 5 minit & Gist dipadam selepas guna |
| Akaun Google | 💾 Disimpan secara tempatan di device sahaja |

---

## 📋 Sejarah Versi

| Versi | Tarikh | Perubahan Utama |
|---|---|---|
| **v1.0.7** | 26 Jul 2026 | Cloud Sync v6.0, Multi-Profile, Android TV D-pad, QR Pairing |
| v1.0.6 | — | Cloud Sync v5.0, Hooks setViewPos & setBookmarkedData |
| v1.0.5 | — | Bug fix Extensions bypass, silent background download |
| v1.0.4 | — | Hide Extensions menu, remove Accounts section |
| v1.0.3 | — | Verify Lesen & Google Sign-In |
| v1.0.0 | — | Pelancaran awal MTSFlix |

---

## 🧪 Cara Test Cloud Sync

### Test Asas (1 Device)
1. Install APK v1.0.7
2. Verify lesen → Login Google → Pilih profil
3. Tonton beberapa episod (tunggu 5-10 saat)
4. **Clear Data** atau **Uninstall** app
5. Install semula → Verify → Login dengan email yang **sama**
6. Continue Watching akan muncul semula ✅

### Test Cross-Device (2+ Device)
1. Install & login di **Phone A** dengan `email@gmail.com`
2. Tonton episod di Phone A
3. Buka app di **Phone B** (sama email, sudah login)
4. Continue Watching di Phone B dikemas kini ✅
5. Toast `"✅ Sejarah tontonan dikemas kini dari cloud"` akan muncul

> ⚠️ **Nota Penting:** Kali pertama guna v1.0.7, data lama (dari APK sebelumnya) belum ada di cloud. Tonton semula sekurang-kurangnya 5 saat dulu baru data tersimpan ke cloud.

---

## 🛠️ Maklumat Teknikal

| Komponen | Teknologi |
|---|---|
| Base App | CloudStream 3 |
| Cloud Storage | GitHub Gist (Private) |
| Sync Engine | MTSFlixCloudSync v6.0 |
| Pairing Relay | GitHub Gist API |
| Pairing Web | jsDelivr CDN (Static HTML) |
| QR Generator | api.qrserver.com |
| Threading | `Thread{}.start()` (background) |
| Data Format | JSON Key-Value dengan typed encoding |

---

## 📦 Muat Turun

| Platform | Link |
|---|---|
| 📱 Android Phone/Tablet | [MTSFlix-v1.0.7.apk](https://github.com/muhamadakmal854-svg/MTSFlix/releases/download/v1.0.7/MTSFlix-v1.0.7.apk) |
| 📺 Android TV / Google TV | [MTSFlix-v1.0.7.apk](https://github.com/muhamadakmal854-svg/MTSFlix/releases/download/v1.0.7/MTSFlix-v1.0.7.apk) (sama) |
| 🌐 TV Pairing Web | [cdn.jsdelivr.net/...pair/](https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/MTSFlix@main/pair/index.html) |

---

<div align="center">

**MTSFlix © 2026 MTS • Hak Cipta Terpelihara**

*Dibina dengan ❤️ berasaskan [CloudStream 3](https://github.com/recloudstream/cloudstream)*

</div>
