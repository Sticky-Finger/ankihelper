# PopupActivity 划词组件分析

> 分析日期: 2026-06-17
> 组件范围: PopupActivity 划词界面（从通知点击/分享/复制等入口拉起后的「文本分词 + 词典查询 + 释义展示」核心交互）

---

## 组件概述

`PopupActivity` 是 AnkiHelper 应用的「悬浮划词主界面」，是用户从外部入口（剪贴板通知点击、Chrome 上下文菜单、FBReader、系统分享）进入后的统一工作面板。它负责：

1. 接收外部传入的待处理文本（包括通知点击带来的 `use_clipboard_content_flag` 特殊标记）
2. 将整段文本分词并以可点选的「词块」形式展示（`BigBangLayout`）
3. 用户选中单词后异步查询当前方案对应的词典
4. 把查询结果以卡片形式渲染到列表中，供用户进一步创建 Anki 卡片

本文聚焦「通知点击 → 划词 → 查词 → 释义渲染」这一主链路。Anki 卡片导出部分（`asyncAddNote` 等）不在本文范围。

---

## 核心文件

### 1. 划词主界面

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java`（约 1,785 行）

**类声明:** `PopupActivity extends Activity implements BigBangLayoutWrapper.ActionListener` (第 131 行)

#### `onCreate()` — 第 235–262 行

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    if(Settings.getInstance(this).getPinkThemeQ()){
        setTheme(R.style.TransparentPink);
    }
    super.onCreate(savedInstanceState);
    setStatusBarColor();
    setContentView(R.layout.activity_popup);
    overridePendingTransition(R.anim.slide_in, R.anim.slide_out);
    scrollView = (ScrollView) findViewById(R.id.scrollView);
    OverScrollDecoratorHelper.setUpOverScroll(scrollView);
    assignViews();
    initBigBangLayout();
    loadData();              // 加载词典列表 + 输出方案列表
    populatePlanSpinner();   // 填充方案下拉
    populateLanguageSpinner();
    setEventListener();
    if (settings.getMoniteClipboardQ()) {
        startCBService();    // 顺带保活 CBWatcherService
    }
    handleIntent();          // 解析 Intent，识别通知来源
    asyncInvokeDroid();      // 后台预热 AnkiDroid API
}
```

#### `handleIntent()` — 第 718–783 行（入口识别）

```java
if (Intent.ACTION_SEND.equals(action) && type.equals("text/plain")) {
    mTextToProcess = intent.getStringExtra(Intent.EXTRA_TEXT);
    if(mTextToProcess != null && mTextToProcess.equals(Constant.USE_CLIPBOARD_CONTENT_FLAG)){
        mTextToProcess = "";                          // 清空，等窗口焦点回调再读剪贴板
        isFromAndroidQClipboard = true;
    }
    ...
}
...
populateWordSelectBox();    // 即使 mTextToProcess 为空也会执行一次（占位）
```

#### `onWindowFocusChanged()` — 第 690–716 行（Android 10+ 实际读剪贴板）

```java
if(isFromAndroidQClipboard) {
    if (!Settings.getInstance(MyApplication.getContext()).getMoniteClipboardQ()) {
        return;
    }
    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (cb.hasPrimaryClip() && cb.hasText()) {
        mTextToProcess = cb.getText().toString();      // 此时才能合法读剪贴板
    }
    populateWordSelectBox();
    ...
}
```

> **为什么走 `onWindowFocusChanged` 而不是 `onCreate`/`onResume`？**
> Android 10+ 限制后台读剪贴板。Activity 通过通知 `PendingIntent` 启动后，必须等其获得窗口焦点（成为前台应用）才能合法读剪贴板。

#### `populateWordSelectBox()` — 第 785–804 行（分词与渲染）

```java
private void populateWordSelectBox() {
    List<String> localSegments = TextSplitter.getLocalSegments(mTextToProcess);
    bigBangLayout.removeAllViews();
    for (String localSegment : localSegments) {
        bigBangLayout.addTextItem(localSegment);
    }
    bigBangLayout.post(new Runnable() {
        @Override
        public void run() {
            String currentWord = FieldUtil.getSelectedText(bigBangLayout.getLines());
            if (!currentWord.equals("") && !currentWord.equals(act.getText().toString())) {
                mCurrentKeyWord = currentWord;
                act.setText(currentWord);
                asyncSearch(currentWord);             // 首词自动查询
            }
        }
    });
}
```

#### `asyncSearch()` — 第 807–840 行（后台词典查询）

```java
private void asyncSearch(final String word) {
    if (word.length() == 0) { showPronounce(false); return; }
    if(currentDicitonary == null || currentOutputPlan == null){ return; }
    showProgressBar();
    showPronounce(true);
    Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                List<Definition> d = currentDicitonary.wordLookup(word);
                Message message = mHandler.obtainMessage();
                message.obj = d;
                message.what = PROCESS_DEFINITION_LIST;
                mHandler.sendMessage(message);
            } catch (Exception e) {
                Message message = mHandler.obtainMessage();
                message.obj = e.getMessage();
                message.what = ASYNC_SEARCH_FAILED;
                mHandler.sendMessage(message);
            }
        }
    });
    thread.start();
    HistoryUtil.saveWordlookup(mTextToProcess, word);
}
```

#### `mHandler` — 第 198–230 行（异步回调派发）

```java
final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case PROCESS_DEFINITION_LIST:
                showSearchButton();
                mDefinitionList = (List<Definition>) msg.obj;
                processDefinitionList(mDefinitionList);
                break;
            case ASYNC_SEARCH_FAILED:
                showSearchButton();
                Toast.makeText(PopupActivity.this, (String) msg.obj, Toast.LENGTH_LONG).show();
                break;
            // TRANSLATION_DONE / default 分支略
        }
    }
};
```

#### `processDefinitionList()` — 第 661–688 行（释义卡片渲染）

```java
private void processDefinitionList(List<Definition> definitionList) {
    if (definitionList.isEmpty()) {
        Toast.makeText(this, R.string.definition_not_found, Toast.LENGTH_SHORT).show();
    } else {
        viewDefinitionList.removeAllViewsInLayout();
        for (Definition def : definitionList) {
            viewDefinitionList.addView(getCardFromDefinition(def));
        }
        // 滚动到顶部等小逻辑略
    }
}
```

> 注意：`viewDefinitionList` 实测是 `LinearLayout` 动态 `addView`，**不是 `RecyclerView`**（PROJECT_ANALYSIS.md 中提到的 RecyclerView 是早期实现的遗留描述，对应代码段已被注释掉，见 665-672 行）。

#### `loadData()` — 第 331–345 行（数据加载）

```java
private void loadData() {
    dictionaryList = DictionaryRegister.getDictionaryObjectList();
    outputPlanList = ExternalDatabase.getInstance().getAllPlan();
    settings = Settings.getInstance(this);
    ...
    if(outputPlanList.size() == 0){
        Utils.showMessage(this, getResources().getString(R.string.toast_no_available_plan));
    }
}
```

---

### 2. 主要 UI 控件（`assignViews()` 第 308-320 行）

| 控件 | 类型 | id | 用途 |
|------|------|----|----|
| `act` | `AutoCompleteTextView` | `edit_text_hwd` | 词条输入框，支持词典自动补全 |
| `bigBangLayout` | `BigBangLayout` | `bigbang` | 自定义分词展示布局（"BigBang" 式词块） |
| `bigBangLayoutWrapper` | `BigBangLayoutWrapper` | `bigbang_wrapper` | 包装器，提供 `ActionListener` 回调 |
| `planSpinner` | `Spinner` | `plan_spinner` | 输出方案选择器 |
| `pronounceLanguageSpinner` | `Spinner` | `language_spinner` | 发音语言选择器 |
| `viewDefinitionList` | `LinearLayout` | `view_definition_list` | 释义卡片容器 |
| `btnSearch` | `Button` | `btn_search` | 查询按钮 |
| `btnPronounce` | `ImageButton` | `btn_pronounce` | 发音按钮 |
| `mBtnEditNote` / `mBtnEditTag` | `ImageButton` | `footer_note` / `footer_tag` | 笔记/标签编辑 |
| `progressBar` | `ProgressBar` | `progress_bar` | 查询加载指示 |

---

### 3. 布局 XML

**文件路径:** `app/src/main/res/layout/activity_popup.xml`

包含上述所有控件；PopupActivity 主题为透明悬浮（`R.style.Transparent` / `TransparentPink`），通过 `overridePendingTransition(R.anim.slide_in, R.anim.slide_out)` 提供侧滑动画。

---

### 4. 协作组件

| 组件 | 文件路径 | 在划词链路中的作用 |
|------|---------|------------------|
| **TextSplitter** | `util/TextSplitter.java` | 把整段文本切成单词 segment 列表（`getLocalSegments()`） |
| **FieldUtil** | `util/FieldUtil.java` | `getSelectedText()` 从 BigBangLayout 提取当前选中文本 |
| **BigBangLayout / Wrapper** | `ui/widget/BigBangLayout*.java` | 自定义控件，把 segment 渲染成可点选词块，管理选中状态 |
| **DictionaryRegister** | `data/dict/DictionaryRegister.java` | 提供可用词典列表（`getDictionaryObjectList()`） |
| **IDictionary** | `data/dict/IDictionary.java` | 词典统一接口，核心方法 `wordLookup(String word)` |
| **OutputPlan** | `data/plan/OutputPlan.java` | 当前方案配置，决定用哪个词典、字段映射等 |
| **ExternalDatabase** | `data/database/ExternalDatabase.java` | `getAllPlan()` 获取所有输出方案 |
| **Settings** | `data/Settings.java` | 提供 `getMoniteClipboardQ()`、`getLastSelectedPlan()` 等配置 |
| **Definition** | `data/model/Definition.java` | 词典返回的释义数据模型，渲染卡片的数据源 |
| **HistoryUtil** | `util/HistoryUtil.java` | `savePopupOpen()` / `saveWordlookup()` 记录使用历史 |

---

## 实现流程

### 通知点击 → 划词 → 查词的完整数据流

```
[通知点击]
    │
    ▼  PendingIntent (CBWatcherService.java:78)
[PopupActivity 启动，EXTRA_TEXT = "use_clipboard_content_flag"]
    │
    ▼  onCreate()
[加载词典/方案/控件 → handleIntent()]
    │
    ▼  handleIntent() — PopupActivity.java:718
[识别 USE_CLIPBOARD_CONTENT_FLAG，置 isFromAndroidQClipboard = true，mTextToProcess = ""]
    │
    ▼  onWindowFocusChanged() — PopupActivity.java:690（Android 10+ 等焦点）
[ClipboardManager 读取真实文本 → mTextToProcess]
    │
    ▼  populateWordSelectBox() — PopupActivity.java:785
[TextSplitter.getLocalSegments() → 把文本切成 segment 列表]
    │
    ▼  bigBangLayout.addTextItem() × N
[词块渲染到 BigBangLayout，用户可点选/拖选]
    │
    ▼  FieldUtil.getSelectedText() 提取首词
[act.setText(首词) → asyncSearch(首词)] — PopupActivity.java:799
    │
    ▼  asyncSearch() — PopupActivity.java:807
[新建线程 → currentDicitonary.wordLookup(word) → List<Definition>]
    │
    ▼  mHandler 处理 PROCESS_DEFINITION_LIST — PopupActivity.java:203
    │
    ▼  processDefinitionList() — PopupActivity.java:661
[清空 viewDefinitionList → 遍历 Definition 列表 → getCardFromDefinition() 生成卡片 → addView]
    │
    ▼
[用户在卡片上勾选/编辑 → 点击搜索按钮触发后续 Anki 卡片创建（本文不展开）]
```

### 其他入口的差异

| 入口 | mTextToProcess 来源 | 是否走 onWindowFocusChanged 读剪贴板 |
|------|------------------|-----------------------------------|
| 通知点击（CBWatcherService PendingIntent） | 等焦点回调后读剪贴板 | ✅ |
| 系统分享（ACTION_SEND + EXTRA_TEXT） | Intent extras 直接传入 | ❌ |
| Chrome 文本选择（ACTION_PROCESS_TEXT） | `intent.getStringExtra(EXTRA_PROCESS_TEXT)` | ❌ |
| FBReader 集成 | Intent extras（含 Base64 编码段） | ❌ |

---

## 关联组件

### 1. CBWatcherService（上游触发源）

详见 [CBWatcherService_Clipboard_Notification_Analysis.md](./CBWatcherService_Clipboard_Notification_Analysis.md)。该通知的 `PendingIntent` 直接指向 `PopupActivity`，并以 `EXTRA_TEXT = "use_clipboard_content_flag"` 作为暗号。

### 2. Anki 卡片创建（下游）

查询结果渲染后，用户在卡片上勾选条目并点击「+」按钮触发 `addNoteToAnki` 等流程，最终通过 `AnkiDroidHelper` 写入 AnkiDroid 数据库。这部分超出本文范围，可后续单独成文。

### 3. 自定义词典查询流程

`asyncSearch()` 中调用的 `currentDicitonary` 来自 `getDictionaryFromOutputPlan(currentOutputPlan)`，由当前选择的输出方案决定。`DictionaryRegister` 通过反射动态加载具体词典实例（参见 PROJECT_ANALYSIS.md「插件化设计」）。

---

## 设计特点

1. **统一入口**: 四种外部入口（通知、分享、文本选择、FBReader）共用同一个 `PopupActivity`，差异在 `handleIntent()` 中通过 Intent action/extra 区分
2. **延迟读剪贴板**: Android 10+ 上利用 `onWindowFocusChanged` 拿到前台身份后再读剪贴板，绕过后台限制
3. **BigBangLayout 词块交互**: 文本以词块形式呈现，支持单击选词、多选拖选，比 EditText 选择更直观
4. **异步查询 + Handler 回调**: 词典查询在子线程执行，避免阻塞 UI；通过 `mHandler` 切回主线程更新 UI
5. **自动首词查询**: 文本加载后自动选中并查询第一个词，减少用户操作步骤
6. **方案驱动词典**: 查询用哪个词典由当前 `OutputPlan` 决定，用户可随时切换方案

---

## 已知问题

### 1. PROJECT_ANALYSIS.md 与实际控件不符

**现象:** PROJECT_ANALYSIS.md 描述 PopupActivity 使用 RecyclerView 显示词典查询结果，但实际代码：

```java
// PopupActivity.java:314
//recyclerViewDefinitionList = (RecyclerView) findViewById(R.id.recycler_view_definition_list);
viewDefinitionList = (LinearLayout) findViewById(R.id.view_definition_list);
```

`PopupActivity.java:665-672` 也有注释掉的 RecyclerView 适配器代码。

**影响:** 文档与实现不一致，新维护者读文档易被误导。

**建议:** 更新 PROJECT_ANALYSIS.md，把 RecyclerView 描述改为 LinearLayout 动态填充。

---

### 2. 拼写错误 `currentDicitonary`

**位置:** PopupActivity.java 多处（如 366, 393, 495, 812, 824 行）

```java
private IDictionary currentDicitonary;   // 应为 currentDictionary
```

**影响:** 仅影响代码可读性，无功能问题。

**建议:** 全局重命名为 `currentDictionary`。

---

### 3. `mHandler` 使用 `HandlerLeak` 抑制警告

**位置:** PopupActivity.java:198

```java
@SuppressLint("HandlerLeak")
final Handler mHandler = new Handler() { ... };
```

**问题:** 非静态内部类 `Handler` 持有外部 Activity 引用，存在内存泄漏风险（如子线程尚未返回时用户退出 Activity）。

**影响:** 长时间词典查询 + 频繁开关 PopupActivity 场景下可能积累泄漏。

**建议:** 改为静态内部类 + WeakReference，或使用 `LiveData`/`ViewModel` 重构。

---

### 4. 词典查询异常吞掉堆栈

**位置:** PopupActivity.java:829-835

```java
} catch (Exception e) {
    String error = e.getMessage();
    Message message = mHandler.obtainMessage();
    message.obj = error;
    message.what = ASYNC_SEARCH_FAILED;
    mHandler.sendMessage(message);
}
```

**问题:** 只把 `e.getMessage()` 透传给 UI，没有 `Log.e` 记录完整堆栈，调试困难。

**影响:** 线上词典查询失败时无法定位根因。

**建议:** 增加 `Log.e("PopupActivity", "asyncSearch failed", e);`。

---

### 5. `cb.getText()` 已废弃

**位置:** PopupActivity.java:700

```java
String text = cb.getText().toString();
```

**问题:** `ClipboardManager.getText()` 自 API 11 起废弃，应使用 `getPrimaryClip()` + `Item.getText()`。

**影响:** 当前可用，但未来 Android 版本可能移除。

**建议:** 改为：
```java
ClipData.Item item = cb.getPrimaryClip().getItemAt(0);
String text = item.getText().toString();
```

（与 CBWatcherService 中 `performClipboardCheck()` 的同类问题一致）

---

## 与其他组件分析文档的关联

| 文档 | 关系 |
|------|------|
| [CBWatcherService_Clipboard_Notification_Analysis.md](./CBWatcherService_Clipboard_Notification_Analysis.md) | 上游：通知点击直接拉起本文的 PopupActivity |
| [PlanEditor_Deck_Selector_Analysis.md](./PlanEditor_Deck_Selector_Analysis.md) | 方案编辑器配置的牌组 ID 最终在 PopupActivity 的卡片创建环节使用 |
| [PlanEditor_Dictionary_Selector_Analysis.md](./PlanEditor_Dictionary_Selector_Analysis.md) | 方案编辑器配置的词典决定 PopupActivity 中 `currentDicitonary` 取哪个实例 |

---

**文档生成:** Claude Code
**最后更新:** 2026-06-17
