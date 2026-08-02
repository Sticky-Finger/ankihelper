# AnkiHelper 添加卡片时的模板（NoteType）选取逻辑分析

> 生成日期: 2026-08-02
> 分析范围: 添加单词卡片时，AnkiHelper 如何确定使用 AnkiDroid 中的哪个笔记类型（model/notetype）
> 涉及代码: `DefaultPlan.java` / `PlanEditorActivity.java` / `PopupActivity.java` / `AnkiDroidHelper.java` / `OutputPlan.java`

---

## 一、核心结论

AnkiHelper 中存在 **两条完全不同** 的模板选取路径，健壮性差异极大：

| 路径 | 触发场景 | 选取方式 | 健壮性 |
|------|---------|---------|--------|
| **A. 默认方案** | 点击"添加默认方案"（`LauncherActivity.btn_add_default_plan`） | **按模板名查找 + 按需创建** | 高 |
| **B. 手动新建/编辑方案** | 在 `PlanEditorActivity` 里用户新建方案 | **取模板列表第 0 项** | 低 |
| **C. 加卡时** | `PopupActivity` 点加号按钮 | **直接透传方案里已存的 ID** | 仅依赖 A/B 路径存入的值 |

> **添加卡片时用的是已持久化的 `modelId`，不会重新查找、不会重新排序。** 一旦方案保存，模板 ID 就冻结，加卡路径只做"裸 ID 透传"，没有任何兜底校验。

---

## 二、关键数据结构

### `OutputPlan.outputModelId`（`OutputPlan.java:19`）

```java
private long outputModelId;   // 持久化到 SQLite（LitePal），跨次启动保持
```

- **类型**: `long`，对应 AnkiDroid 数据库 `notetypes` 表的主键 `id`（毫秒级时间戳，由 AnkiDroid 在创建模板时分配）
- **写入时机**: 仅在方案编辑器"保存"时写一次
- **读取时机**: 每次加卡时直接读取，**不重新查 AnkiDroid**

---

## 三、路径 A：默认方案的模板选取（健壮）

### 入口

`LauncherActivity.askIfAddDefaultPlan()`（`LauncherActivity.java:372`）→ 弹确认框 → `new DefaultPlan(ctx).addDefaultPlan()`。

### 核心方法 `DefaultPlan.getDefaultModelId()`（`DefaultPlan.java:76-92`）

```java
long getDefaultModelId(){
    Long mid = mAnkidroid.findModelIdByName(
        DEFAULT_VOCABULARY_MODEL_NAME,        // "划词助手Antimoon模板"（硬编码常量）
        VocabularyCardModel.FILEDS.length     // 7（最小字段数门槛）
    );
    if (mid == null) {
        vc = new VocabularyCardModel(mContext);
        mid = mAnkidroid.getApi().addNewCustomModel(
            DEFAULT_VOCABULARY_MODEL_NAME,
            vc.FILEDS, vc.Cards, vc.QFMT, vc.AFMT, vc.CSS,
            null, null
        );
    }
    return mid;
}
```

### 关键特性

1. **按名称查找**: 通过 `AnkiDroidHelper.findModelIdByName()` 用硬编码常量 `"划词助手Antimoon模板"` 精确匹配。
2. **字段数门槛**: 第二参数 `numFields=7`，内部调 `getModelList(7)` 只返回字段数 ≥ 7 的模板。
3. **查不到则自动创建**: 通过 `AddContentApi.addNewCustomModel()` 在 AnkiDroid 里创建一个标准模板，字段定义见 `VocabularyCardModel.FILEDS`：
   ```
   单词 / 音标 / 释义 / 笔记 / 例句 / url / 发音
   ```
4. **存在性优于创建**: 已有同名同字段模板时复用现成的，不重复创建。

### `findModelIdByName` 的查找逻辑（`AnkiDroidHelper.java:158-174`）

```java
public Long findModelIdByName(String modelName, int numFields) {
    // 1) 先查 SharedPreferences 缓存（容忍用户在 AnkiDroid 端重命名）
    SharedPreferences modelsDb = mContext.getSharedPreferences(MODEL_REF_DB, MODE_PRIVATE);
    long prefsModelId = modelsDb.getLong(modelName, -1L);
    if (prefsModelId != -1L
        && mApi.getModelName(prefsModelId) != null
        && mApi.getFieldList(prefsModelId).length >= numFields) {
        return prefsModelId;
    }
    // 2) 缓存失效则按名称在 AnkiDroid 列表里精确匹配
    Map<Long, String> modelList = mApi.getModelList(numFields);
    for (Map.Entry<Long, String> entry : modelList.entrySet()) {
        if (entry.getValue().equals(modelName)) {
            return entry.getKey();
        }
    }
    return null;
}
```

- **容忍重命名**: 即使用户在 AnkiDroid 里改过模板名，只要原 ID 还在且字段数够，仍能命中。
- **按 numFields 过滤**: `getModelList(numFields)` 在 API 层就排除字段数不足的模板。

---

## 四、路径 B：手动新建方案的默认值选取（脆弱）

### 入口

`PlanEditorActivity.populateDecksAndModels()` 的 `else`（新建）分支（`PlanEditorActivity.java:240-244`）：

```java
} else {
    currentDeckId = Utils.getMapKeyArray(deckList)[0];
    currentModelId = Utils.getMapKeyArray(modelList)[0];   // ← 取列表第 0 项
    refreshFieldSpinners();
}
```

### 关键问题

1. **取第 0 项，无任何匹配**: `getMapKeyArray()` 把 `Map<Long,String>` 的所有 key 装进数组，取下标 0，**不看模板名、不看字段数、不做筛选**。
2. **不带字段数过滤**: 此处用的是 `getModelList()` 无参重载（`PlanEditorActivity.java:148`），返回 AnkiDroid 里**所有** notetype。
3. **"第 0 项"的顺序不稳定**: 虽然 `Utils.hashMap2LinkedHashMap`（`Utils.java:62-74`）里写了 `Arrays.sort(keyArray)`，但排序结果**未被使用**，循环仍遍历原 HashMap 的 `keySet()`，所以顺序等同于 HashMap 哈希桶遍历顺序——不可预测。

### 编辑已有方案时的回显（`PlanEditorActivity.java:217-239`）

```java
long savedModelId = planForEdit.getOutputModelId();
long[] modelIdList = Utils.getMapKeyArray(modelList);
int modelPos = Utils.getArrayIndex(modelIdList, savedModelId);
if (modelPos == -1) {
    modelPos = 0;    // ← 静默 fallback 到第 0 项，不告警
}
```

**静默回退问题**: 若用户在 AnkiDroid 里删除了原模板，`savedModelId` 在新列表里找不到，代码会**悄悄**回退到第 0 项（哈希顺序的某个模板），用户不察保存后，方案绑定的模板被无声改写。

---

## 五、路径 C：加卡时的模板使用（纯透传）

### 调用点（`PopupActivity.java:1360-1363`）

```java
long deckId = currentOutputPlan.getOutputDeckId();
long modelId = currentOutputPlan.getOutputModelId();
Long result = mAnkiDroid.getApi().addNote(modelId, deckId, exportFields, mTagEditedByUser);
```

### 特征

- **直接读取方案里存的 ID**，传给 `AddContentApi.addNote()`。
- **不查 AnkiDroid**：加卡路径里**完全没有** `getModelList()` / `getModelName()` / `findModelIdByName()` 调用，无任何存在性校验。
- **失败处理弱**: `addNote` 返回 `null` 时（`PopupActivity.java:1398-1399`）只弹一个通用 `Toast("添加失败")`，无定位信息、无"模板已删除"提示。

---

## 六、典型场景行为表

| 场景 | 实际行为 | 原因 |
|------|---------|------|
| 首次"添加默认方案" | ✅ 自动创建 `划词助手Antimoon模板` 并绑定 | 路径 A，`addNewCustomModel` |
| 二次"添加默认方案" | ✅ 复用同名现成模板 | 路径 A，`findModelIdByName` 命中 |
| 默认方案加卡 | ✅ 正常 | 路径 A 存入了正确 ID，路径 C 透传成功 |
| 手动新建方案不改 Spinner | ⚠️ 绑到哈希顺序第 0 项模板（不稳定） | 路径 B，无筛选 |
| 用户在 AnkiDroid 删除已绑定模板 | ❌ 加卡失败，仅弹通用 Toast | 路径 C 无校验 |
| 用户在 AnkiDroid 重命名已绑定模板 | ✅ 加卡正常（ID 不变） | 路径 C 只看 ID，与名称无关 |
| 用户在 AnkiDroid 重建同名模板 | ❌ 原方案失效（新模板是新 ID） | 路径 C 无名称兜底 |
| 编辑方案时原模板已被删 | ⚠️ 静默回退到第 0 项模板 | 路径 B 编辑回显分支 |

---

## 七、跨设备同步场景解释

**现象**: A 设备 AnkiHelper 创建的卡片经 Anki Web 同步到 B 设备；B 设备新装 AnkiHelper 后，加卡仍正常。

**原因**:

1. A 设备通过 `addNewCustomModel` 创建的 `划词助手Antimoon模板` 是 A 设备 AnkiDroid 库里的真实 notetype，**会随 Anki Web 同步**。
2. B 设备 AnkiDroid 同步后，本地存在同名同字段的模板。
3. B 设备新装 AnkiHelper，点击"添加默认方案"时，`DefaultPlan.getDefaultModelId()` 调 `findModelIdByName("划词助手Antimoon模板", 7)` → **按名称在 B 设备本地命中** → 拿到 B 设备本地的 ID 存进方案。
4. 加卡时用 B 设备本地 ID 查 B 设备库，必然成功。

**关键点**: 此场景的健壮性**不依赖** notetype ID 跨设备一致，只依赖两点：
- 模板**名称**是硬编码常量（`"划词助手Antimoon模板"`），跨设备一致；
- Anki Web 同步会复制 notetype 的**字段定义**到对端；
- 默认方案路径走的是**按名称查找**（路径 A），不是依赖固定 ID。

---

## 八、已知缺陷与改进方向

### 缺陷

1. **路径 B 的 `[0]` 默认值不稳定**: 手动新建方案时默认模板由哈希顺序决定，无字段数过滤。
2. **路径 C 无存在性校验**: 模板被删后加卡失败，错误提示笼统。
3. **编辑回显静默回退**: 原模板失效时无声改写 `outputModelId`，用户无感知。
4. **`Utils.hashMap2LinkedHashMap` 排序逻辑无效**: `Arrays.sort(keyArray)` 计算后未使用，是个无效操作。

### 改进方向（仅分析，未实现）

| 缺陷 | 最小修复 | 彻底修复 |
|------|---------|---------|
| 路径 B 默认值不稳定 | 默认值改用 `findModelIdByName(硬编码名, 字段数)` | — |
| 路径 C 无校验 | 加卡前 `getModelName(modelId)` 探测，null 时给明确提示 | 方案同时存 `modelName` 兜底，失效时按名称回查 |
| 编辑回显静默回退 | `modelPos == -1` 时弹提示而非静默 fallback | 同上 |
| 排序逻辑无效 | 删除无用 `Arrays.sort` 或修正为按 key 排序后构造 Map | — |

---

## 九、关键代码索引

| 文件 | 行号 | 作用 |
|------|------|------|
| `data/plan/DefaultPlan.java` | 20-21 | 默认模板/牌组名称硬编码常量 |
| `data/plan/DefaultPlan.java` | 76-92 | `getDefaultModelId()` 按名查找 + 按需创建 |
| `data/plan/DefaultPlan.java` | 32-63 | `addDefaultPlan()` 默认方案入库 |
| `data/plan/VocabularyCardModel.java` | 26-34 | 默认模板字段定义 |
| `data/plan/OutputPlan.java` | 19 | `outputModelId` 字段定义 |
| `anki/AnkiDroidHelper.java` | 158-174 | `findModelIdByName()` 按名称查找 |
| `ui/LauncherActivity.java` | 213-234, 372-416 | "添加默认方案"按钮入口 |
| `ui/plan/PlanEditorActivity.java` | 146-149 | 加载模板列表 |
| `ui/plan/PlanEditorActivity.java` | 209-275 | Spinner 填充与选中处理 |
| `ui/plan/PlanEditorActivity.java` | 376 | 保存方案 |
| `ui/popup/PopupActivity.java` | 1360-1410 | 加卡调用链 |
| `util/Utils.java` | 62-74, 77-85 | `hashMap2LinkedHashMap` / `getMapKeyArray` |

---

**文档生成**: Claude Code
**最后更新**: 2026-08-02
