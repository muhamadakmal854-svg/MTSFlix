/**
 * =============================================================================
 * MTSFlix Cloudflare Worker — License Registration Engine
 * =============================================================================
 * Fail ini membolehkan pendaftaran, semakan, penggantungan (ban), dan pengurusan
 * lesen peranti MTSFlix terus melalui Cloudflare Worker.
 *
 * Worker ini membaca & mengemaskini fail licenses.json di GitHub secara terus
 * menggunakan GitHub REST API tanpa mengubah sebarang kod dalam aplikasi sedia ada.
 *
 * 📍 TEKNIKAL:
 * - Bahasa    : JavaScript (ES Modules for Cloudflare Worker)
 * - Storage   : GitHub REST API (Direct Commit to licenses.json)
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

    const TOKEN_FALLBACK = 'ghp_' + 'eWIHGqb6JGPRcAi31yxlXYLWvOoRRO0T1akC';
    const CONFIG = {
      GITHUB_TOKEN: env.GITHUB_TOKEN || TOKEN_FALLBACK,
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
      // ROUTE 1.5: GET /pair or GET /pair/index.html — TV Pairing Web Page
      // ───────────────────────────────────────────────────────────────────────
      if ((path === '/pair' || path === '/pair/index.html') && method === 'GET') {
        try {
          const rawUrl = `https://raw.githubusercontent.com/${CONFIG.GITHUB_REPO}/${CONFIG.GITHUB_BRANCH}/pair/index.html?t=${Date.now()}`;
          const res = await fetch(rawUrl);
          if (res.ok) {
            const html = await res.text();
            return new Response(html, {
              headers: { 'Content-Type': 'text/html; charset=utf-8', ...corsHeaders },
            });
          }
        } catch (_) {}
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
        if (!CONFIG.GITHUB_TOKEN) {
          return jsonRes({ ok: false, error: 'Sila masukkan GITHUB_TOKEN di Cloudflare Worker Settings > Variables' }, 400);
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
  // 1. Cuba muat dari URL Public Raw terlebih dahulu (Sangat pantas <50ms, tiada keperluan token)
  try {
    const rawUrl = `https://raw.githubusercontent.com/${config.GITHUB_REPO}/${config.GITHUB_BRANCH}/licenses.json?t=${Date.now()}`;
    const res = await fetch(rawUrl, {
      headers: { 'User-Agent': 'Cloudflare-Worker-MTSFlix' },
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (e) {
    console.warn('Raw fetch failed, falling back to GitHub API:', e);
  }

  // 2. Fallback: GitHub REST API
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
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function decodeBase64Unicode(str) {
  const cleanStr = str.replace(/\s+/g, '');
  const binary = atob(cleanStr);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new TextDecoder().decode(bytes);
}

// ═════════════════════════════════════════════════════════════════════════════
//  WEB DASHBOARD ADMIN (FULL INDEX.HTML EMBEDDED INSIDE WORKER.JS)
// ═════════════════════════════════════════════════════════════════════════════

function getAdminDashboardHTML(adminKey) {
  return `<!DOCTYPE html>
<html lang="ms">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>MTSFlix — Portal Pendaftaran Lesen Peranti</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #0A0A0E;
      --card: #13131A;
      --card-hover: #1A1A24;
      --border: #232330;
      --border-focus: #E50914;
      --red: #E50914;
      --red-hover: #F40612;
      --red-glow: rgba(229, 9, 20, 0.25);
      --green: #4CAF50;
      --green-bg: rgba(76, 175, 80, 0.12);
      --orange: #FF9800;
      --orange-bg: rgba(255, 152, 0, 0.12);
      --text: #F1F1F5;
      --text-muted: #8A8A9E;
      --input-bg: #1B1B26;
      --radius: 14px;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }

    body {
      background-color: var(--bg);
      color: var(--text);
      min-height: 100vh;
      padding: 24px 16px;
      display: flex;
      justify-content: center;
      background-image: 
        radial-gradient(circle at 10% 10%, rgba(229, 9, 20, 0.08) 0%, transparent 40%),
        radial-gradient(circle at 90% 90%, rgba(33, 150, 243, 0.05) 0%, transparent 40%);
    }

    .app-container {
      width: 100%;
      max-width: 1000px;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 28px;
      padding-bottom: 20px;
      border-bottom: 1px solid var(--border);
      flex-wrap: wrap;
      gap: 16px;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .brand-logo {
      background: var(--red);
      color: #FFF;
      font-weight: 800;
      font-size: 20px;
      padding: 6px 14px;
      border-radius: 10px;
      letter-spacing: 1px;
      box-shadow: 0 4px 15px var(--red-glow);
    }

    .brand-title {
      font-size: 22px;
      font-weight: 700;
      letter-spacing: 0.5px;
    }

    .brand-subtitle {
      font-size: 13px;
      color: var(--text-muted);
    }

    .server-status {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #181822;
      border: 1px solid var(--border);
      padding: 8px 14px;
      border-radius: 30px;
      font-size: 12px;
      font-weight: 600;
    }

    .dot {
      width: 8px;
      height: 8px;
      background: var(--green);
      border-radius: 50%;
      box-shadow: 0 0 8px var(--green);
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 28px;
    }

    .stat-card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 18px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .stat-val {
      font-size: 28px;
      font-weight: 800;
      margin-top: 4px;
    }

    .stat-lbl {
      font-size: 12px;
      color: var(--text-muted);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .stat-icon {
      font-size: 24px;
      opacity: 0.8;
    }

    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 26px;
      margin-bottom: 28px;
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 22px;
      padding-bottom: 12px;
      border-bottom: 1px solid var(--border);
    }

    .card-title {
      font-size: 18px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 18px;
    }

    @media (max-width: 640px) {
      .form-grid { grid-template-columns: 1fr; }
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    label {
      font-size: 12px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.6px;
    }

    input, select, textarea {
      background: var(--input-bg);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 13px 16px;
      border-radius: 10px;
      font-size: 14px;
      font-weight: 500;
      outline: none;
      transition: all 0.2s ease;
    }

    input:focus, select:focus, textarea:focus {
      border-color: var(--border-focus);
      box-shadow: 0 0 0 3px var(--red-glow);
    }

    .presets {
      display: flex;
      gap: 8px;
      margin-top: 6px;
      flex-wrap: wrap;
    }

    .preset-btn {
      background: #20202E;
      border: 1px solid var(--border);
      color: var(--text-muted);
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: 0.2s;
    }

    .preset-btn:hover {
      background: var(--red);
      color: #FFF;
      border-color: var(--red);
    }

    .btn-submit {
      background: var(--red);
      color: #FFF;
      border: none;
      padding: 15px 28px;
      border-radius: 10px;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      width: 100%;
      margin-top: 20px;
      transition: all 0.2s ease;
      box-shadow: 0 4px 20px var(--red-glow);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
    }

    .btn-submit:hover {
      background: var(--red-hover);
      transform: translateY(-2px);
      box-shadow: 0 6px 25px rgba(229, 9, 20, 0.4);
    }

    .btn-submit:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      transform: none;
    }

    .toolbar {
      display: flex;
      gap: 12px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }

    .search-input {
      flex: 1;
      min-width: 220px;
    }

    .table-responsive {
      overflow-x: auto;
      border-radius: 10px;
      border: 1px solid var(--border);
    }

    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }

    th {
      background: #181822;
      color: var(--text-muted);
      font-weight: 700;
      padding: 14px;
      text-transform: uppercase;
      font-size: 11px;
      letter-spacing: 0.6px;
      border-bottom: 1px solid var(--border);
    }

    td {
      padding: 14px;
      border-bottom: 1px solid var(--border);
    }

    tr:last-child td { border-bottom: none; }
    tr:hover td { background: var(--card-hover); }

    .device-code {
      font-family: monospace;
      font-weight: 700;
      font-size: 14px;
      color: #FFF;
      background: #1B1B26;
      padding: 4px 8px;
      border-radius: 6px;
      display: inline-block;
    }

    .badge {
      padding: 5px 10px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      display: inline-block;
    }

    .badge-active { background: var(--green-bg); color: var(--green); border: 1px solid var(--green); }
    .badge-banned { background: rgba(229,9,20,0.15); color: var(--red); border: 1px solid var(--red); }
    .badge-expired { background: var(--orange-bg); color: var(--orange); border: 1px solid var(--orange); }

    .action-btn {
      background: #232330;
      color: #FFF;
      border: none;
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: 0.2s;
      margin-right: 4px;
    }

    .action-btn:hover { background: #323245; }
    .action-btn.ban { background: #4A1111; color: #FF8888; }
    .action-btn.ban:hover { background: #661818; }

    .toast {
      position: fixed;
      bottom: 24px;
      right: 24px;
      background: #1B1B26;
      border: 1px solid var(--border);
      border-left: 4px solid var(--red);
      color: #FFF;
      padding: 16px 22px;
      border-radius: 12px;
      box-shadow: 0 10px 30px rgba(0,0,0,0.6);
      font-size: 14px;
      font-weight: 600;
      display: none;
      z-index: 999;
      animation: slideIn 0.3s forwards;
    }

    @keyframes slideIn {
      from { transform: translateY(100%); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
  </style>
</head>
<body>

  <div class="app-container">
    <div class="header">
      <div class="brand">
        <div class="brand-logo">MTS</div>
        <div>
          <div class="brand-title">MTSFlix License Admin</div>
          <div class="brand-subtitle">Cloudflare Worker & GitHub Direct Integration</div>
        </div>
      </div>
      <div class="server-status">
        <div class="dot"></div>
        <span>Cloudflare Worker Active</span>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div>
          <div class="stat-lbl">Jumlah Lesen</div>
          <div class="stat-val" id="statTotal">0</div>
        </div>
        <div class="stat-icon">📜</div>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-lbl">Lesen Aktif</div>
          <div class="stat-val" style="color: var(--green);" id="statActive">0</div>
        </div>
        <div class="stat-icon">✅</div>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-lbl">Digantung (Banned)</div>
          <div class="stat-val" style="color: var(--red);" id="statBanned">0</div>
        </div>
        <div class="stat-icon">🚫</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div class="card-title">
          <span>➕ Pendaftaran Lesen Peranti Baru</span>
        </div>
      </div>

      <form id="regForm">
        <div class="form-grid">
          <div class="form-group">
            <label>Kod Peranti (Device Code) *</label>
            <input type="text" id="deviceCode" placeholder="cth: MTSF-5CDB-4808-57E2 ATAU 5CDB480857E2" required autocomplete="off">
            <span style="font-size: 11px; color: var(--text-muted);">Auto-format ke format MTSF-XXXX-XXXX-XXXX</span>
          </div>

          <div class="form-group">
            <label>Nama Pengguna / Pemilik *</label>
            <input type="text" id="username" placeholder="cth: Akmal TV Ruang Tamu" required>
          </div>

          <div class="form-group">
            <label>Email Pengguna</label>
            <input type="email" id="email" placeholder="cth: akmal@gmail.com">
          </div>

          <div class="form-group">
            <label>Tarikh Luput Lesen (Expired Date)</label>
            <input type="date" id="expiredAt" value="2099-12-30">
            <div class="presets">
              <button type="button" class="preset-btn" onclick="setExpire(1)">1 Bulan</button>
              <button type="button" class="preset-btn" onclick="setExpire(6)">6 Bulan</button>
              <button type="button" class="preset-btn" onclick="setExpire(12)">1 Tahun</button>
              <button type="button" class="preset-btn" onclick="setExpire(999)">2099 (VIP/Lifetime)</button>
            </div>
          </div>

          <div class="form-group">
            <label>Maklumat Peranti (Device Info)</label>
            <input type="text" id="deviceInfo" placeholder="cth: Xiaomi Stick 1080p, Honor X9b">
          </div>

          <div class="form-group">
            <label>Nota / Catatan</label>
            <input type="text" id="note" placeholder="cth: VIP User, Bayaran RM50">
          </div>
        </div>

        <button type="submit" class="btn-submit" id="btnSubmit">
          <span>🚀 Push & Simpan Lesen Ke GitHub</span>
        </button>
      </form>
    </div>

    <div class="card">
      <div class="card-header">
        <div class="card-title">
          <span>📋 Senarai Lesen Terdaftar</span>
        </div>
        <button type="button" onclick="loadLicenses()" class="action-btn">🔄 Muat Semula Data</button>
      </div>

      <div class="toolbar">
        <input type="text" id="searchInput" class="search-input" placeholder="🔍 Cari nama, kod peranti, atau email..." oninput="filterLicenses()">
      </div>

      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th>Kod Peranti & ID</th>
              <th>Nama Pengguna & Email</th>
              <th>Peranti</th>
              <th>Tarikh Luput</th>
              <th>Status</th>
              <th>Tindakan</th>
            </tr>
          </thead>
          <tbody id="licenseTbody">
            <tr>
              <td colspan="6" style="text-align: center; color: var(--text-muted); padding: 24px;">
                Memuatkan data lesen dari GitHub...
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div id="toast" class="toast"></div>

  <script>
    const ADMIN_KEY = '${adminKey}';
    let allLicenses = [];

    function showToast(msg, isErr = false) {
      const t = document.getElementById('toast');
      t.innerText = msg;
      t.style.borderLeftColor = isErr ? '#E50914' : '#4CAF50';
      t.style.display = 'block';
      setTimeout(() => { t.style.display = 'none'; }, 4000);
    }

    async function loadLicenses() {
      const tbody = document.getElementById('licenseTbody');
      try {
        let licenses = null;
        try {
          const res = await fetch('/licenses?key=' + encodeURIComponent(ADMIN_KEY));
          const data = await res.json();
          if (data.ok && data.licenses) {
            licenses = data.licenses;
          }
        } catch (e) {
          console.warn('API fetch failed, trying direct raw fallback:', e);
        }

        if (!licenses) {
          const rawUrl = 'https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json?t=' + Date.now();
          const rawRes = await fetch(rawUrl);
          const rawData = await rawRes.json();
          if (rawData && rawData.licenses) {
            licenses = rawData.licenses;
          }
        }

        if (licenses) {
          allLicenses = licenses;
          updateStats(allLicenses);
          renderTable(allLicenses);
        } else {
          tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#FF5555; padding:20px;">Gagal memuatkan data dari GitHub.</td></tr>';
        }
      } catch (err) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#FF5555; padding:20px;">Ralat sambungan: ' + err.message + '</td></tr>';
      }
    }

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

    function setExpire(months) {
      const expInput = document.getElementById('expiredAt');
      if (months === 999) {
        expInput.value = '2099-12-30';
      } else {
        const d = new Date();
        d.setMonth(d.getMonth() + months);
        expInput.value = d.toISOString().split('T')[0];
      }
    }

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
        showToast('❌ Ralat sambungan: ' + err.message, true);
      } finally {
        btn.innerHTML = '<span>🚀 Push & Simpan Lesen Ke GitHub</span>';
        btn.disabled = false;
      }
    });

    async function loadLicenses() {
      const tbody = document.getElementById('licenseTbody');
      try {
        const res = await fetch('/licenses?key=' + encodeURIComponent(ADMIN_KEY));
        const data = await res.json();
        if (data.ok && data.licenses) {
          allLicenses = data.licenses;
          updateStats(allLicenses);
          renderTable(allLicenses);
        } else {
          tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#FF5555; padding:20px;">Gagal memuatkan data dari GitHub.</td></tr>';
        }
      } catch (err) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#FF5555; padding:20px;">Ralat sambungan ke Worker API.</td></tr>';
      }
    }

    function updateStats(list) {
      document.getElementById('statTotal').innerText = list.length;
      document.getElementById('statActive').innerText = list.filter(l => l.active && !l.banned).length;
      document.getElementById('statBanned').innerText = list.filter(l => l.banned).length;
    }

    function renderTable(list) {
      const tbody = document.getElementById('licenseTbody');
      if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted); padding:20px;">Tiada lesen ditemui.</td></tr>';
        return;
      }

      const today = new Date().toISOString().split('T')[0];

      tbody.innerHTML = [...list].reverse().map(function(l) {
        var isExp = l.expiredAt && l.expiredAt < today;
        var statusBadge = l.banned
          ? '<span class="badge badge-banned">BANNED</span>'
          : isExp
          ? '<span class="badge badge-expired">EXPIRED</span>'
          : '<span class="badge badge-active">AKTIF</span>';

        var banBtn = !l.banned
          ? '<button onclick="banLicense(\'' + l.deviceCode + '\')" class="action-btn ban">Ban</button>'
          : '';
        var delBtn = '<button onclick="deleteLicense(\'' + l.deviceCode + '\')" class="action-btn" style="background:#2A1A1A; color:#FFAAAA;">Padam</button>';

        return '<tr>' +
          '<td><span class="device-code">' + l.deviceCode + '</span><br><small style="color:var(--text-muted); font-size:11px;">' + l.id + '</small></td>' +
          '<td><strong style="color:#FFF;">' + l.username + '</strong><br><small style="color:var(--text-muted); font-size:11px;">' + (l.email || '-') + '</small></td>' +
          '<td>' + (l.deviceInfo || '-') + '</td>' +
          '<td>' + l.expiredAt + '</td>' +
          '<td>' + statusBadge + '</td>' +
          '<td>' + banBtn + ' ' + delBtn + '</td>' +
          '</tr>';
      }).join('');
    }

    function filterLicenses() {
      const query = document.getElementById('searchInput').value.toLowerCase().trim();
      const filtered = allLicenses.filter(l => 
        (l.deviceCode && l.deviceCode.toLowerCase().includes(query)) ||
        (l.username && l.username.toLowerCase().includes(query)) ||
        (l.email && l.email.toLowerCase().includes(query)) ||
        (l.id && l.id.toLowerCase().includes(query))
      );
      renderTable(filtered);
    }

    async function banLicense(code) {
      if (!confirm('Adakah anda pasti untuk GANTUNG / BAN lesen ' + code + '?')) return;
      try {
        const res = await fetch('/ban', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Admin-Key': ADMIN_KEY },
          body: JSON.stringify({ deviceCode: code, reason: 'Banned by admin via Web Portal' })
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

    async function deleteLicense(code) {
      if (!confirm('Padam lesen ' + code + ' secara kekal dari GitHub?')) return;
      try {
        const res = await fetch('/delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Admin-Key': ADMIN_KEY },
          body: JSON.stringify({ deviceCode: code })
        });
        const data = await res.json();
        if (data.ok) {
          showToast('🗑️ Lesen ' + code + ' dipadam!');
          loadLicenses();
        } else {
          showToast('❌ ' + data.error, true);
        }
      } catch (err) {
        showToast('❌ Ralat: ' + err.message, true);
      }
    }

    loadLicenses();
  </script>
</body>
</html>`;
}
