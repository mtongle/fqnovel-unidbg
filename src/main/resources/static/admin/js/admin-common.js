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
  var _adminThemeCheck = localStorage.getItem('theme') || localStorage.getItem('admin-theme');
  if (_adminThemeCheck === 'light') document.body.classList.add('light');
  window.toggleAdminTheme = window.toggleTheme;

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

  window.showSkeleton = function (container, count, type) {
    var el = typeof container === 'string' ? document.querySelector(container) : container;
    if (!el) return;
    var n = count || 3;
    var variant = type === 'line' ? 'line' : type === 'circle' ? 'circle' : 'block';
    var html = '';
    for (var i = 0; i < n; i++) html += '<div class="skeleton skeleton-' + variant + '"></div>';
    el.innerHTML = html;
  };

  window.removeSkeleton = function (container) {
    var el = typeof container === 'string' ? document.querySelector(container) : container;
    if (!el) return;
    var nodes = el.querySelectorAll('.skeleton');
    for (var i = nodes.length - 1; i >= 0; i--) {
      if (nodes[i].parentNode === el) el.removeChild(nodes[i]);
    }
  };

  window.renderEmpty = function (container, text, hint, icon) {
    var el = typeof container === 'string' ? document.querySelector(container) : container;
    var html =
      '<div class="empty-state">' +
      '<div class="empty-state-icon">' + escapeHtml(icon || '\u2726') + '</div>' +
      '<div class="empty-state-text">' + escapeHtml(text || '暂无数据') + '</div>' +
      (hint ? '<div class="empty-state-hint">' + escapeHtml(hint) + '</div>' : '') +
      '</div>';
    if (el) el.innerHTML = html;
    return html;
  };

  window.registerShortcut = function () {
    var eventName = 'keydown', key, handler, targetSelector;
    if (arguments.length >= 3) {
      eventName = arguments[0];
      key = arguments[1];
      handler = arguments[2];
      targetSelector = arguments[3];
    } else {
      key = arguments[0];
      handler = arguments[1];
    }
    var parts = String(key).toLowerCase().split('+');
    var needMod = parts.indexOf('ctrl') !== -1 || parts.indexOf('meta') !== -1;
    var k = parts[parts.length - 1];
    var scope = targetSelector ? document.querySelector(targetSelector) : document;
    if (!scope || typeof handler !== 'function') return;
    scope.addEventListener(eventName, function (e) {
      var hasMod = e.ctrlKey || e.metaKey;
      if (hasMod === needMod && String(e.key).toLowerCase() === k) {
        e.preventDefault();
        handler(e);
      }
    });
  };

  window.toast = {
    success: function (m) { showToast(m, 'success'); },
    error: function (m) { showToast(m, 'error'); },
    info: function (m) { showToast(m, 'info'); }
  };

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

})();
