# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 提供在此代码库中工作的指导。

## 语言规范
- 所有对话和文档都使用中文
- 注释使用中文
- 错误提示使用中文
- 文档使用中文 Markdown 格式
- **文件命名规范：**
  - 文件名必须使用英文，避免中文编码导致乱码
  - 推荐使用 PascalCase 或 snake_case 命名风格
  - 示例：`PopupActivity_BusinessLogic_Analysis.md` 而非 `PopupActivity_业务逻辑分析.md`

## 开发语言
- 尽量使用Java语言

## 项目概述

AnkiHelper (Anki 划词助手) 是一个 Android 应用程序，与 AnkiDroid 集成，通过文本选择创建抽认卡。它帮助语言学习者从多种来源（剪贴板、Chrome、FBReader）捕获单词和上下文，并自动生成带词典定义的 Anki 卡片。

## 构建命令

### 构建项目
```bash
# 构建 debug APK
gradlew.bat assembleDebug    # Windows
./gradlew assembleDebug      # Linux/macOS

# 构建 release APK（需要签名配置）
gradlew.bat assembleRelease
```

### 运行测试
```bash
# 运行单元测试
gradlew.bat testDebugUnitTest

# 运行 Android 设备测试（需要连接设备/模拟器）
gradlew.bat connectedAndroidTest
```

### 安装
```bash
# 安装 debug 版本到已连接的设备
gradlew.bat installDebug
```

## 架构设计

### 包结构
主应用代码位于 `app/src/main/java/com/mmjang/ankihelper/`：

- **ui/** - UI 组件（Activities、Fragments、Dialogs）
  - `LauncherActivity` - 主设置入口
  - `PopupActivity` - 用于单词选择和卡片创建的悬浮弹窗
  - 各种设置、方案、统计等活动页面

- **data/** - 数据层
  - `dict/` - 词典实现（11 个内置词典）
  - `database/` - 使用 SQLiteAssetHelper 和 LitePal ORM 的数据库辅助类
  - `plan/` - 输出方案配置（将词典字段映射到 Anki 笔记类型）
  - `model/` - 各种实体的数据模型

- **anki/** - AnkiDroid API 集成
  - `AnkiDroidHelper` - AnkiDroid API 的封装类

- **domain/** - 业务逻辑
  - `CBWatcherService` - 剪贴板监控前台服务
  - 音频发音管理器

- **util/** - 工具类（HTTP、翻译、文件处理）

### 核心架构模式

**MVP (Model-View-Presenter)：** 大部分活动遵循 MVP 模式，Presenter 类处理与 UI 代码分离的业务逻辑。

**面向服务：** 剪贴板监控作为前台服务（`CBWatcherService`）运行，即使应用在后台也能检测文本变化。

**仓储模式：** 数据访问通过数据库辅助类和词典实现进行抽象。

**插件式词典系统：** 每个词典实现通用接口，便于添加新词典。11 个内置词典包括本地（牛津、柯林斯）和在线（韦氏、Vocabulary.com 等）选项。

### 多源文本捕获

应用支持多种文本捕获方法：
1. **剪贴板监控** - 自动检测复制的文本
2. **Chrome 上下文菜单** - 通过 Android 文本选择框架
3. **FBReader 集成** - 定制版 FBReader 直接集成
4. **分享意图** - 从任何应用通用文本分享

### 输出方案系统

"方案"（Plans）是定义以下内容的配置：
- 使用的词典
- 目标 Anki 牌组和笔记类型
- 字段映射（哪个词典输出对应哪个 Anki 字段）
- 关联的标签

这允许用户在不同卡片创建工作流之间快速切换（例如"小说用柯林斯" vs "新闻用韦氏"）。

## 配置说明

### 版本信息
定义在根目录 `build.gradle` 中：
- `VERSION_NAME`: "2.30.7"
- `VERSION_CODE`: 151
- 目标 SDK: 28 (Android 9 Pie)
- 最低 SDK: 19 (Android 4.4 KitKat)
- 编译 SDK: 29 (Android 10)

### 主要依赖
关键库：
- **UI:** Android Support Library v28, Material Design 组件
- **数据库:** SQLiteAssetHelper, LitePal ORM
- **网络:** OkHttp 3.12.1, Jsoup 用于 HTML 解析
- **图片加载:** Glide 4.8.0
- **时间处理:** ThreeTenABP
- **图表:** MPAndroidChart 用于统计

### Maven 仓库
项目使用阿里云镜像以在中国更快构建：
```groovy
maven { url 'https://maven.aliyun.com/repository/google' }
maven { url 'https://maven.aliyun.com/repository/jcenter' }
```

## 重要实现细节

### AnkiDroid 集成
应用通过 AnkiDroid API (`com.ichi2.anki.api`) 进行集成。`AnkiDroidHelper` 类封装了此 API。部分手机限制应用间通信，可能导致添加卡片时崩溃。常见解决方法：保持 AnkiDroid 在后台运行。

### 词典数据
- 本地词典（牛津、柯林斯）作为 SQLite 资源打包
- 在线词典通过 HTTP/Jsoup 解析获取数据
- 自定义词典可导入为制表符分隔的 UTF-8 文本文件

### 剪贴板服务
`CBWatcherService` 作为前台服务运行以监控剪贴板变化。这需要：
- FOREGROUND_SERVICE 权限
- 通知以保持服务存活
- 正确处理 Android 的后台执行限制

### 多语言支持
应用支持：
- 英语（11 个词典）
- 西班牙语、法语、德语（通过欧路在线词典）
- 日语（通过沪江和 jisho.org）

## 测试

存在基础测试架构：
- 单元测试在 `app/src/test/`（JUnit 4.12）
- 设备测试在 `app/src/androidTest/`（Espresso）
- 代码质量工具配置在 `config/quality/`（Checkstyle、FindBugs、PMD）

测试覆盖率较低 - 这是需要改进的领域。

## 已知问题

1. **Android 版本：** 目标 SDK 28，编译 SDK 29 - 考虑更新以支持新的 Android 版本
2. **依赖库：** 部分依赖过时（OkHttp 3.12.1、Kotlin 1.3.50）- 应更新以提升安全性和性能
3. **剪贴板问题：** Android 10+ 限制剪贴板访问 - 参见提交 d9e184e 的变通方案
4. **jisho.org：** 日语词典因服务器位置频繁超时
