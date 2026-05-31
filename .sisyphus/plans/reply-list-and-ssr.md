# 段评回复列表接口实现 + SSR 页面更新

## TL;DR

> **Quick Summary**: 实现 `POST /novel/commentapi/reply/list/:comment_id/v1/` 段评回复获取接口，并在 SSR 评论页面中以"点击加载"方式展示回复内容。
>
> **Deliverables**:
> - `FQCommentReplyListRequest.java` — 新 DTO
> - `FQCommentService.getReplyList()` — 新 Service 方法
> - `FQCommentController POST /reply/list` — 新 API 端点
> - `SsrCommentService.renderReplyListHtml()` — 回复 HTML 渲染
> - `SsrCommentController GET /comment-replies` — SSR 回复 HTML 片段端点
> - SSR 页面更新: 回复按钮 + JS 加载 + CSS 样式
>
> **Estimated Effort**: Short (~1h)
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: DTO → Service → Controller → SSR rendering

---

## Context

### Original Request
从 APK 逆向分析段评回复接口并实现到项目中，然后更新 SSR 段评页面。

### Interview Summary
**Key Discussions**:
- 回复加载方式：选择「点击加载」而非服务端预加载（避免 N+1 问题）
- comment_source 用 502 (NovelParaReply) 而非 2 (NovelParaComment)
- 复用已有的 `FQCommentService.executeCommentPost()` 底层 HTTP 发送

**Research Findings**:
- API: `POST /novel/commentapi/reply/list/:comment_id/v1/`（通过 jadx 反编译 APK 确认）
- 请求体含: comment_id, group_id(=chapterId), group_type=15, comment_source=502, business_param(book_id, need_count), count, cursor
- 响应含: comment(被回复评论), reply_list[](回复列表), comment_list_info(分页), extra(ref_reply)
- 已用签名 API 实测验证通过（code=0, reply_list 有数据）
- UgcReply 含 sub_reply（子回复嵌套）

---

## Work Objectives

### Core Objective
让用户能在 SSR 段评页面中查看每条评论的回复内容。

### Concrete Deliverables
1. `src/main/java/com/anjia/unidbgserver/dto/FQCommentReplyListRequest.java`
2. `FQCommentService.getReplyList()` 方法
3. `FQCommentController` 新增 `POST /reply/list`
4. `SsrCommentService.renderReplyListHtml()` 方法
5. `SsrCommentController` 新增 `GET /comment-replies`
6. SSR 页面 `renderCommentCard()` 更新: 回复按钮 + JS + CSS

### Must Have
- API 端点正确调用 reply/list 并返回 JSON
- SSR 评论卡片显示回复数（已有）
- 点击"查看N条回复"按钮加载回复并展示
- 回复列表显示: 头像、用户名、回复文本、时间、回复对象

### Must NOT Have
- 不在 SSR 时预加载回复（选的是点击加载）
- 不实现 add/reply 接口
- 不实现子回复的无限嵌套渲染（仅渲染第一层 + 子回复的摘要）

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: YES (Maven + JUnit, 但 maven.test.skip=true)
- **Automated tests**: NO (项目默认跳过测试)
- **Agent-Executed QA**: ALWAYS

### QA Policy
每个任务通过 Bash 执行 curl 验证:
- 新 API 端点: curl POST `/api/fqcomment/reply/list` 检查 code=0
- SSR HTML 端点: curl GET `/api/ssr/comment-replies` 检查 HTML 包含回复内容
- 编译验证: `./mvnw clean compile -DskipTests` 无错误

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation — parallel):
├── Task 1: DTO + Service + Controller (3 个独立文件)
├── Task 2: SSR Service renderReplyListHtml
└── Task 3: SSR Controller /comment-replies endpoint

Wave 2 (Integration):
├── Task 4: Update SSR page (comment card + JS + CSS)
└── Task 5: Build + QA verification
```

---

## TODOs

- [x] 1. DTO + Service + Controller

  **What to do**:
  - 创建 `FQCommentReplyListRequest.java` — 字段: commentId, bookId, chapterId, commentSource(默认502), groupType(默认15), count(默认20), cursor
  - 在 `FQCommentService` 新增 `getReplyList(FQCommentReplyListRequest)` 方法，复用 `executeCommentPost()`，路径为 `/novel/commentapi/reply/list/{commentId}/v1`
  - 在 `FQCommentController` 新增 `POST /reply/list` 端点，参数校验 commentId 必填

  **Must NOT do**:
  - 不改动其他已有接口逻辑
  - 不需要新的配置文件

  **Recommended Agent Profile**:
  - Category: `unspecified-high`
    - Reason: 涉及 3 个文件的增改，但模式完全参照现有代码
  - Skills: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO (sequential within task)
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 2, Task 4
  - **Blocked By**: None

  **References**:
  - `FQCommentListRequest.java` — DTO 模式参考（字段命名、默认值风格）
  - `FQCommentService.java:getCommentList()` — Service 方法模式参考（build body → executeCommentPost）
  - `FQCommentController.java:getCommentList()` — Controller 模式参考（参数校验 + 调用 service）
  - `FQCommentIdeaRequest.java` — 简单 DTO 参考

  **Acceptance Criteria**:
  - [ ] 文件创建: `FQCommentReplyListRequest.java` 存在
  - [ ] Service 方法编译无错误

  **QA Scenarios**:
  ```
  Scenario: 调用 reply/list API 成功返回
    Tool: Bash (curl)
    Preconditions: 服务正在运行 (端口 8099)
    Steps:
      1. curl -X POST 'http://127.0.0.1:8099/api/fqcomment/reply/list'
         -H 'Content-Type: application/json'
         -d '{"commentId":"6931556220436299784","bookId":"6707112755507235848","chapterId":"6707197312789119502","count":5}'
      2. 检查响应 JSON 中 code == 0
      3. 检查响应中存在 data.reply_list 字段
    Expected Result: code=0, reply_list 为数组
    Evidence: .sisyphus/evidence/task-1-reply-list.json

  Scenario: commentId 为空返回错误
    Tool: Bash (curl)
    Preconditions: 服务运行中
    Steps:
      1. curl -X POST 'http://127.0.0.1:8099/api/fqcomment/reply/list'
         -H 'Content-Type: application/json'
         -d '{"bookId":"6707112755507235848","chapterId":"6707197312789119502"}'
      2. 检查响应 code != 0 或 message 包含错误
    Expected Result: 参数校验失败提示
    Evidence: .sisyphus/evidence/task-1-reply-list-error.json
  ```

  **Commit**: YES
  - Message: `feat(comment): add reply/list API endpoint`
  - Files: `FQCommentReplyListRequest.java`, `FQCommentService.java`, `FQCommentController.java`

---

- [x] 2. SSR Service — renderReplyListHtml method

  **What to do**:
  - 在 `SsrCommentService` 新增 `renderReplyListHtml(String commentId, String bookId, String chapterId)` 方法
  - 调用 `fqCommentService.getReplyList()` 获取回复数据
  - 解析 `data.reply_list[]` 数组，为每条回复渲染 HTML
  - 回复 HTML 结构（与评论类似但更紧凑）:
    - 左侧缩进 + 左边框线
    - 头像(小号 32px) + 用户名 + "回复 @XXX"(被回复人) + 时间
    - 回复文本（支持 emoji 转换）
    - 子回复(sub_reply) 缩进展示前 3 条 + "查看全部N条子回复"
    - 底部点赞数 + 回复数
  - 返回纯 HTML 片段（不含 DOCTYPE/html/head/body）
  - 异常时返回 `<div class="reply-error">加载回复失败</div>`

  **Must NOT do**:
  - 不包含完整 HTML 页面结构
  - 不修改现有 renderHtml / renderCommentCard

  **References**:
  - `SsrCommentService.java:renderCommentCard()` — 渲染风格参考
  - `SsrCommentService.java:convertEmoji()` — emoji 转换复用
  - `SsrCommentService.java:formatTime()` — 时间格式化复用
  - `SsrCommentService.java:escapeHtml()` — XSS 防护复用
  - `SsrCommentService.java:getAvatarColor()` — 头像颜色复用

  **Acceptance Criteria**:
  - [ ] `renderReplyListHtml()` 方法存在并编译通过
  - [ ] 返回的 HTML 包含回复用户的用户名和回复文本

  **QA Scenarios**:
  ```
  Scenario: 渲染回复 HTML 返回内容
    Tool: Bash (curl 调用 SSR 端点验证)
    Preconditions: Task 1 已完成
    Steps:
      1. Task 2 完成后在 Task 3 一起验证
    Expected Result: 在 Task 3 验证
  ```

  **Commit**: NO (与 Task 3 一起)

---

- [x] 3. SSR Controller — GET /comment-replies endpoint

  **What to do**:
  - 在 `SsrCommentController` 新增 `GET /comment-replies` 端点
  - 参数: `commentId`, `bookId`, `chapterId` (均为 @RequestParam)
  - 返回 `MediaType.TEXT_HTML_VALUE + ";charset=UTF-8"`
  - 调用 `ssrCommentService.renderReplyListHtml(commentId, bookId, chapterId)`
  - 返回 HTML 片段
  - 参数校验: commentId 必填，缺少返回错误 HTML

  **References**:
  - `SsrCommentController.java:getCommentPage()` — 端点模式参考

  **Acceptance Criteria**:
  - [ ] GET `/api/ssr/comment-replies?commentId=&bookId=&chapterId=` 返回 HTML

  **QA Scenarios**:
  ```
  Scenario: 调用 SSR 回复端点返回 HTML
    Tool: Bash (curl)
    Preconditions: Task 1 已完成
    Steps:
      1. curl -s 'http://127.0.0.1:8099/api/ssr/comment-replies?commentId=6931556220436299784&bookId=6707112755507235848&chapterId=6707197312789119502'
      2. 检查输出包含 HTML 标签（div 等）
      3. 检查输出包含回复用户信息或"暂无回复"
    Expected Result: 返回有效的 HTML 片段
    Evidence: .sisyphus/evidence/task-3-ssr-replies.html

  Scenario: commentId 为空
    Tool: Bash (curl)
    Steps:
      1. curl -s 'http://127.0.0.1:8099/api/ssr/comment-replies'
      2. 检查返回错误提示
    Expected Result: 返回错误 HTML
    Evidence: .sisyphus/evidence/task-3-ssr-replies-error.html
  ```

  **Commit**: YES (with Task 2)
  - Message: `feat(ssr): add comment reply list rendering and endpoint`
  - Files: `SsrCommentService.java`, `SsrCommentController.java`

---

- [x] 4. Update SSR page — reply button + JS + CSS

  **What to do**:
  - 在 `SsrCommentService.renderCommentCard()` 中:
    - 在 footer 后面，如果 `replyCount > 0`，添加:
      ```html
      <div class="reply-section" data-comment-id="{commentId}" data-book-id="{bookId}" data-chapter-id="{chapterId}">
        <button class="reply-toggle" onclick="loadReplies(this)">
          <i class="far fa-comment-dots"></i> 查看{replyCount}条回复
        </button>
        <div class="reply-list"></div>
      </div>
      ```
  - 在 `<script>` 中添加 `loadReplies()` 函数:
    ```javascript
    async function loadReplies(btn) {
      if (btn.disabled) return;
      const section = btn.closest('.reply-section');
      const list = section.querySelector('.reply-list');
      if (list.innerHTML.trim()) { /* toggle */ list.style.display = list.style.display === 'none' ? 'block' : 'none'; return; }
      btn.disabled = true;
      btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 加载中...';
      try {
        const resp = await fetch('/api/ssr/comment-replies?commentId=' + section.dataset.commentId + '&bookId=' + section.dataset.bookId + '&chapterId=' + section.dataset.chapterId);
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const html = await resp.text();
        list.innerHTML = html;
        list.style.display = 'block';
        btn.innerHTML = '<i class="far fa-comment-dots"></i> 收起回复';
      } catch(e) {
        list.innerHTML = '<div class="reply-error">加载回复失败</div>';
        list.style.display = 'block';
      } finally { btn.disabled = false; }
    }
    ```
  - 添加 CSS:
    ```css
    .reply-section{margin-top:10px;padding-left:52px}
    .reply-toggle{font-size:13px;color:#4a90d9;cursor:pointer;background:none;border:none;padding:6px 0;display:flex;align-items:center;gap:4px}
    .reply-toggle:hover{color:#357abd}
    .reply-list{display:none;margin-top:8px}
    .reply-card{padding:10px 12px;margin-bottom:6px;background:#f8f8f8;border-radius:8px;border-left:3px solid #e0e0e0}
    .reply-header{display:flex;align-items:center;gap:8px;margin-bottom:4px}
    .reply-avatar{width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:600;color:#fff;flex-shrink:0;overflow:hidden}
    .reply-avatar-img{width:100%;height:100%;object-fit:cover;border-radius:50%}
    .reply-user-name{font-size:13px;font-weight:600;color:#333}
    .reply-to{font-size:12px;color:#999}
    .reply-to .at-user{color:#4a90d9}
    .reply-time{font-size:11px;color:#bbb;margin-left:auto}
    .reply-text{font-size:14px;color:#222;line-height:1.6;word-break:break-word}
    .reply-footer{display:flex;align-items:center;gap:12px;margin-top:4px}
    .reply-footer .stat{font-size:12px;color:#999;display:flex;align-items:center;gap:3px}
    .sub-reply{margin-top:6px;padding-left:16px;border-left:2px solid #eee}
    .sub-reply-card{padding:6px 8px;margin-bottom:4px;font-size:13px;color:#555;background:#f0f0f0;border-radius:6px}
    .sub-reply-more{font-size:12px;color:#4a90d9;cursor:pointer;padding:4px 0;display:inline-block}
    .reply-error{font-size:13px;color:#e74c3c;padding:8px}
    @media(prefers-color-scheme:dark){
      .reply-card{background:#3a3a3a;border-left-color:#555}
      .reply-user-name{color:#e0e0e0}
      .reply-text{color:#ccc}
      .sub-reply-card{background:#444;color:#aaa}
    }
    ```

  **Must NOT do**:
  - 不改动 renderHtml() 的整体结构
  - 不修改已有的主题切换逻辑

  **References**:
  - `SsrCommentService.java:renderCommentCard()` — 在 footer 后插入
  - `SsrCommentService.java:renderHtml()` — script 段的位置
  - `SsrCommentService.java` 中现有的 CSS 块

  **Acceptance Criteria**:
  - [ ] 评论卡片显示回复按钮（replyCount > 0 时）
  - [ ] 点击按钮加载回复并显示
  - [ ] 暗色模式样式正常

  **QA Scenarios**:
  ```
  Scenario: SSR 页面显示回复按钮
    Tool: Bash (curl)
    Preconditions: Task 1-3 已完成
    Steps:
      1. curl -s 'http://127.0.0.1:8099/api/ssr/comment-page?bookId=6707112755507235848&chapterId=6707197312789119502&paraIndex=0'
      2. 检查 HTML 中包含 "reply-toggle" 和 "loadReplies"
      3. 检查 CSS 中包含 ".reply-section"
    Expected Result: 页面渲染正常，回复按钮存在
    Evidence: .sisyphus/evidence/task-4-ssr-page.html
  ```

  **Commit**: YES
  - Message: `feat(ssr): add reply toggle and load logic to comment page`
  - Files: `SsrCommentService.java`

---

## Final Verification Wave

- [x] F1. **编译验证**: `./mvnw clean compile -DskipTests` 成功
- [x] F2. **API 端点测试**: curl POST `/api/fqcomment/reply/list` 返回 code=0
- [x] F3. **SSR 端点测试**: curl GET `/api/ssr/comment-replies?commentId=...` 返回 HTML
- [x] F4. **SSR 页面测试**: curl GET `/api/ssr/comment-page?...` 包含 reply-toggle
- [x] F5. **设备池兼容**: 调用 3 次 reply/list 使用不同设备，均返回成功

---

## Commit Strategy

- **Task 1**: `feat(comment): add reply/list API endpoint`
  - Files: FQCommentReplyListRequest.java, FQCommentService.java, FQCommentController.java
- **Task 2+3**: `feat(ssr): add comment reply list rendering and endpoint`
  - Files: SsrCommentService.java, SsrCommentController.java
- **Task 4**: `feat(ssr): add reply toggle and load logic to comment page`
  - Files: SsrCommentService.java

---

## Success Criteria

### Verification Commands
```bash
# 编译
./mvnw clean compile -DskipTests

# 测试 API 端点
curl -s -X POST 'http://127.0.0.1:8099/api/fqcomment/reply/list' \
  -H 'Content-Type: application/json' \
  -d '{"commentId":"6931556220436299784","bookId":"6707112755507235848","chapterId":"6707197312789119502","count":5}'
# Expected: {"code":0,"data":{"reply_list":[...],...}}

# 测试 SSR HTML 端点
curl -s 'http://127.0.0.1:8099/api/ssr/comment-replies?commentId=6931556220436299784&bookId=6707112755507235848&chapterId=6707197312789119502'
# Expected: HTML containing reply cards or "暂无回复"

# 测试 SSR 页面
curl -s 'http://127.0.0.1:8099/api/ssr/comment-page?bookId=6707112755507235848&chapterId=6707197312789119502&paraIndex=0'
# Expected: HTML containing reply-toggle buttons
```

### Final Checklist
- [ ] All files compile without errors
- [ ] `/api/fqcomment/reply/list` returns success with reply data
- [ ] `/api/ssr/comment-replies` returns valid HTML
- [ ] SSR page shows reply buttons on comments with replies
