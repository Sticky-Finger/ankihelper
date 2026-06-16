# 剪切板查词通知栏常驻消息组件分析

> 分析日期: 2026-06-17
> 组件范围: CBWatcherService 前台服务通知（下拉通知栏中的「Anki划词助手 复制文本后点此通知划词」常驻项）

---

## 组件概述

当用户在主界面（`LauncherActivity`）打开「剪切板查词」开关后，应用启动前台服务 `CBWatcherService` 监听系统剪贴板。Android 前台服务必须挂一条常驻通知，本组件就是这条通知：标题为「Anki 划词助手」，内容根据系统版本分别为「复制文本后点此通知划词」（Android 10+）或「剪切板服务正在运行」（Android 9 及以下）。点击通知会拉起 `PopupActivity`，读取剪贴板内容进入划词界面。

这条通知是 Android 10+ 上剪贴板功能的关键补偿机制：Android 10 起后台应用不能直接读取剪贴板，但点击通知触发的是「前台交互」，可以合法读取剪贴板内容。

---

## 核心文件

### 1. 服务实现（通知生成位置）

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/domain/CBWatcherService.java`

**关键方法:**

#### `onStartCommand()` — 第 52–96 行

负责创建通知通道、构建 `NotificationCompat.Builder`、调用 `startForeground(2333, noti)` 把通知挂到前台。每次服务被启动或重启都会执行。

#### `performClipboardCheck()` — 第 103–126 行

剪贴板变化回调（Android 9 及以下生效）：直接读取剪贴板文本并 `startActivity(PopupActivity)`。

#### `onDestroy()` — 第 44–49 行

调用 `stopForeground(true)` 移除常驻通知。

#### `onCreate()` — 第 38–41 行

注册 `ClipboardManager.OnPrimaryClipChangedListener`。

---

### 2. 通知开关入口

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/ui/LauncherActivity.java`

**关键方法:**

#### 第 89–91 行 — 初始化开关状态
```java
switchMoniteClipboard.setChecked(
        settings.getMoniteClipboardQ()
);
```

#### 第 105–117 行 — 监听开关切换
```java
switchMoniteClipboard.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setMoniteClipboardQ(isChecked);
                if (isChecked) {
                    startCBService();
                } else {
                    stopCBService();
                }
            }
        }
);
```

#### 第 362–370 行 — 启停服务
```java
private void startCBService() {
    Intent intent = new Intent(this, CBWatcherService.class);
    startService(intent);
}

private void stopCBService() {
    Intent intent = new Intent(this, CBWatcherService.class);
    stopService(intent);
}
```

#### 第 244 行 — 应用启动时恢复服务
若 `getMoniteClipboardQ()` 返回 `true`，应用冷启动后会自动重启 `CBWatcherService`，使通知在重启手机/重装后仍能恢复。

---

### 3. 设置项持久化

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/data/Settings.java`

```java
// 第 26 行
private final static String MONITE_CLIPBOARD_Q = "show_clipboard_notification_q";   //是否监听剪切板

// 第 109–116 行
public boolean getMoniteClipboardQ() {
    return sp.getBoolean(MONITE_CLIPBOARD_Q, false);
}

public void setMoniteClipboardQ(boolean moniteClipboardQ) {
    editor.putBoolean(MONITE_CLIPBOARD_Q, moniteClipboardQ);
    editor.commit();
}
```

存储方式：`SharedPreferences`，默认值 `false`（关闭）。

---

### 4. 通知点击跳转目标

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java`

#### 第 729–735 行 — 接收特殊标记
```java
if (Intent.ACTION_SEND.equals(action) && type.equals("text/plain")) {
    String base64 = intent.getStringExtra(Constant.INTENT_ANKIHELPER_BASE64);
    mTextToProcess = intent.getStringExtra(Intent.EXTRA_TEXT);
    if(mTextToProcess != null && mTextToProcess.equals(Constant.USE_CLIPBOARD_CONTENT_FLAG)){
        mTextToProcess = "";
        isFromAndroidQClipboard = true;
    }
    ...
}
```

`PopupActivity` 收到 `USE_CLIPBOARD_CONTENT_FLAG` 时不会把该字符串当作待查词，而是置 `isFromAndroidQClipboard = true`，并在后续主动读取剪贴板内容。

---

### 5. AndroidManifest 服务注册

**文件路径:** `app/src/main/AndroidManifest.xml`

```xml
<!-- 第 11 行 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 第 70–72 行 -->
<service
    android:name=".domain.CBWatcherService"
    android:enabled="true"
    android:exported="true" />
```

---

### 6. 字符串资源

| 用途 | key | 默认（英） | 中文 | 中文(v24) | 文件位置 |
|------|-----|-----------|------|-----------|---------|
| 开关标签 | `monite_clipboard` | Monitor Clipboard | 剪切板查词 | 📋剪切板查词 | `res/values*/strings.xml:13-14` |
| 通知标题 | `app_name` | Anki Helper | **Anki 划词助手** | Anki 划词助手 | `res/values*/strings.xml:2-3` |
| 通知内容（旧版） | `str_clipboard_service_running` | Clipboard Monitor is Running | 剪切板服务正在运行 | 剪切板服务正在运行 | `res/values*/strings.xml:43` |
| 通知内容（Android 10+） | `str_clipboard_service_running_version_q` | Copy text and click this notification | **复制文本后点此通知划词** | （同 `values-zh`） | `res/values/strings.xml:159`、`res/values-zh/strings.xml:69` |
| 特殊标记常量 | `Constant.USE_CLIPBOARD_CONTENT_FLAG` | `use_clipboard_content_flag` | （Java 常量，无 i18n） | — | `util/Constant.java:59` |

---

## 通知构建细节

### 通知通道（Android 8.0+）

```java
// CBWatcherService.java:55-66
String CHANNEL_ONE_ID = "com.mmjang.ankihelper";
String CHANNEL_ONE_NAME = "CBService";
NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
        CHANNEL_ONE_NAME, IMPORTANCE_HIGH);
notificationChannel.enableLights(false);
notificationChannel.setShowBadge(true);
notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
```

- **通道 ID:** `com.mmjang.ankihelper`
- **通道显示名:** `CBService`（用户在系统通知设置中看到的名字）
- **重要性:** `IMPORTANCE_HIGH`（发出声响、横幅弹出）
- **锁屏可见性:** `VISIBILITY_PUBLIC`（锁屏也能看到）
- **角标:** 启用

### 通知体

```java
// CBWatcherService.java:80-91
NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
        .setChannelId(CHANNEL_ONE_ID)
        .setSmallIcon(R.drawable.icon_light)                              // 小图标
        .setContentTitle(getResources().getText(R.string.app_name))        // 标题：Anki 划词助手
        .setContentIntent(pendingIntent)                                  // 点击意图
        .setWhen(System.currentTimeMillis());
if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
    builder = builder.setContentText(getString(R.string.str_clipboard_service_running_version_q));
} else {
    builder = builder.setContentText(getString(R.string.str_clipboard_service_running));
}
Notification noti = builder.build();
noti.flags |= Notification.FLAG_FOREGROUND_SERVICE;
startForeground(2333, noti);
```

### 关键常量

| 常量 | 值 | 含义 |
|------|----|----|
| 通知 ID | `2333` | `startForeground` 的通知 ID，服务生命周期内固定 |
| 通道 ID | `com.mmjang.ankihelper` | 系统通知通道标识 |
| 通道名 | `CBService` | 用户在系统设置中看到的通道名称 |
| 小图标 | `R.drawable.icon_light` | 状态栏小图标 |
| 标记 | `Notification.FLAG_FOREGROUND_SERVICE` | 标记为前台服务通知，不可滑动清除 |
| 返回值 | `START_STICKY` | 服务被杀后系统会尝试重建 |

### 点击意图（PendingIntent）

```java
// CBWatcherService.java:71-78
Intent intentStart = new Intent(getApplicationContext(), PopupActivity.class);
intentStart.setAction(Intent.ACTION_SEND);
intentStart.setType("text/plain");
intentStart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
intentStart.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
intentStart.putExtra(Intent.EXTRA_TEXT, Constant.USE_CLIPBOARD_CONTENT_FLAG);
PendingIntent pendingIntent = PendingIntent.getActivity(
        this, 0, intentStart, PendingIntent.FLAG_UPDATE_CURRENT);
```

- **目标 Activity:** `PopupActivity`
- **额外数据:** `EXTRA_TEXT = "use_clipboard_content_flag"`，作为「请读取剪贴板」的特殊信号
- **Flag:** `FLAG_UPDATE_CURRENT`，保证每次构建都更新已有 PendingIntent 的 extras

---

## 实现流程

### 完整数据流

```
LauncherActivity.onCheckedChanged(isChecked=true)
    ↓
Settings.setMoniteClipboardQ(true)  // 持久化开关状态
    ↓
startCBService()  → startService(CBWatcherService)
    ↓
CBWatcherService.onStartCommand()
    ├─ 注册 ClipboardManager 监听器
    ├─ 创建/获取通知通道（Android 8.0+）
    ├─ 构建 Notification（标题/内容/PendingIntent）
    └─ startForeground(2333, noti)  ← 通知出现在下拉通知栏，常驻
    ↓
[用户复制文本]
    ├─ Android 9-：performClipboardCheck() 直接读取并 startActivity(PopupActivity)
    └─ Android 10+：监听器无法读取内容，等待用户点击通知
            ↓
        点击通知 → PendingIntent 触发
            ↓
        PopupActivity 启动，收到 USE_CLIPBOARD_CONTENT_FLAG
            ↓
        读取剪贴板内容 → 进入划词流程
```

### 关闭流程

```
LauncherActivity.onCheckedChanged(isChecked=false)
    ↓
Settings.setMoniteClipboardQ(false)
    ↓
stopCBService()  → stopService(CBWatcherService)
    ↓
CBWatcherService.onDestroy()
    └─ stopForeground(true)  ← 通知从下拉通知栏消失
```

---

## 关联组件

### 1. OnlockReceiver（开机/解锁恢复）

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/domain/OnlockReceiver.java`

```java
public void onReceive(Context context, Intent intent) {
    Intent newIntent = new Intent(context, CBWatcherService.class);
    Settings settings = Settings.getInstance(context);
    if(settings.getMoniteClipboardQ()) {
        Toast.makeText(context, "debug", Toast.LENGTH_SHORT).show();
        context.startService(newIntent);
    }
}
```

通过系统广播触发，确保手机重启后只要用户开关是「开」就重建服务（和通知）。
注意：代码里残留了 `Toast.makeText(context, "debug", ...)` 一行（看起来是调试遗留），用户每次触发都会看到 `debug` 字样的弹窗。

### 2. PopupActivity 中的服务重启

**文件路径:** `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java:1614-1615`

```java
Intent intent = new Intent(this, CBWatcherService.class);
startService(intent);
```

`PopupActivity` 在某些场景下也会主动 `startService`，保证服务（和通知）存活。

### 3. 开关控件位置

**文件路径:** `app/src/main/res/layout/activity_launcher.xml:65-80`

```xml
<com.balysv.materialripple.MaterialRippleLayout ...>
    <Switch
        android:id="@+id/switch_monite_clipboard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/monite_clipboard"
        ... />
</com.balysv.materialripple.MaterialRippleLayout>
```

主界面列表中的「剪切板查词」行（带 📋 emoji，仅 v24+ 资源）。

---

## 设计特点

1. **前台服务模式:** 利用 `startForeground` 保证服务在后台不被系统杀死，代价是必须挂一条常驻通知
2. **双文案策略:** Android 10+ 因为不能后台读剪贴板，文案明确引导「点击通知划词」；旧版直接监听即可，文案更中性
3. **特殊 Intent 标记:** 用字符串常量 `use_clipboard_content_flag` 作为「请读取剪贴板」的暗号，避免把标记字符串本身当成待查词
4. **START_STICKY + 应用冷启动恢复:** 服务被系统杀死后自动重启；用户重启手机/重开 App 后通过 `LauncherActivity` 和 `OnlockReceiver` 双重恢复
5. **通道与重要级别:** 使用 `IMPORTANCE_HIGH` 保证通知显眼（但会发声，可能打扰用户）

---

## 已知问题

### 1. 调试 Toast 残留

**位置:** `OnlockReceiver.java:16`

```java
Toast.makeText(context, "debug", Toast.LENGTH_SHORT).show();
```

每次 `OnlockReceiver` 触发都会弹一个写着「debug」的 Toast，明显是开发调试遗留代码。

**影响:** 用户每次重启/解锁触接收器都会看到「debug」字样的弹窗，体验突兀。

**建议:** 删除该 Toast 调用。

---

### 2. Android 14+ 前台服务类型缺失

**问题:** Android 14（API 34）起，前台服务必须在 manifest 中声明 `foregroundServiceType`。当前注册：

```xml
<service
    android:name=".domain.CBWatcherService"
    android:enabled="true"
    android:exported="true" />
```

**影响:** 在 Android 14+ 上启动该前台服务会抛出 `MissingForegroundServiceTypeException`，整个剪切板查词功能无法使用。

**建议:** 添加 `android:foregroundServiceType="clipboard"` 或其他合适类型，并在 `startForeground` 时传入对应类型。

---

### 3. Android 10+ 通知点击非「常驻监听」的伪直觉

**现象:** 文案是「复制文本后点此通知划词」，但服务本身在 Android 10+ 上无法在后台读到复制的内容，必须用户手动点击通知触发。用户可能误以为「复制完会自动弹窗」。

**影响:** 期望与实际行为存在差异。

**建议:** 文案已较准确（明确写了「点此通知」），无需修改；但在「剪切板查词」开关旁边可加一行说明，提示 Android 10+ 用户需要点击通知。

---

### 4. `stopForeground(true)` 已废弃

**位置:** `CBWatcherService.java:47`

```java
stopForeground(true);
```

`stopForeground(boolean)` 自 API 24 起被 `stopForeground(int)` 取代，且 `true` 等价于 `STOP_FOREGROUND_REMOVE`，但仍可用。无功能问题。

**建议:** 迁移到 `stopForeground(STOP_FOREGROUND_REMOVE)` 以使用新签名。

---

### 5. PendingIntent 未指定不可变性标志

**位置:** `CBWatcherService.java:78`

```java
PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intentStart, PendingIntent.FLAG_UPDATE_CURRENT);
```

**问题:** Android 12（API 31）起，`PendingIntent` 必须显式指定 `FLAG_IMMUTABLE` 或 `FLAG_MUTABLE`，否则抛出 `IllegalArgumentException`。

**影响:** 在 Android 12+ 设备上点击通知会崩溃，整个剪切板查词通知功能不可用。

**建议:** 改为：
```java
PendingIntent.getActivity(this, 0, intentStart,
    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
```

---

### 6. 未声明 POST_NOTIFICATIONS 权限

**问题:** Android 13（API 33）起，应用发送通知需要运行时申请 `POST_NOTIFICATIONS` 权限。当前 manifest 中未声明该权限，也未在代码中申请。

**影响:** 在 Android 13+ 设备上首次启动服务时通知不显示，用户看不到「点此划词」的入口，可能误以为功能失效。

**建议:** 在 manifest 添加 `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`，并在打开开关时申请权限。

---

## 与项目其他通知的对比

| 特性 | CBWatcherService 通知 | AnkiDroid 添加卡片通知 |
|------|---------------------|----------------------|
| 用途 | 剪贴板入口 + 保活前台服务 | 卡片添加结果反馈 |
| 持续时间 | 长期常驻（开关期间） | 短暂出现 |
| 点击行为 | 拉起 PopupActivity 读取剪贴板 | 无（或跳转 AnkiDroid） |
| 通道 ID | `com.mmjang.ankihelper` | AnkiDroid 自有通道 |
| 重要性 | HIGH（声响 + 横幅） | 取决于 AnkiDroid 配置 |

---

## 适配建议汇总（按优先级）

| 优先级 | 问题 | 修复方式 |
|--------|------|---------|
| 🔴 高 | Android 12+ PendingIntent 不可变性 | `FLAG_UPDATE_CURRENT \| FLAG_IMMUTABLE` |
| 🔴 高 | Android 13+ POST_NOTIFICATIONS 权限 | manifest 声明 + 运行时申请 |
| 🔴 高 | Android 14+ foregroundServiceType | manifest 添加 `clipboard` 类型 |
| 🟡 中 | 调试 Toast 残留 | 删除 `OnlockReceiver.java:16` 的 Toast |
| 🟢 低 | `stopForeground(true)` 废弃签名 | 改为 `STOP_FOREGROUND_REMOVE` |

---

**文档生成:** Claude Code
**最后更新:** 2026-06-17
