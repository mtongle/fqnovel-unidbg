/**
 * fqnovel-unidbg Admin — 共享核心模块
 * 墨与笺 · Ink & Parchment
 * 所有管理页面共享的通用工具与 API 封装
 */
(function () {
  'use strict';

  /* ===========================
     常量
     =========================== */
  const AUTH_KEY = 'admin_auth';
  const AUTH_TIME_KEY = 'admin_auth_time';
  const AUTH_TTL_MS = 24 * 60 * 60 * 1000;
  const API_BASE = '/api/admin';

  /* ===========================
     认证检查
     =========================== */
  (function checkAuth() {
    if (sessionStorage.getItem(AUTH_KEY) !== '1') {
      const page = location.pathname.split('/').pop();
      if (page !== 'login.html') {
        location.href = '/admin/login.html';
        return;
      }
      return;
    }
    var authTime = parseInt(sessionStorage.getItem(AUTH_TIME_KEY), 10);
    if (authTime && Date.now() - authTime > AUTH_TTL_MS) {
      sessionStorage.removeItem(AUTH_KEY);
      sessionStorage.removeItem(AUTH_TIME_KEY);
      if (location.pathname.split('/').pop() !== 'login.html') {
        location.href = '/admin/login.html';
      }
    }
  })();

  /* ===========================
     主题切换
     =========================== */
  var _adminTheme = localStorage.getItem('admin-theme');
  if (_adminTheme === 'light') document.body.classList.add('light');
  window.toggleAdminTheme = function () {
    document.body.classList.toggle('light');
    localStorage.setItem('admin-theme', document.body.classList.contains('light') ? 'light' : 'dark');
  };
  // 高亮当前页面在导航栏中
  document.addEventListener('DOMContentLoaded', function () {
    var cur = location.pathname.split('/').pop().replace(/\.html$/, '') || 'dashboard';
    document.querySelectorAll('.nav-item').forEach(function (el) {
      var section = el.getAttribute('data-section') || '';
      if (section === cur) el.classList.add('active');
    });
    // 移动端侧栏
    var toggle = document.getElementById('mobile-menu-toggle');
    var sidebar = document.querySelector('.sidebar');
    if (toggle && sidebar) {
      toggle.addEventListener('click', function () { sidebar.classList.toggle('open'); });
      document.querySelectorAll('.nav-item').forEach(function (item) {
        item.addEventListener('click', function () {
          if (window.innerWidth <= 768) sidebar.classList.remove('open');
        });
      });
      document.addEventListener('click', function (e) {
        if (window.innerWidth <= 768 && !sidebar.contains(e.target) && e.target !== toggle) {
          sidebar.classList.remove('open');
        }
      });
    }
  });

  /* ===========================
     DOM 快捷操作
     =========================== */
  window.$ = function (id) { return document.getElementById(id); };
  window.qs = function (sel, ctx) { return (ctx || document).querySelector(sel); };
  window.qsa = function (sel, ctx) { return (ctx || document).querySelectorAll(sel); };

  /* ===========================
     Toast 通知系统
     =========================== */
  var _toastContainer = null;
  function ensureToastContainer() {
    if (!_toastContainer) {
      _toastContainer = document.querySelector('.toast-container');
      if (!_toastContainer) {
        _toastContainer = document.createElement('div');
        _toastContainer.className = 'toast-container';
        document.body.appendChild(_toastContainer);
      }
    }
    return _toastContainer;
  }

  window.showToast = function (message, type) {
    type = type || 'info';
    var icons = { success: '✓', error: '✕', info: 'ℹ' };
    var container = ensureToastContainer();
    var toast = document.createElement('div');
    toast.className = 'toast ' + type;
    toast.innerHTML =
      '<span class="toast-icon">' + (icons[type] || 'ℹ') + '</span>' +
      '<span class="toast-message">' + escapeHtml(message) + '</span>' +
      '<button class="toast-close">&times;</button>';
    container.appendChild(toast);
    var closeBtn = toast.querySelector('.toast-close');
    if (closeBtn) closeBtn.addEventListener('click', function () { dismiss(toast); });
    var dismiss = function (el) {
      el.style.animation = 'toastSlideOut 0.3s ease forwards';
      setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 350);
    };
    setTimeout(function () { dismiss(toast); }, 4000);
  };

  /* ===========================
     加载遮罩
     =========================== */
  var _loadingCount = 0;
  var _loadingOverlay = null;

  function ensureLoadingOverlay() {
    if (!_loadingOverlay) {
      _loadingOverlay = document.querySelector('.loading-overlay');
      if (!_loadingOverlay) {
        _loadingOverlay = document.createElement('div');
        _loadingOverlay.className = 'loading-overlay hidden';
        _loadingOverlay.innerHTML = '<div class="loading-spinner"></div>';
        document.body.appendChild(_loadingOverlay);
      }
    }
    return _loadingOverlay;
  }

  window.showLoading = function () {
    _loadingCount++;
    ensureLoadingOverlay().classList.remove('hidden');
  };
  window.hideLoading = function () {
    _loadingCount = Math.max(0, _loadingCount - 1);
    if (_loadingCount === 0) ensureLoadingOverlay().classList.add('hidden');
  };

  /* ===========================
     确认弹窗
     =========================== */
  window.confirmDialog = function (title, message) {
    return new Promise(function (resolve) {
      var overlay = document.createElement('div');
      overlay.className = 'confirm-overlay';
      overlay.innerHTML =
        '<div class="confirm-dialog">' +
        '<h3>' + escapeHtml(title) + '</h3>' +
        '<p>' + escapeHtml(message) + '</p>' +
        '<div class="confirm-actions">' +
        '<button class="btn" id="confirm-cancel">取消</button>' +
        '<button class="btn btn-danger" id="confirm-ok">确认</button>' +
        '</div></div>';
      document.body.appendChild(overlay);
      function cleanup() { if (overlay.parentNode) overlay.parentNode.removeChild(overlay); }
      qs('#confirm-cancel', overlay).addEventListener('click', function () { cleanup(); resolve(false); });
      qs('#confirm-ok', overlay).addEventListener('click', function () { cleanup(); resolve(true); });
      overlay.addEventListener('click', function (e) { if (e.target === overlay) { cleanup(); resolve(false); } });
    });
  };

  /* ===========================
     API 工具
     =========================== */
  window.getAdminToken = function () {
    return sessionStorage.getItem('admin_token') || '';
  };

  function handleUnauthorized(resp) {
    if (resp.status === 401) {
      sessionStorage.removeItem('admin_token');
      sessionStorage.removeItem(AUTH_KEY);
      sessionStorage.removeItem(AUTH_TIME_KEY);
      location.href = '/admin/login.html';
      return true;
    }
    return false;
  }

  window.apiGet = async function (url) {
    var token = getAdminToken();
    var resp = await fetch(API_BASE + url, { headers: token ? { 'X-Admin-Token': token } : {} });
    if (handleUnauthorized(resp)) return null;
    if (!resp.ok) { var t = await resp.text().catch(function () { return ''; }); throw new Error('HTTP ' + resp.status + ': ' + t.slice(0, 200)); }
    var ct = resp.headers.get('content-type') || '';
    return ct.includes('text/plain') ? { _raw: true, text: await resp.text() } : await resp.json();
  };

  window.apiPut = async function (url, body, contentType) {
    var token = getAdminToken();
    var opts = { method: 'PUT', body: body, headers: {} };
    if (token) opts.headers['X-Admin-Token'] = token;
    if (contentType) opts.headers['Content-Type'] = contentType;
    var resp = await fetch(API_BASE + url, opts);
    if (handleUnauthorized(resp)) return null;
    if (!resp.ok) { var t = await resp.text().catch(function () { return ''; }); throw new Error('HTTP ' + resp.status + ': ' + t.slice(0, 200)); }
    return await resp.json();
  };

  window.apiPost = async function (url, params) {
    var token = getAdminToken();
    var fullUrl = API_BASE + url;
    if (params) {
      var q = Object.keys(params).map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]); }).join('&');
      fullUrl += '?' + q;
    }
    var fetchOpts = { method: 'POST' };
    if (token) fetchOpts.headers = { 'X-Admin-Token': token };
    var resp = await fetch(fullUrl, fetchOpts);
    if (handleUnauthorized(resp)) return null;
    if (!resp.ok) { var t2 = await resp.text().catch(function () { return ''; }); throw new Error('HTTP ' + resp.status + ': ' + t2.slice(0, 200)); }
    return await resp.json();
  };

  window.safeFetch = async function (fn, errorMsg) {
    showLoading();
    try { return await fn(); } catch (e) { showToast(errorMsg || e.message, 'error'); return null; }
    finally { hideLoading(); }
  };

  /* ===========================
     通用工具函数
     =========================== */
  window.escapeHtml = function (str) {
    if (!str) return '';
    var d = document.createElement('div');
    d.appendChild(document.createTextNode(str));
    return d.innerHTML;
  };

  window.normalizeImageUrl = function (url) {
    if (!url) return '';
    return url.replace('http://', 'https://');
  };

  window.formatBytes = function (bytes) {
    if (bytes == null || isNaN(bytes)) return '--';
    if (bytes === 0) return '0 B';
    var units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var i = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + units[i];
  };

  window.formatUptime = function (ms) {
    if (ms == null || isNaN(ms)) return '--';
    var totalSec = Math.floor(ms / 1000);
    var h = Math.floor(totalSec / 3600);
    var m = Math.floor((totalSec % 3600) / 60);
    var s = totalSec % 60;
    var parts = [];
    if (h > 0) parts.push(h + 'h');
    if (m > 0) parts.push(m + 'm');
    parts.push(s + 's');
    return parts.join(' ');
  };

  window.formatPercent = function (pct) {
    if (pct == null || isNaN(pct)) return '--';
    return pct + '%';
  };

  window.formatWordCount = function (n) {
    if (n == null || isNaN(n)) return '--';
    n = Number(n);
    if (n >= 10000) return (n / 10000).toFixed(1) + '万字';
    return n + '字';
  };

  window.bookStatusText = function (s) {
    if (s === 0 || s === '0') return '连载';
    if (s === 1 || s === '1') return '完结';
    return s || '--';
  };

  /* ===========================
     HEIC 图片处理
     =========================== */
  window.fixHeicImg = function (img) {
    if (img._heicFixed) return;
    img._heicFixed = true;
    var src = img.src || '';
    if (!src) return;
    loadHeic2Any().then(function () { return fetch(src); })
      .then(function (r) {
        if (!r.ok) throw new Error(r.status);
        var ct = (r.headers.get('content-type') || '').toLowerCase();
        return r.blob().then(function (blob) { return { blob: blob, ct: ct }; });
      })
      .then(function (info) {
        if (info.ct.indexOf('heic') !== -1 || info.ct.indexOf('heif') !== -1) {
          return heic2any({ blob: info.blob, toType: 'image/jpeg', quality: 1 }).then(function (result) {
            var b = Array.isArray(result) ? result[0] : result;
            img.src = URL.createObjectURL(b);
            img.style.display = '';
            img.onerror = null;
            var container = img.closest('.avatar, .comment-avatar, .reply-avatar');
            if (container) { var letter = container.querySelector('.avatar-letter, .reply-avatar > span'); if (letter) letter.style.display = 'none'; }
          });
        }
        img.src = URL.createObjectURL(info.blob);
        img.style.display = '';
        img.onerror = null;
      })
      .catch(function (e) { console.warn('HEIC fail:', e); showFallbackAvatar(img); });
  };

  function showFallbackAvatar(img) {
    img.style.display = 'none';
    var container = img.closest('.avatar, .comment-avatar');
    if (container) { var letter = container.querySelector('.avatar-letter'); if (letter) letter.style.display = 'flex'; }
  }

  function loadHeic2Any() {
    if (typeof heic2any === 'function') return Promise.resolve();
    return new Promise(function (resolve) {
      var s = document.createElement('script');
      s.src = '/js/heic2any.min.js';
      s.onload = resolve;
      s.onerror = resolve;
      document.head.appendChild(s);
    });
  }

  /* ===========================
     锁定 / 登出
     =========================== */
  window.lockPanel = function () {
    var token = getAdminToken();
    if (token) { fetch(API_BASE + '/logout', { method: 'POST', headers: { 'X-Admin-Token': token } }).catch(function () { }); }
    sessionStorage.removeItem('admin_token');
    sessionStorage.removeItem(AUTH_KEY);
    sessionStorage.removeItem(AUTH_TIME_KEY);
    location.href = '/admin/login.html';
  };

  /* ===========================
    通用 API 请求（非 admin 路径）
     =========================== */
  window.apiGetDirect = function (url) {
    return fetch(url).then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });
  };
  window.apiPostDirect = function (url, body) {
    return fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });
  };

  /* ===========================
     页面刷新通用（监控仪表盘用）
     =========================== */
  window.formatCommentTime = function (ts) {
    if (!ts) return '';
    var d = new Date(typeof ts === 'number' && ts < 1e12 ? ts * 1000 : ts);
    if (isNaN(d.getTime())) return String(ts);
    var now = new Date();
    var diff = (now - d) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + '分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + '小时前';
    if (diff < 172800) return '昨天';
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  };

  window.getNested = function (obj, path) {
    var parts = path.split('.');
    for (var i = 0; i < parts.length; i++) {
      if (obj == null || typeof obj !== 'object') return null;
      obj = obj[parts[i]];
    }
    return obj;
  };

  // 通用渲染函数：细线分隔的详情行
  window.renderDetailRows = function (rows) {
    return rows.map(function (r) {
      return '<div class="metric-row"><span>' + escapeHtml(r[0]) + '</span><span>' + r[1] + '</span></div>';
    }).join('');
  };

  // ESC 关闭 Toast
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      var c = document.querySelector('.toast-container');
      if (c) c.innerHTML = '';
    }
  });

  // 全局 HEIC 错误捕获
  document.addEventListener('error', function (e) {
    if (e.target && e.target.tagName === 'IMG') { fixHeicImg(e.target); }
  }, true);

})();
