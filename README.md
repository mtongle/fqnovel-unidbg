# fqnovel-unidbg

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen?logo=spring" alt="Spring Boot 2.7.18">
  <img src="https://img.shields.io/badge/Unidbg-0.9.8-purple" alt="Unidbg 0.9.8">
  <img src="https://img.shields.io/badge/Maven-3.6%2B-red?logo=apachemaven" alt="Maven 3.6+">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
  <br>
  <img src="https://img.shields.io/github/last-commit/mtongle/fqnovel-unidbg?logo=git" alt="Last Commit">
  <img src="https://img.shields.io/github/commit-activity/m/mtongle/fqnovel-unidbg" alt="Commit Activity">
  <img src="https://img.shields.io/github/contributors/mtongle/fqnovel-unidbg" alt="Contributors">
  <img src="https://img.shields.io/github/repo-size/mtongle/fqnovel-unidbg" alt="Repo Size">
  <img src="https://img.shields.io/github/languages/top/mtongle/fqnovel-unidbg" alt="Top Language">
  <br>
  <img src="https://img.shields.io/github/stars/mtongle/fqnovel-unidbg?style=social" alt="Stars">
  <img src="https://img.shields.io/github/forks/mtongle/fqnovel-unidbg?style=social" alt="Forks">
  <img src="https://img.shields.io/badge/状态-活跃开发-success" alt="状态">
</p>

---

基于 **Unidbg** 模拟番茄小说（FQNovel）Android 端 Native so 执行流程的 Spring Boot 后端服务。通过 ARM 代码模拟技术，在服务端直接执行 `libmetasec_ml.so` 签名逻辑，生成 `X-SS-*` 签名头，实现番茄小说开放 API 的签名生成与数据获取。

本项目在 [anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server) 基础上进行了大量增强，核心聚焦于 **设备池化管理** 与 **自动化容错**，适用于需要长期稳定运行的爬取/阅读场景。

## ✨ 特性

- **Unidbg 签名模拟** — 服务端调用 `libmetasec_ml.so` 生成签名，无需真实 Android 设备
- **设备池轮询** — 多设备轮询调用，降低单设备风控概率
- **自动重试与恢复** — 下载任务自动恢复，断点续传，失败自动重试
- **全本下载** — 流式下载全书内容，支持进度查询与自动恢复
- **11 大 API 模块** — 搜索、目录、章节、签名、段评、设备管理、Admin 后台、SSR 段评页面等
- **段评全链路** — 统计→详情→回复分页→SSR 段评预览页 → 章节正文内联徽章
- **Legado 书源** — 可直接配置为阅读 3 书源，手机端无缝阅读
- **内置 Web 面板** — Admin 管理后台 + SSR 段评页面，开箱即用

## 🛠️ 技术栈

| 组件 | 选型 | 组件 | 选型 |
|------|------|------|------|
| 语言 | Java 17 | 核心引擎 | Unidbg 0.9.8 |
| 框架 | Spring Boot 2.7.18 | 构建工具 | Maven 3.6+ / Wrapper |
| 缓存 | Redis（可选） | 部署 | JAR / Docker |

## 🚀 快速开始

### 前置要求

- **JDK 17** — 与 CI 构建一致（Java 21+ 有兼容问题不处理）
- **Maven 3.6+** 或项目自带的 `./mvnw`
- **Redis** — 可选，全本下载等功能需要

### 配置 & 启动

按需修改 `src/main/resources/application.yml`：

```yml
server:
  port: 8099
  address: 0.0.0.0

application:
  unidbg:
    dynarmic: false       # 是否启用 dynarmic 后端
    verbose: false        # unidbg 详细日志（生产关闭）
    async: true           # 异步签名 WorkerPool

fq:
  api:
    base-url: https://api5-normal-sinfonlineb.fqnovel.com
    device-pool:
      enabled: true
      size: 5

spring:
  redis:
    host: 127.0.0.1       # 不需要全本下载可设为 0.0.0.0 禁用
    port: 26586
```

```bash
# 方式一：Maven Wrapper（推荐）
./mvnw package -DskipTests
java -jar target/unidbg-boot-server-0.0.5.jar

# 方式二：本机 Maven
mvn package -T10 -DskipTests && java -jar target/unidbg-boot-server-0.0.5.jar

# 方式三：快捷脚本
./bin/run.sh
```

> **敏感配置**：管理后台密码通过环境变量 `APPLICATION_ADMIN_PASSWORD` 注入（不配置则 `/api/admin/auth` 不可用）；
> Redis 密码通过 `REDIS_PASSWORD` 注入（不配置则按无密码连接）。设备参数由设备池自动生成并回写配置，无需手工维护。

> Docker 方式参考 [anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server)。

## 📡 API 概览

服务默认启动在 `http://127.0.0.1:8099`，共 **11 个路由前缀**：

### 小说内容 `/api/fqnovel`

| 端点 | 说明 |
|------|------|
| `GET /book/{bookId}` | 书籍信息 |
| `GET /chapter/{bookId}/{chapterId}` | 单章内容 |
| `POST /chapter` | POST 单章 |
| `GET /chapter/enriched/{bookId}/{chapterId}` | ⭐ **段评增强章节**（正文内联评论徽章） |
| `GET /comment-badge/{count}` | ⭐ **评论数徽章**（PNG 图片，供增强章节内联引用） |
| `POST /chapters/batch` | ⭐ **批量章节（推荐）** |
| `GET /health` | 健康检查 |

```bash
curl -X POST 'http://127.0.0.1:8099/api/fqnovel/chapters/batch' \
  -H 'Content-Type: application/json' \
  -d '{"bookId":"6707112755507235848","chapterIds":["6707197312789119502"]}'
```

### 搜索与目录 `/api/fqsearch`

| 端点 | 说明 |
|------|------|
| `GET /books` / `POST /books` | 搜索书籍 |
| `GET /directory/{bookId}` / `POST /directory` | 目录查询 |
| `GET /quick` | 快速搜索 |
| `GET /chapters/{bookId}` | 简化章节列表 |

### 全本下载 `/api/fullbook`

| 端点 | 说明 |
|------|------|
| `GET|POST /download` | 发起下载 |
| `POST /download/{bookId}` | 简化下载 |
| `GET /chapters/{bookId}` | 已下载章节 |
| `GET /progress/{bookId}` | 下载进度 |
| `POST /auto-resume/{bookId}` | 恢复下载 |
| `POST /auto-resume-all` | 恢复全部 |
| `POST /trigger-auto-resume` | 触发自动恢复 |
| `GET /check-all-status` | 全部状态 |
| `DELETE /chapters/{bookId}` | 删除章节 |

> 建议配置 Redis 以获得更好的下载体验。

### 设备管理 `/api/device`

| 端点 | 说明 |
|------|------|
| `POST /register` | 注册设备（空参数随机生成） |
| `POST /update-config` | 更新配置 |
| `POST /restart` | 重启进程 |
| `POST /register-and-restart` | 注册后重启 |
| `GET /current-config` | 当前配置 |
| `GET /pool/status` | 设备池状态 |
| `POST /pool/rebuild` | 重建设备池 |

### 签名生成 `/api/fq-signature`

调用 unidbg 模拟 `libmetasec_ml.so` 生成签名。

| 端点 | 说明 |
|------|------|
| `POST /generateSignature` | 生成签名 |
| `POST /generateSignatureWithMap` | Map 方式 |
| `POST /generateSignatureSimple` | 简易签名 |
| `GET /test` | 测试 |

### Redis 缓存 `/api/cache`

| 端点 | 说明 |
|------|------|
| `GET /book/{bookId}/info` | 缓存书籍信息 |
| `GET /book/{bookId}/chapters` | 缓存章节 |
| `GET /keys?pattern=...` | 查询缓存键 |
| `GET /value?key=...` | 查看缓存值 |
| `GET /delete?key=...` | 删除缓存 |

### 段评查询 `/api/fqcomment`

> `aid`/`iid` 由服务端自动获取，`itemVersion` 固定为 `"1"`，无需传入。

| 端点 | 说明 |
|------|------|
| `POST /idea` | 段评统计（各段落评论数） |
| `POST /list` | 段评详情（具体评论内容） |
| `POST /reply/list` | 段评回复列表 |

```bash
# 段评统计
curl -X POST 'http://127.0.0.1:8099/api/fqcomment/idea' \
  -H 'Content-Type: application/json' \
  -d '{"chapterId":"6707197312789119502"}'

# 段评详情
curl -X POST 'http://127.0.0.1:8099/api/fqcomment/list' \
  -H 'Content-Type: application/json' \
  -d '{"chapterId":"6707197312789119502","bookId":"6707112755507235848","paraIndex":0}'

# 段评回复列表
curl -X POST 'http://127.0.0.1:8099/api/fqcomment/reply/list' \
  -H 'Content-Type: application/json' \
  -d '{"commentId":"...","bookId":"...","chapterId":"...","count":5}'
```

### SSR 段评页面 `/api/ssr`

服务端渲染的段评预览页面，可直接在浏览器中访问，支持明暗主题切换。

| 端点 | 说明 |
|------|------|
| `GET /comment-page?bookId=&chapterId=&paraIndex=` | 段评列表页（HTML） |
| `GET /comment-replies?commentId=&...` | 段评回复片段（HTML，支持分页） |

章节正文中的 **段评增强**（`GET /api/fqnovel/chapter/enriched/{bookId}/{chapterId}`）会在有评论的段落末尾自动插入评论数徽章，点击跳转到对应的段评预览页。

```bash
# SSR 段评页面（浏览器访问）
curl 'http://127.0.0.1:8099/api/ssr/comment-page?bookId=...&chapterId=...&paraIndex=0'
```

### Admin 管理后台 `/api/admin`

提供 Web 管理页面（访问 `/api/admin` 自动重定向）。

| 端点 | 说明 |
|------|------|
| `GET /config` / `PUT /config` | 查看/更新配置 |
| `POST /refresh` | 热重载 |
| `POST /restart` | 重启项目 |
| `GET /monitor` | JVM 监控 |
| `GET /device-pool` | 设备池状态 |
| `POST /device-pool/rebuild\|remove\|add` | 设备池管理 |

### Legado 书源段评聚合 `/api/legado`

适配 [Legado（阅读3）](https://github.com/gedoor/legado) 格式的段评聚合接口。

| 端点 | 说明 |
|------|------|
| `POST /comment` | 聚合统计+详情，返回 Legado 兼容格式 |

```bash
curl -X POST 'http://127.0.0.1:8099/api/legado/comment' \
  -H 'Content-Type: application/json' \
  -d '{"bookId":"7276384138653862966","chapterId":"7422333445566778899","paraIndex":0,"count":20}'
```

```json
{"comments":["评论1","评论2"],"commentCount":2,"hasMore":false,"nextCursor":""}
```

## 📂 目录结构

```
src/main/java/com/anjia/unidbgserver/
├── web/          — Controller（12 个，对应 11 个路由前缀）
├── service/      — Service（19 个）
├── unidbg/       — Unidbg 核心引擎（IdleFQ）
├── config/       — Spring 配置类
├── dto/          — 请求/响应 DTO
└── utils/        — 工具类

src/main/resources/
├── com/dragon/read/oversea/gp/  — Unidbg 运行时资源（APK、so、rootfs）
├── legado/fqnovel.json          — Legado 书源配置
├── static/
│   ├── admin/          — Admin 管理页面
│   ├── dashboard.html  — 公开面板（仪表盘）
│   ├── search.html     — 搜索页
│   ├── reader.html     — 阅读页
│   ├── books.html      — 书籍列表页
│   ├── comments.html   — 段评页
│   ├── download.html   — 全本下载页
│   ├── api.html        — API 文档页
│   ├── index.html      — 首页
│   ├── css/            — 段评页面样式
│   ├── js/             — 段评页面脚本
│   └── img/            — 静态图片资源
├── application.yml   — 主配置

bin/       — 启动与管理脚本
docs/      — 项目文档
results/   — 全本下载输出
```

| 文件 | 说明 |
|------|------|
| [`CHANGELOG.md`](CHANGELOG.md) | 版本历史与更新日志 |
| [`AGENTS.md`](AGENTS.md) | 项目架构与 AI 开发指南 |
| [`API.md`](API.md) | 段评接口详细文档 |

## ⚠️ 注意事项

- **避免高频调用单章接口**，优先使用 `POST /chapters/batch` 批量接口
- 设备池可轮换设备降低风控，但仍有 IP 被封风险
- 若用于阅读器，请控制预加载与缓存频率
- `application.unidbg.verbose=true` 会开启详细日志，**极慢**，生产务必关闭
- `restart.sh` 脚本已移至 `bin/restart.sh`，使用相对路径定位项目根目录
- SSR 段评页面可通过明暗主题切换按钮调整阅读配色

## 📜 更新日志

> 完整版本历史请参阅 [CHANGELOG.md](CHANGELOG.md)。

## 📖 相关文档

| 文档 | 说明 |
|------|------|
| [`AGENTS.md`](AGENTS.md) | 项目架构与 AI 开发指南 |
| [`API.md`](API.md) | 段评接口详细文档 |
| [`docs/FQNOVEL_API.md`](docs/FQNOVEL_API.md) | 接口细节 |
| [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) | 项目状态 |
| [`docs/README.md`](docs/README.md) | 文档索引 |
| [`legado/README.md`](src/main/resources/legado/README.md) | Legado 书源说明 |

## 👥 贡献者

<p align="center">
  <a href="https://github.com/mtongle"><img src="https://github.com/mtongle.png?size=64" width="64" height="64" alt="mtongle" style="border-radius:50%;object-fit:cover"></a>&nbsp;&nbsp;
  <a href="https://github.com/zhangyuming"><img src="https://github.com/zhangyuming.png?size=64" width="64" height="64" alt="zhangyuming" style="border-radius:50%;object-fit:cover"></a>&nbsp;&nbsp;
  <a href="https://github.com/divo-s"><img src="https://github.com/divo-s.png?size=64" width="64" height="64" alt="divo-s" style="border-radius:50%;object-fit:cover"></a>
</p>

> 欢迎提交 PR 和 Issue！贡献者列表随仓库动态更新，如有遗漏请补充。

## 🙏 致谢

- [zhkl0228/unidbg](https://github.com/zhkl0228/unidbg) — Android ARM 代码模拟引擎
- [anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server) — 本项目基础框架
- [rudo-rs/fqnovel-api](https://github.com/rudo-rs/fqnovel-api) — Rust 版番茄小说 API 参考

## 📄 免责声明

本项目**仅供学习交流使用**。请在遵守当地法律法规及目标平台服务条款的前提下使用。使用者需自行承担由此产生的风险与责任，项目作者及贡献者不对任何直接或间接损失负责。
