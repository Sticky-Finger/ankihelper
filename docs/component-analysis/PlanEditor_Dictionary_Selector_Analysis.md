# 方案编辑器"选择词典"选择器组件分析

> 分析日期: 2026-06-02  
> 组件范围: PlanEditorActivity 词典选择功能

---

## 组件概述

方案编辑器（PlanEditorActivity）中的"选择词典"选择器用于让用户在创建或编辑输出方案时选择一个词典。该组件基于 Android 标准的 `Spinner` 控件实现，通过下拉列表展示所有可用词典，并在用户选择后更新相关 UI 和数据状态。

---

## 核心文件

### 1. Java 实现

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/ui/plan/PlanEditorActivity.java`

**关键方法:** `populateDictionary()` — 第 150–205 行

该方法负责：
- 从 `DictionaryRegister` 获取词典列表
- 构建并设置 Spinner 适配器
- 编辑已有方案时回显之前选择的词典
- 监听用户选择并更新相关状态

**字段声明:** 第 57–58 行
```java
private Spinner dictionarySpinner;
private TextView dictionaryIntroductionTextView;
```

**View 初始化:** 第 107 行
```java
dictionarySpinner = findViewById(R.id.dictionary_spinner);
```

---

### 2. 布局 XML

**文件路径:** `app/src/main/res/layout/activity_plan_editor.xml`

**词典选择器区域:** 第 33–55 行

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <TextView
        android:layout_width="0dp"
        android:layout_weight="4"
        android:layout_height="wrap_content"
        android:text="@string/tv_choose_dictionary"
        android:textSize="20sp"
        />
    <Spinner
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="6"
        android:id="@+id/dictionary_spinner"
        >
    </Spinner>
</LinearLayout>
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:id="@+id/text_view_dictionary_introduction"
    />
```

**组件说明:**
- `TextView` (第 36–42 行): 显示 "Dictionary:" 标签
- `Spinner` (第 43–49 行): 词典下拉选择器，id 为 `dictionary_spinner`
- `TextView` (第 51–55 行): 显示选中词典的简介，id 为 `text_view_dictionary_introduction`

---

### 3. 数据来源

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/data/dict/DictionaryRegister.java`

该类作为词典注册中心，提供所有已注册词典的列表：

```java
public static List<IDictionary> getDictionaryObjectList()
```

返回的 `List<IDictionary>` 包含所有内置词典和已导入的自定义词典。

---

## 实现流程

### 完整数据流

```
DictionaryRegister.getDictionaryObjectList()
        ↓
提取词典名称数组 (String[])
        ↓
创建 ArrayAdapter 并绑定到 Spinner
        ↓
用户点击 Spinner → 弹出下拉列表
        ↓
OnItemSelectedListener.onItemSelected() 触发
        ↓
更新 currentDictionary + 词典介绍文本 + refreshFieldSpinners()
```

---

### 详细代码逻辑

#### 步骤 1: 加载词典列表

```java
private void populateDictionary() {
    if (dictionaryList == null) {
        dictionaryList = DictionaryRegister.getDictionaryObjectList();
    }

    String[] dictionaryNameList = new String[dictionaryList.size()];
    for (int i = 0; i < dictionaryList.size(); i++) {
        dictionaryNameList[i] = dictionaryList.get(i).getDictionaryName();
    }
```

从 `DictionaryRegister` 获取所有词典对象，提取词典名称构建字符串数组。

#### 步骤 2: 设置 Spinner 适配器

```java
ArrayAdapter<String> dictionarySpinnerAdapter = new ArrayAdapter<>(
        this, R.layout.support_simple_spinner_dropdown_item, dictionaryNameList);
dictionarySpinner.setAdapter(dictionarySpinnerAdapter);
```

使用标准 Android 下拉布局创建适配器并绑定到 Spinner。

#### 步骤 3: 编辑已有方案时回显词典

```java
if (planForEdit != null) {
    String key1 = planForEdit.getDictionaryKey();
    boolean find = false;
    for (int i = 0; i < dictionaryList.size(); i++) {
        IDictionary dict = dictionaryList.get(i);
        String key2 = dict.getDictionaryName();
        if (key1.equals(key2)) {
            currentDictionary = dictionaryList.get(i);
            dictionaryIntroductionTextView.setText(currentDictionary.getIntroduction());
            dictionarySpinner.setSelection(i);
            find = true;
            break;
        }
    }
    if (!find) {
        String message = String.format("词典\"%s\"不存在，请检查是否需要重新导入自定义词典", key1);
        Utils.showMessage(PlanEditorActivity.this, message);
    }
}
```

如果是编辑已有方案，遍历词典列表查找之前保存的词典并选中；若找不到则提示用户。

#### 步骤 4: 监听用户选择

```java
dictionarySpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentDictionary = dictionaryList.get(position);
                dictionaryIntroductionTextView.setText(currentDictionary.getIntroduction());
                refreshFieldSpinners();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        }
);
```

用户选择词典后：
- 更新 `currentDictionary` 字段
- 显示词典简介
- 调用 `refreshFieldSpinners()` 刷新字段映射区域

---

## 关联组件

### 1. 字段映射刷新

`refreshFieldSpinners()` 方法在词典变更时被调用，根据新词典的 `getExportElementsList()` 更新字段映射下拉列表。

### 2. 方案保存

在 `savePlan()` 方法中（约第 345 行），`currentDictionary` 的 key 会被保存到 `OutputPlan` 对象中。

### 3. 字符串资源

**文件路径:** `app/src/main/res/values/strings.xml`

```xml
<string name="tv_choose_dictionary">Dictionary:</string>
```

---

## 设计特点

1. **标准控件**: 使用 Android 原生 `Spinner`，交互简洁直观
2. **数据解耦**: 词典数据通过 `DictionaryRegister` 统一管理，便于扩展
3. **状态回显**: 编辑模式下自动定位并选中已有词典
4. **错误处理**: 词典不存在时给出中文提示，引导用户检查自定义词典
5. **联动更新**: 词典选择变更后自动刷新字段映射区域

---

## 潜在改进点

1. **搜索功能**: 词典数量较多时（23+ 内置词典），可增加搜索过滤
2. **分类展示**: 按语言或类型分组显示词典（英语、日语、多语言等）
3. **自定义词典标识**: 在词典名称后标注"自定义"以区分内置词典

---

## 已知问题：本地词典数据库文件缺失

### 问题现象

编译安装的 App 在"选择词典"下拉列表中找不到"柯林斯英汉双解"、"牛津词典"等本地词典选项，且没有任何错误提示。

### 问题原因

**根本原因：本地数据库文件缺失。** 具体链条如下：

1. **Git 历史中删除了数据库文件** — 提交 `feddb3f`（2017-05-03）将 `collins.db` 和 `ode2_v2.db` 从 `app/src/main/assets/databases/` 目录中移除，并加入了 `.gitignore`
2. **代码引用了新的文件名** — `Collins.java` 中 `DATABASE_NAME` 从 `"collins.db"` 改为了 `"collins_v2.db"`，但 git 中从未存在过 `collins_v2.db`
3. **异常被静默吞掉** — `DictionaryRegister.getDictionaryObjectList()` 中，反射实例化词典时的 `catch` 块（第57-61行）捕获了 `InvocationTargetException` 后不做任何处理，导致 Collins 构造失败时没有任何报错提示，只是悄悄从列表中消失

**触发流程：**
```
Collins 构造函数 → SQLiteAssetHelper → getReadableDatabase()
→ 找不到 collins_v2.db → 抛出异常
→ DictionaryRegister 静默捕获 → 词典未加入列表 → 用户看不到选项
```

### 解决办法

从 git 历史中恢复数据库文件，放到当前代码期望的路径和文件名：

```bash
mkdir -p app/src/main/assets/databases/

# collins.db 在代码中已改名为 collins_v2.db，需要重命名
git show feddb3f~1:app/src/main/assets/databases/collins.db \
    > app/src/main/assets/databases/collins_v2.db

# ode2_v2.db 文件名未变，直接恢复
git show feddb3f~1:app/src/main/assets/databases/ode2_v2.db \
    > app/src/main/assets/databases/ode2_v2.db
```

### 验证结果

恢复两个数据库文件后重新编译：
- "柯林斯英汉双解"词典选项出现，且查询功能正常，与老版本表现一致
- "牛津词典"（Ode2）选项同样出现，缺失的本地词典全部恢复正常

### 遗留问题：数据库文件版本不匹配

**问题现象：** 从 git 历史恢复的 `collins_v2.db` 和 `ode2_v2.db` 虽然能让词典选项出现并正常查询，但查出的释义显示格式异常——换行和空格过多，排版不紧凑，与老版本 App 的表现有差异。

**问题原因：** git 历史中的数据库文件是 2017 年删除前的旧版本，而用户在用的老版本安装包中的数据库文件经过了后续优化和调整，两者不是同一版本。

**解决方案：** 直接从用户正在使用的老版本 App 安装包（APK）中提取数据库文件，替换到当前项目的 `app/src/main/assets/databases/` 目录下，然后重新编译测试。APK 本质是 ZIP 文件，解压后在 `assets/databases/` 下可找到全部 4 个数据库文件（`collins_v2.db` 32M、`forms.db` 5.2M、`ode2_v2.db` 47M、`wb_headwords.db` 2.0M），其中 `forms.db` 和 `wb_headwords.db` 是 git 历史中也没有的额外文件。

**解决方案及验证：**

替换 4 个数据库文件后重新编译安装，释义排版清爽紧凑，与老版本 App 表现一致。注意两点：
1. **`assets/` 目录下所有文件都会打包进 APK**，不要在该目录放置无关文件（如备份 .zip），否则会增大 APK 体积
2. **安装新编译版本前需先卸载老应用**，因为 SQLiteAssetHelper 首次访问数据库时会将 `.db` 文件复制到应用内部存储，之后不再更新；不卸载则仍使用旧数据库缓存副本

---

**文档生成:** Claude Code  
**最后更新:** 2026-06-02
