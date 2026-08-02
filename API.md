 番茄小说段评接口文档

> 基于多源逆向：
> - APK 反编译（番茄小说 6.8.1.32）
> - Rust 二进制逆向（Tomato-Novel-Downloader v2.4.10，带 official-api feature）
> - 公开资料（ad-rules、Xposed 模块、开源社区）
>
> 关键源类（APK）：
> - `com.dragon.read.saas.ugc.rpc.CommentApiService`
> - `com.dragon.read.saas.ugc.model.GetIdeaListRequest`
> - `com.dragon.read.saas.ugc.model.GetCommentListRequest`
> - `com.dragon.read.saas.ugc.model.CommentBusinessParam`
> - `com.dragon.read.net.CommentManager`
>
> 关键源结构（Rust）：
> - `CommentStatsPayload`（6 字段）
> - `CommentListPayload`（11 字段）
> - `ReviewItem`（14 字段）
> - `ReviewResponse`

---

## 0. 前置条件：设备注册

**所有段评接口调用前必须先完成设备注册**，否则请求会被风控拦截。

### 0.1 设备注册（Device Registration）

```
POST https://log.snssdk.com/service/2/device_register/
```

**作用**：模拟番茄小说 App 首次启动，注册设备获取 `device_id` + `install_id` + `iid`。

**Body 提交设备信息**（`DeviceProfile`，16 个字段）：

| 字段 | 说明 | 示例值 |
|---|---|---|
| `device_brand` | 品牌 | OnePlus11 / Xiaomi / Huawei |
| `device_model` | 型号 | RMX1931 / MI 9 |
| `os_version` | 系统版本 | V291IR |
| `os_api` | API 等级 | 32 (Android 12) |
| `cpu_abi` | CPU 架构 | arm64-v8a |
| `rom_version` | ROM 版本 | V291IR+release-keys |
| `cdid` / `clientudid` | 设备 UUID | 17f05006-423a-4172-be4b-7d26a42f2f4a（硬编码种子） |
| `resolution` | 分辨率 | 1440x3040 |
| `dpi` | DPI | 560 |
| `manifest_version_code` | App 版本号 | 70132 |
| `device_platform` | 平台 | android |

**响应**：返回 `device_id`、`install_id`、`iid`（后续所有请求都需要）。

### 0.2 IID 激活

```
GET https://log.snssdk.com/service/2/app_alert_check/
```

激活上一步获取的 `iid`。

### 0.3 注册加密密钥（Register Key）

```
POST https://api5-normal-sinfonlinec.fqnovel.com/reading/crypt/registerkey
```

**作用**：获取 AES 解密密钥（正文内容加密用，段评可能也需要）。

**请求** `RegisterKeyRequest`（11 字段）：
- `key_register_ts`: Unix 时间戳
- `key_version`: 本地维护版本号
- `device_id`, `install_id`, `iid`: 设备三要素
- `device_platform`: "android"
- `aid`: "1967"
- 携带 `X-Argus` 签名头

**响应** `RegisterKeyResponse`：
```json
{
  "key": "base64_encoded_aes_key",
  "keyver": "版本号"
}
```

---

## 1. 段评 API 端点

### 1.1 获取段评统计（comment stats）

- **方法**：`POST`
- **路径**：`/novel/commentapi/comment/list`
- **完整 URL**：`https://api5-normal-sinfonlinec.fqnovel.com/novel/commentapi/comment/list`
- **RPC 对应**：`GetCommentListRequest`

#### 请求参数

**Rust 结构** `CommentStatsPayload`（6 字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `chapter_id` | String | 章节 ID |
| `aid` | String | 固定 `"1967"` |
| `install_id` | String | 设备注册所得 |
| `item_version` | String | 来自目录 API（`directory/all_items/v`） |
| `business_param` | String | 业务参数 |
| `comment_source` | String | 评论来源枚举值 |

#### 响应

JSON，由 `extract_para_counts_from_stats()` 解析（支持至少 5 种格式）：

**格式 1 — 对象映射**：
```json
{
  "0": {"count": 3},
  "1": {"count": 1}
}
```

**格式 2 — data 嵌套**：
```json
{
  "data": {
    "0": {"count": 3},
    "1": {"count": 1}
  }
}
```

**格式 3 — paras 包装**：
```json
{
  "paras": {
    "0": {"count": 3},
    "1": {"count": 1}
  }
}
```

**格式 4 — 数组形式**（`data_list` / `list` / `idea_list` / `ideas`）：
```json
{
  "data_list": [
    {"para_index": 0, "count": 3},
    {"para_index": 1, "count": 1}
  ]
}
```

**格式 5 — 多层嵌套**（`detail.data_list` / `detail.list` / `data.data_list`）

解析器按优先级依次尝试，直到解析到有效数据。

---

### 1.2 获取段评详情（idea list）

- **方法**：`POST`
- **路径**：`/novel/commentapi/idea/list`
- **完整 URL**：`https://api5-normal-sinfonlinec.fqnovel.com/novel/commentapi/idea/list`
- **RPC 对应**：`GetIdeaListRequest`

#### 请求参数

**Rust 结构** `CommentListPayload`（11 字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `chapter_id` | String | 章节 ID |
| `aid` | String | 固定 `"1967"` |
| `install_id` | String | 设备注册所得 |
| `item_version` | String | 来自目录 API |
| `business_param` | String | 业务参数 |
| `comment_source` | String | 评论来源（段评=2） |
| `comment_type` | String | 排序模式（sort_mode: 0 或 2） |
| `count` | u64 | 每段返回数量（`top_n`） |
| `group_type` | String | 分组类型 |
| `extra_query` | String | 额外查询参数 |
| `para_index` | i32 | **段落索引**（从 0 开始） |

> **重要**：每个段落需要**单独请求**此接口。不应在段落级别再开并发——已在章节级 worker 池内运行，段级并发极易触发 IP 风控。

#### 请求示例

```json
{
  "chapter_id": "7422333445566778899",
  "aid": "1967",
  "install_id": "7310102404588896783",
  "item_version": "1",
  "comment_source": "2",
  "comment_type": "2",
  "count": 10,
  "group_type": "15",
  "extra_query": "",
  "para_index": 0
}
```

#### 响应结构

**Rust 结构** `ReviewResponse`：

```rust
struct ReviewResponse {
    reviews: Vec<ReviewItem>,     // 评论列表
    meta: ReviewMeta,              // 段落元信息
}

struct ReviewMeta {
    para_content: Option<String>, // 段落原文（用于回链显示）
}
```

**ReviewItem（14 字段）**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `user` | `ReviewUser` | 用户信息 |
| `text` | String | 评论正文 |
| `images` | `Vec<ReviewImage>` | 评论图片列表 |
| `created_ts` | Option\<i64\> | 创建时间戳（可能含 ms 精度 >1e12） |
| `created_iso` | Option\<String\> | ISO 格式时间 |
| `digg_count` | i64 | 点赞数 |
| `reply_count` | i64 | 回复数 |
| `comment_id` | String | 评论 ID |
| `content_type` | String | 内容类型（"comment" / "expand"） |
| `extra` | String | 额外数据 |
| `total` | i64 | 总量 |
| `content` | | 评论内容 |
| `cursor` | | 翻页游标 |
| `comment` / `common` / `expand` | | 可能的分支字段 |

**ReviewUser（7 字段）**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | String | 用户 ID |
| `user_name` | Option\<String\> | 用户名 |
| `user_avatar` | Option\<String\> | 头像 URL |
| `gender` | Option\<String\> | 性别 |
| `image_data_list` / `image_data` | Option | 头像数据 |
| `user_title_info` | Option\<String\> | 用户称号 |
| `sticker` | Option | 贴纸 |

**ReviewImage（4 字段）**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `url` | String | 图片 URL |
| `height` | i64 | 图片高度 |
| `format` | String | 格式（"webp" / "jpg" / "png" / "gif"） |
| `web_url` | Option\<String\> | Web 访问 URL |

#### 响应示例

```json
{
  "reviews": [
    {
      "user": {
        "user_id": "123456789",
        "user_name": "读者A",
        "user_avatar": "https://p3-reading-sign.fqnovelpic.com/avatar/xxx"
      },
      "text": "这段写得真好！",
      "images": [],
      "created_ts": 1700000000000,
      "digg_count": 42,
      "reply_count": 3,
      "comment_id": "987654321",
      "content_type": "comment",
      "extra": ""
    }
  ],
  "meta": {
    "para_content": "原文段落内容..."
  }
}
```

---

## 2. 枚举值

### 2.1 comment_source（`UgcCommentSourceEnum`）

| 值 | 含义 |
|---|---|
| `0` | None |
| `1` | NovelBookComment |
| `2` | NovelParaComment（段评 ✅） |
| `3` | NovelParaCommentExposed |
| `4` | NovelItemComment |

### 2.2 comment_type / sort_mode

| 值 | 含义 |
|---|---|
| `0` | 默认排序 |
| `1` | SmartHot |
| `2` | TimeAsc（时间正序，优先尝试） |
| `3` | TimeDesc |

段评下载策略：优先尝试 `sort_mode=2`，失败则兜底 `sort_mode=0`。

### 2.3 group_type（`UgcRelativeType`）

| 值 | 含义 |
|---|---|
| `15` | Item（段评用） |

### 2.4 server_channel（`UgcCommentChannelEnum`）

| 值 | 含义 |
|---|---|
| `0` | None |
| `17` | NovelItemCount（用于统计请求） |
| `18` | NovelItemList（用于列表请求） |

---

## 3. 安全头 / 风控

所有请求必须携带以下签名头：

| 头 | 来源 | 说明 |
|---|---|---|
| `X-Argus` | **纯 Rust 实现**（非 JNI） | 反爬签名，基于 `ring::hmac` |
| `X-Ladon` | 同上 | 辅助签名 |
| `X-Khronos` | Unix 时间戳 | 防重放 |
| `x-ss-req-ticket` | Per-request | 请求票据 |
| `x-vc-bd` | App 版本 | 版本兼容性标识 |
| `x-tt-store-region` | `cn-zj` | 区域标识 |
| `x-xs-from-web` | 布尔值 | 标记来源 |
| `x-reading-request` | 布尔值 | 阅读请求标识 |
| `tt-data` | `a` | 设备数据参数 |
| `Cookie` | `install_id=; store-region=cn-zj` | 会话 |

### User-Agent

```
com.dragon.read.oversea.gp/70132 (Linux; U; Android 11; zh_CN; MI 9; Build/RQ3A;tt-ok/3.12.13.4-tiktok)
```

---

## 4. 完整调用流程

```
┌──────────────────────────────────────────────────────────────┐
│  1. Device Registration                                     │
│     POST log.snssdk.com/service/2/device_register/          │
│     → device_id + install_id + iid                          │
├──────────────────────────────────────────────────────────────┤
│  2. IID Activation                                          │
│     GET log.snssdk.com/service/2/app_alert_check/           │
├──────────────────────────────────────────────────────────────┤
│  3. Register Key                                            │
│     POST fqnovel.com/reading/crypt/registerkey              │
│     → AES key (用于解密 batch_full 正文)                     │
├──────────────────────────────────────────────────────────────┤
│  4. Fetch Directory                                         │
│     GET fqnovel.com/reading/bookapi/directory/all_items/v   │
│     → chapter_list + item_version(用于后续请求)              │
├──────────────────────────────────────────────────────────────┤
│  5. For each chapter (in worker pool, 串行 per-chapter):    │
│                                                             │
│  5a. Comment Stats                                          │
│      POST /novel/commentapi/comment/list                   │
│      → Map<para_index, count>                               │
│                                                             │
│  5b. For each para_with_comments (串行, 避免风控):          │
│      POST /novel/commentapi/idea/list                      │
│        ?para_index=N                                         │
│      → ReviewResponse(reviews, meta)                        │
│                                                             │
│  5c. Cache to disk: segment_comments/{chapter_id}.json      │
└──────────────────────────────────────────────────────────────┘
```

---

## 5. 已知问题与限制

1. **域名差异**：海外版 APK（`com.dragon.read.oversea.gp`）的签名引擎生成的 `X-Argus`，在大陆版 `api.fqnovel.com` 的 `commentapi` 路径上返回 `Code 110001 "未知异常"`。
   - 推荐域名：`api5-normal-sinfonlinec.fqnovel.com`（从 Rust 二进制确认）
   - `https://api5-normal-sinfonlinec.fqnovel.com/novel/commentapi/`

2. **段评必须 EPUB**：段评功能仅当输出格式为 EPUB 时启用（`progress.rs:123`）。

3. **并发控制**：段评请求量大（stats + 每段独立请求 + 媒体下载），必须控制并发：
   - 章节级 worker：1-8 线程（配置 `segment_comments_workers`）
   - 段落级：**串行**
   - 媒体下载 worker 在段评 worker 间均分

4. **item_version 依赖**：段评请求依赖目录 API 返回的 `item_version`，若无此值可退回到 `"0"`。

5. **软限流**：请求失败时 200ms 退避（`std::thread::sleep(Duration::from_millis(200))`）。

---

## 6. 缓存格式

下载的段评数据缓存为 JSON 文件：

```json
{
  "chapter_id": "7422333445566778899",
  "book_id": "7276384138653862966",
  "item_version": "1",
  "top_n": 10,
  "paras": {
    "0": {
      "count": 3,
      "detail": {
        "reviews": [...],
        "meta": { "para_content": "..." }
      }
    },
    "1": {
      "count": 1,
      "detail": null
    }
  }
}
```

- 缓存路径：`{book_folder}/segment_comments/{chapter_id}.json`
- 写入方式：原子写入（先写 `.part` 临时文件，再 `rename`）

---

## 7. fqnovel-unidbg 代理接口

以下为本地 unidbg 服务封装的段评代理接口：

### 7.1 段评统计

```bash
curl -X POST "http://127.0.0.1:8099/api/fqcomment/idea" \
  -H "Content-Type: application/json" \
  -d '{
    "chapterId": "7422333445566778899",
    "commentSource": 2
  }'
```

### 7.2 段评列表

```bash
curl -X POST "http://127.0.0.1:8099/api/fqcomment/list" \
  -H "Content-Type: application/json" \
  -d '{
    "chapterId": "7422333445566778899",
    "bookId": "7276384138653862966",
    "paraIndex": 0,
    "commentSource": 2,
    "commentType": 2,
    "groupType": 15,
    "count": 20
  }'
```

### 7.3 Legado 书源聚合接口

```bash
curl -X POST "http://127.0.0.1:8099/api/legado/comment" \
  -H "Content-Type: application/json" \
  -d '{
    "bookId": "7276384138653862966",
    "chapterId": "7422333445566778899",
    "paraIndex": 0,
    "count": 20,
    "cursor": ""
  }'
```

响应：
```json
{
  "comments": ["评论1", "评论2"],
  "commentCount": 2,
  "hasMore": false,
  "nextCursor": ""
}
```
