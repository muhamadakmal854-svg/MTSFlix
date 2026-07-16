#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys, io
# Fix Windows Unicode output (WAJIB untuk Windows cmd/PowerShell)
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

"""
╔══════════════════════════════════════════════════════════╗
║          MTSFlix Master Control Script v3.0             ║
║  Pengurusan penuh aplikasi MTSFlix dalam Python         ║
╠══════════════════════════════════════════════════════════╣
║  Cara guna:                                             ║
║    python master.py          → Menu interaktif          ║
║    python master.py add      → Tambah lesen             ║
║    python master.py list     → Senarai lesen            ║
║    python master.py ban      → Gantung lesen            ║
║    python master.py push     → Push ke GitHub           ║
║    python master.py build    → Bina APK baru            ║
║    python master.py help     → Bantuan                  ║
╚══════════════════════════════════════════════════════════╝
"""

import os, json, csv, re, hashlib, subprocess, shutil, time
import urllib.request, urllib.error, urllib.parse
from datetime import datetime, date, timedelta
from pathlib import Path
from typing import Optional, List, Dict

# ─── Aktifkan warna ANSI di Windows ─────────────────────────────────────────
os.system('')

# ─── Versi ──────────────────────────────────────────────────────────────────
VERSION = "3.0.0"

# ─── Warna Terminal ──────────────────────────────────────────────────────────
class C:
    RED    = '\033[0;31m'
    GREEN  = '\033[0;32m'
    YELLOW = '\033[1;33m'
    CYAN   = '\033[0;36m'
    WHITE  = '\033[1;37m'
    GRAY   = '\033[0;37m'
    BOLD   = '\033[1m'
    BRED   = '\033[91m'
    BGREEN = '\033[92m'
    BCYAN  = '\033[96m'
    NC     = '\033[0m'

def cprint(text, color=C.NC, end='\n'):
    print(f"{color}{text}{C.NC}", end=end)

def ok(msg):   cprint(f"  [OK]  {msg}", C.GREEN)
def warn(msg): cprint(f"  [!!]  {msg}", C.YELLOW)
def err(msg):  cprint(f"  [ERR] {msg}", C.RED)
def info(msg): cprint(f"  [-->] {msg}", C.CYAN)

# ─── Laluan Fail ─────────────────────────────────────────────────────────────
BASE_DIR      = Path(__file__).parent
LICENSE_FILE  = BASE_DIR / "licenses.json"
VERSION_FILE  = BASE_DIR / "version.json"
CONFIG_FILE   = BASE_DIR / "mtsflix_config.json"

# ─── Konfigurasi Lalai ───────────────────────────────────────────────────────
DEFAULT_CONFIG = {
    "github_token":    "",
    "app_repo":        "muhamadakmal854-svg/MTSFlix",
    "provider_repo":   "muhamadakmal854-svg/Provider",
    "branch":          "main",
    "admin_telegram":  "https://t.me/muhamadakmal854",
    "provider_url":    "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json",
    "license_url":     "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json",
    "version_url":     "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/version.json"
}

# ═══════════════════════════════════════════════════════════════════════════
#  KONFIGURASI
# ═══════════════════════════════════════════════════════════════════════════
class Config:
    _data: Dict = {}

    @classmethod
    def load(cls):
        if CONFIG_FILE.exists():
            try:
                cls._data = json.loads(CONFIG_FILE.read_text(encoding='utf-8'))
            except Exception:
                cls._data = {}
        for k, v in DEFAULT_CONFIG.items():
            cls._data.setdefault(k, v)

    @classmethod
    def save(cls):
        CONFIG_FILE.write_text(
            json.dumps(cls._data, indent=2, ensure_ascii=False),
            encoding='utf-8'
        )

    @classmethod
    def get(cls, key: str, default=None):
        return cls._data.get(key, default)

    @classmethod
    def set(cls, key: str, value):
        cls._data[key] = value
        cls.save()

# ═══════════════════════════════════════════════════════════════════════════
#  PENGESAHAN INPUT
# ═══════════════════════════════════════════════════════════════════════════
def validate_code(code: str) -> bool:
    return bool(re.match(r'^MTSF-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$', code.upper()))

def validate_date(d: str) -> bool:
    try:
        datetime.strptime(d, "%Y-%m-%d")
        return True
    except Exception:
        return False

def default_expiry(days: int = 365) -> str:
    return (date.today() + timedelta(days=days)).strftime("%Y-%m-%d")

def days_remaining(expiry_str: str) -> Optional[int]:
    try:
        return (datetime.strptime(expiry_str, "%Y-%m-%d").date() - date.today()).days
    except Exception:
        return None

# ═══════════════════════════════════════════════════════════════════════════
#  PENGURUS LESEN
# ═══════════════════════════════════════════════════════════════════════════
class LicenseManager:

    @staticmethod
    def _load() -> dict:
        if not LICENSE_FILE.exists():
            data = {
                "version": 1,
                "description": "MTSFlix Device License Registry",
                "lastUpdated": date.today().strftime("%Y-%m-%d"),
                "licenses": []
            }
            LICENSE_FILE.write_text(
                json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8'
            )
            return data
        return json.loads(LICENSE_FILE.read_text(encoding='utf-8'))

    @staticmethod
    def _save(data: dict):
        # PENTING: Simpan SEMUA data, termasuk lesen lama
        # Fungsi ini TIDAK akan membuang data lama
        data["lastUpdated"] = date.today().strftime("%Y-%m-%d")
        LICENSE_FILE.write_text(
            json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8'
        )

    @staticmethod
    def _gen_id(code: str) -> str:
        h = hashlib.md5(code.encode()).hexdigest()[:8].upper()
        return f"LIC-{date.today().strftime('%Y%m%d')}-{h}"

    @staticmethod
    def get_status(entry: dict) -> str:
        if entry.get("banned"):
            return "BANNED"
        if not entry.get("active", True):
            return "SUSPEND"
        days = days_remaining(entry.get("expiredAt", "2099-12-31"))
        if days is not None and days < 0:
            return "TAMAT"
        return "AKTIF"

    # ── TAMBAH LESEN BARU ─────────────────────────────────────────────────
    @classmethod
    def add(cls, name: str, email: str, code: str, expiry: str,
            device: str = "Tidak dinyatakan", note: str = "") -> bool:

        code = code.upper()
        if not validate_code(code):
            err(f"Format kod tidak sah: {code}")
            err("Format sepatutnya: MTSF-XXXX-XXXX-XXXX")
            return False
        if not validate_date(expiry):
            err(f"Format tarikh tidak sah: {expiry}")
            err("Format sepatutnya: YYYY-MM-DD (contoh: 2026-12-31)")
            return False

        data = cls._load()  # Muat SEMUA lesen sedia ada

        existing = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)
        if existing:
            warn(f"Kod {code} sudah wujud bagi: {existing.get('username', '?')}")
            confirm = input("  Kemaskini rekod ini? (y/N): ").strip().lower()
            if confirm != 'y':
                info("Dibatalkan.")
                return False
            # Buang rekod lama, tapi SIMPAN semua yang lain
            data["licenses"] = [l for l in data["licenses"] if l.get("deviceCode") != code]

        entry = {
            "id":           cls._gen_id(code),
            "deviceCode":   code,
            "username":     name,
            "email":        email,
            "expiredAt":    expiry,
            "active":       True,
            "banned":       False,
            "bannedReason": "",
            "addedAt":      date.today().strftime("%Y-%m-%d"),
            "deviceInfo":   device,
            "note":         note,
            "lastModified": datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
        }

        data["licenses"].append(entry)
        cls._save(data)  # Simpan SEMUA lesen (lama + baru)

        print()
        ok(f"Lesen {name} ({code}) berjaya ditambah!")
        info(f"ID    : {entry['id']}")
        info(f"Tamat : {expiry}")
        info(f"Jumlah lesen dalam database: {len(data['licenses'])}")
        return True

    # ── PADAM LESEN (KEKAL) ───────────────────────────────────────────────
    @classmethod
    def delete(cls, code: str) -> bool:
        code = code.upper()
        data = cls._load()
        entry = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)

        if not entry:
            err(f"Kod {code} tidak dijumpai dalam database.")
            return False

        print()
        cprint(f"  Rekod yang akan DIPADAM SELAMA-LAMANYA:", C.YELLOW)
        print(f"  Nama    : {entry.get('username','?')}")
        print(f"  Email   : {entry.get('email','?')}")
        print(f"  Peranti : {entry.get('deviceInfo','?')}")
        print(f"  Status  : {cls.get_status(entry)}")
        print()
        cprint("  AMARAN: Tindakan ini TIDAK BOLEH dibatalkan!", C.RED)
        cprint("  Guna 'ban' jika mahu gantung sementara sahaja.", C.YELLOW)
        print()

        confirm = input("  Taip 'DELETE' untuk mengesahkan: ").strip()
        if confirm != 'DELETE':
            info("Dibatalkan.")
            return False

        before = len(data["licenses"])
        data["licenses"] = [l for l in data["licenses"] if l.get("deviceCode") != code]
        cls._save(data)  # Simpan baki lesen yang lain

        print()
        ok(f"Lesen {code} berjaya dipadam!")
        info(f"Baki: {len(data['licenses'])} lesen dalam database")
        return True

    # ── GANTUNG LESEN (BAN) ───────────────────────────────────────────────
    @classmethod
    def ban(cls, code: str, reason: str = "Digantung oleh admin MTSFlix") -> bool:
        code = code.upper()
        data = cls._load()
        entry = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)

        if not entry:
            err(f"Kod {code} tidak dijumpai.")
            return False
        if entry.get("banned"):
            warn(f"Lesen {entry.get('username','?')} sudah digantung.")
            return False

        entry.update({
            "active":       False,
            "banned":       True,
            "bannedReason": reason,
            "bannedAt":     datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "lastModified": datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
        })
        cls._save(data)

        print()
        ok(f"Lesen {entry.get('username','?')} ({code}) berjaya digantung!")
        info(f"Sebab: {reason}")
        info("Guna 'unban' untuk aktifkan semula.")
        return True

    # ── AKTIFKAN SEMULA (UNBAN) ───────────────────────────────────────────
    @classmethod
    def unban(cls, code: str) -> bool:
        code = code.upper()
        data = cls._load()
        entry = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)

        if not entry:
            err(f"Kod {code} tidak dijumpai.")
            return False

        entry.update({
            "active":       True,
            "banned":       False,
            "bannedReason": "",
            "lastModified": datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
        })
        entry.pop("bannedAt", None)
        cls._save(data)

        print()
        ok(f"Lesen {entry.get('username','?')} ({code}) berjaya diaktifkan semula!")
        return True

    # ── PERBAHARUI TARIKH TAMAT ───────────────────────────────────────────
    @classmethod
    def renew(cls, code: str, new_expiry: str) -> bool:
        code = code.upper()
        if not validate_date(new_expiry):
            err(f"Format tarikh tidak sah: {new_expiry}")
            return False

        data = cls._load()
        entry = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)

        if not entry:
            err(f"Kod {code} tidak dijumpai.")
            return False

        old_expiry = entry.get("expiredAt", "?")
        entry["expiredAt"]    = new_expiry
        entry["lastModified"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
        cls._save(data)

        print()
        ok(f"Lesen {entry.get('username','?')} berjaya diperbaharui!")
        print(f"  Lama : {C.YELLOW}{old_expiry}{C.NC}")
        print(f"  Baru : {C.GREEN}{new_expiry}{C.NC}")
        return True

    # ── SENARAI SEMUA LESEN ───────────────────────────────────────────────
    @classmethod
    def list_all(cls, filter_mode: str = "all"):
        data  = cls._load()
        items = data.get("licenses", [])

        fmap = {"active":"AKTIF","banned":"BANNED","expired":"TAMAT","suspended":"SUSPEND"}
        if filter_mode in fmap:
            items = [l for l in items if cls.get_status(l) == fmap[filter_mode]]

        st_colors = {"AKTIF": C.GREEN, "BANNED": C.RED, "TAMAT": C.YELLOW, "SUSPEND": C.YELLOW}

        print()
        if not items:
            warn(f"Tiada lesen dalam kategori '{filter_mode}'.")
            return

        hdr = f"  {'#':<4} {'Kod Peranti':<22} {'Nama':<20} {'Tamat':<14} {'Status':<10} Peranti"
        cprint(hdr, C.CYAN)
        print(f"  {'-'*92}")

        for i, l in enumerate(items, 1):
            st   = cls.get_status(l)
            col  = st_colors.get(st, C.NC)
            code = l.get("deviceCode","?")
            name = l.get("username","?")[:18]
            exp  = l.get("expiredAt","?")
            dev  = l.get("deviceInfo","?")[:16]
            days = days_remaining(exp)
            ds   = f" ({days}d)" if days is not None and days < 60 else ""
            print(f"  {i:<4} {code:<22} {name:<20} {exp}{ds:<6}  {col}{st:<12}{C.NC} {dev}")

        total = len(data.get("licenses",[]))
        aktif = sum(1 for l in data["licenses"] if cls.get_status(l) == "AKTIF")
        print(f"\n  {C.GRAY}Jumlah: {total} rekod | Aktif: {aktif} | Filter: {filter_mode}{C.NC}")

    # ── MAKLUMAT TERPERINCI ───────────────────────────────────────────────
    @classmethod
    def info(cls, code: str):
        code  = code.upper()
        data  = cls._load()
        entry = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)

        if not entry:
            err(f"Kod {code} tidak dijumpai.")
            return

        st     = cls.get_status(entry)
        st_col = {"AKTIF":C.GREEN,"BANNED":C.RED,"TAMAT":C.YELLOW,"SUSPEND":C.YELLOW}.get(st,C.NC)
        exp    = entry.get("expiredAt","?")
        days   = days_remaining(exp)

        if days is not None:
            if days < 0:
                days_str = f"{C.RED}(TAMAT {abs(days)} hari lalu){C.NC}"
            elif days == 0:
                days_str = f"{C.RED}(TAMAT HARI INI!){C.NC}"
            elif days <= 30:
                days_str = f"{C.YELLOW}({days} hari lagi — akan tamat){C.NC}"
            else:
                days_str = f"{C.GREEN}({days} hari lagi){C.NC}"
        else:
            days_str = ""

        print()
        cprint("  === Maklumat Lesen ===", C.BOLD)
        print(f"  ID           : {entry.get('id','?')}")
        print(f"  Kod Peranti  : {C.YELLOW}{code}{C.NC}")
        print(f"  Nama         : {entry.get('username','?')}")
        print(f"  Email        : {entry.get('email','?')}")
        print(f"  Peranti      : {entry.get('deviceInfo','?')}")
        print(f"  Tamat        : {exp} {days_str}")
        print(f"  Status       : {st_col}{st}{C.NC}")
        if entry.get("banned"):
            print(f"  Sebab Ban    : {C.RED}{entry.get('bannedReason','?')}{C.NC}")
            print(f"  Tarikh Ban   : {entry.get('bannedAt','?')}")
        print(f"  Ditambah     : {entry.get('addedAt','?')}")
        print(f"  Kemaskini    : {entry.get('lastModified','?')}")
        if entry.get("note"):
            print(f"  Nota         : {entry.get('note')}")

    # ── SEMAK STATUS (simulasi app) ───────────────────────────────────────
    @classmethod
    def check(cls, code: str):
        code  = code.upper()
        data  = cls._load()
        entry = next((l for l in data["licenses"] if l.get("deviceCode") == code), None)

        print()
        print(f"  Kod  : {C.YELLOW}{code}{C.NC}")
        print()

        if not entry:
            cprint("  Keputusan : NOT_FOUND", C.RED)
            info("Peranti belum didaftarkan. Hantar kod kepada admin MTS.")
        elif entry.get("banned"):
            cprint("  Keputusan : BANNED", C.RED)
            info(f"Sebab: {entry.get('bannedReason','Tiada maklumat')}")
        elif not entry.get("active", True):
            cprint("  Keputusan : SUSPENDED", C.YELLOW)
            info("Lesen digantung. Hubungi admin.")
        else:
            exp  = entry.get("expiredAt","2099-12-31")
            days = days_remaining(exp)
            if days is not None and days < 0:
                cprint("  Keputusan : EXPIRED", C.YELLOW)
                info(f"Tamat pada {exp}. Hubungi admin untuk perbaharui.")
            else:
                cprint("  Keputusan : VALID", C.GREEN)
                print(f"  Pengguna  : {entry.get('username','?')}")
                print(f"  Tamat     : {exp} ({days} hari lagi)")
                info(f"Selamat datang, {entry.get('username','User')}!")

    # ── EKSPORT ───────────────────────────────────────────────────────────
    @classmethod
    def export(cls, fmt: str = "csv"):
        data  = cls._load()
        items = data.get("licenses",[])
        ts    = datetime.now().strftime("%Y%m%d_%H%M%S")

        if fmt == "json":
            out = BASE_DIR / f"mtsflix_licenses_{ts}.json"
            out.write_text(json.dumps(data,indent=2,ensure_ascii=False), encoding='utf-8')
        else:
            out    = BASE_DIR / f"mtsflix_licenses_{ts}.csv"
            fields = ["id","deviceCode","username","email","expiredAt","active","banned",
                      "bannedReason","addedAt","deviceInfo","note","lastModified"]
            with open(out,"w",newline="",encoding="utf-8-sig") as f:
                w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
                w.writeheader()
                w.writerows(items)

        ok(f"Eksport selesai: {out.name}")
        info(f"Jumlah: {len(items)} rekod")
        info(f"Lokasi: {out}")

    # ── STATISTIK ─────────────────────────────────────────────────────────
    @classmethod
    def stats(cls):
        data  = cls._load()
        items = data.get("licenses",[])

        total = len(items)
        aktif = banned = tamat = akan_tamat = suspend = 0

        for l in items:
            st = cls.get_status(l)
            if st == "AKTIF":
                aktif += 1
                days = days_remaining(l.get("expiredAt",""))
                if days is not None and 0 <= days <= 30:
                    akan_tamat += 1
            elif st == "BANNED":
                banned += 1
            elif st == "TAMAT":
                tamat += 1
            elif st == "SUSPEND":
                suspend += 1

        print()
        cprint("  === Statistik Lesen MTSFlix ===", C.BOLD)
        print(f"  Database   : {data.get('description','MTSFlix Registry')}")
        print(f"  Kemaskini  : {data.get('lastUpdated','?')}")
        print()
        print(f"  {C.GREEN}[OK] Aktif       : {aktif}{C.NC}")
        print(f"  {C.RED}[!!] Banned      : {banned}{C.NC}")
        print(f"  {C.YELLOW}[--] Suspend     : {suspend}{C.NC}")
        print(f"  {C.YELLOW}[--] Tamat       : {tamat}{C.NC}")
        print(f"  {C.YELLOW}[!!] Akan Tamat  : {akan_tamat} (30 hari){C.NC}")
        print(f"  {C.GRAY}[==] Jumlah Total : {total}{C.NC}")

# ═══════════════════════════════════════════════════════════════════════════
#  PENGURUS GITHUB
# ═══════════════════════════════════════════════════════════════════════════
class GitHubManager:

    # ── PUSH KE GITHUB ────────────────────────────────────────────────────
    @staticmethod
    def push(message: str = None, files: List[str] = None) -> bool:
        if not shutil.which("git"):
            err("Git tidak dijumpai. Pasang Git dari git-scm.com")
            return False

        if not message:
            message = f"license: Update database {datetime.now().strftime('%Y-%m-%d %H:%M')}"
        if files is None:
            files = ["licenses.json"]

        try:
            os.chdir(BASE_DIR)

            # Periksa sama ada ini git repo
            r = subprocess.run(["git","rev-parse","--git-dir"],
                               capture_output=True, text=True)
            if r.returncode != 0:
                err("Bukan git repository.")
                info("Jalankan: git init && git remote add origin <url>")
                return False

            # Stage fail-fail yang berubah
            for f in files:
                fp = BASE_DIR / f
                if fp.exists():
                    subprocess.run(["git","add",str(fp)], check=True)

            # Semak ada perubahan
            r = subprocess.run(["git","diff","--staged","--quiet"], capture_output=True)
            if r.returncode == 0:
                warn("Tiada perubahan untuk di-commit.")
                return True

            # Commit
            subprocess.run(["git","commit","-m", message], check=True)

            # Pull & Rebase in case remote has changes (e.g. from GitHub Actions builds)
            branch = Config.get("branch","main")
            print("  Menyelaraskan dengan remote (git pull --rebase)...")
            pull_result = subprocess.run(["git","pull","--rebase","--autostash","origin", branch],
                                         capture_output=True, text=True)
            if pull_result.returncode != 0:
                err(f"Gagal menyelaraskan dengan remote (pull --rebase): {pull_result.stderr}")
                info("Sila jalankan 'git pull' secara manual atau selesaikan konflik jika ada.")
                return False

            # Push
            result = subprocess.run(["git","push","origin", branch],
                                    capture_output=True, text=True)
            if result.returncode != 0:
                err(f"Push gagal: {result.stderr}")
                return False

            print()
            ok("Berjaya di-push ke GitHub!")
            info(f"Branch  : {branch}")
            info(f"Mesej   : {message}")
            info("Perubahan aktif dalam ~30 saat.")
            return True

        except subprocess.CalledProcessError as e:
            err(f"Git error: {e}")
            return False
        except Exception as e:
            err(f"Push gagal: {e}")
            return False

    # ── JANA APK (GitHub Actions) ─────────────────────────────────────────
    @staticmethod
    def trigger_build(version: str, release_notes: str = "",
                      mandatory: bool = False, create_release: bool = True) -> bool:
        token = Config.get("github_token","")
        repo  = Config.get("app_repo","")

        if not token:
            err("GitHub Token belum dikonfigurasi.")
            info("Pergi ke Menu Utama → Tetapan → Set GitHub Token")
            info("Cara dapat token: github.com/settings/tokens (scope: repo, workflow)")
            return False

        api_url = f"https://api.github.com/repos/{repo}/actions/workflows/build_release.yml/dispatches"
        payload = {
            "ref": Config.get("branch","main"),
            "inputs": {
                "version_name":    version,
                "release_notes":   release_notes or f"MTSFlix v{version}",
                "mandatory_update": str(mandatory).lower(),
                "create_release":   str(create_release).lower()
            }
        }
        headers = {
            "Authorization": f"token {token}",
            "Content-Type":  "application/json",
            "Accept":        "application/vnd.github.v3+json",
            "User-Agent":    "MTSFlix-Master/3.0"
        }

        try:
            data = json.dumps(payload).encode('utf-8')
            req  = urllib.request.Request(api_url, data=data, headers=headers, method='POST')
            with urllib.request.urlopen(req, timeout=15) as resp:
                if resp.status == 204:
                    print()
                    ok(f"Build v{version} berjaya di-trigger di GitHub Actions!")
                    info(f"Repo    : {repo}")
                    info(f"Versi   : v{version}")
                    info(f"Progress: https://github.com/{repo}/actions")
                    info("APK akan siap dalam ~15-20 minit.")
                    return True

        except urllib.error.HTTPError as e:
            err(f"GitHub API gagal: HTTP {e.code} — {e.reason}")
            if e.code == 401:
                err("Token tidak sah atau tamat tempoh.")
            elif e.code == 404:
                err("Repo atau workflow tidak dijumpai.")
            elif e.code == 422:
                err("Branch atau input tidak sah.")
            return False
        except urllib.error.URLError as e:
            err(f"Tiada sambungan internet: {e.reason}")
            return False
        except Exception as e:
            err(f"Ralat: {e}")
            return False

    # ── KEMASKINI VERSION.JSON ────────────────────────────────────────────
    @staticmethod
    def update_version_json(version: str, release_notes: str = "",
                             mandatory: bool = False) -> bool:
        try:
            if VERSION_FILE.exists():
                data = json.loads(VERSION_FILE.read_text(encoding='utf-8'))
            else:
                data = {}

            repo = Config.get("app_repo","muhamadakmal854-svg/MTSFlix")
            new_code = data.get("versionCode", 0) + 1

            data.update({
                "version":      version,
                "versionCode":  new_code,
                "downloadUrl":  f"https://github.com/{repo}/releases/download/v{version}/MTSFlix-v{version}.apk",
                "releaseNotes": release_notes or f"MTSFlix v{version}",
                "mandatory":    mandatory,
                "sha256":       "",
                "publishedAt":  datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
                "minAndroid":   21,
                "providerUrl":  Config.get("provider_url","")
            })

            VERSION_FILE.write_text(
                json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8'
            )
            ok(f"version.json dikemaskini: v{version} (build #{new_code})")
            return True
        except Exception as e:
            err(f"Gagal kemaskini version.json: {e}")
            return False

    # ── STATUS GIT ────────────────────────────────────────────────────────
    @staticmethod
    def git_status() -> dict:
        try:
            os.chdir(BASE_DIR)
            branch = subprocess.run(
                ["git","branch","--show-current"], capture_output=True, text=True
            ).stdout.strip()
            status = subprocess.run(
                ["git","status","--short"], capture_output=True, text=True
            ).stdout.strip()
            last   = subprocess.run(
                ["git","log","-1","--oneline"], capture_output=True, text=True
            ).stdout.strip()
            return {"ok": True, "branch": branch, "status": status, "last": last}
        except Exception:
            return {"ok": False}

    # ── KEMASKINI PROVIDER URL ─────────────────────────────────────────────
    @staticmethod
    def update_provider_url(new_url: str) -> bool:
        """Kemaskini URL provider dalam konfigurasi."""
        Config.set("provider_url", new_url)

        # Kemaskini juga dalam DefaultRepoSetup.kt jika ada
        default_setup = BASE_DIR / "custom_src" / "license" / "DefaultRepoSetup.kt"
        if default_setup.exists():
            content = default_setup.read_text(encoding='utf-8')
            import re as re_mod
            content = re_mod.sub(
                r'(const val MTS_PROVIDER_URL\s*=\s*")([^"]*)(")',
                f'\\g<1>{new_url}\\g<3>',
                content
            )
            default_setup.write_text(content, encoding='utf-8')
            ok("DefaultRepoSetup.kt dikemaskini.")

        ok(f"Provider URL dikemaskini: {new_url}")
        info("Push changes dan build APK baru untuk kesan perubahan ini.")
        return True

# ═══════════════════════════════════════════════════════════════════════════
#  UI — PEMBANTU PAPARAN
# ═══════════════════════════════════════════════════════════════════════════
def clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')

def print_header(subtitle: str = ""):
    cprint("=" * 60, C.RED)
    cprint("     MTSFlix Master Control v3.0", C.WHITE + C.BOLD)
    if subtitle:
        cprint(f"     {subtitle}", C.CYAN)
    cprint("=" * 60, C.RED)
    print()

def divider():
    cprint("  " + "-"*56, C.GRAY)

def press_enter():
    print()
    input(f"  {C.GRAY}[ Tekan Enter untuk kembali... ]{C.NC}")

def input_field(prompt: str, default: str = "", required: bool = True) -> str:
    dp = f" [{C.CYAN}{default}{C.NC}]" if default else ""
    val = input(f"  {prompt}{dp}: ").strip()
    if not val and default:
        return default
    if not val and required:
        return ""
    return val

def confirm_action(msg: str = "Teruskan?") -> bool:
    ans = input(f"  {msg} (Y/n): ").strip().lower()
    return ans != 'n'

def ask_and_push(commit_msg: str, files: List[str] = None):
    print()
    if confirm_action("Push ke GitHub sekarang?"):
        GitHubManager.push(commit_msg, files or ["licenses.json"])
    else:
        warn("Belum di-push ke GitHub.")
        info("Jalankan 'python master.py push' atau guna menu Push.")

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — TAMBAH LESEN
# ═══════════════════════════════════════════════════════════════════════════
def menu_add():
    clear_screen()
    print_header("Tambah Lesen Peranti Baru")

    name = input_field("Nama penuh pengguna")
    if not name:
        err("Nama tidak boleh kosong.")
        press_enter(); return

    email = input_field("Email pengguna")
    if not email:
        err("Email tidak boleh kosong.")
        press_enter(); return

    print(f"  {C.GRAY}(Kod diambil dari skrin pengesahan dalam app MTSFlix){C.NC}")
    code = input_field("Kod Peranti (MTSF-XXXX-XXXX-XXXX)").upper()
    if not validate_code(code):
        err("Format kod tidak sah. Sepatutnya: MTSF-XXXX-XXXX-XXXX")
        press_enter(); return

    expiry = input_field("Tarikh Tamat (YYYY-MM-DD)", default_expiry(365))
    if not validate_date(expiry):
        err("Format tarikh tidak sah. Sepatutnya: YYYY-MM-DD")
        press_enter(); return

    device = input_field("Model Peranti (contoh: Samsung S24)", "Tidak dinyatakan")
    note   = input_field("Nota admin (pilihan)", "", required=False)

    print()
    divider()
    cprint("  Semakan maklumat:", C.WHITE)
    print(f"  Nama    : {C.GREEN}{name}{C.NC}")
    print(f"  Email   : {email}")
    print(f"  Kod     : {C.YELLOW}{code}{C.NC}")
    print(f"  Tamat   : {expiry}")
    print(f"  Peranti : {device}")
    divider()
    print()

    if not confirm_action("Tambah lesen ini?"):
        info("Dibatalkan.")
        press_enter(); return

    success = LicenseManager.add(name, email, code, expiry, device, note)
    if success:
        ask_and_push(f"Add license: {name} ({code}) exp:{expiry}")
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — PADAM LESEN
# ═══════════════════════════════════════════════════════════════════════════
def menu_delete():
    clear_screen()
    print_header("Padam Lesen Secara Kekal")

    LicenseManager.list_all("all")
    print()
    code = input_field("Kod Peranti untuk dipadam").upper()
    if not validate_code(code):
        err("Format kod tidak sah.")
        press_enter(); return

    LicenseManager.info(code)
    print()
    success = LicenseManager.delete(code)
    if success:
        ask_and_push(f"Delete license: {code}")
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — GANTUNG LESEN
# ═══════════════════════════════════════════════════════════════════════════
def menu_ban():
    clear_screen()
    print_header("Gantung Lesen (Ban)")

    LicenseManager.list_all("active")
    print()
    code = input_field("Kod Peranti untuk digantung").upper()
    if not validate_code(code):
        err("Format kod tidak sah.")
        press_enter(); return

    LicenseManager.info(code)
    print()
    reason = input_field("Sebab penggantungan", "Melanggar syarat penggunaan MTSFlix")

    success = LicenseManager.ban(code, reason)
    if success:
        ask_and_push(f"Ban license: {code} — {reason}")
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — AKTIFKAN SEMULA
# ═══════════════════════════════════════════════════════════════════════════
def menu_unban():
    clear_screen()
    print_header("Aktifkan Semula Lesen (Unban)")

    LicenseManager.list_all("banned")
    print()
    code = input_field("Kod Peranti untuk diaktifkan semula").upper()
    if not validate_code(code):
        err("Format kod tidak sah.")
        press_enter(); return

    success = LicenseManager.unban(code)
    if success:
        ask_and_push(f"Unban license: {code}")
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — PERBAHARUI TARIKH
# ═══════════════════════════════════════════════════════════════════════════
def menu_renew():
    clear_screen()
    print_header("Perbaharui Tarikh Tamat Lesen")

    code = input_field("Kod Peranti").upper()
    if not validate_code(code):
        err("Format kod tidak sah.")
        press_enter(); return

    LicenseManager.info(code)
    print()
    new_exp = input_field("Tarikh Tamat Baru (YYYY-MM-DD)", default_expiry(365))

    success = LicenseManager.renew(code, new_exp)
    if success:
        ask_and_push(f"Renew license: {code} → {new_exp}")
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — SENARAI LESEN
# ═══════════════════════════════════════════════════════════════════════════
def menu_list():
    clear_screen()
    print_header("Senarai Lesen")

    print(f"  {C.WHITE}1{C.NC}. Semua lesen")
    print(f"  {C.GREEN}2{C.NC}. Aktif sahaja")
    print(f"  {C.RED}3{C.NC}. Banned sahaja")
    print(f"  {C.YELLOW}4{C.NC}. Tamat sahaja")
    print(f"  {C.YELLOW}5{C.NC}. Suspend sahaja")
    print()
    choice = input("  Pilih penapis [1-5, default=1]: ").strip()

    fmap = {"1":"all","2":"active","3":"banned","4":"expired","5":"suspended"}
    LicenseManager.list_all(fmap.get(choice,"all"))
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — MAKLUMAT LESEN
# ═══════════════════════════════════════════════════════════════════════════
def menu_info():
    clear_screen()
    print_header("Maklumat Terperinci Lesen")

    code = input_field("Kod Peranti").upper()
    if not validate_code(code):
        err("Format kod tidak sah.")
        press_enter(); return

    LicenseManager.info(code)
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — SIMULASI SEMAKAN APP
# ═══════════════════════════════════════════════════════════════════════════
def menu_check():
    clear_screen()
    print_header("Simulasi Semakan App")

    info("Simulasi yang sama dilakukan oleh app MTSFlix semasa dibuka.")
    print()
    code = input_field("Kod Peranti untuk disemak").upper()
    if not validate_code(code):
        err("Format kod tidak sah.")
        press_enter(); return

    LicenseManager.check(code)
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — PUSH KE GITHUB
# ═══════════════════════════════════════════════════════════════════════════
def menu_push():
    clear_screen()
    print_header("Push ke GitHub")

    st = GitHubManager.git_status()
    print()
    if st.get("ok"):
        info(f"Branch       : {st.get('branch','?')}")
        info(f"Commit terakhir : {st.get('last','?')}")
        if st.get("status"):
            warn(f"Perubahan belum di-commit:\n    {st.get('status')}")
        else:
            ok("Working tree bersih — tiada perubahan.")
    else:
        warn("Tidak dapat membaca status git.")
    print()

    msg = input_field(
        "Mesej commit",
        f"license: Update {datetime.now().strftime('%Y-%m-%d %H:%M')}"
    )

    print()
    files_to_push = ["licenses.json", "version.json"]
    GitHubManager.push(msg, files_to_push)
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — JANA APK (GitHub Actions)
# ═══════════════════════════════════════════════════════════════════════════
def menu_build():
    clear_screen()
    print_header("Jana APK Baru via GitHub Actions")

    token = Config.get("github_token","")
    if not token:
        warn("GitHub Token belum dikonfigurasi!")
        info("Pergi ke: Menu Utama → Tetapan → GitHub Token")
        print()
        info("Cara dapatkan token:")
        info("1. Pergi ke github.com")
        info("2. Settings → Developer settings → Personal access tokens → Tokens (classic)")
        info("3. Generate new token → Scope: repo + workflow")
        info("4. Copy token dan masukkan dalam Tetapan")
        press_enter(); return

    # Baca versi semasa
    current_ver = "1.0.0"
    if VERSION_FILE.exists():
        try:
            vdata = json.loads(VERSION_FILE.read_text(encoding='utf-8'))
            current_ver = vdata.get("version","1.0.0")
        except Exception:
            pass

    info(f"Versi app semasa: v{current_ver}")
    print()

    version = input_field("Versi APK baru (contoh: 1.2.0)", current_ver)
    notes   = input_field("Release notes (penerangan update)", f"MTSFlix v{version}")
    mandatory_ans = input("  Wajib update? pengguna mesti update? (y/N): ").strip().lower()
    mandatory     = mandatory_ans == 'y'
    release_ans   = input("  Buat GitHub Release? (Y/n): ").strip().lower()
    create_release = release_ans != 'n'

    print()
    divider()
    cprint("  Pengesahan Build:", C.WHITE)
    info(f"Versi      : v{version}")
    info(f"Notes      : {notes}")
    info(f"Wajib      : {'Ya' if mandatory else 'Tidak'}")
    info(f"Release    : {'Ya' if create_release else 'Tidak'}")
    divider()
    print()

    if not confirm_action("Trigger build sekarang?"):
        info("Dibatalkan.")
        press_enter(); return

    success = GitHubManager.trigger_build(version, notes, mandatory, create_release)
    if success:
        # Kemaskini version.json secara lokal
        GitHubManager.update_version_json(version, notes, mandatory)
        # Push version.json
        GitHubManager.push(
            f"chore: Bump version to v{version} [skip ci]",
            ["version.json"]
        )
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — KEMASKINI PROVIDER URL
# ═══════════════════════════════════════════════════════════════════════════
def menu_update_url():
    clear_screen()
    print_header("Kemaskini URL Provider Extension")

    current = Config.get("provider_url","")
    info(f"URL semasa: {current}")
    print()
    info("URL ini digunakan oleh semua pengguna MTSFlix untuk download extension provider.")
    info("PERUBAHAN URL MEMERLUKAN APK BARU (rebuild app).")
    print()

    new_url = input_field("URL Plugin JSON baru", current)
    if new_url == current:
        warn("URL tidak berubah.")
        press_enter(); return

    print()
    if not confirm_action(f"Kemaskini URL kepada:\n  {new_url}"):
        info("Dibatalkan.")
        press_enter(); return

    GitHubManager.update_provider_url(new_url)
    print()
    warn("URL telah berubah. Anda perlu build APK baru untuk perubahan ini berkuat kuasa.")
    info("Pergi ke: Menu Utama → Jana APK Baru")
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — EKSPORT
# ═══════════════════════════════════════════════════════════════════════════
def menu_export():
    clear_screen()
    print_header("Eksport Database Lesen")

    print(f"  {C.WHITE}1{C.NC}. CSV  — buka dengan Excel")
    print(f"  {C.WHITE}2{C.NC}. JSON — format asal")
    print()
    choice = input("  Pilih format [1/2, default=1]: ").strip()

    fmt = "json" if choice == "2" else "csv"
    print()
    LicenseManager.export(fmt)
    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — STATISTIK
# ═══════════════════════════════════════════════════════════════════════════
def menu_stats():
    clear_screen()
    print_header("Statistik & Status")

    LicenseManager.stats()

    # Info versi app
    if VERSION_FILE.exists():
        try:
            vdata = json.loads(VERSION_FILE.read_text(encoding='utf-8'))
            print()
            cprint("  === Info Aplikasi ===", C.BOLD)
            info(f"Versi app   : v{vdata.get('version','?')}")
            info(f"Build code  : #{vdata.get('versionCode','?')}")
            info(f"Diterbitkan : {str(vdata.get('publishedAt','?'))[:10]}")
            info(f"Wajib update: {'Ya' if vdata.get('mandatory') else 'Tidak'}")
        except Exception:
            pass

    # Status git
    print()
    st = GitHubManager.git_status()
    if st.get("ok"):
        cprint("  === Status Git ===", C.BOLD)
        info(f"Branch      : {st.get('branch','?')}")
        info(f"Last commit : {st.get('last','?')}")
        if st.get("status"):
            warn(f"Belum di-push: {st.get('status')}")

    press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — TETAPAN
# ═══════════════════════════════════════════════════════════════════════════
def menu_settings():
    while True:
        clear_screen()
        print_header("Tetapan MTSFlix Master")

        token = Config.get("github_token","")
        tok_disp = f"...{token[-6:]}" if len(token) > 6 else ("(belum set)" if not token else "***")

        print(f"  {C.WHITE}1{C.NC}. GitHub Token     : {C.YELLOW}{tok_disp}{C.NC}")
        print(f"  {C.WHITE}2{C.NC}. App Repo         : {Config.get('app_repo','?')}")
        print(f"  {C.WHITE}3{C.NC}. Provider Repo    : {Config.get('provider_repo','?')}")
        print(f"  {C.WHITE}4{C.NC}. Branch           : {Config.get('branch','main')}")
        print(f"  {C.WHITE}5{C.NC}. Admin Telegram   : {Config.get('admin_telegram','?')}")
        print(f"  {C.WHITE}6{C.NC}. Provider URL     : {Config.get('provider_url','?')[:55]}...")
        print()
        print(f"  {C.WHITE}7{C.NC}. Lihat semua tetapan")
        print(f"  {C.WHITE}8{C.NC}. Reset ke tetapan asal")
        print()
        print(f"  {C.GRAY}0{C.NC}. Kembali")
        print()

        choice = input("  Pilih: ").strip()

        if choice == '0':
            break
        elif choice == '1':
            print()
            info("Cara dapatkan GitHub Token:")
            info("  github.com → Settings → Developer settings")
            info("  → Personal access tokens → Tokens (classic)")
            info("  → Generate new token → Scope: repo + workflow")
            print()
            new_tok = input("  GitHub Token baru: ").strip()
            if new_tok:
                Config.set("github_token", new_tok)
                ok("Token disimpan!")
            else:
                warn("Token tidak diubah.")
        elif choice == '2':
            v = input_field("App Repo (owner/repo)", Config.get("app_repo",""))
            if v: Config.set("app_repo", v); ok("App repo dikemaskini!")
        elif choice == '3':
            v = input_field("Provider Repo (owner/repo)", Config.get("provider_repo",""))
            if v: Config.set("provider_repo", v); ok("Provider repo dikemaskini!")
        elif choice == '4':
            v = input_field("Branch", Config.get("branch","main"))
            if v: Config.set("branch", v); ok("Branch dikemaskini!")
        elif choice == '5':
            v = input_field("Admin Telegram URL", Config.get("admin_telegram",""))
            if v: Config.set("admin_telegram", v); ok("Telegram dikemaskini!")
        elif choice == '6':
            v = input_field("Provider URL", Config.get("provider_url",""))
            if v: Config.set("provider_url", v); ok("Provider URL dikemaskini!")
        elif choice == '7':
            print()
            cprint("  === Semua Tetapan ===", C.BOLD)
            for k, v in Config._data.items():
                disp = f"...{v[-6:]}" if k == "github_token" and len(v) > 6 else v
                print(f"  {C.CYAN}{k:<25}{C.NC}: {disp}")
        elif choice == '8':
            if confirm_action("Reset SEMUA tetapan ke asal?"):
                for k, v in DEFAULT_CONFIG.items():
                    Config.set(k, v)
                ok("Tetapan direset!")

        if choice in ('1','2','3','4','5','6','7','8'):
            press_enter()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU — PENGURUSAN LESEN (Sub-Menu)
# ═══════════════════════════════════════════════════════════════════════════
def menu_license_manager():
    while True:
        clear_screen()
        print_header("Pengurusan Lesen Peranti")

        LicenseManager.stats()
        print()
        divider()
        print()

        print(f"  {C.GREEN}1{C.NC}. Tambah lesen baru")
        print(f"  {C.RED}2{C.NC}. Padam lesen (kekal)")
        print(f"  {C.YELLOW}3{C.NC}. Gantung lesen (ban)")
        print(f"  {C.GREEN}4{C.NC}. Aktifkan semula (unban)")
        print(f"  {C.CYAN}5{C.NC}. Perbaharui tarikh tamat")
        divider()
        print(f"  {C.WHITE}6{C.NC}. Senarai semua lesen")
        print(f"  {C.WHITE}7{C.NC}. Maklumat lesen")
        print(f"  {C.WHITE}8{C.NC}. Semak status lesen (simulasi app)")
        divider()
        print(f"  {C.WHITE}9{C.NC}. Eksport database (CSV/JSON)")
        print(f"  {C.CYAN}P{C.NC}. Push ke GitHub")
        print()
        print(f"  {C.GRAY}0{C.NC}. Kembali ke Menu Utama")
        print()

        choice = input("  Pilih: ").strip().lower()

        if   choice == '0': break
        elif choice == '1': menu_add()
        elif choice == '2': menu_delete()
        elif choice == '3': menu_ban()
        elif choice == '4': menu_unban()
        elif choice == '5': menu_renew()
        elif choice == '6': menu_list()
        elif choice == '7': menu_info()
        elif choice == '8': menu_check()
        elif choice == '9': menu_export()
        elif choice == 'p': menu_push()

# ═══════════════════════════════════════════════════════════════════════════
#  MENU UTAMA
# ═══════════════════════════════════════════════════════════════════════════
def main_menu():
    while True:
        clear_screen()
        print_header()

        # Info ringkas di atas menu
        data  = LicenseManager._load()
        total = len(data.get("licenses",[]))
        aktif = sum(1 for l in data["licenses"] if LicenseManager.get_status(l) == "AKTIF")
        ver   = "?"
        if VERSION_FILE.exists():
            try: ver = json.loads(VERSION_FILE.read_text()).get("version","?")
            except Exception: pass

        st = GitHubManager.git_status()
        branch = st.get("branch","?") if st.get("ok") else "?"

        print(f"  {C.GRAY}App: v{ver}  |  Lesen: {aktif}/{total} aktif  |  Branch: {branch}  |  {datetime.now().strftime('%d/%m/%Y %H:%M')}{C.NC}")
        print()
        divider()
        print()

        print(f"  {C.WHITE}1{C.NC}.  Pengurusan Lesen Peranti")
        print(f"  {C.WHITE}2{C.NC}.  Push ke GitHub")
        print(f"  {C.WHITE}3{C.NC}.  Jana APK Baru (GitHub Actions)")
        print(f"  {C.WHITE}4{C.NC}.  Kemaskini URL Provider Extension")
        print(f"  {C.WHITE}5{C.NC}.  Statistik & Status")
        print(f"  {C.WHITE}6{C.NC}.  Tetapan (GitHub Token, Repo, dll)")
        print()
        print(f"  {C.GRAY}0{C.NC}.  Keluar")
        print()

        choice = input("  Pilih: ").strip()

        if   choice == '0': cprint("\n  Selamat tinggal!\n", C.CYAN); break
        elif choice == '1': menu_license_manager()
        elif choice == '2': menu_push()
        elif choice == '3': menu_build()
        elif choice == '4': menu_update_url()
        elif choice == '5': menu_stats()
        elif choice == '6': menu_settings()
        else:
            err(f"Pilihan tidak dikenali: '{choice}'")
            time.sleep(1)

# ═══════════════════════════════════════════════════════════════════════════
#  MOD CLI (Tanpa Menu)
# ═══════════════════════════════════════════════════════════════════════════
def cli_mode(args: List[str]):
    cmd  = args[0].lower()
    rest = args[1:]

    print_header()

    if cmd == "add":
        if len(rest) < 3:
            err("Guna: python master.py add <nama> <email> <kod> [tarikh_tamat] [peranti]")
            sys.exit(1)
        name, email, code = rest[0], rest[1], rest[2]
        expiry = rest[3] if len(rest) > 3 else default_expiry()
        device = rest[4] if len(rest) > 4 else "Tidak dinyatakan"
        note   = rest[5] if len(rest) > 5 else ""
        ok_result = LicenseManager.add(name, email, code, expiry, device, note)
        if ok_result:
            ask_and_push(f"Add license: {name} ({code})")

    elif cmd == "delete":
        if not rest: err("Guna: python master.py delete <kod>"); sys.exit(1)
        r = LicenseManager.delete(rest[0])
        if r: ask_and_push(f"Delete: {rest[0]}")

    elif cmd == "ban":
        if not rest: err("Guna: python master.py ban <kod> [sebab]"); sys.exit(1)
        reason = " ".join(rest[1:]) if len(rest) > 1 else "Digantung oleh admin"
        r = LicenseManager.ban(rest[0], reason)
        if r: ask_and_push(f"Ban: {rest[0]}")

    elif cmd == "unban":
        if not rest: err("Guna: python master.py unban <kod>"); sys.exit(1)
        r = LicenseManager.unban(rest[0])
        if r: ask_and_push(f"Unban: {rest[0]}")

    elif cmd == "renew":
        if len(rest) < 2: err("Guna: python master.py renew <kod> <tarikh>"); sys.exit(1)
        r = LicenseManager.renew(rest[0], rest[1])
        if r: ask_and_push(f"Renew: {rest[0]} → {rest[1]}")

    elif cmd == "list":
        f = rest[0].lstrip("-") if rest else "all"
        LicenseManager.list_all(f)

    elif cmd == "info":
        if not rest: err("Guna: python master.py info <kod>"); sys.exit(1)
        LicenseManager.info(rest[0])

    elif cmd == "check":
        if not rest: err("Guna: python master.py check <kod>"); sys.exit(1)
        LicenseManager.check(rest[0])

    elif cmd == "push":
        msg = " ".join(rest) if rest else f"Update: {datetime.now().strftime('%Y-%m-%d')}"
        GitHubManager.push(msg, ["licenses.json","version.json"])

    elif cmd == "build":
        version = rest[0] if rest else input("  Versi (contoh: 1.2.0): ").strip()
        notes   = " ".join(rest[1:]) if len(rest) > 1 else f"MTSFlix v{version}"
        r = GitHubManager.trigger_build(version, notes)
        if r:
            GitHubManager.update_version_json(version, notes)
            GitHubManager.push(f"chore: v{version} [skip ci]",["version.json"])

    elif cmd == "stats":
        LicenseManager.stats()

    elif cmd == "export":
        fmt = rest[0] if rest else "csv"
        LicenseManager.export(fmt)

    elif cmd in ("help","--help","-h"):
        cprint("Penggunaan: python master.py [perintah] [pilihan]", C.WHITE)
        print()
        cmds = [
            ("(kosong)",                          "Buka menu interaktif"),
            ("add <nama> <email> <kod> [tamat]",  "Tambah lesen baru"),
            ("delete <kod>",                       "Padam lesen KEKAL"),
            ("ban <kod> [sebab]",                  "Gantung lesen"),
            ("unban <kod>",                        "Aktifkan semula"),
            ("renew <kod> <tarikh>",               "Perbaharui tarikh tamat"),
            ("list [active|banned|expired]",       "Senarai lesen"),
            ("info <kod>",                         "Maklumat lesen"),
            ("check <kod>",                        "Simulasi semakan app"),
            ("push [mesej commit]",                "Push ke GitHub"),
            ("build <versi> [notes]",              "Jana APK via GitHub Actions"),
            ("stats",                              "Statistik lesen"),
            ("export [csv|json]",                  "Eksport database"),
        ]
        for c_name, c_desc in cmds:
            print(f"  {C.CYAN}python master.py {c_name:<38}{C.NC} {c_desc}")
        print()
        print(f"  {C.GRAY}Contoh:{C.NC}")
        print(f"  python master.py add \"Ahmad Ali\" \"ahmad@gmail.com\" \"MTSF-A1B2-C3D4-E5F6\" \"2026-12-31\"")
        print(f"  python master.py ban \"MTSF-A1B2-C3D4-E5F6\" \"Melanggar syarat\"")
        print(f"  python master.py renew \"MTSF-A1B2-C3D4-E5F6\" \"2027-12-31\"")
        print(f"  python master.py list --active")
        print()
    else:
        err(f"Perintah tidak dikenali: '{cmd}'")
        info("Jalankan 'python master.py help' untuk bantuan.")
        sys.exit(1)

# ═══════════════════════════════════════════════════════════════════════════
#  MULA
# ═══════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    Config.load()

    if len(sys.argv) > 1:
        cli_mode(sys.argv[1:])
    else:
        main_menu()
