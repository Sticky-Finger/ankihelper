# AnkiHelper 项目源代码结构分析

> 生成日期: 2026-01-01
> 项目版本: 2.30.7
> 分析范围: 全面源代码结构分析

---

## 项目概览

AnkiHelper (Anki 划词助手) 是一个功能丰富的 Android 应用程序，用于与 AnkiDroid 集成创建抽认卡。

**项目统计:**
- Java 源文件: 110 个
- 代码量: 约 20,322 行
- 版本: 2.30.7
- 目标 SDK: 28 (Android 9 Pie)
- 最低 SDK: 19 (Android 4.4 KitKat)

---

## 核心架构

### 分层结构

```
com.mmjang.ankihelper/
├── ui/                    # 表示层 - 用户界面组件
│   ├── popup/            # 悬浮弹窗界面
│   ├── launcher/         # 主界面
│   ├── plan/             # 方案管理界面
│   ├── customdict/       # 自定义词典界面
│   ├── content/          # 内容相关界面
│   ├── about/            # 关于界面
│   ├── stat/             # 统计界面
│   ├── translation/      # 翻译界面
│   └── widget/           # 自定义组件
│
├── domain/               # 业务逻辑层
│   ├── CBWatcherService  # 剪贴板监控前台服务
│   └── PronounceManager  # 发音管理器
│
├── data/                 # 数据层
│   ├── dict/             # 词典实现（22个内置词典）
│   ├── database/         # 数据库管理
│   ├── plan/             # 输出方案配置
│   ├── book/             # 电子书相关
│   ├── model/            # 数据模型
│   ├── content/          # 内容管理
│   └── history/          # 历史记录
│
├── anki/                 # AnkiDroid API 集成
│   └── AnkiDroidHelper   # AnkiDroid API 封装
│
├── util/                 # 工具类
│   ├── Utils.java        # 通用工具方法
│   ├── Translator.java   # 翻译服务
│   ├── TextSplitter.java # 文本分割
│   ├── FieldUtil.java    # 字段处理
│   ├── WanaKanaJava.java # 日语假名转换（933行）
│   └── Constant.java     # 常量定义
│
└── MyApplication.java     # 应用程序入口
```

---

## 关键组件详解

### 1. 应用程序入口

**文件:** `MyApplication.java`
**路径:** `app/src/main/java/com/mmjang/ankihelper/MyApplication.java`

**职责:**
- 继承自 MultiDexApplication，支持方法数超过 65K
- 初始化 LitePal ORM 数据库框架
- 提供全局的 Context、AnkiDroidHelper 和 OkHttpClient 实例
- 管理应用程序级别的状态和资源

---

### 2. 用户界面层 (ui/)

#### 核心界面

**PopupActivity.java** (1,785 行)
**路径:** `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java`

**职责:**
- 单词选择和卡片创建的主界面
- 处理文本输入、词典查询、卡片预览
- 支持多种 UI 组件：
  - AutoCompleteTextView - 自动完成文本输入
  - RecyclerView - 显示词典查询结果
  - ChipGroup - 选择词典标签
- 处理与 AnkiDroid 的交互

**LauncherActivity.java** (477 行)
**路径:** `app/src/main/java/com/mmjang/ankihelper/ui/LauncherActivity.java`

**职责:**
- 主设置入口
- 管理剪贴板监控开关
- 词典设置和配置
- 集成各种子功能入口

#### 其他界面模块

- **PlansManagerActivity** / **PlanEditorActivity** - 方案管理和编辑
- **CustomDictionaryActivity** - 自定义词典管理
- **ContentActivity** - 随机内容展示
- **StatActivity** - 使用统计分析
- **AboutActivity** - 关于信息和应用版本

---

### 3. 词典系统 (data/dict/)

#### 核心接口

**IDictionary.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/data/dict/IDictionary.java`

**定义的方法:**
- `query(String word)` - 查询单词定义
- `query(String word, int limit)` - 限制结果数量的查询
- `getAutoCompleteResults(String word)` - 获取自动完成建议
- `exportElements()` - 导出可配置元素
- `lookup(String word, Context context)` - 带上下文的查询

**DictionaryRegister.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/data/dict/DictionaryRegister.java`

**职责:**
- 词典注册中心，管理所有可用词典
- 通过反射动态加载词典实例
- 维护词典类型和实例的映射关系

#### 内置词典列表 (22个)

**英语词典:**
1. **Ode2** - 牛津英语词典（本地）
2. **Collins** - 柯林斯词典（本地）
3. **WebsterLearners** - 韦氏学习词典（在线）
4. **BingOxford** - 必应牛津词典（在线）
5. **VocabCom** - Vocabulary.com（在线）
6. **DictionaryDotCom** - Dictionary.com（在线）
7. **UrbanDict** - 城市词典（在线）
8. **Longman** - 朗文词典（在线）
9. **WordNet** - 普林斯顿词汇网络（在线）
10. **FreeDict** - 自由词典（在线）

**多语言词典:**
11. **Frdict** - 法语词典（欧路在线）
12. **Esdict** - 西班牙语词典（欧路在线）
13. **Dedict** - 德语词典（欧路在线）

**日语词典:**
14. **JiSho** - jisho.org 在线日语词典
15. **HujiangJapanese** - 沪江日语词典
16. **Handian** - 汉典（中日汉字）

**特色词典:**
17. **Cloze** - 挖空词典（用于填空练习）
18. **Mnemonic** - 记忆术词典
19. **IdiomDict** - 习语词典
20. **PhraseDict** - 短语词典
21. **SentenceDict** - 例句词典
22. **PhoneticDict** - 音标词典

---

### 4. 数据层 (data/)

#### 数据库管理

**ExternalDatabase.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/data/database/ExternalDatabase.java`

**职责:**
- 外部数据库管理
- 使用 SQLiteAssetHelper 管理预置数据库
- 支持多数据源访问

**LitePal ORM:**
- 对象关系映射框架
- 简化数据库操作
- 自动处理表结构和数据迁移

#### 输出方案系统

**OutputPlan.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/data/plan/OutputPlan.java`

**数据模型属性:**
- 词典配置
- 目标牌组
- 笔记类型
- 字段映射（词典字段 → Anki 字段）
- 关联标签

**相关类:**
- **VocabularyCardModel.java** - 词汇卡片模型
- **DefaultPlan.java** - 默认方案定义
- **OutputPlanPOJO.java** - 方案数据传输对象

#### 配置管理

**Settings.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/data/Settings.java`

**设计模式:** 单例模式

**管理配置项:**
- 剪贴板监控开关
- 主题设置
- 语言设置
- 词典优先级
- 默认方案选择

**存储方式:** SharedPreferences

---

### 5. 业务逻辑层 (domain/)

#### 剪贴板监控服务

**CBWatcherService.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/domain/CBWatcherService.java`

**服务类型:** 前台服务 (Foreground Service)

**核心功能:**
- 持续监控剪贴板变化
- 检测到文本变化时启动 PopupActivity
- 包含英文文本过滤逻辑
- 使用通知保持服务运行

**需要的权限:**
- FOREGROUND_SERVICE
- android.permission.READ_CLIPBOARD

#### 发音管理

**PronounceManager.java**

**职责:**
- 管理音频发音
- 在线发音下载和缓存
- 支持多种发音源

---

### 6. AnkiDroid 集成 (anki/)

**AnkiDroidHelper.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/anki/AnkiDroidHelper.java`

**封装的功能:**
- 添加卡片到 AnkiDroid
- 更新现有卡片
- 删除卡片
- 查询牌组列表
- 查询笔记类型
- 获取字段定义

**API 版本:** com.ichi2.anki.api

**已知问题:**
- 部分手机限制应用间通信
- 解决方法：保持 AnkiDroid 在后台运行

---

### 7. 工具类 (util/)

#### 核心工具类

**Utils.java**
- 通用工具方法集合
- 文本处理
- 格式转换

**Translator.java**
- 集成翻译服务
- 支持多种翻译 API

**TextSplitter.java**
- 智能文本分割
- 句子边界检测

**FieldUtil.java**
- Anki 字段处理
- 字段验证和格式化

**WanaKanaJava.java** (933 行)
- 日语假名转换
- 平假名 ↔ 片假名转换
- 罗马字转换

**Constant.java**
**路径:** `app/src/main/java/com/mmjang/ankihelper/util/Constant.java`

**定义常量:**
- Intent action 字符串
- 文件路径常量
- 导出元素类型
- 配置键名

---

## 模块职责划分

### 文本捕获模块

**组件:**
- `CBWatcherService` - 监控剪贴板变化
- `PopupActivity` - 处理文本选择和分享
- Intent Filter - 支持 SEND 和 PROCESS_TEXT 两种文本获取方式

**工作流程:**
1. 用户复制文本 → 剪贴板变化
2. CBWatcherService 检测变化
3. 启动 PopupActivity 显示
4. 用户选择单词并创建卡片

### 词典查询模块

**组件:**
- `DictionaryRegister` - 词典注册和管理
- `IDictionary` - 词典统一接口
- 各种词典实现 - 具体词义查询服务

**查询流程:**
1. 接收单词输入
2. DictionaryRegistry 查找可用词典
3. 调用每个词典的 query() 方法
4. 合并结果并显示

### 卡片生成模块

**组件:**
- `OutputPlan` - 定义卡片生成规则
- `AnkiDroidHelper` - 与 AnkiDroid 交互
- `Definition` - 词汇定义数据结构

**生成流程:**
1. 选择输出方案
2. 根据方案配置获取词典字段
3. 映射到 Anki 字段
4. 通过 AnkiDroid API 添加卡片

### 用户界面模块

**组件:**
- `LauncherActivity` - 主界面和设置
- `PopupActivity` - 核心卡片创建界面
- 各种管理 Activity - 方案、词典、统计等管理

### 数据管理模块

**技术栈:**
- LitePal - ORM 数据库框架
- SQLiteAssetHelper - 预置数据库管理
- SharedPreferences - 配置存储

**数据类型:**
- 词典数据（本地数据库）
- 用户配置（SharedPreferences）
- 输出方案（LitePal）
- 查询历史（LitePal）

---

## 设计模式应用

### 1. MVP (Model-View-Presenter) 模式

**应用场景:** 大部分活动页面

**实现方式:**
- View: Android Activities 和 Fragments
- Presenter: Activities 中的业务逻辑代码
- Model: 数据层类

**优点:** 分离关注点，便于测试

### 2. 插件化设计

**应用场景:** 词典系统

**实现方式:**
- 统一接口 `IDictionary`
- 通过反射动态加载词典实例
- `DictionaryRegister` 管理插件注册

**优点:**
- 易于扩展新词典
- 词典可独立开发和测试
- 支持自定义词典

### 3. 单例模式

**应用场景:**
- `Settings` - 全局设置管理
- `MyApplication` - 应用程序全局状态

**优点:**
- 确保全局唯一实例
- 集中管理共享资源

### 4. 服务导向架构

**应用场景:** 剪贴板监控

**实现方式:**
- `CBWatcherService` 作为前台服务运行
- 独立于 UI 生命周期
- 使用通知保持服务存活

**优点:**
- 支持后台持续运行
- 不受 Activity 生命周期影响

### 5. 仓储模式 (Repository Pattern)

**应用场景:**
- `ExternalDatabase` 封装数据访问
- `DictionaryRegister` 管理词典数据源

**优点:**
- 抽象数据访问层
- 隔离业务逻辑和数据源

---

## 主要功能实现

### 1. 剪贴板监控

**实现文件:** `CBWatcherService.java`

**工作原理:**
1. 注册为前台服务
2. 使用 ClipboardManager 监听剪贴板变化
3. 检测到新文本时过滤非英文内容
4. 启动 PopupActivity 显示结果

**关键代码位置:** `CBWatcherService.java:152`

### 2. 单词查询

**实现文件:** `data/dict/` 目录下各词典实现

**查询流程:**
```
用户输入
  ↓
DictionaryRegistry.getAvailableDicts()
  ↓
对每个词典调用 dict.query(word)
  ↓
合并结果返回 Definition 对象
  ↓
UI 显示
```

**关键接口:** `IDictionary.java:15`

### 3. 卡片创建

**实现文件:** `PopupActivity.java`

**创建流程:**
1. 用户输入单词
2. 查询词典获取定义
3. 选择输出方案
4. 填写字段内容
5. 预览卡片
6. 调用 AnkiDroid API 添加卡片

**关键代码位置:** `PopupActivity.java:1245`

### 4. 方案管理

**实现文件:** `data/plan/OutputPlan.java`

**管理功能:**
- 创建新方案
- 编辑现有方案
- 删除方案
- 设置默认方案

**存储方式:** LitePal ORM

### 5. Anki 集成

**实现文件:** `anki/AnkiDroidHelper.java`

**集成方式:**
- 使用 AnkiDroid API
- 通过 Intent 调用
- 处理权限和错误

**API 版本:** com.ichi2.anki.api (API Level 1)

---

## 技术栈详解

### 核心框架

**Android SDK:**
- 目标 API: 28 (Android 9 Pie)
- 最低 API: 19 (Android 4.4 KitKat)
- 编译 SDK: 29 (Android 10)

**数据库:**
- LitePal 1.3.1 - ORM 框架
- SQLiteAssetHelper - 预置数据库管理

### UI 框架

**Android Support Library v28:**
- AppCompatActivity
- Fragment
- RecyclerView
- Material Design 组件

**自定义组件:**
- BigBangLayout - 文本选择布局
- 自定义 Chip 组件

### 网络和工具

**网络请求:**
- OkHttp 3.12.1 - HTTP 客户端
- Jsoup 1.10.2 - HTML 解析

**时间处理:**
- ThreeTenABP - JSR-310 日期时间 API

### 多媒体

**图片加载:**
- Glide 4.8.0 - 图片加载和缓存框架

### 构建工具

**Gradle:**
- Android Gradle Plugin 3.5.0
- Gradle Wrapper 5.4.1

**Maven 仓库:**
- 阿里云镜像（加速国内构建）
- Google Maven
- JCenter

---

## 项目特色

### 1. 多源文本捕获

支持四种文本获取方式：
- **剪贴板监控** - 自动检测复制的文本
- **Chrome 上下文菜单** - 通过 Android 文本选择框架
- **FBReader 集成** - 定制版 FBReader 直接集成
- **分享意图** - 从任何应用通用文本分享

### 2. 丰富的词典支持

- 22 个内置词典
- 支持英语、法语、德语、西班牙语、日语
- 本地词典和在线词典结合
- 可导入自定义词典

### 3. 灵活的方案系统

- 可配置的词典组合
- 自定义牌组和笔记类型
- 灵活的字段映射
- 支持快速切换工作流

### 4. 插件化架构

- 词典系统完全解耦
- 通过接口和反射实现动态加载
- 易于添加新词典
- 支持用户自定义词典

### 5. 后台服务

- 剪贴板监控作为前台服务运行
- 即使应用在后台也能工作
- 需要通知权限保持服务存活

### 6. 多语言支持

- 英语（11 个词典）
- 法语、德语、西班牙语（欧路词典）
- 日语（3 个词典）

---

## 已知问题和限制

### 1. Android 版本

**问题:** 目标 SDK 28，编译 SDK 29

**影响:**
- 未适配 Android 10+ 的新特性
- 可能需要更新以支持新的 Android 版本

### 2. 依赖库版本

**过时的依赖:**
- OkHttp 3.12.1（存在安全更新）
- Kotlin 1.3.50（当前最新 1.9+）
- Support Library v28（已迁移到 AndroidX）

**建议:** 更新依赖以提升安全性和性能

### 3. 剪贴板问题

**问题:** Android 10+ 限制剪贴板访问

**变通方案:** 参见提交 d9e184e

**影响:** 部分功能可能受限

### 4. jisho.org 超时

**问题:** 日语词典因服务器位置频繁超时

**影响:** JiSho 词典查询可能失败

### 5. 应用间通信

**问题:** 部分手机限制应用间通信

**表现:** 添加卡片到 AnkiDroid 时可能崩溃

**解决方法:** 保持 AnkiDroid 在后台运行

---

## 测试覆盖

### 单元测试

**位置:** `app/src/test/`

**框架:** JUnit 4.12

**覆盖情况:** 较低，需要改进

### 设备测试

**位置:** `app/src/androidTest/`

**框架:** Espresso

**覆盖情况:** 基础测试存在

### 代码质量工具

**位置:** `config/quality/`

**工具:**
- Checkstyle - 代码风格检查
- FindBugs - Bug 检测
- PMD - 代码质量分析

---

## 构建和运行

### 构建 Debug APK

```bash
# Windows
gradlew.bat assembleDebug

# Linux/macOS
./gradlew assembleDebug
```

### 构建 Release APK

```bash
gradlew.bat assembleRelease
```

**注意:** 需要配置签名信息

### 运行测试

```bash
# 单元测试
gradlew.bat testDebugUnitTest

# 设备测试
gradlew.bat connectedAndroidTest
```

### 安装到设备

```bash
gradlew.bat installDebug
```

---

## 代码质量评估

### 优点

1. **结构清晰** - 分层架构明确，职责分离
2. **模块化** - 功能模块独立，易于维护
3. **可扩展** - 插件化设计支持扩展
4. **功能完整** - 实现了完整的卡片创建工作流

### 改进建议

1. **更新依赖** - 升级过时的库版本
2. **增加测试** - 提高单元测试覆盖率
3. **迁移到 AndroidX** - 替换 Support Library
4. **提升 SDK 版本** - 适配新的 Android 版本
5. **改进架构** - 考虑引入 MVVM 或 Clean Architecture
6. **优化性能** - 减少主线程耗时操作
7. **增强错误处理** - 添加更完善的异常处理

---

## 总结

AnkiHelper 是一个结构良好、功能完整的 Android 应用程序，采用清晰的分层架构和模块化设计。项目展现了成熟 Android 开发的最佳实践：

- ✅ 清晰的架构分层
- ✅ 插件式词典系统
- ✅ 完善的 AnkiDroid 集成
- ✅ 灵活的配置系统
- ✅ 多语言支持
- ✅ 后台服务实现

同时也有改进空间，特别是在依赖更新、测试覆盖和 Android 版本适配方面。总体而言，这是一个便于维护和扩展的高质量代码库。

---

**文档生成:** Claude Code
**最后更新:** 2026-01-01
