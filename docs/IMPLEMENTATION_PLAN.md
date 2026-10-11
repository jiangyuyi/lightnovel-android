# 轻之国度 Android 客户端实施计划

## 1. 项目目标

实现一个面向 Android 的非官方轻之国度客户端，使用站点当前公开 Web BFF/API 提供以下能力：

- 邮箱注册、用户名/邮箱密码登录、会话恢复与退出登录。
- 首页推荐、排行、新书、原创、同人、EPUB、最近更新等分区展示与跳转。
- 搜索、标签/频道筛选、书籍详情、书籍不同版本信息。
- 书籍 → 分卷 → 章节三级目录；把站点当前可用的“分卷”作为合集型内容的核心承载。
- 章节阅读、上一章/下一章、目录跳转、阅读进度同步；默认左右分页并保留上下滚动模式。
- 解析 `body_html` 中原站已展开的插图 URL，把 `body_text` 的 `[res]...[/res]` 资源标记显示为正文插图。
- 字体、字号、行高、内容宽度、背景主题切换，并持久化本地设置。
- 登录后加入/移出书架、查看书架。
- 评论先实现匿名只读列表；发布、回复、点赞暂不纳入首版，避免在未充分验证风控和上传接口前产生写操作。

项目定位为学习与个人使用客户端，不绕过站点权限、付费、验证码或内容访问控制；不在仓库中保存用户密码、验证码、会话令牌、网页内容或书籍正文。

## 2. 调研结论

### 2.1 技术与访问方式

- 网站：`https://www.lightnovel.fun/`
- 公开 Web BFF 基址：`https://www.lightnovel.fun/api/pc-proxy/`
- 评论读取基址：`https://api.lightnovel.fun/pc-comment-proxy/`
- 请求以 `POST application/json` 为主，统一响应外层大致为 `{ code, data, message?, t? }`。
- 登录成功后响应包含 `auth.security_key`、`auth.uid` 与 `user`；站点 Web 端会把令牌写入 `localStorage/cookie`。Android 端改用加密偏好存储，不使用 Cookie 模拟网页会话。
- `code == 0` 视为业务成功；HTTP 成功但业务码非 0 仍需转换成可读错误。
- 图片来自 `api.lightnovel.fun`、`res.lightnovel.fun` 等 HTTPS 域名；大陆网络下与 API 一样通过 Cronet/QUIC 下载，再交给 Coil 解码和显示。
- `body_text` 保留历史 `[res]资源类型,资源编号[/res]` 标记，而 `body_html` 已把标记转换为带签名参数的 `<img src="...">`；客户端必须以 `body_html` 为插图映射依据，不能自行拼接资源 URL。

### 2.2 已验证的主要 API

下表中的路径均相对于 Web BFF 基址，除非另有说明。

| 能力 | 方法与路径 | 主要参数 | 实测/前端确认的主要返回 |
| --- | --- | --- | --- |
| 会话恢复 | `POST api/bff/auth-session-v1` | `security_key` | `logged_in`, `auth`, `user` |
| 密码登录 | `POST api/bff/auth-password-login-v1` | `username`, `password` | `auth.security_key`, `auth.uid`, `user` |
| 邮箱注册状态 | `POST api/bff/auth-email-status-v1` | `email` | `registered` |
| 发送注册验证码 | `POST api/bff/auth-email-register-captcha-send-v1` | `email` | 业务状态 |
| 邮箱注册 | `POST api/bff/auth-email-register-v1` | `email`, `captcha`, `code`, `nickname`, `password` | 登录态与用户信息 |
| 首页频道 | `POST api/bff/home-feed-v1` 等 | `page`, `page_size`, `read_filter`, `status_filter`, `category_filter`, 可选 `security_key` | `list/cards`, `pagination/page_info` |
| 原创频道 | `POST api/bff/home-original-feed-v1` | 同首页频道 | 书籍列表 |
| 同人频道 | `POST api/bff/home-fanfic-feed-v1` | 同首页频道 | 书籍列表 |
| EPUB 频道 | `POST api/bff/home-epub-feed-v1` | 同首页频道 | 书籍列表 |
| 最近更新 | `POST api/bff/home-recent-updates-feed-v1` | 同首页频道 | 书籍列表 |
| 排行 | `POST api/bff/book-rank-list-v1` | `rank_scene`（`weekly_hot` / `daily_fresh`）, `page`, `page_size` | `list`, `snapshot`, `pagination` |
| 搜索分类 | `POST api/bff/apk-search-taxonomy-v1` | `{}` | `channels`, `tabs/groups/sections/tag_items` |
| 搜索 | `POST api/bff/apk-search-result-v1` | `q`, `primary_tag`, `channel_code`, `work_type`, `preset`, `page`（0 起）, `pageSize`, `sort` 等 | `list`, `filters`, 分页信息 |
| 旧数字分区 | `POST api/search/get-article-by-cate` | `gid`, `parent_gid`, `page`, `pageSize`, `whitelist_only` | 文章/书籍列表 |
| 书籍详情 | `POST api/new-content-read/get-book-detail` | `book_id`, `with_volumes: 0`, 可选 `security_key` | 书籍、作者、简介、封面、统计、默认章节、不同版本 |
| 阅读入口 | `POST api/bff/reader-bootstrap-v1` | `book_id`, 可选 `security_key` | 默认/续读章节、书架状态、默认分卷、书籍摘要 |
| 分卷列表 | `POST api/new-content-read/get-book-volumes` | `book_id`, `page`, `page_size` | `list[].volume_id/title/chapter_count` |
| 分卷章节 | `POST api/new-content-read/get-volume-chapters` | `book_id`, `volume_id`, `page`, `page_size` | `list[].chapter_id/title/locked` |
| 章节正文 | `POST api/new-content-read/get-chapter-detail` | `book_id`, `chapter_id`, 可选 `security_key` | `body_snapshot.body_text/body_html`, 章节导航和发布者 |
| 查询书架状态 | `POST api/new-content-read/get-book-library-state` | `security_key`, `book_id` | `in_shelf`, 历史与进度 |
| 切换书架 | `POST api/new-content-read/toggle-book-shelf` | `security_key`, `book_id`, `action: add/remove`, `source: pc_web` | 操作状态；随后重新查询状态 |
| 我的书架 | `POST api/bff/bookshelf-v1` | `security_key`, `page`, `pageSize` | 书籍列表 |
| 保存阅读进度 | `POST api/new-content-read/save-book-history` | `security_key`, `book_id`, `volume_id`, `chapter_id`, `progress_percent`, `last_position`, `read_finished` | 操作状态 |
| 保存服务端阅读设置 | `POST api/bff/save-my-reader-settings-v1` | `security_key`, `font_size`, `line_height`, `theme`, `page_mode`, `traditional_chinese`, `updated_at` | 设置对象 |
| 只读评论 | 评论基址 `POST api/new-content-read/get-book-comments` | `book_id`, `volume_id`, `chapter_id`, `view`, `comment_id`, `page`, `pageSize`, 可选 `security_key` | `list`, `hots`, `root_comment`, `page_info` |

首页频道映射：

- 热门：`home-feed-v1`
- 新书：热门接口配合新书场景/排序，首版优先使用排行榜 `daily_fresh` 保持稳定。
- 原创：`home-original-feed-v1`
- 同人：`home-fanfic-feed-v1`
- EPUB：`home-epub-feed-v1`
- 最近更新：`home-recent-updates-feed-v1`
- 排行：`book-rank-list-v1`

### 2.3 “合集”与内容层级评估

网站导航目前显示“合集（维护中）”，没有发现可稳定消费的独立公开合集列表接口。当前稳定内容模型是：

```text
Book（书籍）
├─ alternate_versions（同书其他版本，可选）
└─ Volume（分卷，可分页，多卷）
   └─ Chapter（章节，可分页，含锁定状态）
```

因此首版策略为：

1. 目录页面按分卷折叠/展开，完整支持多卷书籍，这覆盖用户实际阅读所需的合集型结构。
2. 书籍详情展示 `alternate_versions`，允许跳转到同书其他版本。
3. 数据模型保留 `Collection`/`CollectionEntry` 扩展点；独立“合集”入口显示维护提示，不调用猜测接口。
4. 若后续站点恢复合集并暴露稳定 API，只需新增 Repository 与路由，不改动阅读核心。

### 2.4 评论评估

- 匿名读取已经验证可用，且使用独立评论代理域名。
- 发布、回复、点赞均要求 `security_key`，并涉及内容审核、提及、图片上传、评分与举报语义。
- 首版实现书籍级只读评论；章节级/分卷级评论的数据模型保留作用域字段。
- 评论写入不在首版范围，UI 明确显示“只读”。

## 3. Android 技术方案

### 3.1 技术栈

- Kotlin、Jetpack Compose、Material 3。
- 单 Activity + Navigation Compose。
- Coroutines + Flow；ViewModel 管理 UI 状态。
- 嵌入式 Cronet（HTTP/3/QUIC）+ Kotlinx Serialization；目前跟随系统 DNS 与当前 CDN，连接失败时有限尝试传输协议及经验证的只读备用代理，不固定 CDN IP。实际排障记录见 [网络与 CDN 排障](NETWORK_CDN.md)。
- Coil 加载网络图片。
- DataStore 保存普通阅读设置；Android Keystore 支持的加密存储保存 `security_key` 和 `uid`。
- 最低 Android 8.0（API 26），目标 SDK 35；Java 17。

### 3.2 模块与目录

首版采用单 `app` 模块、按功能分包，降低初始化与镜像下载成本：

```text
app/src/main/java/.../
├─ core/
│  ├─ network/       # API、响应解包、错误映射、日志脱敏
│  ├─ model/         # Book/Volume/Chapter/User/Comment
│  ├─ session/       # 加密令牌与会话状态
│  ├─ preferences/   # ReaderPreferences/DataStore
│  └─ ui/            # 主题、通用组件
├─ feature/
│  ├─ auth/
│  ├─ discover/
│  ├─ search/
│  ├─ book/
│  ├─ bookshelf/
│  ├─ reader/
│  └─ comments/
└─ MainActivity.kt
```

### 3.3 网络层策略

- 所有 API 请求统一走 HTTPS。
- 优先使用 HTTP/3/QUIC；对可重试的连接重置重建 Cronet 引擎并切换备用边缘地址，避免向用户暴露底层网络异常。
- 请求体使用已验证字段，不复刻网页私有签名逻辑，不直接访问未验证的内部源站。
- 响应模型对站点历史字段兼容：优先读取 snake_case，必要处为旧/新字段提供回退。
- 列表统一适配 `data.list`、`data.cards` 和分页 `pagination/page_info`。
- 正文只在内存和阅读进度中使用，不落盘缓存完整内容。
- 日志拦截器仅 Debug 开启，并永久屏蔽 `password`、`captcha`、`code`、`security_key`。
- 对 401/403、业务码、超时、DNS、服务端 5xx 分别映射；页面支持重试。

### 3.4 会话与安全

- 密码仅随登录/注册请求发送，不保存。
- `security_key` 和 `uid` 使用 Keystore 支持的加密存储。
- 启动时调用 `auth-session-v1` 恢复会话；无效时清除本地令牌并回到游客态。
- 退出登录清理本地会话。站点前端当前退出同样是本地清理，没有发现必要的服务端注销端点。
- 注册流程：邮箱 → 检查状态/发送验证码 → 验证码、昵称、密码 → 自动登录。

## 4. 页面与交互计划

### 4.1 主导航

- 发现：顶部频道 Tab（热门、新书、原创、同人、EPUB、更新）与排行入口。
- 书架：游客显示登录引导；登录后显示书架和最近阅读。
- 搜索：关键词、频道与标签筛选。
- 我的：登录/注册、当前用户、阅读设置说明、退出登录。

### 4.2 书籍详情

- 封面、标题、作者、简介、标签、章节/字数/评分统计。
- “开始阅读/继续阅读”根据 `reader-bootstrap-v1` 选择章节。
- “加入书架/移出书架”登录后可用；游客点击弹出登录页。
- 分卷目录按需加载章节；展示章节锁定状态。
- 同书其他版本存在时展示跳转卡片。
- 底部只读评论列表，失败不阻断详情和阅读。

### 4.3 阅读器

- 顶部：返回、书名/章名、目录。
- 正文：优先按顺序解析 `body_html` 的段落与 `<img>`；服务器生成的签名图片 URL 经 Cronet 加载。无 HTML 时读取 `body_text`，并隐藏无法可靠解析的原始 `[res]` 标记。
- 翻页：默认根据屏幕可用宽高、字体、字号、行高和页边距测量正文，拆分为固定屏幕页面；点击左/右 30% 区域或横向滑动翻页，到章节边界后进入上一章/下一章。
- 兼容：设置中可切换为传统上下滚动；两种模式共用段落级进度锚点，切换排版后保持大致阅读位置。
- 底部：上一章、下一章、阅读设置。
- 设置：
  - 翻页方式：左右翻页、上下滚动。
  - 字体：系统无衬线、系统衬线、等宽三档。
  - 字号：14–32sp。
  - 行高：1.2–2.2。
  - 内容宽度/页边距：紧凑、标准、宽松。
  - 背景：白、米黄、护眼绿、深色。
- 本地 DataStore 即时持久化；登录后以节流方式同步服务端设置。
- 翻页或滚动过程中按段落锚点防抖保存位置与百分比。
- 正文加载失败保留章节目录与重试入口。

## 5. 大陆网络与构建计划

`settings.gradle.kts` 中按以下顺序配置仓库：

1. 阿里云 Google Maven 镜像。
2. 阿里云 Maven Central 镜像。
3. 阿里云 Gradle Plugin Portal 镜像。
4. 官方 `google()`、`mavenCentral()`、`gradlePluginPortal()` 作为回退。

Gradle Wrapper 的 `distributionUrl` 优先使用可用的大陆 Gradle 分发镜像；如镜像不包含所选版本则回退官方分发地址。工程不依赖本机全局 Gradle。

## 6. 测试与验收

### 6.1 自动测试

- API envelope 成功/错误解包。
- Book、Volume、Chapter、Comment 的历史字段兼容解析。
- 频道 → endpoint 映射和分页参数。
- 阅读器正文清理、`[res]`/`body_html` 插图转换、上一章/下一章定位。
- 阅读偏好序列化与范围约束。
- Session 安全存储不保存密码，日志脱敏。

### 6.2 构建检查

- `./gradlew test`。
- `./gradlew lintDebug`（若 Android SDK 组件完整）。
- `./gradlew assembleDebug`。
- APK 安装/界面冒烟测试在存在模拟器或设备时执行；若本机无设备则在 README 中明确说明。

### 6.3 功能验收清单

- 游客可浏览各频道、排行、搜索、书籍与章节正文。
- 可完成邮箱注册 UI 全流程；发送验证码属于外部副作用，只由真实用户点击触发，自动测试不调用。
- 已有账户可登录，重启后恢复登录态，退出后清除会话。
- 登录后可加入/移出书架并刷新状态。
- 多分卷书籍能展开所有分卷并跳转章节。
- 阅读器可点击/滑动左右翻页，并能切换上下滚动、字体、字号、行高和四种背景，重启后仍保留。
- 带 `[res]` 的章节不显示原始标记，原站 `body_html` 中对应插图可加载并保持正文顺序。
- 登录用户阅读进度会同步，游客至少保留本地阅读位置。
- 评论读取失败不影响正文和书籍详情。

## 7. 实施顺序

1. 计划文档与 API 结论落盘（本文档）。
2. 初始化 Git 和 Android Compose 骨架，配置大陆镜像与 Wrapper。
3. 实现网络、模型、Repository、会话与偏好基础设施。
4. 实现发现/排行/搜索/书架主导航。
5. 实现书籍详情、不同版本、分卷与章节目录。
6. 实现阅读器与进度/设置同步。
7. 实现只读评论。
8. 添加测试、运行构建与静态检查、修复问题。
9. 完善 README（功能、构建、API 风险、隐私与免责声明）。
10. 检查差异，提交到 Git，创建 `jiangyuyi` 名下 GitHub 仓库并推送。

## 8. 风险与应对

- **非公开稳定 SDK**：API 可能变更。集中封装 endpoint 和兼容解析，UI 不直接依赖原始 JSON。
- **BFF 限流/风控**：不并发抓取大量正文；分页加载、请求去重、合理超时。
- **注册验证码**：只在用户明确点击后调用，不自动发送，不绕过验证码。
- **内容版权**：不批量下载、不提交正文样本、不提供离线整本导出。
- **合集尚未恢复**：按现有分卷/版本建模并保留扩展点，不调用猜测接口。
- **评论写入复杂**：首版只读，避免误发内容与审核风险。
- **国内依赖下载波动**：阿里云镜像优先、官方源回退、Wrapper 固定版本。
- **站点错误**：实测某些筛选组合可能返回 5xx；UI 需要降级到频道 Feed 并提供重试。

## 9. 完成定义

满足以下条件才视为本次任务完成：

- Android 工程可在 Java 17 环境通过 Debug 构建。
- 核心匿名浏览、登录/注册、书架、分卷目录、正文阅读和阅读设置均有完整实现。
- 只读评论可用或因接口故障以清晰降级呈现。
- 测试与构建结果记录在 README。
- Git 历史包含有意义的初始提交，远端仓库位于 `https://github.com/jiangyuyi/` 下并已推送。
