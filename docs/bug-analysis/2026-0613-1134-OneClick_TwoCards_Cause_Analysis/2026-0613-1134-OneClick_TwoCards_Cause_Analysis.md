# 一次添加产生两张卡片的问题分析报告

## 现象描述

使用 AnkiHelper 的划词添加功能，点击一次"添加"按钮，在 AnkiDroid 中会得到两张卡片：
- 一张 **recite**（背诵卡）
- 一张 **type**（打字卡）

## 根因分析

### 从添加流程分析

AnkiHelper 的卡片添加流程中，**只调用了一次** AnkiDroid API 的 `addNote` 方法：

**PopupActivity.java:1279**
```java
Long result = mAnkiDroid.getApi().addNote(modelId, deckId, exportFields, mTagEditedByUser);
```

`addNote` 添加的是**一条笔记（note）**，而不是直接创建卡片。AnkiDroid 收到这条笔记后，会根据该笔记类型（Note Type）中定义的卡片模板（Card Template）数量自动生成对应的卡片。**一条笔记可以对应多张卡片**，这是 Anki 的核心机制。

### 从笔记类型创建流程分析

AnkiHelper 在首次运行或创建默认方案时，会在 AnkiDroid 中创建一个名为 **"划词助手Antimoon模板"** 的笔记类型：

**DefaultPlan.java:76-92**
```java
long getDefaultModelId(){
    Long mid = mAnkidroid.findModelIdByName(DEFAULT_VOCABULARY_MODEL_NAME, ...);
    if (mid == null) {
        vc = new VocabularyCardModel(mContext);
        mid = mAnkidroid.getApi().addNewCustomModel(modelName,
                vc.FILEDS, vc.Cards, vc.QFMT, vc.AFMT, vc.CSS, null, null);
    }
    return mid;
}
```

该笔记类型在创建时通过 `AddContentApi.addNewCustomModel()` 方法定义了**两个卡片模板**：

**VocabularyCardModel.java:24**
```java
String[] Cards = {"recite", "type"};   // 两个卡片模板
```

每个模板都有自己的正面（问题）HTML 和背面（答案）HTML，定义在 `assets/vocabulary_card_model.html` 文件中。

### AnkiDroid API 的 addNote 内部机制

从 `AddContentApi.java` 可以看到，`addNote` 在 AnkiDroid Content Provider 中插入笔记后，会将该笔记生成的所有卡片移动到用户指定的牌组：

**AddContentApi.java:112-129**
```java
Uri cardsUri = Uri.withAppendedPath(newNoteUri, "cards");
final Cursor cardsCursor = mResolver.query(cardsUri, null, null, null, null);
while (cardsCursor.moveToNext()) {
    String ord = cardsCursor.getString(cardsCursor.getColumnIndex(Card.CARD_ORD));
    ContentValues cardValues = new ContentValues();
    cardValues.put(Card.DECK_ID, deckId);
    Uri cardUri = Uri.withAppendedPath(Uri.withAppendedPath(newNoteUri, "cards"), ord);
    mResolver.update(cardUri, cardValues, null, null);
}
```

这里查询 `cardsUri` 返回的结果数量，就是该笔记类型定义的模板数量。**2 个模板 → 2 张卡片**，所以 `addNote` 后 AnkiDroid 会生成 recite 和 type 两张卡片。

## 结论

**非 AnkiHelper 的 Bug，而是 Anki 笔记类型多模板机制的正常行为。**

| 角色 | 行为 |
|------|------|
| AnkiHelper | 调用 `addNote` 添加一条笔记（仅一次） |
| AnkiDroid | 根据笔记类型 "划词助手Antimoon模板" 的 2 个模板（recite + type），自动生成 2 张卡片 |

在 Anki 生态中，一个笔记类型可以定义多个卡片模板以适应不同的复习方式（如：背诵卡片、打字填空卡片），这是设计特性。AnkiHelper 的默认方案沿用了这一机制，为用户提供了两种复习形式的选择。

## 对应解决策略

- **保留双模板但每次只生成一种**：需要在 AnkiHelper 代码中修改 `addNote` 调用，只保留一张卡片（将不需要的卡片移动到已删除牌组）。此方法较为迂回。
- **修改默认笔记类型的模板定义**：在 `VocabularyCardModel.java` 中将 `Cards` 数组缩减为一个模板，并相应调整正面背面 HTML 模板。此改动影响所有新建用户。
- **用户自行在 AnkiDroid 中操作**：编辑笔记类型，删除不需要的模板（注意此操作不可逆，详见下文）。

## 已知问题：删模板后无法恢复

如果用户在 AnkiDroid 中手动删除了 `type` 模板，由于 `DefaultPlan.getDefaultModelId()` 的判断逻辑是"笔记类型名称是否存在"而非"模板是否完整"，**即使模板已不完整，也不会重新创建缺失的模板**。解决方法：在 AnkiDroid 中完全删除"划词助手Antimoon模板"笔记类型，让 AnkiHelper 下次使用时重新创建。
