// Comment Page — Literary Scroll v2 (书卷风)
// 明暗主题:body.light 类切换(与 SSR head 防闪脚本一致);主题 key 兼容旧 comment-theme
(function(){
  var theme = localStorage.getItem('theme') || localStorage.getItem('comment-theme');
  var prefersDark = window.matchMedia('(prefers-color-scheme:dark)').matches;
  if (theme === 'light' || (!theme && !prefersDark)) {
    document.body.classList.add('light');
  }
})();

/* ============ T9 阅读字号调节(与公开/管理 reader 同 key:localStorage.fontSize) ============
   读取 fontSize(数字 px)写入 --reader-font-size CSS 变量,联动 .comment-text/.reply-text;
   模板结构零改动,控制按钮由本文件动态注入 .theme-toggle 左侧 */
(function(){
  var FONT_KEY = 'fontSize';
  var FONT_MIN = 12, FONT_MAX = 28, FONT_STEP = 2;

  function currentSize() {
    var v = parseInt(localStorage.getItem(FONT_KEY), 10);
    return (v >= FONT_MIN && v <= FONT_MAX) ? v : 16;
  }

  function applySize(px) {
    document.documentElement.style.setProperty('--reader-font-size', px + 'px');
  }

  function saveSize(px) {
    localStorage.setItem(FONT_KEY, String(px));
    applySize(px);
  }

  function initFontSize() {
    applySize(currentSize());
    var toggle = document.querySelector('.theme-toggle');
    if (!toggle) return;
    var box = document.createElement('div');
    box.className = 'font-size-controls';
    box.innerHTML =
      '<button type="button" data-dir="-1" title="减小字号">A-</button>' +
      '<button type="button" data-dir="0" title="重置字号">重置</button>' +
      '<button type="button" data-dir="1" title="增大字号">A+</button>';
    box.addEventListener('click', function(e) {
      var btn = e.target.closest('button');
      if (!btn) return;
      var dir = parseInt(btn.getAttribute('data-dir'), 10);
      var next = dir === 0 ? 16 : currentSize() + dir * FONT_STEP;
      next = Math.max(FONT_MIN, Math.min(FONT_MAX, next));
      saveSize(next);
    });
    document.body.appendChild(box);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initFontSize);
  } else {
    initFontSize();
  }
})();

function toggleTheme(){
  document.body.classList.toggle('light');
  localStorage.setItem('theme',
    document.body.classList.contains('light') ? 'light' : 'dark');
  var btn = document.getElementById('themeToggleBtn');
  if (btn) btn.textContent = document.body.classList.contains('light') ? '✦' : '✦';
}

function toggleExpand(btn){
  var text = btn.previousElementSibling;
  text.classList.toggle('collapsed');
  btn.textContent = text.classList.contains('collapsed') ? '✦ 展开全文' : '✦ 收起';
}

async function loadReplies(btn){
  if(btn.disabled) return;
  var sec = btn.closest('.reply-section');
  var list = sec.querySelector('.reply-list');
  if(list.innerHTML.trim()){
    list.style.display = list.style.display==='none'?'block':'none';
    btn.innerHTML = list.style.display==='block'
      ? '✦ 收起回复'
      : '✦ ' + (btn.getAttribute('data-label')||'查看回复');
    return;
  }
  btn.disabled = true;
  btn.setAttribute('data-label', btn.textContent.trim());
  btn.innerHTML = '⟳ 加载中...';
  try {
    var r = await fetch('/api/ssr/comment-replies?commentId='+sec.dataset.commentId+'&bookId='+sec.dataset.bookId+'&chapterId='+sec.dataset.chapterId);
    if(!r.ok) throw new Error('HTTP '+r.status);
    var h = await r.text();
    list.innerHTML = h;
    list.style.display = 'block';
    btn.innerHTML = '✦ 收起回复';
    // 处理回复中的头像
    processAvatars();
  } catch(e){
    list.innerHTML = '<div class="reply-error">✦ 加载回复失败</div>';
    list.style.display = 'block';
  } finally {
    btn.disabled = false;
  }
}

async function loadMoreReplies(btn){
  if(btn.disabled) return;
  btn.disabled = true;
  var orig = btn.innerHTML;
  btn.innerHTML = '⟳ 加载中...';
  var list = btn.closest('.reply-section')?.querySelector('.reply-list');
  if(!list){ btn.disabled = false; return; }
  try {
    var r = await fetch('/api/ssr/comment-replies?commentId='+btn.dataset.commentId+'&bookId='+btn.dataset.bookId+'&chapterId='+btn.dataset.chapterId+'&cursor='+btn.dataset.cursor);
    if(!r.ok) throw new Error('HTTP '+r.status);
    var h = await r.text();
    var tmp = document.createElement('div');
    tmp.innerHTML = h;
    var newBtn = tmp.querySelector('.reply-load-more');
    // 先把旧按钮从当前位置移除
    btn.remove();
    // 追加新内容（跳过新按钮，后续用 btn 替代）
    Array.from(tmp.children).forEach(function(c){
      if(c.classList.contains('reply-load-more')) return;
      list.appendChild(c);
    });
    if(newBtn){
      // 有更多页：更新旧按钮数据，放到底部
      btn.dataset.cursor = newBtn.dataset.cursor;
      btn.innerHTML = newBtn.innerHTML;
      btn.disabled = false;
      list.appendChild(btn);
      btn.scrollIntoView({ block:'nearest', behavior:'smooth' });
    } else {
      // 没有更多页：按钮不再需要
      var last = list.lastChild;
      if(last) last.scrollIntoView({ block:'nearest', behavior:'smooth' });
    }
    // 处理新追加回复中的头像
    processAvatars();
  } catch(e){
    // 出错时恢复按钮，但不要重新插入（可能已被 remove）
    btn.innerHTML = orig;
    btn.disabled = false;
    // 如果按钮不在 DOM 中，重新加回去
    if(!btn.parentNode && list) list.appendChild(btn);
    console.error('loadMoreReplies error', e);
  }
}

var _heic2anyLoaded = false;

function loadHeic2Any() {
  if (typeof heic2any === 'function') return Promise.resolve();
  return new Promise(function(resolve) {
    var s = document.createElement('script');
    s.src = '/js/heic2any.min.js';
    s.onload = resolve;
    s.onerror = resolve;
    document.head.appendChild(s);
  });
}

// 主动转换所有头像 HEIC 图片（页面加载时走一遍，不用等 onerror）
function processAvatars() {
  var imgs = document.querySelectorAll('.avatar-img, .reply-avatar-img');
  if (!imgs.length) return;
  loadHeic2Any().then(function() {
    Array.from(imgs).forEach(function(img) {
      if (img._heicFixed) return;
      img._heicFixed = true;
      var src = img.src || '';
      if (!src) return;
      fetch(src).then(function(r) {
        if (!r.ok) throw new Error(r.status);
        var ct = (r.headers.get('content-type') || '').toLowerCase();
        return r.blob().then(function(blob) { return { blob: blob, ct: ct }; });
      }).then(function(info) {
        if (info.ct.indexOf('heic') !== -1 || info.ct.indexOf('heif') !== -1) {
          return heic2any({ blob: info.blob, toType: 'image/jpeg', quality: 1 }).then(function(result) {
            var b = Array.isArray(result) ? result[0] : result;
            img.src = URL.createObjectURL(b);
            img.onerror = null;
            var cont = img.closest('.avatar, .reply-avatar');
            if (cont) {
              var l = cont.querySelector('.avatar-letter, .reply-avatar > span');
              if (l) l.style.display = 'none';
            }
          });
        } else {
          img.src = URL.createObjectURL(info.blob);
          img.onerror = null;
        }
      }).catch(function(e) {
        console.warn('Avatar proc fail:', e);
      });
    });
  });
}

// 内联评论图片 onerror 兜底：加载失败时尝试 HEIC 转换
function fixHeicImg(img) {
  if (img._heicFixed) return;
  img._heicFixed = true;
  var src = img.src || '';
  if (!src) return;
  loadHeic2Any().then(function() {
    return fetch(src);
  }).then(function(r) {
    if (!r.ok) throw new Error(r.status);
    var ct = (r.headers.get('content-type') || '').toLowerCase();
    return r.blob().then(function(blob) { return { blob: blob, ct: ct }; });
  }).then(function(info) {
    if (info.ct.indexOf('heic') !== -1 || info.ct.indexOf('heif') !== -1) {
      return heic2any({ blob: info.blob, toType: 'image/jpeg', quality: 1 }).then(function(result) {
        var b = Array.isArray(result) ? result[0] : result;
        img.src = URL.createObjectURL(b);
        img.style.display = '';
        img.onerror = null;
        var cont = img.closest('.avatar, .reply-avatar');
        if (cont) {
          var l = cont.querySelector('.avatar-letter, .reply-avatar > span');
          if (l) l.style.display = 'none';
        }
      });
    } else {
      img.src = URL.createObjectURL(info.blob);
      img.style.display = '';
      img.onerror = null;
    }
  }).catch(function(e) {
    console.warn('fixHeicImg fail:', e);
    img.style.display = 'none';
  });
}

// 页面加载后主动处理头像
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', processAvatars);
} else {
  processAvatars();
}

// 点击评论图片展开/收起
document.addEventListener('click', function(e) {
  var target = e.target;
  if (!target.classList.contains('comment-image')) return;
  if (target.classList.contains('expanded')) {
    target.classList.remove('expanded');
    target.style.maxHeight = '400px';
  } else {
    document.querySelectorAll('.comment-image.expanded').forEach(function(img) {
      img.classList.remove('expanded');
      img.style.maxHeight = '400px';
    });
    target.classList.add('expanded');
    target.style.maxHeight = '';
  }
});
