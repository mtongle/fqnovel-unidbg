# FQNovel Unidbg 项目状态文档

## 📊 项目概览

**项目名称**: fqnovel-unidbg
**当前分支**: main
**最新版本**: 0.0.5
**状态**: ✅ 稳定运行

## 🏗️ 项目架构

### 核心服务
- **FQNovelService**: 小说章节内容获取服务
- **DeviceManagementService**: 设备注册和管理服务
- **FQEncryptService**: FQ签名加密服务
- **FullBookDownloadService**: 全本下载服务

### API接口
- **端口**: 8099
- **基础URL**: http://127.0.0.1:8099

> 详细接口文档见 [README.md](../README.md) 与 [FQNOVEL_API.md](./FQNOVEL_API.md)

## 🚀 主要功能

### 1. 设备注册功能 ✅

**API端点**:
- `POST /api/device/register` - 设备注册
- `POST /api/device/register-and-restart` - 设备注册并重启
- `GET /api/device/health` - 设备管理服务健康检查

**功能特点**:
- 自动生成设备信息（品牌、型号、ID等）
- 支持自动重启服务
- 动态更新配置文件
- 错误处理和日志记录

### 2. 章节批量获取功能 ✅

**API端点**:
- `POST /api/fqnovel/chapters/batch` - 批量获取章节内容
- `POST /api/fqnovel/chapter` - 获取单章内容
- `GET /api/fqnovel/chapter/{bookId}/{chapterId}` - 获取单章内容
- `GET /api/fqnovel/book/{bookId}` - 获取书籍信息

**功能特点**:
- 支持章节范围批量获取
- 自动解密章节内容
- 自动处理非法访问恢复

### 3. 全本下载功能 ✅

**API端点**:
- `POST /api/fullbook/download` - 发起全本下载
- `GET /api/fullbook/progress/{bookId}` - 查询下载进度
- `POST /api/fullbook/auto-resume/{bookId}` - 恢复下载
- `POST /api/fullbook/auto-resume-all` - 恢复全部下载

**功能特点**:
- 流式下载，支持进度查询
- 断点续传
- 自动恢复

### 4. 搜索与目录功能 ✅

**API端点**:
- `GET /api/fqsearch/books` / `POST /api/fqsearch/books` - 搜索书籍
- `GET /api/fqsearch/directory/{bookId}` - 获取目录
- `GET /api/fqsearch/quick` - 快速搜索

### 5. 分类发现功能 ✅

**API端点**:
- `GET /api/fqnovel/category/front` - 分类发现页
- `GET /api/fqnovel/category/landing` - 分类下书籍列表
- `GET /api/fqnovel/category/cell` - 分类Cell数据

### 6. 签名生成功能 ✅

**API端点**:
- `POST /api/fq-signature/generateSignature` - 生成签名
- `POST /api/fq-signature/generateSignatureWithMap` - Map方式签名
- `POST /api/fq-signature/generateSignatureSimple` - 简化签名

### 7. 段评功能 ✅

**API端点**:
- `POST /api/fqcomment/idea` - 段评统计
- `POST /api/fqcomment/list` - 段评列表
- `POST /api/fqcomment/reply/list` - 段评回复列表
- `GET /api/ssr/comment-page` - SSR段评页面
- `GET /api/ssr/comment-replies` - SSR段评回复
- `POST /api/legado/comment` - Legado兼容段评

### 8. 管理后台 ✅

- **登录**: `POST /api/admin/auth`（密码通过环境变量 `APPLICATION_ADMIN_PASSWORD` 配置）
- **配置管理**: `GET/PUT /api/admin/config`
- **监控**: `GET /api/admin/monitor`
- **设备池管理**: `GET /api/admin/device-pool` 等
- **Web界面**: 访问 `http://127.0.0.1:8099/api/admin` 自动跳转登录页

## 🛠️ 技术栈

- **Java 17** + Spring Boot 2.7.18
- **Unidbg 0.9.8**（ARM 模拟）
- **Redis**（可选，全本下载/缓存功能需要）

## 📋 部署指南

### 前置条件

- JDK 17+
- Maven 3.6+ 或项目自带 `./mvnw`
- Redis（可选）

### 启动

```bash
# 方式一：Maven Wrapper（推荐）
./mvnw package -DskipTests
java -jar target/unidbg-boot-server-0.0.5.jar

# 方式二：快捷脚本
./bin/run.sh
```

### 敏感配置

- 管理后台密码：环境变量 `APPLICATION_ADMIN_PASSWORD`（不配置则 /api/admin/auth 不可用）
- Redis 密码：环境变量 `REDIS_PASSWORD`（不配置则按无密码连接）
- 设备参数由设备池自动生成并回写配置，无需手工维护

## 📝 测试

```bash
./mvnw verify
```

现有测试：SsrCommentServiceTest（43）、CommentEnrichmentServiceTest（15）、
FQNovelServiceRetryPolicyTest（5）、FQEncryptServiceTest（2，手动冒烟）、
SsrCommentControllerTest（4）。
