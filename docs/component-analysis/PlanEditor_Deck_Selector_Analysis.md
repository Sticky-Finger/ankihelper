# 方案编辑器"牌组"选择器组件分析

> 分析日期: 2026-06-02  
> 组件范围: PlanEditorActivity 牌组选择功能

---

## 组件概述

方案编辑器（PlanEditorActivity）中的"牌组"（Deck）选择器用于让用户在创建或编辑输出方案时选择目标 Anki 牌组。该组件基于 Android 标准的 `Spinner` 控件实现，通过下拉列表展示用户 AnkiDroid 中的所有牌组，并在用户选择后保存牌组 ID 到输出方案配置中。

---

## 核心文件

### 1. Java 实现

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/ui/plan/PlanEditorActivity.java`

**关键方法:**

#### `loadDecksAndModels()` — 第 145–148 行
```java
private void loadDecksAndModels() {
    deckList = Utils.hashMap2LinkedHashMap(mAnkiDroid.getApi().getDeckList());
    modelList = Utils.hashMap2LinkedHashMap(mAnkiDroid.getApi().getModelList());
}
```

该方法负责：
- 通过 AnkiDroid API 获取牌组列表
- 获取笔记类型列表
- 将 HashMap 转换为 LinkedHashMap 以保持顺序

#### `populateDecksAndModels()` — 第 207–272 行
该方法负责：
- 构建并设置牌组 Spinner 适配器
- 设置牌组选择监听器
- 同步处理笔记类型选择器

**字段声明:** 第 48、54、59 行
```java
private Map<Long, String> deckList;  // 牌组ID → 牌组名称 的映射
private long currentDeckId;           // 当前选中的牌组ID
private Spinner deckSpinner;          // 牌组选择器控件
```

**View 初始化:** 第 75 行
```java
deckSpinner = findViewById(R.id.deck_spinner);
```

---

### 2. 布局 XML

**文件路径:** `app/src/main/res/layout/activity_plan_editor.xml`

**牌组选择器区域:** 第 57–73 行

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    >
    <TextView
        android:layout_width="0dp"
        android:layout_weight="4"
        android:layout_height="wrap_content"
        android:text="@string/tv_select_deck"
        android:textSize="20sp" />
    <Spinner
        android:id="@+id/deck_spinner"
        android:layout_width="0dp"
        android:layout_weight="6"
        android:layout_height="wrap_content"></Spinner>
</LinearLayout>
```

**组件说明:**
- `LinearLayout`: 水平布局容器
- `TextView` (weight=4): 显示 "Deck:" 标签
- `Spinner` (weight=6): 牌组下拉选择器，id 为 `deck_spinner`

---

### 3. 数据来源

**文件路径:** `app/src/main/java/com/ichi2/anki/api/AddContentApi.java`

**核心方法:** `getDeckList()` — 第 510–524 行

```java
public Map<Long, String> getDeckList() {
    // Get the current model
    final Cursor allDecksCursor = mResolver.query(Deck.CONTENT_ALL_URI, null, null, null, null);
    if (allDecksCursor == null) {
        return null;
    }
    Map<Long, String> decks = new HashMap<>();
    try {
        while (allDecksCursor.moveToNext()) {
            long deckId = allDecksCursor.getLong(allDecksCursor.getColumnIndex(Deck.DECK_ID));
            String name = allDecksCursor.getString(allDecksCursor.getColumnIndex(Deck.DECK_NAME));
            decks.put(deckId, name);
        }
    } finally {
        allDecksCursor.close();
    }
    return decks;
}
```

该方法通过 ContentProvider 查询 AnkiDroid 数据库，获取所有牌组的 ID 和名称映射。

---

## 实现流程

### 完整数据流

```
loadDecksAndModels()
    ↓
mAnkiDroid.getApi().getDeckList()
    ↓
Utils.hashMap2LinkedHashMap() 转换
    ↓
populateDecksAndModels()
    ↓
Utils.getMapValueArray() 提取牌组名称数组
    ↓
创建 ArrayAdapter 并绑定到 Spinner
    ↓
用户点击 Spinner → 弹出下拉列表
    ↓
OnItemSelectedListener.onItemSelected() 触发
    ↓
更新 currentDeckId
    ↓
savePlan() 保存到 OutputPlanPOJO
```

---

### 详细代码逻辑

#### 步骤 1: 加载牌组列表

```java
private void loadDecksAndModels() {
    deckList = Utils.hashMap2LinkedHashMap(mAnkiDroid.getApi().getDeckList());
    modelList = Utils.hashMap2LinkedHashMap(mAnkiDroid.getApi().getModelList());
}
```

通过 AnkiDroid API 获取牌组列表，并使用 `Utils.hashMap2LinkedHashMap()` 转换为 LinkedHashMap 以保持牌组顺序。

#### 步骤 2: 设置 Spinner 适配器

```java
private void populateDecksAndModels() {
    ArrayAdapter<String> deckSpinnerAdapter = new ArrayAdapter<>(
            this, R.layout.support_simple_spinner_dropdown_item, Utils.getMapValueArray(deckList));
    deckSpinner.setAdapter(deckSpinnerAdapter);
```

使用标准 Android 下拉布局创建适配器，`Utils.getMapValueArray()` 提取牌组名称数组。

#### 步骤 3: 编辑已有方案时回显牌组

```java
if (planForEdit != null) {
    // 回显笔记类型
    int modelPosition = Utils.getPositionByKey(modelList, planForEdit.getModelId());
    modelSpinner.setSelection(modelPosition);
    // 回显牌组
    int deckPosition = Utils.getPositionByKey(deckList, planForEdit.getOutputDeckId());
    deckSpinner.setSelection(deckPosition);
    // ... 其他回显逻辑
}
```

如果是编辑已有方案，通过牌组 ID 查找对应位置并选中。

#### 步骤 4: 监听用户选择

```java
deckSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentDeckId = Utils.getMapKeyArray(deckList)[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        }
);
```

用户选择牌组后，通过 `Utils.getMapKeyArray()` 获取对应位置的牌组 ID 并更新 `currentDeckId`。

---

## 关联组件

### 1. 笔记类型选择器

`populateDecksAndModels()` 方法同时初始化牌组和笔记类型两个选择器，它们通常需要配套使用。

### 2. 方案保存

在 `savePlan()` 方法中（约第 346 行），`currentDeckId` 会被保存到 `OutputPlanPOJO` 对象中：

```java
planPOJO.setOutputDeckId(currentDeckId);
```

### 3. 工具类方法

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/util/Utils.java`

- **hashMap2LinkedHashMap()** (第 62–75 行): 将 HashMap 转换为 LinkedHashMap，保持插入顺序
- **getMapKeyArray()** (第 77–85 行): 获取 Map 的键数组（牌组 ID 数组）
- **getMapValueArray()** (第 87–95 行): 获取 Map 的值数组（牌组名称数组）
- **getPositionByKey()**: 根据 key 获取在 Map 中的位置

### 4. 数据模型

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/data/plan/OutputPlanPOJO.java`

```java
private long outputDeckId;  // 第17行

public void setOutputDeckId(long outputDeckId) {
    this.outputDeckId = outputDeckId;
}

public long getOutputDeckId() {
    return outputDeckId;
}
```

### 5. 字符串资源

**文件路径:** `app/src/main/res/values/strings.xml`

```xml
<string name="tv_select_deck">Deck:</string>
```

---

## 设计特点

1. **标准控件**: 使用 Android 原生 `Spinner`，交互简洁直观
2. **ID-Name 映射**: 通过 Map 结构维护牌组 ID 和名称的映射关系
3. **顺序保持**: 使用 LinkedHashMap 确保牌组按固定顺序显示
4. **状态回显**: 编辑模式下自动定位并选中已有牌组
5. **数据源集成**: 直接从 AnkiDroid 数据库获取牌组列表，数据实时同步

---

## 与词典选择器的对比

| 特性 | 牌组选择器 | 词典选择器 |
|------|-----------|-----------|
| 数据来源 | AnkiDroid API（ContentProvider） | DictionaryRegister（本地注册） |
| 数据结构 | Map<Long, String>（ID → 名称） | List<IDictionary>（对象列表） |
| 存储值 | long（牌组 ID） | String（词典名称 key） |
| 数据刷新 | 每次打开重新获取 | 缓存到 dictionaryList |
| 关联组件 | 笔记类型选择器 | 字段映射区域 |

---

## 已知问题（已解决）：牌组列表过长查找不便

### 问题现象

当用户 AnkiDroid 中的牌组数量较多时（50+），方案编辑器中的"牌组"选择器下拉列表过长，用户需要滚动查找特定牌组，体验不佳。

### 问题原因

**根本原因：** 使用标准 Android `Spinner` 控件，下拉列表直接展示所有牌组，没有搜索或过滤功能。

**影响范围：**
- 拥有大量牌组的重度用户
- 需要频繁切换牌组的场景
- 牌组名称相似的查找场景

### 解决方案：对话框搜索

保持原有 Spinner 控件不变，点击时弹出带有搜索框的对话框。对话框中显示可搜索过滤的牌组列表，用户选择后更新 Spinner 显示内容。

**UI 交互流程：**
```
用户点击 Spinner
    ↓
弹出牌组选择对话框（DeckSelectionDialog）
    ↓
对话框顶部：搜索 EditText + RecyclerView 牌组列表
    ↓
用户输入关键字 → RecyclerView 过滤显示
    ↓
用户点击某个牌组
    ↓
关闭对话框，更新 Spinner 显示选中牌组，更新 currentDeckId
```

### 涉及文件

#### 新建文件

| 文件 | 说明 |
|------|------|
| `app/src/main/res/layout/dialog_deck_selection.xml` | 牌组选择对话框布局 |
| `app/src/main/res/layout/item_deck_selection.xml` | 牌组列表项布局 |
| `app/src/main/java/com/mmjang/ankihelper/ui/plan/DeckSelectionDialog.java` | 牌组选择对话框类 |
| `app/src/main/java/com/mmjang/ankihelper/ui/plan/DeckSearchAdapter.java` | RecyclerView 适配器 |

#### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `app/src/main/java/com/mmjang/ankihelper/ui/plan/PlanEditorActivity.java` | 移除 Spinner 默认下拉监听，添加触摸拦截和对话框回调 |
| `app/src/main/res/values/strings.xml` | 添加搜索相关字符串 |

### 实现要点

1. **Spinner 默认行为拦截**：使用 `setOnTouchListener` 并 `return true` 消费所有触摸事件，阻止 Spinner 弹出内置下拉列表
2. **点击弹出对话框**：在 `ACTION_UP` 事件中调用 `showDeckSelectionDialog()` 弹出自定义搜索对话框
3. **当前选中显示**：通过 `updateDeckSpinnerDisplay()` 将 Spinner 适配器替换为仅含当前选中名称的单行适配器
4. **搜索过滤**：`DeckSearchAdapter.filter()` 方法对牌组名称进行大小写不敏感的 `contains` 匹配

### 验证结果

经过编译安装和功能测试验证：
- 点击牌组 Spinner → 弹出搜索对话框 ✅
- 输入关键字 → 牌组列表实时过滤 ✅
- 选择牌组 → 对话框关闭，Spinner 更新显示选中名称 ✅
- 编辑已有方案 → 牌组名称正确回显 ✅
- 保存方案 → 牌组 ID 正确保存 ✅

---

**文档生成:** Claude Code
**最后更新:** 2026-06-02
