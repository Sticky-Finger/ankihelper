# 笔记持久化 + 笔记按钮 UI 状态指示

## 上下文

当前问题：

1. **笔记被清空**：添加卡片到 Anki 后（`PopupActivity.java:1370`），`mNoteEditedByUser = ""` 被硬编码清空。用户希望添加卡片后笔记保留。

2. **无视觉提示**：笔记按钮（`footer_note`，✏️图标）一直保持相同外观，用户无法一眼看出是否已有笔记内容。

## 修改方案

### 文件：`ui/popup/PopupActivity.java`

#### 1. 笔记不清空

删除第 1370 行 `mNoteEditedByUser = "";`，添加卡片成功后不再清空笔记。

**注意**：`resetPopupState()` 中的 `mNoteEditedByUser = ""` 保留不动（那是新 Intent 进入时重置整次划词会话的状态，与「添加卡片后保持笔记」不冲突——添加卡片不触发 onNewIntent）。

#### 2. 笔记按钮 UI 指示

新增 `updateNoteButtonUI()` 方法：
```java
private void updateNoteButtonUI() {
    boolean hasContent = mNoteEditedByUser != null && !mNoteEditedByUser.isEmpty();
    mBtnEditNote.setAlpha(hasContent ? 1.0f : 0.6f);
}
```
- 笔记为空 → `alpha 0.6f`（半透明，当前默认）
- 笔记有内容 → `alpha 1.0f`（完全不透明，颜色加深效果）

**调用时机**（3 处）：

| 位置 | 场景 |
|------|------|
| `setupEditNoteDialog()` — 对话确认回调内 `mNoteEditedByUser = edt.getText().toString()` 之后 | 用户编辑笔记确认后立即刷新图标 |
| `onResume()` — 已有 `updateLockButtonUI()` 旁 | Activity 恢复时刷新 |
| 添加卡片成功后（第 1370 行附近）— `clearBigbangSelection()` 之后 | 如果将来其他地方修改笔记内容，保持同步 |

### 涉及文件

| 文件 | 改什么 |
|------|--------|
| `ui/popup/PopupActivity.java` | 删除第 1370 行 `mNoteEditedByUser = ""`；新增 `updateNoteButtonUI()` 方法；在 `setupEditNoteDialog()`、`onResume()`、添加卡片成功处调用 |

仅此一个文件，无新增资源。

## 验证

1. 添加单词后笔记保留：打开 Popup → 写笔记 → 点 + 添加 → 确认笔记内容还在
2. 笔记按钮视觉反馈：无内容时半透明 → 写笔记 → 图标变实 → 清除（编辑为空）→ 恢复半透明
