// Comment Page — Ink & Parchment
(function(){
  var theme = localStorage.getItem('comment-theme');
  var prefersDark = window.matchMedia('(prefers-color-scheme:dark)').matches;
  if (theme === 'light' || (!theme && !prefersDark)) {
    document.body.classList.add('light');
  }
})();

function toggleTheme(){
  document.body.classList.toggle('light');
  localStorage.setItem('comment-theme',
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
  try {
    var r = await fetch('/api/ssr/comment-replies?commentId='+btn.dataset.commentId+'&bookId='+btn.dataset.bookId+'&chapterId='+btn.dataset.chapterId+'&cursor='+btn.dataset.cursor);
    if(!r.ok) throw new Error('HTTP '+r.status);
    var h = await r.text();
    var list = btn.closest('.reply-section')?.querySelector('.reply-list');
    if(list){
      var tmp = document.createElement('div');
      tmp.innerHTML = h;
      var newBtn = tmp.querySelector('.reply-load-more');
      if(newBtn){
        btn.dataset.cursor = newBtn.dataset.cursor;
        btn.innerHTML = newBtn.innerHTML;
        Array.from(tmp.children).forEach(function(c){
          if(!c.classList.contains('reply-load-more')) list.appendChild(c);
        });
        // 滚动到查看更多按钮，让新加载的内容可见
        btn.scrollIntoView({ block:'nearest', behavior:'smooth' });
      } else {
        btn.remove();
        Array.from(tmp.children).forEach(function(c){ list.appendChild(c); });
        // 滚动到最后一条新回复
        var last = list.lastChild;
        if(last) last.scrollIntoView({ block:'nearest', behavior:'smooth' });
      }
    }
  } catch(e){
    btn.innerHTML = orig;
    console.error('loadMoreReplies error', e);
  } finally {
    btn.disabled = false;
  }
}

var _heic2anyLoaded = false;

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
    console.warn('HEIC fail:', e);
    img.style.display = 'none';
  });
}

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

document.addEventListener('error', function(e) {
  if (e.target && e.target.tagName === 'IMG') fixHeicImg(e.target);
}, true);
