# 剪贴板锁定 + PopupActivity 状态保留

## 背景

用户在划词时（PopupActivity 打开中）想去别处复制「笔记」内容来粘贴，遇到两个问题：

1. **剪贴板被覆盖**：复制的笔记内容替换了 CBWatcherService 正在监听的剪贴板文本，破坏了划词流程
2. **Popup 被销毁**：切到其他 app 后 PopupActivity 被系统销毁（因为 `noHistory="true"`），之前选的词、写的笔记全丢。只能点通知重新进入，但状态已清空

本功能一次解决这两个问题：添加剪贴板锁定开关 + 让 PopupActivity 可以保活。

---

## 设计方案

### 核心思路

- **三个入口共享一个锁状态**（共享 Settings 中的 `clipboard_locked` 布尔值）：
  1. 通知栏按钮（下拉通知栏点 🔒/🔓）
  2. PopupActivity 底部锁定按钮
  3. 两者同步，任一入口切换，另一个立即感知
- **锁定行为**：`CBWatcherService.performClipboardCheck()` 和 `PopupActivity.onWindowFocusChanged()` 中的剪贴板读取都检查锁状态
- **状态保留**：移除 `noHistory`（让 Activity 切走时保活）+ SharedPreferences 持久化关键字段（兜底）

### 交互流程

```
用户复制单词 A → 通知 → PopupActivity 展示 A
    ↓
用户在 Popup 里选中词、查释义
    ↓
用户点「🔒 锁定」（通知栏或 Popup 底部按钮均可）
    ↓
用户切到其他 app 复制笔记内容 B
    ├─ CBWatcherService 检测到剪贴板变化 → 检查锁状态 → 已锁定，忽略
    ├─ PopupActivity 因移除 noHistory，进入 onStop 但不销毁（短时间）
    └─ 如果 PopupActivity 被杀（长时间或内存压力）→ onPause 已保存状态
    ↓
用户回到 Popup（或点通知重新进入）
    ├─ Activity 还活着 → onResume，状态完整保留
    └─ Activity 已销毁 → 新实例从 SharedPreferences 恢复 mTextToProcess/A、笔记等
    ↓
用户粘贴 B 到笔记框
    ↓
用户点「🔓 解锁」→ CBWatcherService 恢复监听
```

---

## 涉及文件

### 新建文件

| 文件 | 说明 |
|------|------|
| `app/src/main/res/drawable/ic_lock_closed.xml` | 锁定状态图标 (vector drawable) |
| `app/src/main/res/drawable/ic_lock_open.xml` | 解锁状态图标 (vector drawable) |

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `app/src/main/java/com/mmjang/ankihelper/data/Settings.java` | 新增 `clipboard_locked` 布尔字段 + 6 个 Popup 状态字段的 getter/setter |
| `app/src/main/java/com/mmjang/ankihelper/domain/CBWatcherService.java` | `performClipboardCheck()` 加锁检查；通知构建加 `addAction()` 锁定按钮；处理 `ACTION_TOGGLE_LOCK` / `ACTION_UPDATE_NOTIFICATION` 意图；重构通知构建为独立方法 |
| `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java` | 生命周期中保存/恢复状态；底部加锁定按钮；`onNewIntent()` 处理；`onWindowFocusChanged()` 加锁检查 |
| `app/src/main/AndroidManifest.xml` | 移除 PopupActivity 的 `android:noHistory="true"` |
| `app/src/main/res/layout/activity_popup.xml` | 底部 footer 区域新增锁定按钮 |
| `app/src/main/res/values/strings.xml` | 新增锁定相关提示文案（默认英文） |
| `app/src/main/res/values-zh/strings.xml` | 新增锁定相关提示文案（中文） |

---

## 各文件改动详情

### 1. Settings.java — 状态持久化

**新增锁定状态字段：**
```java
private final static String CLIPBOARD_LOCKED = "clipboard_locked";

public boolean getClipboardLocked() {
    return sp.getBoolean(CLIPBOARD_LOCKED, false);  // 默认未锁定
}

public void setClipboardLocked(boolean locked) {
    editor.putBoolean(CLIPBOARD_LOCKED, locked);
    editor.commit();
}
```

**新增 Popup 状态字段（6 个核心字段）：**
```java
private final static String POPUP_TEXT = "popup_text";           // mTextToProcess
private final static String POPUP_TARGET_WORD = "popup_word";    // mTargetWord
private final static String POPUP_NOTE = "popup_note";           // mNoteEditedByUser
private final static String POPUP_TAGS = "popup_tags";           // mTagEditedByUser (逗号分隔)
private final static String POPUP_NOTE_ID = "popup_note_id";     // mUpdateNoteId
private final static String POPUP_UPDATE_ACTION = "popup_action";// mUpdateAction
```

每个字段提供 `getXxx()` / `setXxx()` 方法，遵循现有的 `commit()` 模式。`Set<String>` 类型的 tags 用逗号拼接/拆分序列化（复用 `Utils` 中已有工具方法或内联处理）。

### 2. CBWatcherService.java — 锁定逻辑 + 通知按钮

**重构通知构建：** 将现有的通知创建代码提取为 `buildNotification()` 方法，便于锁定状态变化时重建通知。

**`performClipboardCheck()` 加锁检查（约第 103 行）：**
```java
private void performClipboardCheck() {
    if (!Settings.getInstance(MyApplication.getContext()).getMoniteClipboardQ()) {
        return;
    }
    // 新增：锁定状态检查
    if (Settings.getInstance(MyApplication.getContext()).getClipboardLocked()) {
        return;
    }
    // ... 原有剪贴板读取逻辑 ...
}
```

**`onStartCommand()` 中处理 action 意图：**
```java
if (intent != null && "ACTION_TOGGLE_LOCK".equals(intent.getAction())) {
    boolean current = Settings.getInstance(this).getClipboardLocked();
    Settings.getInstance(this).setClipboardLocked(!current);
    updateNotification();  // 刷新通知以显示新锁状态
    return START_STICKY;
}
if (intent != null && "ACTION_UPDATE_NOTIFICATION".equals(intent.getAction())) {
    updateNotification();
    return START_STICKY;
}
```

**通知构建中加 `addAction()`：**
```java
// 在 buildNotification() 中，builder 构建后
Intent lockIntent = new Intent(this, CBWatcherService.class);
lockIntent.setAction("ACTION_TOGGLE_LOCK");
PendingIntent lockPI = PendingIntent.getService(
    this, 1, lockIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

boolean locked = Settings.getInstance(this).getClipboardLocked();
int iconRes = locked ? R.drawable.ic_lock_open : R.drawable.ic_lock_closed;
String actionText = locked ? getString(R.string.unlock_clipboard) : getString(R.string.lock_clipboard);

builder.addAction(iconRes, actionText, lockPI);
```

**`updateNotification()` 方法：**
```java
private void updateNotification() {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.notify(2333, buildNotification());
}
```
> 注意：不要递归调用 `startService`，直接用 `NotificationManager.notify()` 原地更新。

### 3. PopupActivity.java — 状态保活 + 锁定按钮

**状态保存（onPause）：**
```java
@Override
protected void onPause() {
    super.onPause();
    if (mTextToProcess != null && !mTextToProcess.isEmpty()) {
        settings.setPopupText(mTextToProcess);
        settings.setPopupTargetWord(mTargetWord != null ? mTargetWord : "");
        settings.setPopupNote(mNoteEditedByUser);
        settings.setPopupTags(joinTags(mTagEditedByUser));
        settings.setPopupNoteId(mUpdateNoteId);
        settings.setPopupUpdateAction(mUpdateAction != null ? mUpdateAction : "");
    }
}
```

**状态恢复（onCreate 中 handleIntent 之前）：**
```java
// 在 handleIntent() 调用之前，检查是否需要恢复
String savedText = settings.getPopupText();
if (savedText != null && !savedText.isEmpty() && !isNewContentIntent(getIntent())) {
    mTextToProcess = savedText;
    mTargetWord = settings.getPopupTargetWord();
    mNoteEditedByUser = settings.getPopupNote();
    mTagEditedByUser = splitTags(settings.getPopupTags());
    mUpdateNoteId = settings.getPopupNoteId();
    mUpdateAction = settings.getPopupUpdateAction();
    // 继续正常流程（populateWordSelectBox 等）
}
```

**`onNewIntent()` 处理（singleInstance + REORDER_TO_FRONT 时关键）：**
```java
@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (isNewContentIntent(intent)) {
        clearSavedState();
        handleIntent();  // 处理新划词内容
    }
    updateLockButtonUI();
}
```

**锁定按钮（底部 footer 区域）：**
- `assignViews()` 中绑定 `mBtnLock = findViewById(R.id.footer_lock)`
- 点击时：切换 `settings.setClipboardLocked()` → 更新自身图标 → `startService(CBWatcherService)` 带 `ACTION_UPDATE_NOTIFICATION`
- `onResume()` 中 `updateLockButtonUI()` 根据当前锁状态切换图标

**`onWindowFocusChanged()` 加锁检查（第 693 行附近）：**
```java
if (isFromAndroidQClipboard) {
    if (settings.getClipboardLocked()) {   // 新增
        isFromAndroidQClipboard = false;
        return;
    }
    // ... 原有剪贴板读取逻辑 ...
}
```

### 4. AndroidManifest.xml — 移除 noHistory

```xml
<activity
    android:name=".ui.popup.PopupActivity"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:theme="@style/Transparent"
    android:windowSoftInputMode="stateAlwaysHidden">
    <!-- 移除 android:noHistory="true" -->
</activity>
```

### 5. activity_popup.xml — 底部 footer 加锁按钮

在现有的 `footer_note` 和 `footer_tag` 旁边新增一个 `ImageButton`：

```xml
<ImageButton
    android:id="@+id/footer_lock"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:layout_gravity="center"
    android:padding="8dp"
    android:adjustViewBounds="true"
    android:scaleType="centerInside"
    android:src="@drawable/ic_lock_open"
    android:background="?attr/selectableItemBackgroundBorderless" />
```

### 6. 图标资源 — ic_lock_closed.xml / ic_lock_open.xml

使用 Android Material Design 的锁图标 path data，创建两个 24dp vector drawable：
- `ic_lock_closed.xml`：填充的锁（表示已锁定）
- `ic_lock_open.xml`：打开的锁（表示未锁定）

> 由于项目使用 Support Library 而非 AndroidX，vector drawable 通过 `app:srcCompat` 或直接引用均可。

### 7. 字符串资源

| key | 默认（英文） | 中文 |
|-----|------------|------|
| `lock_clipboard` | Lock Clipboard | 锁定剪贴板 |
| `unlock_clipboard` | Unlock Clipboard | 解锁剪贴板 |
| `clipboard_locked_toast` | Clipboard monitoring locked | 剪贴板监听已锁定 |
| `clipboard_unlocked_toast` | Clipboard monitoring unlocked | 剪贴板监听已解锁 |

---

## 边界情况处理

| 场景 | 行为 |
|------|------|
| 锁定时用户手动清空剪贴板 | `performClipboardCheck()` 直接 return，不处理 |
| 锁定时点通知 | Popup 打开，`onWindowFocusChanged` 检查锁状态 → 已锁，不读剪贴板；恢复持久化的旧状态 |
| 锁定时重启手机 | SharedPreferences 持久化，锁状态保留；CBWatcherService 重启后读锁状态并构建带锁图标的通知 |
| 解锁后剪贴板是笔记内容 B | 用户已解锁表示不再需要保护 A，CBWatcherService 以 B 为新的监听目标，这是预期行为 |
| Popup 被系统内存压力杀掉 | `onPause()` 已持久化状态，下次进入时恢复 |
| 快速反复切换锁定 | `commit()` 同步写，无竞态问题 |

---

## 验证方案

1. **锁定后切走复制**：打开剪贴板查词 → 选一个词 → 点锁定 → 切到浏览器复制一段文字 → 切回 AnkiHelper → 确认没有触发新划词，原有 Popup 状态保留
2. **锁定状态下点通知**：锁定后 → 关闭 Popup → 复制新内容 → 点通知 → 确认恢复的是上次的词和笔记而非新内容
3. **未锁定时点通知**：未锁定 → 关闭 Popup → 复制新内容 → 点通知 → 确认正常读新内容（现有行为不受影响）
4. **通知栏锁定按钮**：下拉通知栏 → 看到 🔒/🔓 按钮 → 点击 → 确认锁状态切换 → 确认 Popup 中按钮图标同步
5. **锁定后手动解锁再复制**：解锁 → 复制新文本 → 确认新触发划词（正常恢复监听）
6. **重启手机后锁状态**：锁定后重启 → 确认通知栏显示锁定状态 → 复制文本 → 确认不触发划词

---

**计划生成:** Claude Code  
**最后更新:** 2026-06-17
