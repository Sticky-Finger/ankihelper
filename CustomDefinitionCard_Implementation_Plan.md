# 添加自定义释义卡片功能 - 实现计划

## 需求概述

当用户选择单词后，在词典查询结果中添加一个"自定义释义"卡片：
- 该卡片默认填充选中的单词
- 用户可以手动编辑释义、音标等内容
- 编辑后的内容可以正常添加到 Anki

## 问题根源分析

1. **单词识别不准确**：
   - 文件：`app/src/main/java/com/mmjang/ankihelper/data/dict/Collins.java:70-98`
   - 问题：`wordLookup()` 方法会调用 `getForms(key)` 获取单词原形，导致"tabular"被识别为"table"

2. **查不到释义**：
   - 当本地词典未查到时，显示"本地词典未查到，以下是有道在线释义"
   - 用户无法手动编辑或补充内容

## 实现方案

### 方案概述

在查询结果列表中添加一个"自定义释义"卡片，该卡片：
- 默认显示选中的单词
- 提供"编辑"按钮，点击后弹出对话框允许用户编辑内容
- 编辑后的内容可以正常添加到 Anki

### 具体实现步骤

#### 步骤 1：创建自定义 Definition 对象

**文件**：`app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java`

**位置**：在 `processDefinitionList()` 方法中，处理查询结果后添加自定义卡片

**实现逻辑**：
```java
private void processDefinitionList(List<Definition> definitionList) {
    viewDefinitionList.removeAllViewsInLayout();

    // 先显示词典查询结果
    for (Definition def : definitionList) {
        viewDefinitionList.addView(getCardFromDefinition(def));
    }

    // 添加自定义释义卡片
    Definition customDef = createCustomDefinition(mCurrentKeyWord);
    viewDefinitionList.addView(getCustomCardFromDefinition(customDef));
}
```

#### 步骤 2：创建自定义 Definition

**方法**：`createCustomDefinition(String word)`

**实现逻辑**：
```java
private Definition createCustomDefinition(String word) {
    Map<String, String> exportElements = new HashMap<>();
    exportElements.put("单词", word);
    exportElements.put("释义", "");  // 默认为空，用户可编辑
    exportElements.put("音标", "");  // 默认为空，用户可编辑

    String displayHtml = "<b>" + word + "</b><br/>[自定义释义 - 点击编辑按钮填写内容]";

    return new Definition(exportElements, displayHtml);
}
```

#### 步骤 3：创建自定义卡片视图

**方法**：`getCustomCardFromDefinition(Definition def)`

**实现逻辑**：
- 复用现有的 `getCardFromDefinition()` 方法
- 在卡片上添加"编辑"按钮（替换或附加到"添加到 Anki"按钮）

```java
private View getCustomCardFromDefinition(final Definition def) {
    View view = LayoutInflater.from(PopupActivity.this)
            .inflate(R.layout.definition_item, null);

    TextView textVeiwDefinition = view.findViewById(R.id.textview_definition);
    ImageButton btnAddDefinition = view.findViewById(R.id.btn_add_definition);

    // 显示内容
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        textVeiwDefinition.setText(Html.fromHtml(def.getDisplayHtml(), Html.FROM_HTML_MODE_COMPACT));
    } else {
        textVeiwDefinition.setText(Html.fromHtml(def.getDisplayHtml()));
    }

    // 添加到 Anki 按钮逻辑（与现有代码相同）
    btnAddDefinition.setOnClickListener(...);

    // 添加编辑按钮
    ImageButton btnEdit = view.findViewById(R.id.btn_edit_definition);  // 需要在布局中添加
    btnEdit.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            setupEditCustomDefinitionDialog(def);
        }
    });

    return view;
}
```

#### 步骤 4：创建编辑对话框

**方法**：`setupEditCustomDefinitionDialog(Definition def)`

**实现逻辑**：
- 参考 `setupEditNoteDialog()` 方法
- 创建对话框，包含多个 EditText：
  - 单词（默认填充选中的单词）
  - 释义（默认为空）
  - 音标（默认为空）
  - 例句（可选）

```java
private void setupEditCustomDefinitionDialog(final Definition def) {
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(PopupActivity.this);
    LayoutInflater inflater = PopupActivity.this.getLayoutInflater();
    final View dialogView = inflater.inflate(R.layout.dialog_edit_custom_definition, null);
    dialogBuilder.setView(dialogView);

    final EditText editWord = dialogView.findViewById(R.id.edit_word);
    final EditText editDefinition = dialogView.findViewById(R.id.edit_definition);
    final EditText editPhonetic = dialogView.findViewById(R.id.edit_phonetic);

    // 填充当前值
    editWord.setText(def.getExportElement("单词"));
    editDefinition.setText(def.getExportElement("释义"));
    editPhonetic.setText(def.getExportElement("音标"));

    dialogBuilder.setTitle("编辑自定义释义");
    dialogBuilder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            // 更新 Definition 的内容
            String newWord = editWord.getText().toString();
            String newDefinition = editDefinition.getText().toString();
            String newPhonetic = editPhonetic.getText().toString();

            // 更新 exportElements
            Map<String, String> exportElements = new HashMap<>();
            exportElements.put("单词", newWord);
            exportElements.put("释义", newDefinition);
            exportElements.put("音标", newPhonetic);

            // 更新显示 HTML
            String displayHtml = "<b>" + newWord + "</b>";
            if (!newPhonetic.isEmpty()) {
                displayHtml += " [" + newPhonetic + "]";
            }
            displayHtml += "<br/>" + newDefinition;

            // 更新 Definition 对象（需要添加 setter 方法或重新创建）
            // 刷新显示
            refreshCustomDefinitionCard(def, exportElements, displayHtml);
        }
    });

    AlertDialog b = dialogBuilder.create();
    b.show();
}
```

#### 步骤 5：创建对话框布局文件

**文件**：`app/src/main/res/layout/dialog_edit_custom_definition.xml`

**布局内容**：
```xml
<LinearLayout
    android:orientation="vertical"
    android:padding="16dp"
    ...>

    <TextView
        android:text="单词"
        .../>

    <EditText
        android:id="@+id/edit_word"
        android:hint="输入单词"
        .../>

    <TextView
        android:text="音标"
        .../>

    <EditText
        android:id="@+id/edit_phonetic"
        android:hint="输入音标（可选）"
        .../>

    <TextView
        android:text="释义"
        .../>

    <EditText
        android:id="@+id/edit_definition"
        android:hint="输入释义"
        android:minLines="3"
        .../>
</LinearLayout>
```

#### 步骤 6：添加字符串资源

**文件**：`app/src/main/res/values/strings.xml`

**添加内容**：
```xml
<string name="dialog_edit_custom_definition">编辑自定义释义</string>
<string name="hint_custom_definition_word">输入单词</string>
<string name="hint_custom_definition_phonetic">输入音标（可选）</string>
<string name="hint_custom_definition">输入释义</string>
<string name="custom_definition_title">自定义释义</string>
<string name="custom_definition_hint">[自定义释义 - 点击编辑按钮填写内容]</string>
```

#### 步骤 7：修改卡片布局（可选）

**文件**：`app/src/main/res/layout/definition_item.xml`

**修改内容**：在现有的"添加到 Anki"按钮旁边添加"编辑"按钮

```xml
<LinearLayout
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">

    <ImageButton
        android:id="@+id/btn_edit_definition"
        android:layout_width="25dp"
        android:layout_height="25dp"
        android:layout_margin="5dp"
        android:padding="10dp"
        android:background="?attr/icon_edit"
        android:visibility="gone"
        />

    <ImageButton
        android:id="@+id/btn_add_definition"
        .../>
</LinearLayout>
```

**注意**：这个按钮只在自定义释义卡片上显示，可以通过代码控制其可见性。

#### 步骤 8：处理 Definition 更新

**问题**：Definition 类目前没有 setter 方法，无法更新其内容

**解决方案**：修改 Definition 类，添加 setter 方法或添加更新方法

**文件**：`app/src/main/java/com/mmjang/ankihelper/data/dict/Definition.java`

**添加方法**：
```java
public void updateDisplayHtml(String displayHtml) {
    this.displayHtml = displayHtml;
}

public void updateExportElement(String key, String value) {
    this.exportElements.put(key, value);
}
```

或者，创建一个新的 Definition 对象并替换列表中的旧对象。

## 关键文件列表

### 需要修改的文件

1. **PopupActivity.java**
   - 路径：`app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java`
   - 修改内容：
     - 添加 `createCustomDefinition()` 方法
     - 添加 `getCustomCardFromDefinition()` 方法
     - 添加 `setupEditCustomDefinitionDialog()` 方法
     - 修改 `processDefinitionList()` 方法，添加自定义卡片
     - 添加 `refreshCustomDefinitionCard()` 方法（如果需要）

2. **Definition.java**
   - 路径：`app/src/main/java/com/mmjang/ankihelper/data/dict/Definition.java`
   - 修改内容：
     - 添加 setter 方法或 update 方法

3. **definition_item.xml**（可选）
   - 路径：`app/src/main/res/layout/definition_item.xml`
   - 修改内容：
     - 添加编辑按钮（如果需要在所有卡片上显示）

### 需要新建的文件

1. **dialog_edit_custom_definition.xml**
   - 路径：`app/src/main/res/layout/dialog_edit_custom_definition.xml`
   - 内容：编辑对话框的布局

2. **strings.xml**（添加资源）
   - 路径：`app/src/main/res/values/strings.xml`
   - 内容：添加对话框相关的字符串资源

## 实现优先级

### 高优先级（必须实现）
1. 创建 `createCustomDefinition()` 方法
2. 修改 `processDefinitionList()` 方法，添加自定义卡片
3. 创建 `setupEditCustomDefinitionDialog()` 方法
4. 创建 `dialog_edit_custom_definition.xml` 布局文件
5. 修改 Definition 类，添加更新方法

### 中优先级（建议实现）
1. 添加字符串资源
2. 完善编辑对话框的验证逻辑
3. 添加编辑后的视觉反馈

### 低优先级（可选）
1. 修改 `definition_item.xml`，添加独立的编辑按钮
2. 添加自定义释义卡片的特殊样式
3. 保存自定义释义到本地数据库

## 测试计划

### 测试场景

1. **基本功能测试**：
   - 选择单词后，检查是否显示自定义释义卡片
   - 点击编辑按钮，检查对话框是否正常弹出
   - 编辑内容后，检查卡片是否正确更新
   - 点击"添加到 Anki"，检查是否能正常添加

2. **边界情况测试**：
   - 选择的单词为空
   - 编辑的内容包含特殊字符
   - 编辑后单词发生变化

3. **集成测试**：
   - 与现有词典查询结果一起显示
   - 切换不同方案时自定义卡片的表现
   - 多次选择单词时的行为

## 注意事项

1. **兼容性**：
   - 确保 Android 4.4+ (API 19) 的兼容性
   - 使用 `Build.VERSION.SDK_INT` 检查 API 级别

2. **性能**：
   - 避免频繁创建和销毁对话框
   - 合理使用 `ViewHolder` 模式（如果使用 RecyclerView）

3. **用户体验**：
   - 自定义卡片应该有明显的视觉区分
   - 编辑按钮应该易于点击
   - 对话框应该提供清晰的提示

4. **数据一致性**：
   - 确保编辑后的数据能正确导出到 Anki
   - 处理空值和非法输入