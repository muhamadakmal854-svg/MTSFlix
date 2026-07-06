#!/bin/bash
# ============================================================
#  MTSFlix License Manager v2.0
#  Full CRUD License Management System
#
#  PERINTAH:
#    add     - Tambah lesen peranti baru
#    delete  - Padam lesen secara kekal
#    ban     - Gantung lesen (data kekal, boleh unban)
#    unban   - Aktifkan semula lesen yang digantung
#    renew   - Perbaharui tarikh tamat lesen
#    list    - Senarai lesen dengan penapis
#    info    - Maklumat terperinci satu lesen
#    check   - Simulasi semakan app terhadap lesen
#    push    - Push licenses.json ke GitHub
#    export  - Eksport database ke CSV/JSON
#    stats   - Statistik lesen
#    help    - Tunjuk bantuan ini
# ============================================================
set -e

LICENSE_FILE="${LICENSE_FILE:-licenses.json}"
GITHUB_BRANCH="${GITHUB_BRANCH:-main}"

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
NC='\033[0m'
BOLD='\033[1m'

# ─── Header ──────────────────────────────────────────────────────────────────
print_header() {
  echo ""
  echo -e "${RED}╔══════════════════════════════════════════════════════╗${NC}"
  echo -e "${RED}║${WHITE}${BOLD}         MTSFlix License Manager v2.0                ${NC}${RED}║${NC}"
  echo -e "${RED}╚══════════════════════════════════════════════════════╝${NC}"
  echo ""
}

# ─── Validation ──────────────────────────────────────────────────────────────
check_deps() {
  for dep in python3 git; do
    if ! command -v "$dep" &>/dev/null; then
      echo -e "${RED}ERROR: $dep tidak dijumpai. Sila pasang terlebih dahulu.${NC}"
      exit 1
    fi
  done
}

ensure_file() {
  if [ ! -f "$LICENSE_FILE" ]; then
    echo -e "${YELLOW}Membuat $LICENSE_FILE baharu...${NC}"
    python3 - << 'PYEOF'
import json
from datetime import datetime
data = {
  "version": 1,
  "description": "MTSFlix Device License Registry",
  "lastUpdated": datetime.now().strftime("%Y-%m-%d"),
  "licenses": []
}
import os, sys
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
print(f"{fname} dicipta.")
PYEOF
  fi
}

validate_code() {
  if [[ ! "$1" =~ ^MTSF-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$ ]]; then
    echo -e "${RED}ERROR: Format kod peranti tidak sah.${NC}"
    echo -e "  Sepatutnya: ${YELLOW}MTSF-XXXX-XXXX-XXXX${NC}"
    return 1
  fi
}

validate_date() {
  if [[ ! "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    echo -e "${RED}ERROR: Format tarikh tidak sah. Guna YYYY-MM-DD${NC}"
    return 1
  fi
}

default_expiry() {
  python3 -c "from datetime import datetime, timedelta; print((datetime.now()+timedelta(days=365)).strftime('%Y-%m-%d'))"
}

# ─── CMD: ADD ─────────────────────────────────────────────────────────────────
cmd_add() {
  local name="$1" email="$2" code="$3"
  local expiry="${4:-$(default_expiry)}"
  local device="${5:-Tidak dinyatakan}"
  local note="${6:-}"

  if [ -z "$name" ] || [ -z "$email" ] || [ -z "$code" ]; then
    echo -e "${RED}ERROR: Maklumat tidak lengkap.${NC}"
    echo ""
    echo "Guna: bash manage_license.sh add <nama> <email> <kod_peranti> [tarikh_tamat] [info_peranti] [nota]"
    echo ""
    echo "Contoh:"
    echo "  bash manage_license.sh add \"Ahmad Ali\" \"ahmad@gmail.com\" \"MTSF-A1B2-C3D4-E5F6\" \"2026-12-31\" \"Samsung S24\""
    exit 1
  fi

  validate_code "$code" || exit 1
  validate_date "$expiry" || exit 1
  ensure_file

  print_header
  echo -e "${CYAN}>>> Tambah Lesen Baru${NC}"
  echo ""

  # Check if code already exists
  EXISTS=$(python3 - << PYEOF
import json, os
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
found = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
print("yes" if found else "no")
print(found.get("username","") if found else "")
PYEOF
)
  EXISTS_STATUS=$(echo "$EXISTS" | head -1)
  EXISTS_USER=$(echo "$EXISTS" | tail -1)

  if [ "$EXISTS_STATUS" = "yes" ]; then
    echo -e "${YELLOW}AMARAN: Kod peranti $code sudah wujud! (${EXISTS_USER})${NC}"
    read -p "Kemaskini rekod ini? (y/N): " CONFIRM
    [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ] && { echo "Dibatalkan."; exit 0; }
    # Remove existing
    python3 - << PYEOF
import json, os
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
data["licenses"] = [l for l in data["licenses"] if l.get("deviceCode") != "$code"]
with open(fname, "w") as f: json.dump(data, f, indent=2, ensure_ascii=False)
PYEOF
  fi

  python3 - << PYEOF
import json, os
from datetime import datetime
from hashlib import md5

fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)

lic_id = "LIC-" + datetime.now().strftime("%Y%m%d") + "-" + md5("$code".encode()).hexdigest()[:8].upper()

entry = {
  "id": lic_id,
  "deviceCode": "$code",
  "username": "$name",
  "email": "$email",
  "expiredAt": "$expiry",
  "active": True,
  "banned": False,
  "bannedReason": "",
  "addedAt": datetime.now().strftime("%Y-%m-%d"),
  "deviceInfo": "$device",
  "note": "$note",
  "lastModified": datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
}

data["licenses"].append(entry)
data["lastUpdated"] = datetime.now().strftime("%Y-%m-%d")
with open(fname, "w") as f: json.dump(data, f, indent=2, ensure_ascii=False)

print(f"  ID          : {lic_id}")
print(f"  Nama        : $name")
print(f"  Email       : $email")
print(f"  Kod Peranti : $code")
print(f"  Tamat       : $expiry")
print(f"  Peranti     : $device")
print(f"  Total       : {len(data['licenses'])} lesen dalam database")
PYEOF

  echo ""
  echo -e "${GREEN}✅ Lesen berjaya ditambah!${NC}"
  _ask_push "Add license for $name ($code) exp:$expiry"
}

# ─── CMD: DELETE ──────────────────────────────────────────────────────────────
cmd_delete() {
  local code="$1"
  [ -z "$code" ] && { echo "Guna: bash manage_license.sh delete <kod_peranti>"; exit 1; }
  ensure_file
  print_header
  echo -e "${RED}>>> PADAM KEKAL Lesen${NC}"
  echo ""

  INFO=$(python3 - << PYEOF
import json, os
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
e = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
if e:
    status = "BANNED" if e.get("banned") else ("AKTIF" if e.get("active") else "TIDAK AKTIF")
    print(f"  Nama   : {e.get('username','?')}")
    print(f"  Email  : {e.get('email','?')}")
    print(f"  Tamat  : {e.get('expiredAt','?')}")
    print(f"  Status : {status}")
    print(f"  Peranti: {e.get('deviceInfo','?')}")
else:
    print("NOT_FOUND")
PYEOF
)

  if echo "$INFO" | grep -q "NOT_FOUND"; then
    echo -e "${RED}RALAT: Kod $code tidak dijumpai dalam database.${NC}"; exit 1
  fi

  echo -e "${YELLOW}Rekod yang akan DIPADAM SELAMA-LAMANYA:${NC}"
  echo "$INFO"
  echo ""
  echo -e "${RED}⚠️  AMARAN: Tindakan ini KEKAL. Pengguna TIDAK akan dapat akses MTSFlix selepas ini.${NC}"
  echo -e "${RED}    Untuk gantung sementara, guna perintah 'ban' sebaliknya.${NC}"
  echo ""
  read -p "Taip 'DELETE' untuk mengesahkan: " CONFIRM
  [ "$CONFIRM" != "DELETE" ] && { echo "Dibatalkan."; exit 0; }

  python3 - << PYEOF
import json, os
from datetime import datetime
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
before = len(data["licenses"])
data["licenses"] = [l for l in data["licenses"] if l.get("deviceCode") != "$code"]
data["lastUpdated"] = datetime.now().strftime("%Y-%m-%d")
with open(fname, "w") as f: json.dump(data, f, indent=2, ensure_ascii=False)
print(f"  Dipadam: {before - len(data['licenses'])} rekod")
print(f"  Baki   : {len(data['licenses'])} lesen dalam database")
PYEOF

  echo -e "${GREEN}✅ Lesen berjaya dipadam!${NC}"
  _ask_push "Delete license $code"
}

# ─── CMD: BAN ─────────────────────────────────────────────────────────────────
cmd_ban() {
  local code="$1" reason="${2:-Digantung oleh admin MTSFlix}"
  [ -z "$code" ] && { echo "Guna: bash manage_license.sh ban <kod_peranti> [sebab]"; exit 1; }
  ensure_file
  print_header
  echo -e "${YELLOW}>>> GANTUNG Lesen${NC}"
  echo ""

  RESULT=$(python3 - << PYEOF
import json, os
from datetime import datetime
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
e = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
if not e:
    print("NOT_FOUND")
elif e.get("banned"):
    print(f"ALREADY_BANNED:{e.get('username','?')}")
else:
    e["active"] = False
    e["banned"] = True
    e["bannedReason"] = "$reason"
    e["bannedAt"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    e["lastModified"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    data["lastUpdated"] = datetime.now().strftime("%Y-%m-%d")
    with open(fname, "w") as f: json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"BANNED:{e.get('username','?')}")
PYEOF
)

  case "$RESULT" in
    NOT_FOUND) echo -e "${RED}RALAT: Kod tidak dijumpai.${NC}"; exit 1 ;;
    ALREADY_BANNED:*) echo -e "${YELLOW}Lesen ${RESULT#ALREADY_BANNED:} sudah digantung.${NC}"; exit 0 ;;
    BANNED:*)
      NAME="${RESULT#BANNED:}"
      echo -e "${GREEN}✅ Lesen $NAME ($code) berjaya digantung!${NC}"
      echo -e "   Sebab: ${YELLOW}$reason${NC}"
      echo -e "   ${GRAY}Guna 'unban' untuk aktifkan semula.${NC}"
      ;;
  esac
  _ask_push "Ban license $code: $reason"
}

# ─── CMD: UNBAN ───────────────────────────────────────────────────────────────
cmd_unban() {
  local code="$1"
  [ -z "$code" ] && { echo "Guna: bash manage_license.sh unban <kod_peranti>"; exit 1; }
  ensure_file
  print_header
  echo -e "${GREEN}>>> AKTIFKAN SEMULA Lesen${NC}"
  echo ""

  RESULT=$(python3 - << PYEOF
import json, os
from datetime import datetime
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
e = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
if not e:
    print("NOT_FOUND")
else:
    e["active"] = True
    e["banned"] = False
    e["bannedReason"] = ""
    e.pop("bannedAt", None)
    e["lastModified"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    data["lastUpdated"] = datetime.now().strftime("%Y-%m-%d")
    with open(fname, "w") as f: json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"UNBANNED:{e.get('username','?')}")
PYEOF
)

  case "$RESULT" in
    NOT_FOUND) echo -e "${RED}RALAT: Kod tidak dijumpai.${NC}"; exit 1 ;;
    UNBANNED:*)
      NAME="${RESULT#UNBANNED:}"
      echo -e "${GREEN}✅ Lesen $NAME ($code) berjaya diaktifkan semula!${NC}"
      ;;
  esac
  _ask_push "Unban license $code"
}

# ─── CMD: RENEW ───────────────────────────────────────────────────────────────
cmd_renew() {
  local code="$1" new_expiry="$2"
  if [ -z "$code" ] || [ -z "$new_expiry" ]; then
    echo "Guna: bash manage_license.sh renew <kod_peranti> <tarikh_baru_YYYY-MM-DD>"
    echo "Contoh: bash manage_license.sh renew MTSF-A1B2-C3D4-E5F6 2027-12-31"
    exit 1
  fi
  validate_date "$new_expiry" || exit 1
  ensure_file
  print_header
  echo -e "${CYAN}>>> PERBAHARUI Tarikh Lesen${NC}"
  echo ""

  RESULT=$(python3 - << PYEOF
import json, os
from datetime import datetime
fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
e = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
if not e:
    print("NOT_FOUND")
else:
    old = e.get("expiredAt","?")
    e["expiredAt"] = "$new_expiry"
    e["lastModified"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    data["lastUpdated"] = datetime.now().strftime("%Y-%m-%d")
    with open(fname, "w") as f: json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"RENEWED:{e.get('username','?')}:{old}")
PYEOF
)

  case "$RESULT" in
    NOT_FOUND) echo -e "${RED}RALAT: Kod tidak dijumpai.${NC}"; exit 1 ;;
    RENEWED:*)
      IFS=':' read -r _ NAME OLD_EXP <<< "$RESULT"
      echo -e "${GREEN}✅ Lesen $NAME ($code) berjaya diperbaharui!${NC}"
      echo -e "   Lama : ${YELLOW}$OLD_EXP${NC}"
      echo -e "   Baru : ${GREEN}$new_expiry${NC}"
      ;;
  esac
  _ask_push "Renew license $code to $new_expiry"
}

# ─── CMD: LIST ────────────────────────────────────────────────────────────────
cmd_list() {
  local filter="${1:-all}"
  ensure_file
  print_header

  python3 - << PYEOF
import json, os
from datetime import datetime, date

fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
licenses = data.get("licenses", [])
today = date.today()
filter_mode = "$filter"

def get_status(l):
    if l.get("banned"): return "BANNED"
    if not l.get("active"): return "SUSPEND"
    try:
        if datetime.strptime(l.get("expiredAt","2099-01-01"),"%Y-%m-%d").date() < today:
            return "TAMAT"
    except: pass
    return "AKTIF"

def status_color(s):
    return {"AKTIF":"\033[32m","BANNED":"\033[31m","TAMAT":"\033[33m","SUSPEND":"\033[31m"}.get(s,"\033[0m")

# Apply filter
if filter_mode == "--active":
    licenses = [l for l in licenses if get_status(l) == "AKTIF"]
elif filter_mode == "--banned":
    licenses = [l for l in licenses if get_status(l) == "BANNED"]
elif filter_mode == "--expired":
    licenses = [l for l in licenses if get_status(l) == "TAMAT"]
elif filter_mode == "--suspended":
    licenses = [l for l in licenses if get_status(l) == "SUSPEND"]

if not licenses:
    print(f"  Tiada lesen dalam kategori '{filter_mode}'.")
else:
    fmt = "  {:<4} {:<22} {:<22} {:<12} {:<10} {}"
    print(fmt.format("#", "Kod Peranti", "Nama", "Tamat", "Status", "Peranti"))
    print("  " + "-"*88)
    for i,l in enumerate(licenses, 1):
        st = get_status(l)
        col = status_color(st)
        name = l.get("username","?")[:20]
        device = l.get("deviceInfo","?")[:20]
        exp = l.get("expiredAt","?")
        code = l.get("deviceCode","?")
        print(f"  {i:<4} {code:<22} {name:<22} {exp:<12} {col}{st:<10}\033[0m {device}")

print()
total = len(data.get("licenses",[]))
aktif = sum(1 for l in data["licenses"] if get_status(l)=="AKTIF")
print(f"  Jumlah: {total} rekod | Aktif: {aktif} | Penapis: {filter_mode}")
PYEOF
}

# ─── CMD: INFO ────────────────────────────────────────────────────────────────
cmd_info() {
  local code="$1"
  [ -z "$code" ] && { echo "Guna: bash manage_license.sh info <kod_peranti>"; exit 1; }
  ensure_file
  print_header

  python3 - << PYEOF
import json, os
from datetime import datetime, date

fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
e = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
today = date.today()

if not e:
    print(f"\033[31m  RALAT: Kod $code tidak dijumpai.\033[0m")
else:
    expiry_str = e.get("expiredAt","?")
    try:
        exp_date = datetime.strptime(expiry_str,"%Y-%m-%d").date()
        days_left = (exp_date - today).days
        if days_left < 0: expiry_info = f"{expiry_str} \033[31m(TAMAT {abs(days_left)} hari lalu)\033[0m"
        elif days_left == 0: expiry_info = f"{expiry_str} \033[31m(TAMAT HARI INI!)\033[0m"
        elif days_left <= 30: expiry_info = f"{expiry_str} \033[33m({days_left} hari lagi - akan tamat)\033[0m"
        else: expiry_info = f"{expiry_str} \033[32m({days_left} hari lagi)\033[0m"
    except: expiry_info = expiry_str

    if e.get("banned"): status = "\033[31mBANNED\033[0m"
    elif e.get("active"): status = "\033[32mAKTIF\033[0m"
    else: status = "\033[33mSUSPEND\033[0m"

    print(f"\033[1m  === Maklumat Lesen ===\033[0m")
    print(f"  ID           : {e.get('id','?')}")
    print(f"  Kod Peranti  : \033[33m{e.get('deviceCode','?')}\033[0m")
    print(f"  Nama         : {e.get('username','?')}")
    print(f"  Email        : {e.get('email','?')}")
    print(f"  Peranti      : {e.get('deviceInfo','?')}")
    print(f"  Tamat        : {expiry_info}")
    print(f"  Status       : {status}")
    if e.get("banned"):
        print(f"  Sebab Ban    : \033[31m{e.get('bannedReason','?')}\033[0m")
        print(f"  Tarikh Ban   : {e.get('bannedAt','?')}")
    print(f"  Ditambah     : {e.get('addedAt','?')}")
    print(f"  Kemaskini    : {e.get('lastModified','?')}")
    if e.get("note"): print(f"  Nota         : {e.get('note')}")
PYEOF
}

# ─── CMD: CHECK ───────────────────────────────────────────────────────────────
cmd_check() {
  local code="$1"
  [ -z "$code" ] && { echo "Guna: bash manage_license.sh check <kod_peranti>"; exit 1; }
  ensure_file
  print_header
  echo -e "${CYAN}>>> Simulasi Semakan App${NC}"
  echo ""

  python3 - << PYEOF
import json, os
from datetime import datetime, date

fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
e = next((l for l in data["licenses"] if l.get("deviceCode") == "$code"), None)
today = date.today()

print(f"  Kod : $code")
print()

if not e:
    print("\033[31m  Keputusan : ❌ NOT_FOUND\033[0m")
    print("  Mesej     : Peranti belum didaftarkan. Hantar kod kepada admin MTS.")
elif e.get("banned"):
    print("\033[31m  Keputusan : 🚫 BANNED\033[0m")
    print(f"  Sebab     : {e.get('bannedReason','Tiada maklumat')}")
elif not e.get("active"):
    print("\033[33m  Keputusan : ⏸️ SUSPENDED\033[0m")
    print("  Mesej     : Lesen digantung. Hubungi admin.")
else:
    expiry_str = e.get("expiredAt","2099-12-31")
    try:
        exp_date = datetime.strptime(expiry_str,"%Y-%m-%d").date()
        if exp_date < today:
            print("\033[33m  Keputusan : ⏰ EXPIRED\033[0m")
            print(f"  Tamat     : {expiry_str}")
            print("  Mesej     : Lesen tamat. Hubungi admin untuk perbaharui.")
        else:
            days_left = (exp_date - today).days
            print("\033[32m  Keputusan : ✅ VALID\033[0m")
            print(f"  Pengguna  : {e.get('username','?')}")
            print(f"  Tamat     : {expiry_str} ({days_left} hari lagi)")
            print(f"  Mesej     : Selamat datang, {e.get('username','User')}!")
    except:
        print("\033[32m  Keputusan : ✅ VALID\033[0m")
PYEOF
}

# ─── CMD: PUSH ────────────────────────────────────────────────────────────────
cmd_push() {
  local msg="${1:-license: Update license database}"
  ensure_file
  echo ""
  echo -e "${CYAN}>>> Push ke GitHub (${GITHUB_BRANCH})...${NC}"

  # Ensure we're in a git repo
  if ! git rev-parse --git-dir &>/dev/null; then
    echo -e "${RED}RALAT: Bukan dalam direktori git.${NC}"
    exit 1
  fi

  git add "$LICENSE_FILE"

  if git diff --staged --quiet; then
    echo -e "${YELLOW}Tiada perubahan untuk di-commit dalam $LICENSE_FILE.${NC}"
    return 0
  fi

  git commit -m "$msg"
  git push origin "$GITHUB_BRANCH"
  echo -e "${GREEN}✅ licenses.json berjaya di-push ke GitHub!${NC}"
  echo -e "   Branch  : ${CYAN}$GITHUB_BRANCH${NC}"
  echo -e "   Mesej   : $msg"
  echo -e "   Masa    : $(date '+%Y-%m-%d %H:%M:%S')"
  echo -e "   ${GRAY}Pengguna akan mendapat akses dalam ~30 saat.${NC}"
}

_ask_push() {
  local msg="$1"
  echo ""
  read -p "Push ke GitHub sekarang? (Y/n): " PUSH_NOW
  if [ "$PUSH_NOW" != "n" ] && [ "$PUSH_NOW" != "N" ]; then
    cmd_push "$msg"
  else
    echo -e "${YELLOW}Nota: Lesen belum dikemaskini di GitHub.${NC}"
    echo -e "      Jalankan: ${WHITE}bash manage_license.sh push${NC}"
  fi
}

# ─── CMD: EXPORT ──────────────────────────────────────────────────────────────
cmd_export() {
  local fmt="${1:-csv}"
  ensure_file
  print_header
  echo -e "${CYAN}>>> Eksport Database Lesen${NC}"
  echo ""

  python3 - << PYEOF
import json, csv, os
from datetime import datetime

fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
licenses = data.get("licenses", [])
fmt = "$fmt"
ts = datetime.now().strftime("%Y%m%d_%H%M%S")

if fmt == "csv":
    outfile = f"mtsflix_licenses_{ts}.csv"
    fields = ["id","deviceCode","username","email","expiredAt","active","banned","bannedReason","addedAt","deviceInfo","note","lastModified"]
    with open(outfile,"w",newline="",encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        w.writerows(licenses)
    print(f"  Eksport ke: {outfile}")
else:
    outfile = f"mtsflix_licenses_{ts}.json"
    with open(outfile,"w",encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"  Eksport ke: {outfile}")

print(f"  Jumlah    : {len(licenses)} rekod")
PYEOF
}

# ─── CMD: STATS ───────────────────────────────────────────────────────────────
cmd_stats() {
  ensure_file
  print_header
  echo -e "${CYAN}>>> Statistik Lesen MTSFlix${NC}"
  echo ""

  python3 - << PYEOF
import json, os
from datetime import datetime, date

fname = os.environ.get("LICENSE_FILE", "licenses.json")
with open(fname) as f: data = json.load(f)
licenses = data.get("licenses",[])
today = date.today()

total=0; aktif=0; banned=0; tamat=0; akan_tamat=0; suspend=0
for l in licenses:
    total += 1
    if l.get("banned"): banned += 1; continue
    if not l.get("active"): suspend += 1; continue
    try:
        exp = datetime.strptime(l.get("expiredAt","2099-12-31"),"%Y-%m-%d").date()
        days = (exp-today).days
        if days < 0: tamat += 1
        elif days <= 30: akan_tamat += 1
        else: aktif += 1
    except: aktif += 1

print(f"  Database   : {data.get('description','MTSFlix License Registry')}")
print(f"  Kemaskini  : {data.get('lastUpdated','?')}")
print()
print(f"\033[32m  ✅ Aktif        : {aktif}\033[0m")
print(f"\033[31m  🚫 Banned       : {banned}\033[0m")
print(f"\033[33m  ⏸️  Suspend      : {suspend}\033[0m")
print(f"\033[33m  ⏰ Tamat        : {tamat}\033[0m")
print(f"\033[33m  ⚠️  Nak Tamat    : {akan_tamat} (dalam 30 hari)\033[0m")
print(f"\033[37m  📊 Jumlah Total : {total}\033[0m")
PYEOF
}

# ─── CMD: HELP ────────────────────────────────────────────────────────────────
cmd_help() {
  print_header
  echo -e "${WHITE}${BOLD}Penggunaan:${NC} bash manage_license.sh <perintah> [pilihan]"
  echo ""
  echo -e "${CYAN}Perintah:${NC}"
  echo ""
  printf "  ${GREEN}%-10s${NC} %s\n" "add"     "Tambah lesen peranti baru"
  printf "  ${RED}%-10s${NC} %s\n"   "delete"  "Padam lesen SECARA KEKAL (data hilang)"
  printf "  ${YELLOW}%-10s${NC} %s\n" "ban"    "Gantung lesen (data kekal, boleh unban)"
  printf "  ${GREEN}%-10s${NC} %s\n" "unban"   "Aktifkan semula lesen yang digantung"
  printf "  ${CYAN}%-10s${NC} %s\n"  "renew"   "Perbaharui tarikh tamat lesen"
  printf "  ${WHITE}%-10s${NC} %s\n" "list"    "Senarai lesen [--active|--banned|--expired|--suspended]"
  printf "  ${WHITE}%-10s${NC} %s\n" "info"    "Maklumat terperinci satu lesen"
  printf "  ${WHITE}%-10s${NC} %s\n" "check"   "Simulasi semakan app terhadap lesen"
  printf "  ${CYAN}%-10s${NC} %s\n"  "push"    "Push licenses.json ke GitHub"
  printf "  ${WHITE}%-10s${NC} %s\n" "export"  "Eksport database [csv|json]"
  printf "  ${WHITE}%-10s${NC} %s\n" "stats"   "Tunjuk statistik lesen"
  echo ""
  echo -e "${CYAN}Contoh:${NC}"
  echo "  bash manage_license.sh add \"Ahmad Ali\" \"ahmad@gmail.com\" \"MTSF-A1B2-C3D4-E5F6\" \"2026-12-31\""
  echo "  bash manage_license.sh ban  \"MTSF-A1B2-C3D4-E5F6\" \"Melanggar syarat penggunaan\""
  echo "  bash manage_license.sh renew \"MTSF-A1B2-C3D4-E5F6\" \"2027-12-31\""
  echo "  bash manage_license.sh list --active"
  echo "  bash manage_license.sh stats"
  echo ""
  echo -e "${GRAY}Nota: licenses.json diubah secara tempatan dan mesti di-push ke GitHub${NC}"
  echo -e "${GRAY}      menggunakan perintah 'push' atau secara automatik selepas setiap operasi.${NC}"
  echo ""
}

# ─── Main ─────────────────────────────────────────────────────────────────────
check_deps

COMMAND="${1:-help}"
shift 2>/dev/null || true

case "$COMMAND" in
  add)     cmd_add    "$@" ;;
  delete)  cmd_delete "$@" ;;
  ban)     cmd_ban    "$@" ;;
  unban)   cmd_unban  "$@" ;;
  renew)   cmd_renew  "$@" ;;
  list)    cmd_list   "$@" ;;
  info)    cmd_info   "$@" ;;
  check)   cmd_check  "$@" ;;
  push)    cmd_push   "$@" ;;
  export)  cmd_export "$@" ;;
  stats)   cmd_stats ;;
  help|--help|-h) cmd_help ;;
  *)
    echo -e "${RED}RALAT: Perintah tidak dikenali: '$COMMAND'${NC}"
    echo ""
    cmd_help
    exit 1
    ;;
esac
