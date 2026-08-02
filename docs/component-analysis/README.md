# 组件分析文档导航

> 本目录（`docs/component-analysis/`）存放针对具体组件的深度分析文档。
> 下面以 [`PROJECT_ANALYSIS.md`](../../PROJECT_ANALYSIS.md) 中的项目目录树为骨架，在每个组件所在位置标注其分析文档。
>
> 图例：`→ xxx.md` 表示该位置已有对应的组件分析文档；`（暂无）` 表示尚未覆盖。

---

## 目录树与文档对应关系

```
com.mmjang.ankihelper/                  # app/src/main/java/com/mmjang/ankihelper/
│
├── ui/                                 # 表示层 - 用户界面组件
│   ├── popup/                          # 悬浮弹窗界面
│   │   └── PopupActivity.java          # 单词选择和卡片创建主界面
│   │       ├── 划词组件（分词 + 词典查询 + 释义渲染） → PopupActivity_Word_Lookup_Analysis.md
│   │       └── 添加卡片（modelId 透传） → NoteType_Model_Resolution_Analysis.md（路径 C 加卡点）
│   ├── LauncherActivity.java           # 主界面（位于 ui/ 根目录）
│   │   └── 添加默认方案入口            → NoteType_Model_Resolution_Analysis.md（路径 A 触发点）
│   ├── plan/                           # 方案管理界面
│   │   ├── PlanEditorActivity.java     # 方案编辑器
│   │   │   ├── 牌组选择器              → PlanEditor_Deck_Selector_Analysis.md
│   │   │   ├── 词典选择器              → PlanEditor_Dictionary_Selector_Analysis.md
│   │   │   └── 模板选择器（手动新建分支） → NoteType_Model_Resolution_Analysis.md（路径 B）
│   │   └── PlansManagerActivity.java   # 方案列表管理                          （暂无）
│   ├── customdict/                     # 自定义词典界面                        （暂无）
│   ├── content/                        # 内容相关界面                          （暂无）
│   ├── about/                          # 关于界面                              （暂无）
│   ├── stat/                           # 统计界面                              （暂无）
│   ├── translation/                    # 翻译界面                              （暂无）
│   ├── behaviour/                      # 行为定义（ScrollAwareFABBehaviour）   （暂无）
│   └── widget/                         # 自定义组件                            （暂无）
│
├── domain/                             # 业务逻辑层
│   ├── CBWatcherService.java           # 剪贴板监控前台服务
│   │   └── 下拉通知栏常驻消息           → CBWatcherService_Clipboard_Notification_Analysis.md
│   ├── PronounceManager.java           # 发音管理器                            （暂无）
│   ├── PlayAudioManager.java           # 音频播放管理器                        （暂无）
│   └── OnlockReceiver.java             # 锁屏/开机恢复接收器                   （暂无）
│
├── data/                               # 数据层
│   ├── dict/                           # 词典实现（23个内置词典）
│   │   ├── customdict/                 # 自定义词典子包                        （暂无）
│   │   └── JPDeinflector/              # 日语动词变形子包                      （暂无）
│   │   ├── IDictionary.java            # 词典统一接口                          （暂无）
│   │   └── DictionaryRegister.java     # 词典注册中心                          （暂无）
│   ├── database/                       # 数据库管理                            （暂无）
│   │   └── ExternalDatabase.java
│   ├── plan/                           # 输出方案配置
│   │   ├── OutputPlan.java             # 卡片生成方案定义                      （暂无）
│   │   │   └── outputModelId 字段      → NoteType_Model_Resolution_Analysis.md（持久化的模板 ID）
│   │   ├── OutputPlanPOJO.java         # 方案数据传输对象                      （暂无）
│   │   └── DefaultPlan.java            # 默认方案定义
│   │       └── getDefaultModelId()     → NoteType_Model_Resolution_Analysis.md（路径 A：按名查找 + 自动创建）
│   ├── book/                           # 电子书相关                            （暂无）
│   ├── model/                          # 数据模型                              （暂无）
│   ├── content/                        # 内容管理                              （暂无）
│   ├── history/                        # 历史记录                              （暂无）
│   ├── quote/                          # 名言引用                              （暂无）
│   ├── read/                           # 阅读位置                              （暂无）
│   └── Settings.java                   # 全局配置单例（含剪切板查词开关）       （暂无）
│
├── anki/                               # AnkiDroid API 集成
│   └── AnkiDroidHelper.java            # AnkiDroid API 封装
│       └── findModelIdByName()         → NoteType_Model_Resolution_Analysis.md（按名称查找 + 容忍重命名）
│
├── util/                               # 工具类
│   ├── Utils.java                      # 通用工具方法                          （暂无）
│   ├── Translator.java                 # 翻译服务                              （暂无）
│   ├── TextSplitter.java               # 文本分割                              （暂无）
│   ├── FieldUtil.java                  # 字段处理                              （暂无）
│   ├── WanaKanaJava.java               # 日语假名转换                          （暂无）
│   └── Constant.java                   # 常量定义                              （暂无）
│
└── MyApplication.java                  # 应用程序入口                          （暂无）
```

---

## 已收录文档清单

| # | 文档 | 组件 | 所属包/文件 |
|---|------|------|------------|
| 1 | [PlanEditor_Deck_Selector_Analysis.md](./PlanEditor_Deck_Selector_Analysis.md) | 方案编辑器「牌组」选择器 | `ui/plan/PlanEditorActivity.java` |
| 2 | [PlanEditor_Dictionary_Selector_Analysis.md](./PlanEditor_Dictionary_Selector_Analysis.md) | 方案编辑器「词典」选择器 | `ui/plan/PlanEditorActivity.java` |
| 3 | [CBWatcherService_Clipboard_Notification_Analysis.md](./CBWatcherService_Clipboard_Notification_Analysis.md) | 剪切板查词触发的下拉通知栏常驻消息 | `domain/CBWatcherService.java` |
| 4 | [PopupActivity_Word_Lookup_Analysis.md](./PopupActivity_Word_Lookup_Analysis.md) | PopupActivity 划词组件（通知点击后的分词/查询/渲染主链路） | `ui/popup/PopupActivity.java` |
| 5 | [NoteType_Model_Resolution_Analysis.md](./NoteType_Model_Resolution_Analysis.md) | 添加卡片时的模板（NoteType）选取逻辑：默认方案 / 手动新建 / 加卡三条路径 | `data/plan/DefaultPlan.java`、`ui/plan/PlanEditorActivity.java`、`ui/popup/PopupActivity.java`、`anki/AnkiDroidHelper.java`（跨组件业务逻辑） |

---

## 文档命名规范

参考 [`CLAUDE.md`](../../CLAUDE.md) 的项目约定：

- **文件名使用英文**，避免中文编码导致乱码
- **PascalCase 或 snake_case**，建议格式：`<类名>_<子功能>_Analysis.md`
- 示例：`PopupActivity_BusinessLogic_Analysis.md` 而非 `PopupActivity_业务逻辑分析.md`

---

## 新增文档时

1. 在 `docs/component-analysis/` 下新建 `.md` 文件，命名遵循上述规范
2. 在本文档的「目录树」中找到对应位置，把 `（暂无）` 改为 `→ 你的文档名.md`
3. 在「已收录文档清单」表格中追加一行
