# fqnovel-unidbg

使用 unidbg 模拟番茄小说 Android 端 so 执行流程，提供签名、搜索、目录、章节、全本下载、设备管理等 API ，

本folk为设备池增强版，可进行设备池轮询调用与自动重试

## 项目简述

- 本项目基于 [anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server) 二次开发。
- 目标是复用 Android 端关键流程，实现番茄小说接口签名与数据获取。
- 当前包含小说查询与章节获取能力，并扩展了设备注册、设备池和全本下载能力。
- `src/main/resources/legado/fqnovel.json` 提供了阅读 3（Legado）书源配置示例。

## 主要能力

- 书籍搜索、目录查询、章节获取（单章/批量）
- 全本下载（流式返回、进度查询、自动恢复）
- 设备注册、配置更新、设备池管理
- FQ 签名生成接口
- Redis 缓存查看与删除接口

## 环境要求

- JDK 11+（`pom.xml` 编译目标为 1.8）
- Maven 3.6+
- Redis（用于缓存与部分功能支持，可不选择）
- Linux/macOS（脚本默认按类 Unix 环境编写）

## 快速开始

### 1) 配置参数

按需修改 `src/main/resources/application.yml`：

```yml
server:
  port: 8099

fq:
  api:
    device-pool:
      enabled: true
      size: 5

spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password: your-password
    database: 0
```

> 当前仓库默认端口配置为 `8099`（以 `application.yml` 为准）。
> 
> Redis按需配置，如不需要全本下载即可填写IP为0.0.0.0以禁用，但需忽略日志中的连接错误提示（？

### 2) 编译并启动

> 推荐使用 Java 11/17，Java 21及以上所产生的Bug一概不处理
>
> 参考自[anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server)

方式一（推荐，使用 Maven Wrapper）：

```bash
./mvnw package -DskipTests
java -jar target/unidbg-boot-server-0.0.1-SNAPSHOT.jar
```

方式二（确保本机已安装Maven）：

```bash
mvn package -T10 -DskipTests
java -jar target\unidbg-boot-server-0.0.1-SNAPSHOT.jar
```

方式三（编译为docker镜像，未试验）：参考[anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server)

## API 概览

### 1) 小说内容接口 `/api/fqnovel`

- `GET /book/{bookId}`：获取书籍信息
- `GET /chapter/{bookId}/{chapterId}`：获取单章内容
- `POST /chapter`：POST 方式获取单章
- `POST /chapters/batch`：批量获取章节内容
- `GET /health`：健康检查

示例：

```bash
curl -X POST 'http://127.0.0.1:8099/api/fqnovel/chapters/batch' \
  -H 'Content-Type: application/json' \
  -d '{
    "bookId": "6707112755507235848",
    "chapterIds": ["6707197312789119502"]
  }'
```

### 2) 搜索与目录接口 `/api/fqsearch`

- `GET /books`：搜索书籍
- `POST /books`：POST 方式搜索书籍
- `GET /directory/{bookId}`：获取目录
- `POST /directory`：POST 方式获取目录
- `GET /quick`：快速搜索
- `GET /chapters/{bookId}`：简化章节列表

### 3) 全本下载接口 `/api/fullbook`

> 注意：最好配置Redis缓存

- `GET /download`：GET 方式发起全本下载
- `POST /download`：POST 方式发起全本下载
- `POST /download/{bookId}`：简化下载
- `GET /chapters/{bookId}`：查看已下载章节
- `GET /progress/{bookId}`：查看下载进度
- `POST /auto-resume/{bookId}`：恢复指定书下载
- `POST /auto-resume-all`：恢复所有未完成任务
- `POST /trigger-auto-resume`：手动触发自动恢复任务
- `GET /check-all-status`：检查全部下载状态
- `DELETE /chapters/{bookId}`：删除已下载章节
- `GET /health`：健康检查

### 4) 设备管理接口 `/api/device`

- `POST /register`：注册设备
- `POST /update-config`：更新设备配置
- `POST /restart`：重启项目
- `POST /register-and-restart`：注册后自动重启
- `GET /current-config`：当前设备配置
- `GET /pool/status`：设备池状态
- `POST /pool/rebuild`：重建设备池
- `GET /health`：健康检查

### 5) 签名接口 `/api/fq-signature`

- `POST /generateSignature`
- `POST /generateSignatureWithMap`
- `POST /generateSignatureSimple`
- `GET /test`
- `GET /health`

### 6) Redis缓存接口 `/api/cache`

- `GET /book/{bookId}/info`
- `GET /book/{bookId}/chapters`
- `GET /keys?pattern=...`
- `GET /value?key=...`
- `GET /delete?key=...`

## 目录说明

- `src/main/java/com/anjia/unidbgserver/web/`：控制器接口层
- `src/main/java/com/anjia/unidbgserver/service/`：业务实现
- `src/main/resources/com/dragon/read/oversea/gp/`：unidbg 运行资源
- `src/main/resources/legado/fqnovel.json`：Legado 书源配置
- `tools/`：辅助脚本（设备注册、导出合并等）
- `docs/`：项目文档

## 注意事项

- 请避免高频调用单章接口（`/api/fqnovel/chapter/{bookId}/{chapterId}`），否则可能触发风控（虽然可以轮换设备，但是有封IP的可能行）。
- 建议优先使用批量章节接口（`/api/fqnovel/chapters/batch`）。
- 若用于阅读器，请控制预加载与缓存频率，降低风控概率。

## 相关文档

- `docs/FQNOVEL_API.md`：接口细节
- `docs/PROJECT_STATUS.md`：项目状态
- `docs/README.md`：文档索引
- `src/main/resources/legado/README.md`：Legado 说明

## 免责声明

本项目仅供学习交流使用。请在遵守当地法律法规及目标平台条款的前提下使用。使用者需自行承担由此产生的风险与责任，项目作者及贡献者不对任何直接或间接损失负责。

## 致谢

- [zhkl0228/unidbg](https://github.com/zhkl0228/unidbg)
- [anjia0532/unidbg-boot-server](https://github.com/anjia0532/unidbg-boot-server)
- [rudo-rs/fqnovel-api](https://github.com/rudo-rs/fqnovel-api)
