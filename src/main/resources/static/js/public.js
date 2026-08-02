/**
 * fqnovel-unidbg — 公开核心模块
 * 墨与笺 · Ink & Parchment
 * 共享给公开页面的通用工具函数（无 auth 依赖）
 */
(function () {
  'use strict';

  /* ===========================
     主题切换
     =========================== */
  var _theme = localStorage.getItem('theme');
  if (_theme === 'light') document.body.classList.add('light');

  window.toggleTheme = function () {
    document.body.classList.toggle('light');
    localStorage.setItem('theme', document.body.classList.contains('light') ? 'light' : 'dark');
  };

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
    var icons = { success: '\u2713', error: '\u2715', info: '\u2139' };
    var container = ensureToastContainer();
    var toast = document.createElement('div');
    toast.className = 'toast ' + type;
    toast.innerHTML =
      '<span class="toast-icon">' + (icons[type] || '\u2139') + '</span>' +
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

  window.toast = {
    success: function (m) { showToast(m, 'success'); },
    error: function (m) { showToast(m, 'error'); },
    info: function (m) { showToast(m, 'info'); }
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
      function cleanup() { document.removeEventListener('keydown', onKey); if (overlay.parentNode) overlay.parentNode.removeChild(overlay); }
      function onKey(e) { if (e.key === 'Escape') { cleanup(); resolve(false); } }
      document.addEventListener('keydown', onKey);
      qs('#confirm-cancel', overlay).addEventListener('click', function () { cleanup(); resolve(false); });
      qs('#confirm-ok', overlay).addEventListener('click', function () { cleanup(); resolve(true); });
      overlay.addEventListener('click', function (e) { if (e.target === overlay) { cleanup(); resolve(false); } });
    });
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

  // A 桶头像降级修复:原实现仅识别 SSR 的 .avatar/.comment-avatar 容器,
  // 公开页(如 comments.html)头像无该容器类 → 图片加载失败时无任何回退。
  // 通用回退:找不到 .avatar-letter 时,以 alt 首字符生成占位字母块替换 img。
  function showFallbackAvatar(img) {
    var w = img.offsetWidth || 42;
    var h = img.offsetHeight || 42;
    img.style.display = 'none';
    var container = img.closest('.avatar, .comment-avatar, .reply-avatar') || img.parentElement;
    if (container && container !== img) {
      var letter = container.querySelector('.avatar-letter, .reply-avatar > span');
      if (letter) { letter.style.display = 'flex'; return; }
    }
    var name = (img.getAttribute('alt') || '').trim();
    var div = document.createElement('div');
    div.className = 'avatar-fallback';
    div.textContent = name ? name.charAt(0) : '?';
    div.style.width = w + 'px';
    div.style.height = h + 'px';
    if (img.parentNode) img.parentNode.replaceChild(div, img);
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

  // ESC 关闭 Toast(仅关最上层;confirm-overlay 打开时让与其关联的自身 Esc 处理)
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if (document.querySelector('.confirm-overlay')) return;
      var c = document.querySelector('.toast-container');
      var toast = c && c.lastElementChild;
      if (toast) {
        toast.style.animation = 'toastSlideOut 0.3s ease forwards';
        setTimeout(function () { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 350);
      }
    }
  });

  // 全局 HEIC 错误捕获
  document.addEventListener('error', function (e) {
    if (e.target && e.target.tagName === 'IMG') { fixHeicImg(e.target); }
  }, true);

})();
