<p align="center">
  <img src="docs/app-icon.svg" width="112" alt="LightNovel LK 图标">
</p>

# LightNovel Android

轻之国度（`lightnovel.fun`）的非官方 Android 客户端。项目基于 2026-08-06 实测的站点 Web BFF/API 实现，使用 Kotlin、Jetpack Compose 和 Material 3。

[下载最新 Release APK](https://github.com/jiangyuyi/lightnovel-android/releases/latest)

> 本项目仅用于学习与个人使用，不隶属于轻之国度。请遵守站点规则和内容版权要求，不要批量抓取、分发或商业使用站点内容。

## 已实现

- 用户名/邮箱密码登录、邮箱验证码注册、会话恢复与退出。
- 热门、排行、新书、原创、同人、EPUB、最近更新分区。
- 搜索分类、标签筛选和书籍跳转。
- 书籍详情、同书其他版本、分卷和章节目录。
- 登录后加入/移出书架、我的书架。
- 登录用户个人概览：头像、UID、用户组、轻币、关注/粉丝/发布统计。
- 关注与粉丝列表、关系状态、分页加载及带确认的关注切换。
- 云端阅读记录、续读跳转和带确认的单条删除。
- 发布管理：作品状态、审核进度、卷章/字数和公开详情跳转。
- 正文阅读、上一章/下一章、目录返回、阅读进度保存。
- 默认按屏幕自动排版并左右翻页，支持点击左右区域或横向滑动；也可切回上下滚动。
- 按原站 `body_html` 解析正文插图，将 `[res]...[/res]` 对应为真实图片并按正文顺序展示。
- 无衬线/衬线/等宽字体，14–32sp 字号、行高、页边距和白色/米黄/护眼绿/深色背景。
- 书籍评论匿名只读展示；评论故障不会影响书籍详情和阅读。
- Android Keystore 加密保存 `security_key`；不保存密码和验证码。

网站的独立“合集”分区目前标记为维护中。本客户端按实际可用的数据实现“书籍 → 分卷 → 章节”三级目录，并展示 `alternate_versions`；合集页会显示维护说明，不调用猜测接口。

完整的 API 调研、合集/评论评估、架构与验收计划见 [实施计划](docs/IMPLEMENTATION_PLAN.md)；账户功能和 1.2.0 消息中心设计见 [账户与消息计划](docs/ACCOUNT_AND_MESSAGES_PLAN.md)。版本变更见 [CHANGELOG](CHANGELOG.md)。

## 截图

<p align="center">
  <img src="docs/screenshots/discover.png" width="250" alt="发现页">
  <img src="docs/screenshots/reader-illustration.png" width="250" alt="分页阅读与正文插图">
  <img src="docs/screenshots/reader-scroll.png" width="250" alt="上下滚动与护眼背景">
</p>

## 构建

要求：

- JDK 17
- Android SDK 35
- 无需安装全局 Gradle

Windows：

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

macOS/Linux：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

仓库优先使用阿里云的 Google Maven、Maven Central 和 Gradle Plugin Portal 镜像，并保留官方源回退。Gradle Wrapper 分发使用腾讯云镜像。若你的网络不能访问该镜像，可把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改回：

```text
https://services.gradle.org/distributions/gradle-8.9-bin.zip
```

Debug APK 生成于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release 签名

Release 构建强制要求签名，避免误发布未签名 APK。Windows 首次配置时运行：

```powershell
./scripts/setup-release-signing.ps1
```

脚本会在本机安全提示中读取签名密码，生成 `.signing/lightnovel-release.jks` 和 `signing.properties`，并通过已登录的 GitHub CLI 写入仓库 Actions Secrets。密码、私钥和本地签名配置均被 `.gitignore` 排除，不会提交到 Git。

请把 `.signing/lightnovel-release.jks` 与 `signing.properties` 离线备份；丢失签名密钥后将无法用相同应用 ID 发布可覆盖安装的更新。配置完成后可构建：

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

已签名 APK 生成于：

```text
app/build/outputs/apk/release/app-release.apk
```

### GitHub Release

`.github/workflows/release.yml` 会在推送 `v*` 标签时执行测试、Lint、签名构建、`apksigner` 验证，并发布 APK 与 SHA-256 校验文件：

```powershell
git tag v1.1.0
git push origin v1.1.0
```

也可以在 GitHub Actions 页面手动运行 `Android Release` 并填写版本标签。

## 当前验证结果

- `testDebugUnitTest`：通过，包含认证错误提示、正文插图、账户资料、用户关系、阅读记录和发布作品解析。
- `lintDebug`：通过。
- `assembleDebug`：通过。
- `assembleRelease`：使用独立 Release 密钥签名，并通过 `apksigner verify`。
- 小米 Android 16 真机已验证：首页/分区、图片加载、书籍详情、分卷章节、分页正文、点击/滑动翻页、上下滚动兼容模式、字体字号与背景设置、用户手动登录、进程重启后的会话恢复、书架、个人概览、3 个关注、0 粉丝空状态、多条阅读记录及 0 个发布作品空状态。
- 密码由用户在手机上手动输入；测试过程未读取、记录或保存密码。临时加入的测试书籍已移出，书架恢复原状。

## API 与隐私

应用只使用 HTTPS：

- Web BFF：`https://www.lightnovel.fun/api/pc-proxy/`
- 评论读取：`https://api.lightnovel.fun/pc-comment-proxy/`

站点没有为本项目提供稳定 SDK，因此 API 可能变化。API 与站点图片统一使用嵌入式 Cronet，优先建立 HTTP/3/QUIC 连接；遇到大陆网络上的可重试连接重置时会重建引擎并轮换备用 CDN 边缘地址。网络层同时集中处理响应信封、历史字段兼容和错误映射。Debug/Release 均不会记录密码、验证码或 `security_key`。

正文只用于当前阅读页面，不随 Git 提交，也不提供整本离线导出。游客阅读设置和位置保存在 DataStore；登录用户的书架、阅读进度和阅读设置会按站点 API 同步。

## 工程结构

```text
app/src/main/java/io/github/jiangyuyi/lightnovel/
├─ core/
│  ├─ data/          Repository
│  ├─ model/         书籍、分卷、章节、评论与阅读设置
│  ├─ network/       Cronet/QUIC、API 解包与兼容解析
│  ├─ preferences/   阅读偏好和本地进度
│  ├─ session/       Keystore 加密会话
│  └─ ui/            主题与通用组件
└─ feature/
   ├─ auth/
   ├─ book/
   ├─ bookshelf/
   ├─ discover/
   ├─ account/       关注、粉丝、阅读记录与发布管理
   ├─ profile/
   ├─ reader/
   └─ search/
```

## 已知限制

- 站点接口可能临时返回 5xx，页面提供错误提示和重试。
- 独立合集频道维护中；当前实现以分卷和同书版本覆盖实际阅读结构。
- 评论为只读，发布、回复、点赞、图片上传和举报未实现。
- EPUB 频道可以浏览；为避免批量下载与版权风险，首版不实现整本导出。
- 发布管理当前为只读作品状态视图，完整作者编辑工作台尚未接入。
- 动态、私信、发帖尚未接入；消息中心已按 1.2.0 版本单独规划。

## 许可证

客户端源代码使用 [MIT License](LICENSE)。站点内容、书籍正文、插图及轻之国度相关商标不因本许可证而改变其原有权利归属。
