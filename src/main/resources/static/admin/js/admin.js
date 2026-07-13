(function() {
  'use strict';

  // Theme toggle — delegated to admin-common.js (toggleAdminTheme = toggleTheme)
  var _adminThemeCheck = localStorage.getItem('admin-theme');
  if (_adminThemeCheck === 'light') document.body.classList.add('light');

  const state = {
    currentSection: 'dashboard',
    monitorInterval: null,
  };

  /* ===========================
     DOM REFS ($, qs, qsa from public.js)
     =========================== */
  const dom = {};

  function cacheDom() {
    dom.sidebar = qs('.sidebar');
    dom.navItems = qsa('.nav-item');
    dom.sections = qsa('.section');
    dom.toastContainer = $('toast-container');
    dom.loadingOverlay = $('loading-overlay');

    dom.btnRestart = $('btn-restart');
    dom.btnLock = $('btn-lock');
    dom.btnRefreshDashboard = $('btn-refresh-dashboard');
    dom.btnLoadConfig = $('btn-load-config');
    dom.btnSaveConfig = $('btn-save-config');
    dom.btnHotReload = $('btn-hot-reload');
    dom.btnRefreshPool = $('btn-refresh-pool');
    dom.btnAddDevice = $('btn-add-device');
    dom.btnRebuildPool = $('btn-rebuild-pool');
    dom.btnRefreshSystem = $('btn-refresh-system');
    dom.mobileToggle = $('mobile-menu-toggle');

    dom.configEditor = $('config-editor');
    dom.configMessage = $('config-message');

    dom.poolStats = $('pool-stats');
    dom.deviceTableBody = $('device-table-body');

    dom.memBar = qs('#mem-bar .progress-fill');
    dom.memUsed = $('mem-used');
    dom.memMax = $('mem-max');
    dom.memPct = $('mem-pct');
    dom.threadActive = $('thread-active');
    dom.threadTotal = $('thread-total');
    dom.poolSize = $('pool-size');
    dom.poolEnabled = $('pool-enabled');
    dom.redisDot = qs('#redis-status .dot');
    dom.redisText = $('redis-text');
    dom.cacheHitrate = $('cache-hitrate');
    dom.cacheSize = $('cache-size');
    dom.cacheHits = $('cache-hits');
    dom.diskBar = qs('#disk-bar .progress-fill');
    dom.diskFree = $('disk-free');
    dom.diskTotal = $('disk-total');
    dom.diskPct = $('disk-pct');

    dom.jvmDetail = $('jvm-detail');
    dom.httpDetail = $('http-detail');
    dom.redisDetail = $('redis-detail');
    dom.threadDetail = $('thread-detail');
    dom.diskDetail = $('disk-detail');
    dom.cacheDetail = $('cache-detail');

    // Book Search
    dom.bsQuery = $('bs-query');
    dom.bsTabType = $('bs-tab-type');
    dom.btnBsSearch = $('btn-bs-search');
    dom.bsResultBody = $('bs-result-body');
    dom.bsResultCount = $('bs-result-count');
    dom.bsPagination = $('bs-pagination');

    // Chapter Viewer
    dom.cvBookId = $('cv-book-id');
    dom.cvChapterId = $('cv-chapter-id');
    dom.btnCvLoad = $('btn-cv-load');
    dom.btnCvPrev = $('btn-cv-prev');
    dom.btnCvNext = $('btn-cv-next');
    dom.btnCvComments = $('btn-cv-comments');
    dom.cvTitle = $('cv-title');
    dom.cvContent = $('cv-content');

    // Comment Viewer
    dom.cmBookId = $('cm-book-id');
    dom.cmChapterId = $('cm-chapter-id');
    dom.cmParaIdx = $('cm-para-idx');
    dom.btnCmIdea = $('btn-cm-idea');
    dom.btnCmList = $('btn-cm-list');
    dom.cmIdeaResult = $('cm-idea-result');
    dom.cmListResult = $('cm-list-result');
    dom.cmListCount = $('cm-list-count');
  }

  /* Toast, Loading, Confirm — provided by public.js (window.showToast, showLoading, hideLoading, confirmDialog) */

  /* API helpers — provided by admin-common.js (window.getAdminToken, apiGet, apiPut, apiPost, safeFetch) */
  /* Utilities — provided by public.js (escapeHtml, normalizeImageUrl, fixHeicImg, formatBytes, formatUptime, formatPercent) */

  /* ===========================
     NAVIGATION
     =========================== */
  function switchSection(sectionId) {
    state.currentSection = sectionId;

    dom.navItems.forEach(function(item) {
      item.classList.toggle('active', item.dataset.section === sectionId);
    });

    dom.sections.forEach(function(sec) {
      sec.classList.toggle('active', sec.id === 'section-' + sectionId);
    });

    if (sectionId === 'dashboard') refreshDashboard();
    if (sectionId === 'config') loadConfig();
    if (sectionId === 'device-pool') refreshDevicePool();
    if (sectionId === 'system') refreshSystemMonitor();
    if (sectionId === 'book-search') { /* loaded on interaction */ }
    if (sectionId === 'chapter-viewer') { /* loaded on interaction */ }
    if (sectionId === 'comment-viewer') { /* loaded on interaction */ }
  }

  /* ===========================
     DASHBOARD
     =========================== */
  async function refreshDashboard() {
    const data = await safeFetch(function() { return apiGet('/monitor'); }, '加载监控数据失败');
    if (!data) return;

    // Memory
    var jvm = data.jvm || {};
    var usedMB = jvm.usedMemoryMB || 0;
    var maxMB = jvm.maxMemoryMB || 0;
    var memPct = jvm.memoryUsagePercent || 0;

    if (dom.memBar) {
      dom.memBar.style.width = Math.min(memPct, 100) + '%';
      dom.memBar.className = 'progress-fill' + (memPct > 85 ? ' danger' : memPct > 65 ? ' warning' : '');
    }
    if (dom.memUsed) dom.memUsed.textContent = formatBytes((jvm.usedMemory || 0));
    if (dom.memMax) dom.memMax.textContent = formatBytes((jvm.maxMemory || 0));
    if (dom.memPct) dom.memPct.textContent = formatPercent(memPct);

    // Threads
    var threads = data.threads || {};
    if (dom.threadActive) dom.threadActive.textContent = threads.activeCount != null ? threads.activeCount : '--';
    if (dom.threadTotal) dom.threadTotal.textContent = threads.totalCount != null ? threads.totalCount : '--';

    // Device pool
    var pool = data.devicePool || {};
    if (dom.poolSize) dom.poolSize.textContent = (pool.currentSize != null ? pool.currentSize : '--') + ' / ' + (pool.targetSize != null ? pool.targetSize : '--');
    if (dom.poolEnabled) dom.poolEnabled.textContent = pool.enabled ? '已启用' : '未启用';

    // Redis
    var redis = data.redis || {};
    if (dom.redisDot) {
      dom.redisDot.className = 'dot ' + (redis.connected ? 'green' : 'red');
    }
    if (dom.redisText) dom.redisText.textContent = redis.connected ? '已连接' : (redis.error ? '连接失败' : '未配置');

    // Signature cache
    var cache = data.signatureCache || {};
    if (dom.cacheHitrate) dom.cacheHitrate.textContent = cache.hitRate != null ? (cache.hitRate * 100).toFixed(1) + '%' : '--';
    if (dom.cacheSize) dom.cacheSize.textContent = cache.cacheSize != null ? cache.cacheSize : (cache.size != null ? cache.size : '--');
    if (dom.cacheHits) dom.cacheHits.textContent = (cache.hits != null ? cache.hits : '--') + ' / ' + (cache.misses != null ? cache.misses : '--');

    // Disk
    var disk = data.disk || {};
    var diskPct = disk.usedPercent || 0;
    if (dom.diskBar) {
      dom.diskBar.style.width = Math.min(diskPct, 100) + '%';
      dom.diskBar.className = 'progress-fill' + (diskPct > 85 ? ' danger' : diskPct > 65 ? ' warning' : '');
    }
    if (dom.diskFree) dom.diskFree.textContent = formatBytes((disk.freeSpaceMB || 0) * 1024 * 1024);
    if (dom.diskTotal) dom.diskTotal.textContent = formatBytes((disk.totalSpaceMB || 0) * 1024 * 1024);
    if (dom.diskPct) dom.diskPct.textContent = formatPercent(diskPct);
  }

  /* ===========================
     CONFIG EDITOR
     =========================== */
  var _configLoaded = false;

  async function loadConfig(force) {
    if (_configLoaded && !force) return;
    var result = await safeFetch(function() { return apiGet('/config'); }, '加载配置失败');
    if (!result) return;
    if (dom.configEditor) dom.configEditor.value = result.text || '';
    dom.configMessage.classList.add('hidden');
    _configLoaded = true;
  }

  async function saveConfig() {
    var content = dom.configEditor ? dom.configEditor.value : '';
    if (!content.trim()) {
      showToast('配置内容不能为空', 'error');
      return;
    }

    var btn = dom.btnSaveConfig;
    if (btn) { btn.disabled = true; btn.textContent = '保存中...'; }

    var result = await safeFetch(function() {
      return apiPut('/config', content, 'text/plain');
    }, '保存配置失败');

    if (btn) { btn.disabled = false; btn.textContent = '保存配置'; }

    if (!result) return;

    if (result.success) {
      showToast(result.message || '配置已保存', 'success');
      dom.configMessage.textContent = result.message;
      dom.configMessage.className = 'message success';
      dom.configMessage.classList.remove('hidden');
    } else {
      showToast(result.message || '保存失败', 'error');
      dom.configMessage.textContent = result.message;
      dom.configMessage.className = 'message error';
      dom.configMessage.classList.remove('hidden');
    }
  }

  async function hotReload() {
    var result = await safeFetch(function() { return apiPost('/refresh'); }, '热重载失败');
    if (!result) return;

    if (result.success) {
      var keys = result.changedKeys;
      var keyStr = keys && keys.length ? ' 变更键: ' + keys.join(', ') : '';
      showToast(result.message + keyStr, 'success');
    } else {
      showToast(result.message || '热重载失败', 'error');
    }
  }

  /* ===========================
     DEVICE POOL
     =========================== */
  async function refreshDevicePool() {
    var data = await safeFetch(function() { return apiGet('/device-pool'); }, '加载设备池失败');
    if (!data) return;

    renderPoolStats(data);
    renderDeviceTable(data.devices || []);
  }

  function renderPoolStats(data) {
    if (!dom.poolStats) return;
    dom.poolStats.innerHTML =
      '<div class="pool-stat-item"><span class="stat-value">' + (data.enabled ? '<svg class="ic"><use href="/css/icons.svg#icon-check"/></svg>' : '✕') + '</span><span class="stat-label">状态</span></div>' +
      '<div class="pool-stat-item"><span class="stat-value">' + (data.currentSize || 0) + '</span><span class="stat-label">当前数量</span></div>' +
      '<div class="pool-stat-item"><span class="stat-value">' + (data.targetSize || 0) + '</span><span class="stat-label">目标数量</span></div>' +
      '<div class="pool-stat-item"><span class="stat-value">' + (data.nextIndex || 0) + '</span><span class="stat-label">轮询索引</span></div>';
  }

  function renderDeviceTable(devices) {
    if (!dom.deviceTableBody) return;
    if (!devices || devices.length === 0) {
      dom.deviceTableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text-muted);padding:30px;">暂无设备数据</td></tr>';
      return;
    }

    dom.deviceTableBody.innerHTML = devices.map(function(d) {
      return '<tr>' +
        '<td style="font-size:0.75rem;">' + escapeHtml(d.deviceId || '--') + '</td>' +
        '<td>' + escapeHtml(d.deviceBrand || '--') + '</td>' +
        '<td>' + escapeHtml(d.deviceType || '--') + '</td>' +
        '<td style="font-size:0.75rem;">' + escapeHtml((d.installId || '--').slice(0, 16)) + '…</td>' +
        '<td>' + escapeHtml(d.versionName || d.versionCode || '--') + '</td>' +
        '<td><button class="btn btn-sm btn-danger remove-device" data-device-id="' + escapeHtml(d.deviceId || '') + '">移除</button></td>' +
        '</tr>';
    }).join('');

    // Attach remove handlers
    dom.deviceTableBody.querySelectorAll('.remove-device').forEach(function(btn) {
      btn.addEventListener('click', function() {
        var deviceId = this.dataset.deviceId;
        removeDevice(deviceId);
      });
    });
  }

  async function addDevice() {
    var result = await safeFetch(function() { return apiPost('/device-pool/add'); }, '添加设备失败');
    if (!result) return;
    if (result.success) {
      showToast(result.message || '设备已添加', 'success');
      refreshDevicePool();
    } else {
      showToast(result.message || '添加失败', 'error');
    }
  }

  async function removeDevice(deviceId) {
    if (!deviceId) return;
    var confirmed = await confirmDialog('移除设备', '确定要移除设备 ' + deviceId + ' 吗？');
    if (!confirmed) return;

    var result = await safeFetch(function() { return apiPost('/device-pool/remove', { deviceId: deviceId }); }, '移除设备失败');
    if (!result) return;
    if (result.success) {
      showToast(result.message || '设备已移除', 'success');
      refreshDevicePool();
    } else {
      showToast(result.message || '移除失败', 'error');
    }
  }

  async function rebuildPool() {
    var confirmed = await confirmDialog('重建设备池', '确定要重新构建整个设备池吗？');
    if (!confirmed) return;

    var result = await safeFetch(function() { return apiPost('/device-pool/rebuild'); }, '重建设备池失败');
    if (!result) return;
    if (result.success) {
      showToast(result.message || '设备池已重建', 'success');
      refreshDevicePool();
    } else {
      showToast(result.message || '重建失败', 'error');
    }
  }

  /* ===========================
     SYSTEM MONITOR
     =========================== */
  async function refreshSystemMonitor() {
    var data = await safeFetch(function() { return apiGet('/monitor'); }, '加载系统监控失败');
    if (!data) return;

    // JVM
    var jvm = data.jvm || {};
    if (dom.jvmDetail) {
      dom.jvmDetail.innerHTML = renderDetailRows([
        ['已用内存', formatBytes(jvm.usedMemory || 0)],
        ['最大内存', formatBytes(jvm.maxMemory || 0)],
        ['使用率', formatPercent(jvm.memoryUsagePercent)],
        ['CPU 核心', jvm.availableProcessors != null ? jvm.availableProcessors : '--'],
        ['系统负载', jvm.systemLoadAverage != null ? jvm.systemLoadAverage : 'N/A'],
        ['运行时间', formatUptime(jvm.uptime)],
      ]);
    }

    // HTTP Client
    var http = data.httpClient || {};
    if (dom.httpDetail) {
      dom.httpDetail.innerHTML = renderDetailRows([
        ['连接超时', http.connectTimeoutMs != null ? http.connectTimeoutMs + 'ms' : '--'],
        ['读取超时', http.readTimeoutMs != null ? http.readTimeoutMs + 'ms' : '--'],
        ['最大连接数', http.maxConnections != null ? http.maxConnections : '--'],
        ['每路由连接', http.maxConnectionsPerRoute != null ? http.maxConnectionsPerRoute : '--'],
      ]);
    }

    // Redis
    var redis = data.redis || {};
    if (dom.redisDetail) {
      if (redis.connected) {
        dom.redisDetail.innerHTML = renderDetailRows([
          ['状态', '<span class="badge badge-green">已连接</span>'],
          ['Ping', redis.ping || '--'],
        ]);
      } else {
        dom.redisDetail.innerHTML = '<div class="detail-empty">' + escapeHtml(redis.error || redis.message || 'Redis 未配置') + '</div>';
      }
    }

    // Threads
    var threads = data.threads || {};
    if (dom.threadDetail) {
      dom.threadDetail.innerHTML = renderDetailRows([
        ['活跃线程', threads.activeCount != null ? threads.activeCount : '--'],
        ['总线程数', threads.totalCount != null ? threads.totalCount : '--'],
      ]);
    }

    // Disk
    var disk = data.disk || {};
    if (dom.diskDetail) {
      dom.diskDetail.innerHTML = renderDetailRows([
        ['总空间', formatBytes((disk.totalSpaceMB || 0) * 1024 * 1024)],
        ['可用空间', formatBytes((disk.freeSpaceMB || 0) * 1024 * 1024)],
        ['使用率', formatPercent(disk.usedPercent)],
      ]);
    }

    // Cache
    var cache = data.signatureCache || {};
    if (dom.cacheDetail) {
      var hitRate = cache.hitRate != null ? (cache.hitRate * 100).toFixed(1) + '%' : '--';
      dom.cacheDetail.innerHTML = renderDetailRows([
        ['命中率', hitRate],
        ['缓存数量', cache.cacheSize != null ? cache.cacheSize : (cache.size != null ? cache.size : '--')],
        ['命中', cache.hits != null ? cache.hits : '--'],
        ['未命中', cache.misses != null ? cache.misses : '--'],
      ]);
    }
  }

  /* renderDetailRows — provided by public.js (window.renderDetailRows) */

  /* ===========================
     RESTART
     =========================== */
  async function restartApp() {
    var confirmed = await confirmDialog('重启应用', '确定要重启整个应用吗？服务将会暂时不可用。');
    if (!confirmed) return;

    var result = await safeFetch(function() { return apiPost('/restart'); }, '重启失败');
    if (!result) return;
    if (result.success) {
      showToast(result.message || '重启已触发', 'success');
    } else {
      showToast(result.message || '重启失败', 'error');
    }
  }

  /* lockPanel — provided by admin-common.js (window.lockPanel) */

  /* ===========================
     BOOK SEARCH
     =========================== */
  var _bsState = { offset: 0, query: '', tabType: 1, searchId: null };
  var _bsDetailBookId = null;

  /* formatWordCount, bookStatusText, apiGetDirect, apiPostDirect — provided by public.js */

  async function searchBooks(offset) {
    var query = dom.bsQuery.value.trim();
    if (!query) { showToast('请输入搜索关键词', 'error'); return; }
    if (offset == null) offset = 0;
    _bsState.query = query;
    _bsState.tabType = parseInt(dom.bsTabType.value, 10);
    _bsState.offset = offset;

    showLoading();
    try {
      var resp = await apiGetDirect('/api/fqsearch/books?query=' + encodeURIComponent(query)
        + '&tabType=' + _bsState.tabType + '&offset=' + offset + '&count=20');
      if (!resp || resp.code !== 0) {
        showToast((resp && resp.message) || '搜索失败', 'error');
        return;
      }
      renderSearchResults(resp.data);
    } catch (e) {
      showToast('搜索请求失败: ' + e.message, 'error');
    } finally {
      hideLoading();
    }
  }

  function renderSearchResults(data) {
    var books = (data && data.books) || [];
    var total = data && data.total;
    var body = dom.bsResultBody;
    var count = dom.bsResultCount;

    if (count) count.textContent = total != null ? '（共 ' + total + ' 条）' : '';

    if (!books.length) {
      body.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--text-muted);padding:30px;">未找到结果</td></tr>';
      renderPagination(dom.bsPagination, 0, 1);
      return;
    }

    body.innerHTML = books.map(function(b) {
      var coverSrc = normalizeImageUrl(b.coverUrl || '');
      var coverImg = coverSrc
        ? '<img src="' + escapeHtml(coverSrc) + '" onerror="fixHeicImg(this)" style="width:32px;height:44px;object-fit:cover;border-radius:3px;vertical-align:middle;margin-right:6px;">'
        : '';
      return '<tr>' +
        '<td>' + coverImg + '<span>' + escapeHtml(b.bookName || '--') + '</span></td>' +
        '<td>' + escapeHtml(b.author || '--') + '</td>' +
        '<td>' + bookStatusText(b.status) + '</td>' +
        '<td>' + formatWordCount(b.wordCount) + '</td>' +
        '<td>' + (b.rating != null ? b.rating : '--') + '</td>' +
        '<td>' + escapeHtml(b.category || '--') + '</td>' +
        '<td><button class="btn btn-sm btn-primary bs-view-detail" data-book-id="' + escapeHtml(b.bookId) + '">详情</button></td>' +
        '</tr>';
    }).join('');

    body.querySelectorAll('.bs-view-detail').forEach(function(btn) {
      btn.addEventListener('click', function() { showBookDetail(this.dataset.bookId); });
    });

    var totalPages = total != null ? Math.ceil(total / 20) : 1;
    renderPagination(dom.bsPagination, _bsState.offset, totalPages, function(pageOffset) {
      searchBooks(pageOffset);
    });
  }

  function renderPagination(container, currentOffset, totalPages, onPageClick) {
    if (!container) return;
    if (totalPages <= 1 && currentOffset === 0) { container.innerHTML = ''; return; }

    var currentPage = Math.floor(currentOffset / 20) + 1;
    var html = '';
    if (currentPage > 1) html += '<button class="page-btn" data-offset="' + (currentOffset - 20) + '">上一页</button>';

    var start = Math.max(1, currentPage - 2);
    var end = Math.min(totalPages, currentPage + 2);
    for (var i = start; i <= end; i++) {
      var off = (i - 1) * 20;
      html += '<button class="page-btn' + (i === currentPage ? ' active' : '') + '" data-offset="' + off + '">' + i + '</button>';
    }

    if (currentPage < totalPages) html += '<button class="page-btn" data-offset="' + (currentOffset + 20) + '">下一页</button>';
    html += '<span class="page-info">' + currentPage + '/' + totalPages + '</span>';

    container.innerHTML = html;
    if (onPageClick) {
      container.querySelectorAll('.page-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
          if (!this.disabled) onPageClick(parseInt(this.dataset.offset, 10));
        });
      });
    }
  }

  function makeBookDetailCard(d) {
    _bsDetailBookId = d.bookId || _bsDetailBookId;
    var bid = _bsDetailBookId;
    var fch = d.firstChapterItemId || '';

    var coverUrl = normalizeImageUrl(d.coverUrl);
    var coverHtml = coverUrl
      ? '<img class="book-detail-cover" src="' + escapeHtml(coverUrl) + '" alt="cover" onerror="fixHeicImg(this)" style="width:140px;border-radius:var(--radius-sm);box-shadow:0 2px 12px rgba(0,0,0,0.3);">'
      : '<div style="width:140px;height:200px;background:var(--bg-tertiary);border-radius:var(--radius-sm);display:flex;align-items:center;justify-content:center;color:var(--text-muted);font-size:2rem;"><svg class="ic"><use href="/css/icons.svg#icon-book"/></svg></div>';

    var vipHtml = '';
    if (d.vipBook === true || d.vipBook === '1' || d.vipBook === 'true') {
      vipHtml = '<div style="padding:6px 0;display:flex;justify-content:space-between;font-size:0.85rem;border-bottom:1px solid rgba(255,255,255,0.04);"><span style="color:var(--text-secondary);">付费</span><span style="color:var(--text-primary);font-weight:500;">是</span></div>';
    }

    var card = document.createElement('div');
    card.className = 'card';
    card.style.marginTop = '12px';
    card.id = 'bs-detail-card';
    card.innerHTML =
      '<div class="card-header"><svg class="ic"><use href="/css/icons.svg#icon-book"/></svg> 书籍详情 <span style="float:right;cursor:pointer;color:var(--text-muted);" class="bs-close-card">✕</span></div>' +
      '<div class="card-body"><div class="book-detail">' + coverHtml +
      '<div class="book-detail-info">' +
      '<h3>' + escapeHtml(d.bookName || '--') + '</h3>' +
      '<div style="color:var(--text-secondary);font-size:0.9rem;margin-bottom:8px;">' + escapeHtml(d.author || '--') + '</div>' +
      '<div style="color:var(--text-secondary);font-size:0.82rem;line-height:1.6;max-height:80px;overflow-y:auto;margin-bottom:10px;">' + escapeHtml((d.description || '').slice(0, 500)) + '</div>' +
      '<div style="display:flex;flex-wrap:wrap;gap:16px 24px;margin-bottom:10px;">' +
        '<div><span style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">状态</span><br><span style="font-size:0.85rem;font-weight:600;color:var(--text-primary);">' + bookStatusText(d.status) + '</span></div>' +
        '<div><span style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">字数</span><br><span style="font-size:0.85rem;font-weight:600;color:var(--text-primary);">' + formatWordCount(d.wordNumber) + '</span></div>' +
        '<div><span style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">章节</span><br><span style="font-size:0.85rem;font-weight:600;color:var(--text-primary);">' + (d.totalChapters != null ? d.totalChapters : '--') + '</span></div>' +
        '<div><span style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">评分</span><br><span style="font-size:0.85rem;font-weight:600;color:var(--text-primary);">' + (d.score != null ? d.score : '--') + '</span></div>' +
        '<div><span style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">分类</span><br><span style="font-size:0.85rem;font-weight:600;color:var(--text-primary);">' + escapeHtml(d.category || '--') + '</span></div>' +
        '<div><span style="font-size:0.7rem;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">阅读</span><br><span style="font-size:0.85rem;font-weight:600;color:var(--text-primary);">' + escapeHtml(d.readCount || '--') + '</span></div>' +
        vipHtml +
      '</div>' +
      '<div class="flex gap-8" style="flex-wrap:wrap;">' +
        '<button class="btn btn-primary btn-sm bs-show-dir" data-book-id="' + escapeHtml(bid) + '"><svg class="ic"><use href="/css/icons.svg#icon-clipboard"/></svg> 查看目录</button>' +
        '<button class="btn btn-sm bs-read-book" data-book-id="' + escapeHtml(bid) + '" data-first-chapter="' + escapeHtml(fch) + '"><svg class="ic"><use href="/css/icons.svg#icon-book"/></svg> 开始阅读</button>' +
        '<button class="btn btn-sm btn-outline bs-close-card">收起</button>' +
      '</div>' +
      '</div></div></div>';
    return card;
  }

  function makeDirectoryCard(data, bookId) {
    var items = (data && (data.itemDataList || data.item_data_list)) || [];
    var card = document.createElement('div');
    card.className = 'card';
    card.style.marginTop = '12px';
    card.id = 'bs-directory-card';

    var tableBody = '';
    if (!items.length) {
      tableBody = '<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--text-muted);">暂无目录数据</td></tr>';
    } else {
      tableBody = items.map(function(item, idx) {
        var ci = item.chapter_index || item.chapterIndex || idx + 1;
        var cwn = item.chapter_word_number || item.chapterWordNumber;
        var ct = item.chapter_type || item.chapterType;
        var isf = item.is_free != null ? item.is_free : item.isFree;
        var iid = item.item_id || item.itemId;
        var chapType = ct === '1' ? '番外' : '正文';
        return '<tr>' +
          '<td>' + ci + '</td>' +
          '<td>' + escapeHtml(item.title || '--') + '</td>' +
          '<td>' + formatWordCount(cwn) + '</td>' +
          '<td>' + chapType + '</td>' +
          '<td>' + (isf ? '<span class="badge badge-green">免费</span>' : '<span class="badge badge-orange">付费</span>') + '</td>' +
          '<td><button class="btn btn-sm bs-read-chapter" data-book-id="' + escapeHtml(bookId) + '" data-chapter-id="' + escapeHtml(iid) + '">阅读</button></td>' +
          '</tr>';
      }).join('');
    }

    card.innerHTML =
      '<div class="card-header"><svg class="ic"><use href="/css/icons.svg#icon-clipboard"/></svg> 章节目录 <span style="float:right;cursor:pointer;color:var(--text-muted);" class="bs-close-card">✕</span></div>' +
      '<div class="card-body">' +
      '<div class="table-wrapper"><table class="data-table"><thead><tr><th>序号</th><th>章节名</th><th>字数</th><th>类型</th><th>免费</th><th>操作</th></tr></thead><tbody>' + tableBody + '</tbody></table></div>' +
      '</div></div>';

    if (items.length) {
      card.querySelectorAll('.bs-read-chapter').forEach(function(btn) {
        btn.addEventListener('click', function() {
          loadChapterDirect(this.dataset.bookId, this.dataset.chapterId);
        });
      });
    }
    return card;
  }

  async function showBookDetail(bookId) {
    if (!bookId) return;
    showLoading();
    try {
      var resp = await apiGetDirect('/api/fqnovel/book/' + encodeURIComponent(bookId));
      if (!resp || resp.code !== 0) {
        showToast((resp && resp.message) || '获取详情失败', 'error');
        return;
      }
      var container = document.getElementById('bs-detail-container');
      if (!container) return;
      container.innerHTML = '';
      var detailCard = makeBookDetailCard(resp.data);
      container.appendChild(detailCard);
      detailCard.scrollIntoView({ behavior: 'smooth', block: 'start' });

      detailCard.querySelectorAll('.bs-close-card').forEach(function(btn) {
        btn.addEventListener('click', function() { container.innerHTML = ''; });
      });
      var dirBtn = detailCard.querySelector('.bs-show-dir');
      if (dirBtn) {
        dirBtn.addEventListener('click', function() {
          showBookDirectory(this.dataset.bookId);
        });
      }
      var readBtn = detailCard.querySelector('.bs-read-book');
      if (readBtn) {
        readBtn.addEventListener('click', function() {
          var bid = this.dataset.bookId;
          var cid = this.dataset.firstChapter;
          if (bid && cid) loadChapterDirect(bid, cid);
          else showToast('缺少章节信息', 'error');
        });
      }
    } catch (e) {
      showToast('请求失败: ' + e.message, 'error');
    } finally {
      hideLoading();
    }
  }

  async function showBookDirectory(bookId) {
    if (!bookId) return;
    showLoading();
    try {
      var resp = await apiGetDirect('/api/fqsearch/directory/' + encodeURIComponent(bookId));
      if (!resp || resp.code !== 0) {
        showToast((resp && resp.message) || '获取目录失败', 'error');
        return;
      }
      var container = document.getElementById('bs-detail-container');
      if (!container) return;
      var oldDir = container.querySelector('#bs-directory-card');
      if (oldDir) oldDir.remove();

      var dirCard = makeDirectoryCard(resp.data, bookId);
      container.appendChild(dirCard);
      dirCard.scrollIntoView({ behavior: 'smooth', block: 'start' });

      dirCard.querySelectorAll('.bs-close-card').forEach(function(btn) {
        btn.addEventListener('click', function() { dirCard.remove(); });
      });
    } catch (e) {
      showToast('请求失败: ' + e.message, 'error');
    } finally {
      hideLoading();
    }
  }

  /* ===========================
     CHAPTER VIEWER
     =========================== */
  var _cvState = { bookId: null, chapterId: null, prevId: null, nextId: null };

  function loadChapterDirect(bookId, chapterId) {
    if (dom.cvBookId) dom.cvBookId.value = bookId;
    if (dom.cvChapterId) dom.cvChapterId.value = chapterId;
    switchSection('chapter-viewer');
    loadChapter();
  }

  async function loadChapter() {
    var bookId = dom.cvBookId.value.trim();
    var chapterId = dom.cvChapterId.value.trim();
    if (!bookId || !chapterId) { showToast('请输入 Book ID 和 Chapter ID', 'error'); return; }

    dom.btnCvPrev.disabled = true;
    dom.btnCvNext.disabled = true;
    dom.btnCvComments.disabled = true;

    showLoading();
    try {
      var resp = await apiGetDirect('/api/fqnovel/chapter/' + encodeURIComponent(bookId) + '/' + encodeURIComponent(chapterId));
      if (!resp || resp.code !== 0) {
        showToast((resp && resp.message) || '获取章节失败', 'error');
        dom.cvContent.innerHTML = '<div class="detail-empty">获取章节失败</div>';
        return;
      }
      renderChapter(resp.data);
    } catch (e) {
      showToast('请求失败: ' + e.message, 'error');
      dom.cvContent.innerHTML = '<div class="detail-empty">加载出错: ' + escapeHtml(e.message) + '</div>';
    } finally {
      hideLoading();
    }
  }

  function renderChapter(data) {
    if (!data) return;
    _cvState.bookId = data.bookId;
    _cvState.chapterId = data.chapterId;
    _cvState.prevId = data.prevChapterId;
    _cvState.nextId = data.nextChapterId;

    if (dom.cvTitle) dom.cvTitle.textContent = data.title || ('章节 ' + data.chapterId);
    if (dom.cvContent) {
      dom.cvContent.innerHTML = (data.txtContent || data.rawContent || '内容为空').replace(/\n/g, '<br>');
    }

    dom.btnCvPrev.disabled = !data.prevChapterId;
    dom.btnCvNext.disabled = !data.nextChapterId;
    dom.btnCvComments.disabled = false;

    // Auto-fill comment fields
    if (dom.cmBookId) dom.cmBookId.value = data.bookId || '';
    if (dom.cmChapterId) dom.cmChapterId.value = data.chapterId || '';
  }

  function prevChapter() {
    if (_cvState.prevId) {
      dom.cvChapterId.value = _cvState.prevId;
      loadChapter();
    }
  }

  function nextChapter() {
    if (_cvState.nextId) {
      dom.cvChapterId.value = _cvState.nextId;
      loadChapter();
    }
  }

  function viewComments() {
    switchSection('comment-viewer');
  }

  /* ===========================
     COMMENT VIEWER
     =========================== */
  function jsonToVisual(obj, depth) {
    if (depth == null) depth = 0;
    if (depth > 4) return '<div style="color:var(--text-muted);padding:4px 0;">(深度过大，已截断)</div>';
    if (obj === null || obj === undefined) return '<span style="color:var(--text-muted);">null</span>';
    if (typeof obj === 'boolean') return '<span style="color:#e74c3c;font-weight:500;">' + obj + '</span>';
    if (typeof obj === 'number') return '<span style="color:#2ecc71;font-weight:500;">' + obj + '</span>';
    if (typeof obj === 'string') {
      if (obj.length > 200) obj = obj.slice(0, 200) + '…';
      return '<span style="color:#e0e0f0;">' + escapeHtml(obj) + '</span>';
    }
    if (Array.isArray(obj)) {
      if (obj.length === 0) return '<span style="color:var(--text-muted);">[]</span>';
      var items = obj.map(function(item, i) {
        return '<div style="padding:4px 0 4px ' + ((depth + 1) * 16) + 'px;border-bottom:1px solid rgba(255,255,255,0.04);">' +
          '<span style="color:var(--text-muted);font-size:0.75rem;margin-right:6px;">#' + i + '</span>' +
          jsonToVisual(item, depth + 1) + '</div>';
      }).join('');
      return '<div>' + items + '</div>';
    }
    if (typeof obj === 'object') {
      var keys = Object.keys(obj);
      if (keys.length === 0) return '<span style="color:var(--text-muted);">{}</span>';
      var rows = keys.map(function(k) {
        var val = obj[k];
        var valHtml = jsonToVisual(val, depth + 1);
        return '<div style="display:flex;padding:5px 0 5px ' + (depth * 16) + 'px;border-bottom:1px solid rgba(255,255,255,0.04);gap:8px;">' +
          '<span style="color:var(--accent-blue);font-size:0.82rem;font-weight:500;min-width:120px;flex-shrink:0;font-family:monospace;">' + escapeHtml(k) + '</span>' +
          '<span style="flex:1;">' + valHtml + '</span></div>';
      }).join('');
      return '<div>' + rows + '</div>';
    }
    return escapeHtml(String(obj));
  }

  function renderIdeaStats(raw) {
    if (!raw || typeof raw !== 'object') return '<div class="detail-empty">无数据</div>';
    // Navigate to stats: resp.data.data.data["0"] = {count:N}
    var stats = raw.data || raw;
    if (typeof stats !== 'object') return '<div class="detail-empty">无数据</div>';
    // stats might be { data: {...}, item_version: "..." } — unwrap if stats.data has numeric keys
    if (stats.data && typeof stats.data === 'object' && !Array.isArray(stats.data)) {
      var nestedKeys = Object.keys(stats.data).filter(function(k) { return /^\d+$/.test(k); });
      if (nestedKeys.length > 0) {
        stats = stats.data;
      }
    }
    var keys = Object.keys(stats).filter(function(k) { return /^\d+$/.test(k); });
    if (keys.length === 0) return '<div class="detail-empty">无数据</div>';

    var total = 0;
    var rows = keys.map(function(k) {
      var info = stats[k];
      if (!info || typeof info !== 'object') return '';
      var count = info.count || info.comment_count || 0;
      total += count;
      return '<div style="display:flex;align-items:center;justify-content:space-between;padding:6px 10px;border-bottom:1px solid var(--border-color);font-size:0.85rem;">' +
        '<span style="color:var(--text-secondary);">段落 ' + k + '</span>' +
        '<span><span style="font-weight:600;color:var(--text-primary);font-family:monospace;">' + count + '</span> 条评论</span>' +
        '</div>';
    }).filter(Boolean).join('');

    return '<div style="margin-bottom:8px;padding:8px 10px;background:var(--bg-tertiary);border-radius:var(--radius-sm);display:flex;gap:20px;font-size:0.85rem;">' +
      '<span><svg class="ic"><use href="/css/icons.svg#icon-stats"/></svg> 共 <strong>' + total + '</strong> 条段评</span>' +
      '<span><svg class="ic"><use href="/css/icons.svg#icon-file-text"/></svg> <strong>' + keys.length + '</strong> 个段落</span>' +
      '</div>' +
      '<div>' + rows + '</div>';
  }

  async function queryCommentIdea() {
    var chapterId = dom.cmChapterId.value.trim();
    if (!chapterId) { showToast('请输入 Chapter ID', 'error'); return; }
    showLoading();
    try {
      var resp = await apiPostDirect('/api/fqcomment/idea', {
        chapterId: chapterId,
        bookId: dom.cmBookId.value.trim() || null
      });
      if (!resp || resp.code !== 0) {
        showToast((resp && resp.message) || '查询统计失败', 'error');
        return;
      }
      dom.cmIdeaResult.innerHTML = resp.data
        ? renderIdeaStats(resp.data)
        : '<div class="detail-empty">无数据</div>';
      showToast('段评统计查询成功', 'success');
    } catch (e) {
      showToast('请求失败: ' + e.message, 'error');
    } finally {
      hideLoading();
    }
  }

  function findCommentArray(data) {
    if (!data || typeof data !== 'object') return null;
    // Handle common nesting: resp.data.data.xxx
    var inner = data.data || data.result || data.response || data;
    // Try known field names for comment lists
    var candidates = ['data_list', 'comments', 'list', 'comment_list', 'items', 'replies', 'records'];
    for (var i = 0; i < candidates.length; i++) {
      if (Array.isArray(inner[candidates[i]]) && inner[candidates[i]].length > 0) {
        return inner[candidates[i]];
      }
    }
    // Also check if inner has an inner data layer
    if (inner.data && Array.isArray(inner.data)) return inner.data;
    // Try any array field at the inner level
    var keys = Object.keys(inner);
    for (var i = 0; i < keys.length; i++) {
      if (Array.isArray(inner[keys[i]])) return inner[keys[i]];
    }
    return null;
  }

  function extractComment(c) {
    var item = c.comment || c;
    var common = item.common || item;
    var content = common.content || {};
    var userInfo = common.user_info || {};
    var baseInfo = userInfo.base_info || userInfo;
    var expand = item.expand || {};
    return {
      id: common.comment_id || item.comment_id || '',
      userId: baseInfo.user_id || '',
      userName: baseInfo.user_name || baseInfo.nickname || baseInfo.name || item.user_name || '匿名',
      avatarUrl: normalizeImageUrl(baseInfo.user_avatar || baseInfo.avatar_url || baseInfo.avatar || item.user_avatar || ''),
      description: baseInfo.description || userInfo.description || '',
      content: content.text || content.content || common.content_text || item.content || '(无内容)',
      quoteText: expand.para_src_content || '',
      time: common.create_timestamp || common.create_time || item.create_time || 0,
      likeCount: common.digg_count || common.like_count || item.digg_count || 0,
      replyCount: common.reply_count || item.reply_count || 0,
      isAuthor: baseInfo.is_author || userInfo.is_author || false,
      isVip: baseInfo.is_vip || userInfo.is_vip || false,
      gender: baseInfo.gender || baseInfo.profile_gender || 0
    };
  }

  function getField(obj) {
    for (var i = 0; i < arguments.length - 1; i++) {
      var val = obj && obj[arguments[i]];
      if (val != null && val !== '') return val;
    }
    return null;
  }

  /* formatCommentTime, getNested — provided by public.js */

  function getAvatarUrl(comment) {
    return getField(comment, 'user_info.avatar_url', 'user_info.avatar_url', 'user.avatar_url', 'user.avatar', 'avatar_url', 'avatar', 'user_info.avatar_url');
  }

  /* getNested — provided by public.js */

  function renderCommentCards(rawComments) {
    if (!rawComments || !rawComments.length) {
      return '<div class="detail-empty">暂无评论</div>';
    }
    return rawComments.map(function(c) {
      var e = extractComment(c);

      var avatarHtml = e.avatarUrl
        ? '<img src="' + escapeHtml(e.avatarUrl) + '" onerror="fixHeicImg(this)" style="width:42px;height:42px;border-radius:50%;object-fit:cover;flex-shrink:0;"><div style="display:none;width:42px;height:42px;border-radius:50%;background:var(--accent-blue-bg);color:var(--accent-blue);align-items:center;justify-content:center;font-size:1rem;font-weight:600;flex-shrink:0;">' + escapeHtml((e.userName.charAt(0) || '?').toUpperCase()) + '</div>'
        : '<div style="width:42px;height:42px;border-radius:50%;background:var(--accent-blue-bg);color:var(--accent-blue);display:flex;align-items:center;justify-content:center;font-size:1rem;font-weight:600;flex-shrink:0;">' + escapeHtml((e.userName.charAt(0) || '?').toUpperCase()) + '</div>';

      var genderIcon = e.gender === 1 ? '♂️' : e.gender === 2 ? '♀️' : '';
      var badges = [];
      if (e.isAuthor) badges.push('<span style="font-size:0.65rem;background:var(--accent-orange-bg);color:var(--accent-orange);padding:1px 6px;border-radius:8px;font-weight:600;">作者</span>');
      if (e.isVip) badges.push('<span style="font-size:0.65rem;background:var(--accent-blue-bg);color:var(--accent-blue);padding:1px 6px;border-radius:8px;font-weight:600;">VIP</span>');

      var quoteHtml = e.quoteText
        ? '<div style="margin:6px 0;padding:8px 10px;background:var(--bg-tertiary);border-left:3px solid var(--accent-blue);border-radius:4px;font-size:0.78rem;color:var(--text-muted);line-height:1.5;">"' + escapeHtml(e.quoteText.slice(0, 300)) + '"</div>'
        : '';

      var descHtml = e.description
        ? '<div style="font-size:0.72rem;color:var(--text-muted);margin-bottom:6px;">' + escapeHtml(e.description.slice(0, 60)) + '</div>'
        : '';

      var idHtml = e.id
        ? '<span style="font-size:0.65rem;color:var(--text-muted);font-family:monospace;">#' + escapeHtml(e.id) + '</span>'
        : '';

      return '<div style="display:flex;gap:14px;padding:16px 0;border-bottom:1px solid var(--border-color);">' +
        avatarHtml +
        '<div style="flex:1;min-width:0;">' +
        '<div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;margin-bottom:2px;">' +
        '<span style="font-size:0.9rem;font-weight:600;color:var(--accent-blue);">' + escapeHtml(e.userName) + '</span>' +
        (genderIcon ? '<span style="font-size:0.8rem;">' + genderIcon + '</span>' : '') +
        badges.join('') +
        '<span style="font-size:0.72rem;color:var(--text-muted);margin-left:auto;">' + formatCommentTime(e.time) + '</span>' +
        '</div>' +
        descHtml +
        quoteHtml +
        '<div style="font-size:0.85rem;line-height:1.7;color:var(--text-primary);word-wrap:break-word;">' + escapeHtml(e.content) + '</div>' +
        '<div style="display:flex;align-items:center;gap:20px;margin-top:8px;">' +
        (e.likeCount > 0 ? '<span style="font-size:0.78rem;color:var(--text-muted);display:flex;align-items:center;gap:3px;"><svg class="ic"><use href="/css/icons.svg#icon-thumbs-up"/></svg> ' + e.likeCount + '</span>' : '') +
        (e.replyCount > 0 ? '<span style="font-size:0.78rem;color:var(--text-muted);display:flex;align-items:center;gap:3px;"><svg class="ic"><use href="/css/icons.svg#icon-comment"/></svg> ' + e.replyCount + '</span>' : '') +
        idHtml +
        '</div></div></div>';
    }).join('');
  }

  function detectApiError(data) {
    if (!data || typeof data !== 'object') return null;
    var errCode = data.code || data.err_no || data.err_code || data.error_code || data.status_code;
    if (errCode && errCode !== 0 && errCode !== '0' && errCode !== 200) {
      var baseResp = data.BaseResp || data.base_resp || {};
      return {
        code: errCode,
        message: data.message || data.err_msg || data.err_tips || data.error_message || data.msg
          || baseResp.StatusMessage || baseResp.status_message || baseResp.message
          || '未知错误'
      };
    }
    return null;
  }

  async function queryCommentList() {
    var chapterId = dom.cmChapterId.value.trim();
    var bookId = dom.cmBookId.value.trim();
    var paraIdx = parseInt(dom.cmParaIdx.value, 10) || 0;
    if (!chapterId || !bookId) { showToast('请输入 Book ID 和 Chapter ID', 'error'); return; }
    showLoading();
    try {
      var resp = await apiPostDirect('/api/fqcomment/list', {
        chapterId: chapterId,
        bookId: bookId,
        paraIndex: paraIdx,
        count: 20
      });
      if (!resp || resp.code !== 0) {
        showToast((resp && resp.message) || '查询段评失败', 'error');
        return;
      }
      var raw = resp.data;
      if (raw) {
        var apiErr = detectApiError(raw);
        if (apiErr) {
          if (dom.cmListCount) dom.cmListCount.textContent = '';
          dom.cmListResult.innerHTML =
            '<div style="padding:20px;text-align:center;">' +
            '<div style="margin-bottom:8px;"><svg class="ic" style="width:32px;height:32px;"><use href="/css/icons.svg#icon-alert"/></svg></div>' +
            '<div style="color:var(--accent-red);font-weight:600;margin-bottom:4px;">API 错误 ' + apiErr.code + '</div>' +
            '<div style="color:var(--text-secondary);font-size:0.85rem;">' + escapeHtml(apiErr.message) + '</div>' +
            '</div>';
          showToast('API 返回错误 ' + apiErr.code + ': ' + apiErr.message, 'error');
          return;
        }
        var comments = findCommentArray(raw);
        var totalCount = raw.data && raw.data.common_list_info && raw.data.common_list_info.total;
        if (dom.cmListCount) {
          dom.cmListCount.textContent = totalCount ? '（共 ' + totalCount + ' 条）' : (comments ? '（' + comments.length + ' 条）' : '');
        }
        if (comments) {
          dom.cmListResult.innerHTML = renderCommentCards(comments);
        } else {
          dom.cmListResult.innerHTML = jsonToVisual(raw);
        }
      } else {
        dom.cmListCount.textContent = '';
        dom.cmListResult.innerHTML = '<div class="detail-empty">无数据</div>';
      }
      showToast('段评详情查询成功', 'success');
    } catch (e) {
      showToast('请求失败: ' + e.message, 'error');
    } finally {
      hideLoading();
    }
  }

  /* ===========================
     MONITOR AUTO-REFRESH
     =========================== */
  function startMonitorRefresh() {
    stopMonitorRefresh();
    state.monitorInterval = setInterval(function() {
      if (state.currentSection === 'dashboard') refreshDashboard();
    }, 30000);
  }

  function stopMonitorRefresh() {
    if (state.monitorInterval) {
      clearInterval(state.monitorInterval);
      state.monitorInterval = null;
    }
  }

  /* ===========================
     INIT
     =========================== */
  function init() {
    cacheDom();

    // Navigation
    dom.navItems.forEach(function(item) {
      item.addEventListener('click', function() {
        switchSection(this.dataset.section);
      });
    });

    // Dashboard
    if (dom.btnRefreshDashboard) {
      dom.btnRefreshDashboard.addEventListener('click', refreshDashboard);
    }

    // Config
    if (dom.btnLoadConfig) dom.btnLoadConfig.addEventListener('click', function() { loadConfig(true); });
    if (dom.btnSaveConfig) dom.btnSaveConfig.addEventListener('click', saveConfig);
    if (dom.btnHotReload) dom.btnHotReload.addEventListener('click', hotReload);

    // Device pool
    if (dom.btnRefreshPool) dom.btnRefreshPool.addEventListener('click', refreshDevicePool);
    if (dom.btnAddDevice) dom.btnAddDevice.addEventListener('click', addDevice);
    if (dom.btnRebuildPool) dom.btnRebuildPool.addEventListener('click', rebuildPool);

    // System monitor
    if (dom.btnRefreshSystem) dom.btnRefreshSystem.addEventListener('click', refreshSystemMonitor);

    // Restart
    if (dom.btnRestart) dom.btnRestart.addEventListener('click', restartApp);

    // Lock panel
    if (dom.btnLock) dom.btnLock.addEventListener('click', lockPanel);

    // Book Search
    if (dom.btnBsSearch) dom.btnBsSearch.addEventListener('click', function() { searchBooks(0); });
    if (dom.bsQuery) dom.bsQuery.addEventListener('keydown', function(e) { if (e.key === 'Enter') searchBooks(0); });

    // Chapter Viewer
    if (dom.btnCvLoad) dom.btnCvLoad.addEventListener('click', loadChapter);
    if (dom.btnCvPrev) dom.btnCvPrev.addEventListener('click', prevChapter);
    if (dom.btnCvNext) dom.btnCvNext.addEventListener('click', nextChapter);
    if (dom.btnCvComments) dom.btnCvComments.addEventListener('click', viewComments);
    if (dom.cvChapterId) dom.cvChapterId.addEventListener('keydown', function(e) { if (e.key === 'Enter') loadChapter(); });
    if (dom.cvBookId) dom.cvBookId.addEventListener('keydown', function(e) { if (e.key === 'Enter') loadChapter(); });

    // Comment Viewer
    if (dom.btnCmIdea) dom.btnCmIdea.addEventListener('click', queryCommentIdea);
    if (dom.btnCmList) dom.btnCmList.addEventListener('click', queryCommentList);
    if (dom.cmChapterId) dom.cmChapterId.addEventListener('keydown', function(e) { if (e.key === 'Enter') queryCommentIdea(); });

    // Mobile sidebar toggle
    if (dom.mobileToggle) {
      dom.mobileToggle.addEventListener('click', function() {
        dom.sidebar.classList.toggle('open');
      });
      // Close sidebar when nav item clicked on mobile
      dom.navItems.forEach(function(item) {
        item.addEventListener('click', function() {
          if (window.innerWidth <= 768) {
            dom.sidebar.classList.remove('open');
          }
        });
      });
      // Close sidebar when clicking outside
      document.addEventListener('click', function(e) {
        if (window.innerWidth <= 768 && !dom.sidebar.contains(e.target) && e.target !== dom.mobileToggle) {
          dom.sidebar.classList.remove('open');
        }
      });
    }

    // Initial load
    switchSection('dashboard');
    startMonitorRefresh();

    // Keyboard shortcut - Escape to close toasts
    document.addEventListener('keydown', function(e) {
      if (e.key === 'Escape') {
        dom.toastContainer.innerHTML = '';
      }
    });
    // Global HEIC image error handler (capture phase, runs before inline onerror)
    document.addEventListener('error', function(e) {
      if (e.target && e.target.tagName === 'IMG') {
        fixHeicImg(e.target);
      }
    }, true);

  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
