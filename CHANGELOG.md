# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 新增 `FQCryptoTest` 加解密单元测试（round-trip / registerkey / gzip / hex 校验）
- 新增全局 `GlobalExceptionHandler` 统一错误响应结构（缺失参数/类型错误 → 400）

### Security
- `AdminAuthFilter`：URL 解码防 `/api/%61dmin` 编码绕过、fail-closed 校验、令牌 24h TTL + 过期清理、兼容尾斜杠
- `AdminController`：移除 `admin123` 默认密码（改为必填环境变量 `APPLICATION_ADMIN_PASSWORD`）、常量时间密码比较、`/config` 返回敏感键脱敏
- `application.yml`：删除 Redis 明文密码（改 `REDIS_PASSWORD` 注入）、host 0.0.0.0→127.0.0.1、删除幽灵 `profiles.active=prod`
- 全 service 签名结果含 `error` 键时中止（原把 error 当 HTTP header 静默发出）
- `LoggingAspect`：query string 敏感参数脱敏、POST body 经 `ContentCachingRequestWrapper` 真实读取

### Fixed
- `FQNovelService`：批量章节 `dataMap.get(itemIds.get(0))` 双重 NPE 判空
- `FullBookDownloadService`：章节排序按 chapterIndex（原按标题字典序）、Flux 取消检查、除零保护
- `FQEncryptServiceWorker`：线程池耗尽时无限忙等改有限次重试
- `CommentEnrichmentService`：段评图标段落索引与 API `para_index` 对齐（空段/标题行错位修复）
- `RedisService.deleteBook`：补删 novel 前缀孤儿 key
- `DeviceManagementService`：移除 `System.exit(0)` 自杀式重启、`registerDeviceAndRestart` 补齐 autoRestart 行为
- `FQRegisterKeyService`：方法级 synchronized 改设备维度分段锁
- 搜索/分类接口：count/limit/offset 范围校验、quickSearch 补默认 tabType

### Changed
- 构建升级：Spring Boot 2.6.3→2.7.18、spring-cloud 2021.0.7→2021.0.9、Java 11→17
- 命名统一：6 个 `Fq` 前缀类重命名为 `FQ`（`git mv` 保留历史）
- DTO 清理：`FQRegisterKeyPayload`/`FQBatchFullResponse` 内嵌加密/解密逻辑移至 service 层
- `bin/restart.sh`：端口 9999→8099、`mvn`→`./mvnw`
- 删除 jib 死配置、SleuthAsyncConfig 无引用 Bean、断链符号链接、模板文件与遗留日志
- `FQEncryptController`：`.get()` 阻塞改返回 CompletableFuture

## [v0.0.5] - 2026-07-17

### Added
- feat(admin): 重构管理后台，新增9个功能页面和公共JS库
- feat(tracing): configure Sleuth async MDC propagation via ThreadPoolTaskExecutor
- feat(log): add AOP logging aspect with token masking and truncation
- feat(log): add colored console appender with traceId pattern and Sleuth sampler config
- chore(deps): add spring-cloud-sleuth 3.1.x, spring-boot-starter-aop, spring-cloud BOM 2021.0.7

### Fixed
- fix(frontend): comprehensive frontend fix — CSS variable, sidebar, nav, return links
- fix(security): 修复 SnakeYAML RCE、API 未授权访问、客户端认证绕过
- fix(log): remove e.printStackTrace(), i18n CacheController errors, add missing @Slf4j

### Changed
- refactor: 清理代码并优化前后端
- refactor(log): standardize log levels — reduce excessive INFO, fix empty catch blocks
- refactor(log): remove 40 isDebugEnabled() guards across controllers

## [v0.0.4] - 2026-06-25

### Added
- SSR段评页面评论图片展示：解析 API 返回的 content.image_data_list.image_data[]
- 分类发现/落地页 API: GET /api/fqnovel/category 系列端点
- Legado 书源新增分类支持（男生/女生/出版/有声书）
- 管理后台界面重构与优化

### Fixed
- SSR段评回复"加载更多"按钮卡住问题，改造 loadMoreReplies 逻辑
- 段评详情页头像 http → https 统一，消除混合内容警告
- 仅有图片无文字的评论也能正常渲染
- renderCommentPage 添加 exception 异常兜底

### Changed
- 图片处理改进：评论头像通过 processAvatars() 主动 fetch+heic2any 转换HEIC
- 回复头像异步加载后调用 processAvatars() 处理，保留 onerror 兜底
- 评论内联图片用 onerror=fixHeicImg(this) 按需转换，避免全量转换浪费
- 移除全局 error 监听器，改用每个 `<img>` 独立 onerror 处理
- TempFileUtils: HashMap → ConcurrentHashMap，处理并发创建临时文件
- FQApiUtils: 支持可选的搜索API域名
- 设备池: IdleFQ 重置时 TempFileUtils 缓存保护
- .gitignore: 添加 .sisyphus/ 和 fqnovel-unidbg.jar

## [v0.0.3] - 2026-06-25

### Added
- 段评增强章节内容：章节正文段落末尾自动插入评论数徽章
- SSR 段评预览页面：服务端渲染的段评列表页，支持明暗主题、展开全文、回复分页
- SSR 段评回复：支持分页加载回复，每页 5 条，滚动加载
- 图片代理：服务端直转图片，HEIC→JPEG 自动转换
- comment reply/list API endpoint
- enriched chapter content endpoint with comment badges

### Fixed
- IdleFQ重置时因TempFileUtils缓存被清空导致资源文件提取失败
- 固定段评SVG按钮高度+自适应宽度，回复加载后自动滚动
- force https avatar URLs and improve reply display
- update comment JSON field paths and fix error handling
- 添加遗漏的静态资源和工具类文件

### Changed
- 合并书源为段评增强版，移除冗余文件
- 优化 README，添加动态徽章、贡献者头像栏和正式介绍
- 更新 README，补充 SSR 段评页面、图片代理、段评增强等新模块

### Test
- 添加 SSR parser 和 rendering 单元测试

## [v0.0.2] - 2026-05-23

### Added
- 段评接口支持，适配扁平响应格式
- 设备池增强

### Fixed
- 更新段评DTO字段，移除无效serverChannel参数
- 修正段评API上游路径和请求参数
- add .gitattributes for LFS tracking and enforce git lfs pull in workflow

### Changed
- Java 11 升级
- 全面文档更新

## [v0.0.1] - 2026-05-04

### Added
- 项目初始化：基于 unidbg 的 FQNovel 模拟签名引擎
- 签名结果 Caffeine 本地缓存，去重 Map 重载逻辑
- HTTP 连接池、Caffeine 缓存依赖及统一线程池配置
- 设备池功能：设备注册、管理与自动重启
- 全本下载 + Redis 缓存查看 API
- 全文导出工具与缓存合并脚本
- 正文接口增加重试与自动刷新 RegisterKey
- 自动检查任务进度和错误处理优化
- 扩展 API 接口返回所有字段
- GitHub Actions 自动发布工作流，附带 Legado 书源模板和预配置版本

### Fixed
- Legado 书源配置增强，支持备用字段与纯数字 bookId 识别
- ILLEGAL_ACCESS 三重恢复机制（signer 重置 + 测试覆盖）
- totalChapters 字段显示问题及多种 fallback 策略
- 章节目录大小用于 totalChapters；null-safe maxChapters
- rticket 初始化错误
- 设备注册并重启接口优化

### Changed
- Service 层统一依赖注入，Redis KEYS 改用 SCAN
- 添加 Maven Wrapper、运行脚本、AI Agent 文档及 .gitignore 更新
- 设备注册接口改为仅注册+写入配置，无内部重启
- 修复 folk → fork 拼写错误
- 优化设备注册并重启接口
