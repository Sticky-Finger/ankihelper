# PopupActivity 业务逻辑深度分析

## 一、组件概述

**PopupActivity** 是 AnkiHelper 的核心组件，承担了**单词查询、词典展示、卡片创建**的核心业务流程。这是一个 1785 行的大型 Activity，实现了完整的"从文本到 Anki 卡片"的工作流。

---

## 二、核心业务流程

### 1. 初始化流程

```
onCreate()
  ├─ 设置主题（粉色/默认）
  ├─ 加载布局
  ├─ 初始化组件
  ├─ 加载数据
  ├─ 填充方案和语言选择器
  ├─ 设置事件监听
  ├─ 启动剪贴板监控服务
  ├─ 处理 Intent
  └─ 异步调用 AnkiDroid
```

**关键初始化方法：**

| 方法 | 行号 | 职责 |
|------|------|------|
| `assignViews()` | 308-329 | 绑定 UI 组件到变量 |
| `loadData()` | 331-345 | 加载词典列表、方案列表、设置、默认标签 |
| `populatePlanSpinner()` | 347-441 | 填充方案选择器，设置默认/上次使用的方案 |
| `populateLanguageSpinner()` | 443-453 | 填充发音语言选择器 |
| `initBigBangLayout()` | 455-466 | 初始化文本选择控件 |
| `setEventListener()` | 468-649 | 注册所有事件监听器 |

---

### 2. 文本接收与处理流程

#### 2.1 文本来源

PopupActivity 支持多种文本输入方式：

```java
// handleIntent() - 行 718-783
// 支持的 Intent 类型：
1. ACTION_SEND + "text/plain"        // 分享文本
2. ACTION_PROCESS_TEXT + "text/plain" // Android 文本选择框架
3. Intent 参数：
   - INTENT_ANKIHELPER_BASE64        // Base64 编码文本
   - INTENT_ANKIHELPER_TARGET_WORD   // 预设目标单词
   - INTENT_ANKIHELPER_TARGET_URL    // 来源 URL
   - INTENT_ANKIHELPER_PLAN_NAME     // 指定方案
   - INTENT_ANKIHELPER_NOTE          // 预设笔记
   - INTENT_ANKIHELPER_NOTE_ID       // 更新现有卡片
   - INTENT_ANKIHELPER_UPDATE_ACTION // 更新模式
```

#### 2.2 文本分割与单词选择

```java
// populateWordSelectBox() - 行 785-803
// 使用 TextSplitter 将文本分割为可选择的片段
List<String> localSegments = TextSplitter.getLocalSegments(mTextToProcess);
bigBangLayout.addTextItem(localSegment);  // 添加到 BigBangLayout
```

**BigBangLayout 功能：**
- 自定义文本选择控件，支持单词级别的选择
- 实现接口 `ActionListener`（行 1654-1713）
- 用户选择单词时触发 `onSelected()` → 自动查询词典

---

### 3. 词典查询流程

```
用户输入/选择单词
  ↓
asyncSearch(word) - 行 807-840
  ↓
后台线程：currentDicitonary.wordLookup(word)
  ↓
Handler 处理结果：PROCESS_DEFINITION_LIST
  ↓
processDefinitionList(definitionList) - 行 661-688
  ↓
为每个 Definition 创建卡片视图
```

**核心代码片段：**

```java
// asyncSearch() - 行 807
private void asyncSearch(final String word) {
    showProgressBar();  // 显示进度条
    Thread thread = new Thread(new Runnable() {
        public void run() {
            List<Definition> d = currentDicitonary.wordLookup(word);
            Message message = mHandler.obtainMessage();
            message.obj = d;
            message.what = PROCESS_DEFINITION_LIST;
            mHandler.sendMessage(message);
        }
    });
    thread.start();
    HistoryUtil.saveWordlookup(mTextToProcess, word); // 记录查询历史
}
```

**Handler 消息处理：**
- `PROCESS_DEFINITION_LIST` (1) - 显示查询结果
- `ASYNC_SEARCH_FAILED` (2) - 显示错误信息
- `TRANSLATION_DONE` (3) - 显示翻译结果
- `TRANSLATION_FAILED` (4) - 翻译失败

---

### 4. 定义结果展示

#### 4.1 卡片视图生成

```java
// getCardFromDefinition() - 行 902-1374
// 为每个 Definition 创建一个卡片视图，包含：
- textview_definition: 显示定义内容（支持 HTML）
- defImage: 显示图片（如果有）
- btnAddDefinition: 添加到 Anki 的按钮
- 音频播放功能（某些词典）
```

**特殊处理：**

1. **图片加载**（行 950-953）
   ```java
   if(def.getImageUrl()!=null && !def.getImageUrl().isEmpty()){
       Glide.with(this).load(def.getImageUrl()).into(defImage);
   }
   ```

2. **音频播放**（行 955-1052）
   - 支持的词典：`EudicSentence`, `SolrDictionary`, `RenRenCiDianSentence`
   - 点击定义文本可播放音频
   - 使用 `MediaPlayer` 异步加载和播放

3. **文本内搜索**（行 1730-1784）
   - 自定义 ActionMode.Callback
   - 用户可以选择定义文本中的单词再次查询

---

### 5. 卡片添加/更新流程

这是**最核心的业务逻辑**（行 1058-1373）：

#### 5.1 取消已添加的卡片

```java
Long noteIdAdded = (Long) btnAddDefinition.getTag(R.id.TAG_NOTE_ID);
if (noteIdAdded != null) {
    // 从 Anki 删除卡片
    Utils.deleteNote(PopupActivity.this, noteIdAdded.longValue());
    // 重置按钮状态
    btnAddDefinition.setTag(R.id.TAG_NOTE_ID, null);
}
```

#### 5.2 字段映射逻辑

```java
// 行 1119-1189
// 根据当前方案的字段映射，填充导出字段
String[] exportFields = new String[currentOutputPlan.getFieldsMap().size()];
for (String exportedFieldKey : currentOutputPlan.getFieldsMap().values()) {
    // 处理共享导出元素：
    // 0-空值, 1-普通句子, 2-加粗句子, 3-挖空(保留), 4-挖空(去除)
    // 5-用户笔记, 6-URL, 7-所有定义HTML, 8-翻译

    // 处理词典特定字段：
    if (def.hasElement(exportedFieldKey)) {
        exportFields[i] = def.getExportElement(exportedFieldKey);
    }
}
```

#### 5.3 媒体文件下载

```java
// 行 1191-1274
// 针对不同词典下载音频/图片：
if (currentDicitonary instanceof EudicSentence) {
    fetch.enqueue(request, successCallback, errorCallback);
}
```

#### 5.4 添加新卡片

```java
// 行 1278-1316
if (mUpdateNoteId == 0) {
    // 调用 AnkiDroid API 添加卡片
    Long result = mAnkiDroid.getApi().addNote(modelId, deckId, exportFields, mTagEditedByUser);

    // 如果 Anki 笔记类型有 noteId 字段，更新它
    if (field.replace(" ", "").toLowerCase().equals("noteid")) {
        exportFields[count] = result.toString();
        mAnkiDroid.getApi().updateNoteFields(result.longValue(), exportFields);
    }

    // 保存历史记录
    HistoryUtil.saveNoteAdd(...);
}
```

#### 5.5 更新现有卡片

```java
// 行 1317-1358
else {
    NoteInfo note = mAnkiDroid.getApi().getNote(mUpdateNoteId);
    String[] original = note.getFields();

    if (mUpdateAction.equals("replace")) {
        // 替换模式：空字段保留原值
        for (int j = 0; j < original.length; j++) {
            if (exportFields[j].isEmpty()) {
                exportFields[j] = original[j];
            }
        }
    } else {
        // 追加模式（默认）：用 <br/> 连接
        for (int j = 0; j < original.length; j++) {
            if (original[j].trim().isEmpty() || exportFields[j].trim().isEmpty()) {
                exportFields[j] = original[j] + exportFields[j];
            } else {
                exportFields[j] = original[j] + "<br/>" + exportFields[j];
            }
        }
    }

    // 合并标签
    tags.addAll(mTagEditedByUser);
    mAnkiDroid.getApi().updateNoteFields(mUpdateNoteId, exportFields);
    mAnkiDroid.getApi().updateNoteTags(mUpdateNoteId, tags);
}
```

---

### 6. 翻译功能

```java
// asyncTranslate() - 行 842-873
// 自动检测语言方向：
if(RegexUtil.isChineseSentence(mTextToProcess)){
    result = Translator.translate(mTextToProcess, "zh", "en"); // 中→英
} else {
    result = Translator.translate(mTextToProcess, "auto", "zh"); // 外→中
}

// 按钮状态变化：
translate_normal → translate_wait → translate_done
```

---

### 7. 方案切换逻辑

```java
// 行 490-514
planSpinner.setOnItemSelectedListener(...) {
    currentOutputPlan = outputPlanList.get(position);
    currentDicitonary = getDictionaryFromOutputPlan(currentOutputPlan);
    setActAdapter(currentDicitonary);
    settings.setLastSelectedPlan(currentOutputPlan.getPlanName());

    // 如果当前有查询内容，使用新方案重新查询
    if(!isDuringPlanSpinnerInit && !actContent.trim().isEmpty()) {
        asyncSearch(actContent);
    }
}
```

**左右切换按钮：**
- `mBtnFooterRotateLeft` (行 617-637) - 切换到上一个方案
- `mBtnFooterRotateRight` (行 595-615) - 切换到下一个方案
- 循环切换逻辑

---

### 8. 标签和笔记编辑

#### 8.1 笔记编辑

```java
// setupEditNoteDialog() - 行 1376-1401
// 简单的对话框，允许用户输入自定义笔记
mNoteEditedByUser = edt.getText().toString();
```

#### 8.2 标签编辑

```java
// setupEditTagDialog() - 行 1403-1491
// 功能：
- 显示当前标签
- 从数据库加载历史标签（UserTag）
- Chip 组件展示，支持点击选择
- 可设为默认标签
- 新标签自动保存到数据库
```

---

### 9. Android Q 剪贴板特殊处理

```java
// 行 693-714
if(isFromAndroidQClipboard) {
    if (!Settings.getInstance().getMoniteClipboardQ()) {
        return;
    }
    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (cb.hasPrimaryClip() && cb.hasText()) {
        mTextToProcess = cb.getText().toString();
    }
    populateWordSelectBox();
    setTargetWord();
}
```

**变通方案：** Android 10+ 限制剪贴板访问，使用特殊标志 `USE_CLIPBOARD_CONTENT_FLAG`

---

## 三、关键状态变量

| 变量名 | 类型 | 作用 |
|--------|------|------|
| `currentOutputPlan` | OutputPlanPOJO | 当前选择的输出方案 |
| `currentDicitonary` | IDictionary | 当前使用的词典 |
| `mTextToProcess` | String | 待处理的原始文本 |
| `mTargetWord` | String | 预设的目标单词 |
| `mUpdateNoteId` | Long | 要更新的笔记ID（0=新建） |
| `mUpdateAction` | String | 更新模式：replace/append |
| `mNoteEditedByUser` | String | 用户编辑的笔记内容 |
| `mTagEditedByUser` | Set<String> | 用户编辑的标签集合 |
| `isDuringPlanSpinnerInit` | boolean | 方案选择器初始化标志 |
| `isFetchDownloading` | boolean | 是否正在下载媒体 |

---

## 四、与外部组件的交互

### 1. 数据层交互

```java
// 词典系统
DictionaryRegister.getDictionaryObjectList()  // 获取所有词典
currentDicitonary.wordLookup(word)            // 查询单词
currentDicitonary.getAutoCompleteAdapter()    // 自动完成适配器

// 方案系统
ExternalDatabase.getInstance().getAllPlan()    // 获取所有方案

// 设置
Settings.getInstance()                          // 单例设置管理

// 历史记录
HistoryUtil.saveWordlookup()                   // 保存查询历史
HistoryUtil.savePopupOpen()                    // 保存打开记录
HistoryUtil.saveNoteAdd()                      // 保存添加记录
```

### 2. AnkiDroid API 交互

```java
AnkiDroidHelper mAnkiDroid = MyApplication.getAnkiDroid();

// 添加卡片
mAnkiDroid.getApi().addNote(modelId, deckId, fields, tags)

// 更新卡片
mAnkiDroid.getApi().updateNoteFields(noteId, fields)
mAnkiDroid.getApi().updateNoteTags(noteId, tags)

// 查询卡片
mAnkiDroid.getApi().getNote(noteId)

// 删除卡片
Utils.deleteNote(context, noteId)
```

### 3. 媒体下载管理

```java
// Fetch 库（下载管理器）
FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(this)
    .setDownloadConcurrentLimit(3)
    .build();

fetch.enqueue(request, successCallback, errorCallback);

// 下载监听器
fetch.addListener(new FetchListener() {
    onCompleted()   // 下载完成
    onError()       // 下载失败
});
```

---

## 五、业务逻辑亮点

### 1. 智能单词识别

```java
// 行 278-296
private void setTargetWord() {
    if (mTargetWord != null) {
        // 如果预设了目标单词，自动选中
        item.setSelected(true);
        act.setText(mTargetWord);
        asyncSearch(mTargetWord);
    } else {
        // 如果输入只是纯英文，直接查询
        if(mTextToProcess.matches("[a-zA-Z\\-]*")){
            act.setText(mTextToProcess);
            asyncSearch(mTextToProcess);
        }
    }
}
```

### 2. 方案自动恢复

```java
// 行 360-424
// 智能方案选择逻辑：
1. 优先使用 Intent 指定的方案
2. 否则使用上次使用的方案
3. 如果上次方案不存在，使用第一个方案
4. 所有方案无效时显示错误提示
```

### 3. 防止数据丢失

```java
// 默认使用 append 追加模式
// 用户必须明确指定 replace 模式
// 避免"覆盖"导致的意外数据丢失
```

### 4. 自动完成适配

```java
// 行 875-899
// 根据词典类型设置不同的自动完成适配器：
- SimpleCursorAdapter（本地词典）
- UrbanAutoCompleteAdapter（Urban Dictionary）
```

---

## 六、已知问题和改进空间

### 1. 线程管理

```java
// 行 818-838
// 使用 new Thread() 而不是线程池
// 建议改用 ExecutorService
Thread thread = new Thread(new Runnable() { ... });
thread.start();
```

### 2. 内存泄漏风险

```java
// 行 199-230
// Handler 非静态内部类，可能泄漏 Activity
@SuppressLint("HandlerLeak")
final Handler mHandler = new Handler() { ... }
```

### 3. 方法过长

```java
// getCardFromDefinition() 方法 472 行！
// 应该拆分为更小的方法：
- buildDefinitionCard()
- setupAudioPlayback()
- setupAddButtonListener()
- handleMediaDownload()
```

### 4. 硬编码字符串

```java
// 行 1293
if (field.replace(" ", "").toLowerCase().equals("noteid")) {
    // 应该使用常量
}
```

### 5. 异常处理不完善

```java
// 行 759-761
catch(Exception e){
    // 空异常处理，应该至少记录日志
}
```

---

## 七、业务流程图总结

```
┌─────────────────────────────────────────────────────────────┐
│                     PopupActivity 入口                       │
│  (剪贴板/分享/文本选择/Intent)                                │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│              文本分割 + 单词选择                             │
│  (TextSplitter + BigBangLayout)                              │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│              异步词典查询                                    │
│  (Dictionary.wordLookup)                                      │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│           显示定义结果 + 用户交互                            │
│  - 查看定义                                                   │
│  - 播放音频                                                   │
│  - 编辑标签/笔记                                              │
│  - 翻译句子                                                   │
│  - 切换方案                                                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│              字段映射 + 媒体下载                             │
│  (OutputPlan 字段映射 + Fetch 下载音频/图片)                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│              添加/更新 Anki 卡片                             │
│  (AnkiDroid API: addNote / updateNoteFields)                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 总结

PopupActivity 是一个功能丰富但职责过重的组件（1785 行）。它实现了完整的"文本→词典→卡片"工作流，但存在以下特点：

**优点：**
- 业务逻辑完整，功能强大
- 支持多种输入方式和词典
- 智能的状态恢复和配置管理
- 防数据丢失的设计

**需要改进：**
- 方法过长，职责不清晰
- 缺少架构模式（如 MVP/MVVM）
- 线程和内存管理有优化空间
- 测试覆盖率低

建议重构为更小的、职责单一的组件，并引入更现代的架构模式。

---

**文档生成时间:** 2026-01-01
**分析源文件:** `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java`
**源文件行数:** 1785 行