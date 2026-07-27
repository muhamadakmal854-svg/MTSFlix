/**
 * =============================================================================
 * MTSFlix Cloudflare Worker — License Registration Engine
 * =============================================================================
 * Fail ini membolehkan pendaftaran, semakan, penggantungan (ban), dan pengurusan
 * lesen peranti MTSFlix terus melalui Cloudflare Worker.
 *
 * Worker ini membaca & mengemaskini fail `licenses.json` di GitHub secara terus
 * menggunakan GitHub REST API tanpa mengubah sebarang kod dalam aplikasi sedia ada.
 *
 * 📍 TEKNIKAL:
 * - Bahasa    : JavaScript (ES Modules for Cloudflare Worker)
 * - Storage   : GitHub REST API (Direct Commit to `licenses.json`)
 * - Features  : 
 *     1. REST API (POST /register, GET /verify, POST /ban, GET /licenses)
 *     2. Web Admin Dashboard (Visual Web UI untuk urus lesen dari browser)
 *     3. Auto-Formatting (DeviceCode, Dates, IDs)
 *     4. Security (Admin Key Authentication)
 *
 * 🔧 PEMBOLEH UBAH PERSEKITARAN (Cloudflare Worker Environment Variables):
 * - GITHUB_TOKEN  : GitHub Personal Access Token (PAT) dengan skop 'repo' (WAJIB)
 * - GITHUB_REPO   : "muhamadakmal854-svg/MTSFlix" (Lalai)
 * - GITHUB_BRANCH : "main" (Lalai)
 * - ADMIN_KEY     : "MTSFLIX2026" (Kunci rahsia untuk akses Admin Dashboard & API write)
 * =============================================================================
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    // CORS Headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Admin-Key',
    };

    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    const CONFIG = {
      GITHUB_TOKEN: env.GITHUB_TOKEN || '', // Dapatkan dari Cloudflare Worker Secret / Env Variable
      GITHUB_REPO: env.GITHUB_REPO || 'muhamadakmal854-svg/MTSFlix',
      GITHUB_BRANCH: env.GITHUB_BRANCH || 'main',
      ADMIN_KEY: env.ADMIN_KEY || '', // Kosong = tiada kunci keselamatan
    };

    // Helper Response
    const jsonRes = (data, status = 200) => {
      return new Response(JSON.stringify(data, null, 2), {
        status,
        headers: {
          'Content-Type': 'application/json',
          ...corsHeaders,
        },
      });
    };

    // Helper Authentication
    const isAuthorized = (req) => {
      if (!CONFIG.ADMIN_KEY) return true;
      const keyHeader = req.headers.get('X-Admin-Key') || req.headers.get('Authorization')?.replace('Bearer ', '');
      const keyQuery = url.searchParams.get('key');
      return keyHeader === CONFIG.ADMIN_KEY || keyQuery === CONFIG.ADMIN_KEY;
    };

    try {
      // ───────────────────────────────────────────────────────────────────────
      // ROUTE 1: GET / or GET /index.html or GET /admin — Web Dashboard Admin
      // ───────────────────────────────────────────────────────────────────────
      if ((path === '/' || path === '/index.html' || path === '/admin') && method === 'GET') {
        const html = getAdminDashboardHTML(CONFIG.ADMIN_KEY);
        return new Response(html, {
          headers: { 'Content-Type': 'text/html; charset=utf-8', ...corsHeaders },
        });
      }

      // ───────────────────────────────────────────────────────────────────────
      // ROUTE 2: GET /verify?code=MTSF-XXXX-XXXX-XXXX — Semak Lesen Peranti
      // ───────────────────────────────────────────────────────────────────────
      if (path === '/verify' && method === 'GET') {
        const deviceCode = url.searchParams.get('code') || url.searchParams.get('deviceCode');
        if (!deviceCode) {
          return jsonRes({ ok: false, error: 'Parameter code atau deviceCode diperlukan' }, 400);
        }

        const formattedCode = formatDeviceCode(deviceCode);
        const repoData = await fetchLicensesFromGitHub(CONFIG);
        const target = repoData.licenses.find(
          (l) => l.deviceCode.toUpperCase() === formattedCode.toUpperCase()
        );

        if (!target) {
          return jsonRes({ ok: false, valid: false, message: 'Lesen tidak ditemui dalam pangkalan data' }, 404);
        }

        const today = new Date().toISOString().split('T')[0];
        const isExpired = target.expiredAt && target.expiredAt < today;
        const isValid = target.active && !target.banned && !isExpired;

        return jsonRes({
          ok: true,
          valid: isValid,
          license: target,
          status: target.banned
            ? 'BANNED'
            : !target.active
            ? 'INACTIVE'
            : isExpired
            ? 'EXPIRED'
            : 'ACTIVE',
        });
      }

      // ───────────────────────────────────────────────────────────────────────
      // ROUTE 3: GET /licenses — Dapatkan Senarai Semua Lesen
      // ───────────────────────────────────────────────────────────────────────
      if (path === '/licenses' && method === 'GET') {
        if (!isAuthorized(request)) {
          return jsonRes({ ok: false, error: 'Akses ditolak: Admin key tidak sah' }, 401);
        }
        const repoData = await fetchLicensesFromGitHub(CONFIG);
        return jsonRes({
          ok: true,
          total: repoData.licenses.length,
          lastUpdated: repoData.lastUpdated,
          licenses: repoData.licenses,
        });
      }

      // ───────────────────────────────────────────────────────────────────────
      // ROUTE 4: POST /register ATAU POST /add — Daftar Lesen Baru
      // ───────────────────────────────────────────────────────────────────────
      if ((path === '/register' || path === '/add') && method === 'POST') {
        if (!isAuthorized(request)) {
          return jsonRes({ ok: false, error: 'Akses ditolak: Admin key tidak sah' }, 401);
        }

        let body = {};
        try {
          body = await request.json();
        } catch (_) {
          return jsonRes({ ok: false, error: 'Format JSON body tidak sah' }, 400);
        }

        const { deviceCode, username, email, expiredAt, deviceInfo, note } = body;

        if (!deviceCode || !username) {
          return jsonRes(
            { ok: false, error: 'Medan deviceCode dan username adalah wajib' },
            400
          );
        }

        const formattedCode = formatDeviceCode(deviceCode);
        const repoFile = await fetchLicensesFileFromGitHub(CONFIG);
        const repoData = JSON.parse(decodeBase64Unicode(repoFile.content));

        // Semak jika peranti sudah wujud
        const existingIdx = repoData.licenses.findIndex(
          (l) => l.deviceCode.toUpperCase() === formattedCode.toUpperCase()
        );

        const now = new Date();
        const dateStr = now.toISOString().split('T')[0];
        const timeStr = now.toISOString().split('.')[0];
        const defaultExpire = '2099-12-30';

        let newOrUpdatedLicense;

        if (existingIdx >= 0) {
          // Kemaskini lesen sedia ada
          newOrUpdatedLicense = {
            ...repoData.licenses[existingIdx],
            username: username.trim(),
            email: (email || repoData.licenses[existingIdx].email || '').trim(),
            expiredAt: expiredAt || repoData.licenses[existingIdx].expiredAt || defaultExpire,
            active: true,
            banned: false,
            bannedReason: '',
            deviceInfo: deviceInfo !== undefined ? deviceInfo : repoData.licenses[existingIdx].deviceInfo || 'Tidak dinyatakan',
            note: note !== undefined ? note : repoData.licenses[existingIdx].note || 'Updated via Cloudflare Worker',
            lastModified: timeStr,
          };
          repoData.licenses[existingIdx] = newOrUpdatedLicense;
        } else {
          // Cipta lesen baru
          const hexId = generateHex(8);
          const dateCompact = dateStr.replace(/-/g, '');
          const licenseId = `LIC-${dateCompact}-${hexId}`;

          newOrUpdatedLicense = {
            id: licenseId,
            deviceCode: formattedCode,
            username: username.trim(),
            email: (email || `${username.toLowerCase().replace(/\s+/g, '')}@gmail.com`).trim(),
            expiredAt: expiredAt || defaultExpire,
            active: true,
            banned: false,
            bannedReason: '',
            addedAt: dateStr,
            deviceInfo: deviceInfo || 'Tidak dinyatakan',
            note: note || 'Registered via Cloudflare Worker',
            lastModified: timeStr,
          };
          repoData.licenses.push(newOrUpdatedLicense);
        }

        repoData.lastUpdated = dateStr;

        // Push kemas kini ke GitHub
        const commitMsg = `feat(license): Pendaftaran lesen ${formattedCode} (${username}) via Cloudflare Worker`;
        await saveLicensesToGitHub(CONFIG, repoData, repoFile.sha, commitMsg);

        return jsonRes({
          ok: true,
          message: existingIdx >= 0 ? 'Lesen sedia ada berjaya dikemas kini!' : 'Lesen baru berjaya didaftarkan!',
          license: newOrUpdatedLicense,
        });
      }

      // ───────────────────────────────────────────────────────────────────────
      // ROUTE 5: POST /ban — Gantung/Ban Lesen Peranti
      // ───────────────────────────────────────────────────────────────────────
      if (path === '/ban' && method === 'POST') {
        if (!isAuthorized(request)) {
          return jsonRes({ ok: false, error: 'Akses ditolak: Admin key tidak sah' }, 401);
        }

        let body = {};
        try { body = await request.json(); } catch (_) {}
        const deviceCode = body.deviceCode || body.code || url.searchParams.get('code');
        const reason = body.reason || 'Disukai pentadbir / Banned by admin';

        if (!deviceCode) {
          return jsonRes({ ok: false, error: 'deviceCode diperlukan' }, 400);
        }

        const formattedCode = formatDeviceCode(deviceCode);
        const repoFile = await fetchLicensesFileFromGitHub(CONFIG);
        const repoData = JSON.parse(decodeBase64Unicode(repoFile.content));

        const idx = repoData.licenses.findIndex(
          (l) => l.deviceCode.toUpperCase() === formattedCode.toUpperCase() || l.id === deviceCode
        );

        if (idx === -1) {
          return jsonRes({ ok: false, error: 'Lesen tidak ditemui' }, 404);
        }

        repoData.licenses[idx].banned = true;
        repoData.licenses[idx].active = false;
        repoData.licenses[idx].bannedReason = reason;
        repoData.licenses[idx].lastModified = new Date().toISOString().split('.')[0];
        repoData.lastUpdated = new Date().toISOString().split('T')[0];

        const commitMsg = `fix(license): Gantung lesen ${formattedCode} via Cloudflare Worker`;
        await saveLicensesToGitHub(CONFIG, repoData, repoFile.sha, commitMsg);

        return jsonRes({
          ok: true,
          message: `Lesen peranti ${formattedCode} berjaya digantung (banned)!`,
          license: repoData.licenses[idx],
        });
      }

      // ───────────────────────────────────────────────────────────────────────
      // ROUTE 6: POST /delete — Padam Lesen Peranti
      // ───────────────────────────────────────────────────────────────────────
      if (path === '/delete' && method === 'POST') {
        if (!isAuthorized(request)) {
          return jsonRes({ ok: false, error: 'Akses ditolak: Admin key tidak sah' }, 401);
        }

        let body = {};
        try { body = await request.json(); } catch (_) {}
        const deviceCode = body.deviceCode || body.code || url.searchParams.get('code');

        if (!deviceCode) {
          return jsonRes({ ok: false, error: 'deviceCode diperlukan' }, 400);
        }

        const formattedCode = formatDeviceCode(deviceCode);
        const repoFile = await fetchLicensesFileFromGitHub(CONFIG);
        const repoData = JSON.parse(decodeBase64Unicode(repoFile.content));

        const originalLen = repoData.licenses.length;
        repoData.licenses = repoData.licenses.filter(
          (l) => l.deviceCode.toUpperCase() !== formattedCode.toUpperCase() && l.id !== deviceCode
        );

        if (repoData.licenses.length === originalLen) {
          return jsonRes({ ok: false, error: 'Lesen tidak ditemui untuk dipadam' }, 404);
        }

        repoData.lastUpdated = new Date().toISOString().split('T')[0];
        const commitMsg = `chore(license): Padam lesen ${formattedCode} via Cloudflare Worker`;
        await saveLicensesToGitHub(CONFIG, repoData, repoFile.sha, commitMsg);

        return jsonRes({
          ok: true,
          message: `Lesen ${formattedCode} berjaya dipadam dari pangkalan data.`,
        });
      }

      // Route Tidak Ditemui
      return jsonRes({ ok: false, error: `Laluan ${method} ${path} tidak ditemui` }, 404);

    } catch (err) {
      return jsonRes(
        {
          ok: false,
          error: err.message || 'Ralat pelayan dalaman',
        },
        500
      );
    }
  },
};

// ═════════════════════════════════════════════════════════════════════════════
//  HELPER FUNCTIONS — GITHUB API INTEGRATION
// ═════════════════════════════════════════════════════════════════════════════

async function fetchLicensesFileFromGitHub(config) {
  const url = `https://api.github.com/repos/${config.GITHUB_REPO}/contents/licenses.json?ref=${config.GITHUB_BRANCH}`;
  const res = await fetch(url, {
    headers: {
      Authorization: `token ${config.GITHUB_TOKEN}`,
      'User-Agent': 'Cloudflare-Worker-MTSFlix',
      Accept: 'application/vnd.github.v3+json',
    },
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Gagal mengambil licenses.json dari GitHub (${res.status}): ${errText}`);
  }

  return await res.json();
}

async function fetchLicensesFromGitHub(config) {
  const fileData = await fetchLicensesFileFromGitHub(config);
  const contentDecoded = decodeBase64Unicode(fileData.content);
  return JSON.parse(contentDecoded);
}

async function saveLicensesToGitHub(config, jsonObject, sha, commitMessage) {
  const url = `https://api.github.com/repos/${config.GITHUB_REPO}/contents/licenses.json`;
  const jsonString = JSON.stringify(jsonObject, null, 2);
  const base64Content = encodeBase64Unicode(jsonString);

  const payload = {
    message: commitMessage,
    content: base64Content,
    sha: sha,
    branch: config.GITHUB_BRANCH,
  };

  const res = await fetch(url, {
    method: 'PUT',
    headers: {
      Authorization: `token ${config.GITHUB_TOKEN}`,
      'User-Agent': 'Cloudflare-Worker-MTSFlix',
      'Content-Type': 'application/json',
      Accept: 'application/vnd.github.v3+json',
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Gagal commit ke GitHub (${res.status}): ${errText}`);
  }

  return await res.json();
}

// ═════════════════════════════════════════════════════════════════════════════
//  FORMATTERS & UTILS
// ═════════════════════════════════════════════════════════════════════════════

function formatDeviceCode(raw) {
  if (!raw) return '';
  let clean = raw.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');

  if (clean.startsWith('MTSF')) {
    clean = clean.substring(4);
  }

  clean = clean.padEnd(12, 'X').substring(0, 12);
  const p1 = clean.substring(0, 4);
  const p2 = clean.substring(4, 8);
  const p3 = clean.substring(8, 12);

  return `MTSF-${p1}-${p2}-${p3}`;
}

function generateHex(length) {
  const chars = '0123456789ABCDEF';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

function encodeBase64Unicode(str) {
  return btoa(
    encodeURIComponent(str).replace(/%([0-9A-F]{2})/g, function toSolidBytes(match, p1) {
      return String.fromCharCode('0x' + p1);
    })
  );
}

function decodeBase64Unicode(str) {
  const cleanStr = str.replace(/\n/g, '').replace(/\r/g, '');
  return decodeURIComponent(
    atob(cleanStr)
      .split('')
      .map(function (c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      })
      .join('')
  );
}

// ═════════════════════════════════════════════════════════════════════════════
//  WEB DASHBOARD ADMIN (HTML/CSS/JS)
// ═════════════════════════════════════════════════════════════════════════════

function getAdminDashboardHTML(adminKey) {
  return `<!DOCTYPE html>
<html lang="ms">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>MTSFlix — Portal Pendaftaran Lesen (Cloudflare Worker)</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #0B0B0E;
      --card-bg: #141419;
      --border: #23232C;
      --red: #E50914;
      --red-hover: #F40612;
      --text: #EEEEEE;
      --text-muted: #888899;
      --green: #4CAF50;
      --orange: #FF9800;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }
    body { background-color: var(--bg); color: var(--text); padding: 20px; display: flex; justify-content: center; min-height: 100vh; }
    .container { width: 100%; max-width: 900px; margin-top: 20px; }
    .header { text-align: center; margin-bottom: 30px; }
    .brand { font-size: 32px; font-weight: 700; letter-spacing: 1px; color: var(--red); display: inline-block; }
    .brand span { color: #FFF; }
    .subtitle { color: var(--text-muted); font-size: 14px; margin-top: 5px; }

    .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; padding: 24px; margin-bottom: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
    .card-title { font-size: 18px; font-weight: 600; margin-bottom: 16px; border-bottom: 1px solid var(--border); padding-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }

    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; } }
    .form-group { display: flex; flex-direction: column; }
    .form-group.full { grid-column: 1 / -1; }
    label { font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
    input, select { background: #1C1C24; border: 1px solid var(--border); color: #FFF; padding: 12px 14px; border-radius: 10px; font-size: 14px; outline: none; transition: 0.2s; }
    input:focus, select:focus { border-color: var(--red); box-shadow: 0 0 0 3px rgba(229,9,20,0.2); }

    .btn { background: var(--red); color: #FFF; border: none; padding: 14px 24px; border-radius: 10px; font-weight: 600; font-size: 15px; cursor: pointer; transition: 0.2s; width: 100%; margin-top: 16px; }
    .btn:hover { background: var(--red-hover); transform: translateY(-2px); }
    .btn-secondary { background: #2A2A36; color: #FFF; width: auto; padding: 8px 14px; font-size: 12px; margin: 0; }
    .btn-secondary:hover { background: #3A3A4A; }

    .table-container { overflow-x: auto; margin-top: 10px; }
    table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
    th { background: #1C1C24; color: var(--text-muted); font-weight: 600; padding: 12px; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; }
    td { padding: 12px; border-bottom: 1px solid var(--border); }
    tr:hover { background: rgba(255,255,255,0.02); }

    .badge { padding: 4px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; text-transform: uppercase; display: inline-block; }
    .badge-active { background: rgba(76,175,80,0.15); color: #4CAF50; border: 1px solid #4CAF50; }
    .badge-banned { background: rgba(229,9,20,0.15); color: #E50914; border: 1px solid #E50914; }

    .toast { position: fixed; bottom: 20px; right: 20px; background: #222; color: #FFF; padding: 14px 20px; border-radius: 10px; border-left: 4px solid var(--red); box-shadow: 0 10px 20px rgba(0,0,0,0.5); display: none; z-index: 99; font-size: 14px; }
  </style>
</head>
<body>

  <div class="container">
    <div class="header">
      <div class="brand">MTS<span>FLIX</span></div>
      <div class="subtitle">Cloudflare Worker — Engine Pendaftaran Lesen Peranti</div>
    </div>

    <!-- Pendaftaran Lesen -->
    <div class="card">
      <div class="card-title">➕ Daftar / Kemaskini Lesen Peranti</div>
      <form id="regForm">
        <div class="form-grid">
          <div class="form-group">
            <label>Kod Peranti (Device Code)</label>
            <input type="text" id="deviceCode" placeholder="cth: MTSF-5CDB-4808-57E2 ATAU 5CDB480857E2" required>
          </div>
          <div class="form-group">
            <label>Nama Pengguna / Pemilik</label>
            <input type="text" id="username" placeholder="cth: Akmal TV Ruang Tamu" required>
          </div>
          <div class="form-group">
            <label>Email Pengguna</label>
            <input type="email" id="email" placeholder="cth: akmal@gmail.com">
          </div>
          <div class="form-group">
            <label>Tarikh Luput (Expired Date)</label>
            <input type="date" id="expiredAt" value="2099-12-30">
          </div>
          <div class="form-group">
            <label>Maklumat Peranti (Device Info)</label>
            <input type="text" id="deviceInfo" placeholder="cth: Xiaomi Stick 1080p, Phone Honor">
          </div>
          <div class="form-group">
            <label>Nota / Catatan</label>
            <input type="text" id="note" placeholder="cth: VIP, Pembayaran RM50">
          </div>
        </div>
        <button type="submit" class="btn" id="btnSubmit">🚀 Push & Simpan Lesen Ke GitHub</button>
      </form>
    </div>

    <!-- Senarai Lesen Sedia Ada -->
    <div class="card">
      <div class="card-title">
        <span>📋 Senarai Lesen Terdaftar</span>
        <button onclick="loadLicenses()" class="btn-secondary">🔄 Muat Semula</button>
      </div>
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>ID & Kod Peranti</th>
              <th>Pengguna / Email</th>
              <th>Peranti</th>
              <th>Luput</th>
              <th>Status</th>
              <th>Tindakan</th>
            </tr>
          </thead>
          <tbody id="licenseTable">
            <tr><td colspan="6" style="text-align:center; color: var(--text-muted); padding:20px;">Memuatkan data dari GitHub...</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div id="toast" class="toast"></div>

  <script>
    const ADMIN_KEY = '${adminKey}';

    function showToast(msg, isErr = false) {
      const t = document.getElementById('toast');
      t.innerText = msg;
      t.style.borderLeftColor = isErr ? '#E50914' : '#4CAF50';
      t.style.display = 'block';
      setTimeout(() => t.style.display = 'none', 4000);
    }

    // Auto Format Device Code as user types
    document.getElementById('deviceCode').addEventListener('input', (e) => {
      let val = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
      if (val.startsWith('MTSF')) val = val.substring(4);
      if (val.length > 0) {
        let parts = [];
        for (let i = 0; i < val.length && i < 12; i += 4) {
          parts.push(val.substring(i, i + 4));
        }
        e.target.value = 'MTSF-' + parts.join('-');
      }
    });

    document.getElementById('regForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = document.getElementById('btnSubmit');
      btn.innerText = '⏳ Sedang Push ke GitHub...';
      btn.disabled = true;

      const payload = {
        deviceCode: document.getElementById('deviceCode').value,
        username: document.getElementById('username').value,
        email: document.getElementById('email').value,
        expiredAt: document.getElementById('expiredAt').value,
        deviceInfo: document.getElementById('deviceInfo').value,
        note: document.getElementById('note').value
      };

      try {
        const res = await fetch('/register', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Admin-Key': ADMIN_KEY
          },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.ok) {
          showToast('✅ ' + data.message);
          document.getElementById('regForm').reset();
          document.getElementById('expiredAt').value = '2099-12-30';
          loadLicenses();
        } else {
          showToast('❌ ' + (data.error || 'Gagal mendaftar lesen'), true);
        }
      } catch (err) {
        showToast('❌ Ralat: ' + err.message, true);
      } finally {
        btn.innerText = '🚀 Push & Simpan Lesen Ke GitHub';
        btn.disabled = false;
      }
    });

    async function loadLicenses() {
      const tbody = document.getElementById('licenseTable');
      try {
        const res = await fetch('/licenses?key=' + encodeURIComponent(ADMIN_KEY));
        const data = await res.json();
        if (data.ok && data.licenses) {
          tbody.innerHTML = data.licenses.reverse().map(l => \`
            <tr>
              <td>
                <strong style="color:#FFF;">\${l.deviceCode}</strong><br>
                <small style="color:var(--text-muted);">\${l.id}</small>
              </td>
              <td>
                <div>\${l.username}</div>
                <small style="color:var(--text-muted);">\${l.email || '-'}</small>
              </td>
              <td>\${l.deviceInfo || '-'}</td>
              <td>\${l.expiredAt}</td>
              <td>
                <span class="badge \${l.banned ? 'badge-banned' : 'badge-active'}">
                  \${l.banned ? 'BANNED' : 'AKTIF'}
                </span>
              </td>
              <td>
                \${!l.banned ? \`<button onclick="banLicense('\${l.deviceCode}')" class="btn-secondary" style="background:#4A1111; color:#FF8888;">Ban</button>\` : ''}
              </td>
            </tr>
          \`).join('');
        } else {
          tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#FF5555;">Gagal memuatkan lesen.</td></tr>';
        }
      } catch (err) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#FF5555;">Ralat sambungan.</td></tr>';
      }
    }

    async function banLicense(code) {
      if (!confirm('Adakah anda pasti untuk GANTUNG/BAN lesen ' + code + '?')) return;
      try {
        const res = await fetch('/ban', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Admin-Key': ADMIN_KEY },
          body: JSON.stringify({ deviceCode: code, reason: 'Banned via Admin Dashboard' })
        });
        const data = await res.json();
        if (data.ok) {
          showToast('🚫 Lesen ' + code + ' telah digantung!');
          loadLicenses();
        } else {
          showToast('❌ ' + data.error, true);
        }
      } catch (err) {
        showToast('❌ Ralat: ' + err.message, true);
      }
    }

    // Auto load on start
    loadLicenses();
  </script>
</body>
</html>`;
}
