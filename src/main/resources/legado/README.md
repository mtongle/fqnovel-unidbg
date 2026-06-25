# FQNovel Legado 书源配置

本目录包含用于 @gedoor/legado 阅读3 的 FQNovel API 书源配置文件。

## 书源文件

### fqnovel.json
- **名称**: FQNovel-unidbg
- **类型**: 段评增强版 (SSR)
- **功能**: 支持搜索、发现、详情、目录、章节内容、段评徽章嵌入
- **特点**: 章节正文段落末尾自动显示评论数徽章，点击半屏弹出段评预览页；内置 `jsLib` 实现段评弹窗

## 使用方法

### 1. 启动 FQNovel API 服务
```bash
# 确保服务在 localhost:8099 运行（默认端口）
java -jar target/unidbg-boot-server-0.0.3-SNAPSHOT.jar
```

### 2. 导入书源到 Legado
1. 打开 Legado 阅读 APP
2. 进入「书源管理」
3. 选择「导入书源」 
4. 复制对应的 JSON 配置文件内容
5. 粘贴并导入

### 3. 书源配置说明

#### API 端点映射
- **搜索**: `/api/fqsearch/books` 
- **书籍详情**: `/api/fqnovel/book/{bookId}`
- **书籍目录**: `/api/fqsearch/directory/{bookId}`
- **章节内容（段评增强）**: `/api/fqnovel/chapter/enriched/{bookId}/{chapterId}`
- **批量章节**: `/api/fqnovel/chapters/batch`

#### 关键参数
- `bookId`: 书籍唯一标识
- `itemId`: 章节唯一标识  
- `chapterRange`: 章节范围 (如 "1-30")
- `chapterIds`: 章节 ID 列表 (如 [
  "7271262165057667646",
  "7271262274424144446"
  ])
- `query`: 搜索关键词
- `offset`: 分页偏移量
- `count`: 每页数量

### 3. 段评接口（书源专用聚合）
- **接口**: `POST /api/legado/comment`
- **请求体**:
  - `bookId` 书籍ID
  - `chapterId` 章节ID
  - `paraIndex` 段落索引（从1开始）
  - `count` 可选，默认20
  - `cursor` 可选，分页游标
- **返回结构**:
  - `data.comments`: 段评文本数组
  - `data.commentCount`: 本次返回数量
  - `data.hasMore`: 是否有下一页
  - `data.nextCursor`: 下一页游标


### 修改服务地址
如果 FQNovel API 服务部署在其他地址，需要修改以下字段:
- `bookSourceUrl`
- `exploreUrl` 
- `searchUrl`
- `ruleBookInfo.tocUrl`
- `ruleExplore.bookUrl`
- `ruleSearch.bookUrl`

> 从 Release 下载的 `legado-fqnovel-template.json` 使用占位符域名，
> `legado-fqnovel-preconfigured.json` 预设为 `localhost:8099`，
> 也可直接编辑 `bookSourceUrl` 为你的实际部署地址。