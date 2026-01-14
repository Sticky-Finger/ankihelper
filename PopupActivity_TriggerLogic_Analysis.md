# PopupActivity 触发逻辑分析

## 概述

本文档详细分析了 AnkiHelper 应用中 PopupActivity 组件的触发和显示机制，重点关注通知栏常驻图标的点击触发流程。

## 核心业务逻辑

### 1. 通知栏图标的创建

**文件位置：** `app/src/main/java/com/mmjang/ankihelper/domain/CBWatcherService.java:54-96`

`CBWatcherService` 作为前台服务运行，负责创建常驻通知栏图标。

#### 关键代码

```java
// 创建通知渠道（Android 8.0+ 需要）
String CHANNEL_ONE_ID = "com.mmjiang.ankihelper";
String CHANNEL_ONE_NAME = "CBService";
NotificationChannel notificationChannel = null;
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
    notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
            CHANNEL_ONE_NAME, IMPORTANCE_HIGH);
    notificationChannel.setShowBadge(true);
    notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    manager.createNotificationChannel(notificationChannel);
}

// 创建 PendingIntent 用于点击通知时启动 PopupActivity
Intent intentStart = new Intent(getApplicationContext(), PopupActivity.class);
intentStart.setAction(Intent.ACTION_SEND);
intentStart.setType("text/plain");
intentStart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
intentStart.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
intentStart.putExtra(Intent.EXTRA_TEXT, Constant.USE_CLIPBOARD_CONTENT_FLAG);
PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intentStart, PendingIntent.FLAG_UPDATE_CURRENT);

// 构建通知
NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
        .setChannelId(CHANNEL_ONE_ID)
        .setSmallIcon(R.drawable.icon_light)
        .setContentTitle(getResources().getText(R.string.app_name))
        .setContentIntent(pendingIntent)  // 绑定点击事件
        .setWhen(System.currentTimeMillis());

// 不同版本显示不同的内容文本
if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
    builder.setContentText(getString(R.string.str_clipboard_service_running_version_q));
} else {
    builder.setContentText(getString(R.string.str_clipboard_service_running));
}

// 设置为前台服务，显示常驻通知
Notification noti = builder.build();
noti.flags |= Notification.FLAG_FOREGROUND_SERVICE;
startForeground(2333, noti);
```

#### 关键点说明

1. **前台服务**：使用 `startForeground()` 确保服务不被系统杀死，通知栏图标常驻
2. **PendingIntent**：通过 `setContentIntent(pendingIntent)` 绑定点击事件
3. **特殊标记**：传递 `Constant.USE_CLIPBOARD_CONTENT_FLAG` 标识来源为通知栏点击
4. **Activity 标志**：
   - `FLAG_ACTIVITY_NEW_TASK`：在新任务栈中启动
   - `FLAG_ACTIVITY_REORDER_TO_FRONT`：如果 Activity 已存在，将其移到前台

### 2. PopupActivity 接收点击事件

**文件位置：** `app/src/main/java/com/mmjang/ankihelper/ui/popup/PopupActivity.java:718-783`

当用户点击通知栏图标时，会触发 `handleIntent()` 方法处理传入的 Intent。

#### 关键代码

```java
private void handleIntent() {
    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();

    // 检查是否是 SEND action 且类型为 text/plain
    if (Intent.ACTION_SEND.equals(action) && type.equals("text/plain")) {
        String base64 = intent.getStringExtra(Constant.INTENT_ANKIHELPER_BASE64);
        mTextToProcess = intent.getStringExtra(Intent.EXTRA_TEXT);

        // 检查是否是通知点击触发的（使用剪贴板内容）
        if(mTextToProcess != null && mTextToProcess.equals(Constant.USE_CLIPBOARD_CONTENT_FLAG)){
            mTextToProcess = "";
            isFromAndroidQClipboard = true;  // 标记从通知栏触发
        }

        // 处理 base64 编码的内容
        if(base64 != null && !base64.equals("0")){
            mTextToProcess = new String(Base64.decode(mTextToProcess, Base64.DEFAULT));
        }

        // 获取其他 Intent 数据
        mTargetWord = intent.getStringExtra(Constant.INTENT_ANKIHELPER_TARGET_WORD);
        mUrl = intent.getStringExtra(Constant.INTENT_ANKIHELPER_TARGET_URL);
        // ... 其他数据处理
    }

    // 处理 PROCESS_TEXT action（Android 文本选择菜单）
    if (Intent.ACTION_PROCESS_TEXT.equals(action) && type.equals("text/plain")) {
        mTextToProcess = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
    }

    // 处理文本并显示界面
    populateWordSelectBox();
    // ... 其他初始化逻辑
}
```

#### 关键点说明

1. **来源识别**：通过 `Constant.USE_CLIPBOARD_CONTENT_FLAG` 标识通知栏点击来源
2. **剪贴板标记**：设置 `isFromAndroidQClipboard = true`，后续会读取剪贴板内容
3. **多种 Action 支持**：
   - `ACTION_SEND`：通用文本发送（通知栏点击、分享等）
   - `ACTION_PROCESS_TEXT`：Android 文本选择菜单

### 3. AndroidManifest.xml 配置

**文件位置：** `app/src/main/AndroidManifest.xml:29-51`

```xml
<activity
    android:name=".ui.popup.PopupActivity"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:noHistory="true"
    android:theme="@style/Transparent"
    android:windowSoftInputMode="stateAlwaysHidden">

    <!-- 处理 SEND action 的 Intent Filter -->
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.BROWSABLE" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>

    <!-- 处理 PROCESS_TEXT action 的 Intent Filter -->
    <intent-filter>
        <action android:name="android.intent.action.PROCESS_TEXT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

#### 配置说明

- `launchMode="singleInstance"`：确保只有一个 PopupActivity 实例
- `excludeFromRecents="true"`：不在最近任务列表中显示
- `noHistory="true"`：退出后不保留在任务栈中
- `theme="@style/Transparent"`：透明背景，实现悬浮窗效果

## 完整的触发链路

```
用户点击通知栏图标
    ↓
PendingIntent 触发（CBWatcherService.java:71-78）
    ↓
启动 PopupActivity，传递 USE_CLIPBOARD_CONTENT_FLAG 标记
    ↓
PopupActivity.handleIntent() 识别标记（PopupActivity.java:718-783）
    ↓
设置 isFromAndroidQClipboard = true
    ↓
读取剪贴板内容并显示 Popup 窗口
```

## 其他触发方式

除了通知栏点击，PopupActivity 还支持以下触发方式：

### 1. 剪贴板自动监控

**文件位置：** `CBWatcherService.java` 中的剪贴板监控逻辑

当剪贴板内容发生变化时，服务会检测是否为英语文本，如果是则自动触发 PopupActivity。

```java
private void performClipboardCheck() {
    if (!Settings.getInstance(MyApplication.getContext()).getMoniteClipboardQ()) {
        return;
    }

    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (cb.hasPrimaryClip() && cb.hasText()) {
        String text = cb.getText().toString();
        if (isEnglish(text)) {  // 检查是否是英语文本
            Intent intent = new Intent(getApplicationContext(), PopupActivity.class);
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(intent);
        }
    }
}
```

### 2. Chrome 浏览器文本选择

通过 Android 文本选择框架集成，用户在 Chrome 中选中文本后可以通过菜单直接打开 PopupActivity。

### 3. FBReader 集成

定制版 FBReader 直接集成，通过自定义协议触发 PopupActivity。

### 4. 通用分享

从任何应用分享文本到 AnkiHelper，会触发 `ACTION_SEND` Intent 启动 PopupActivity。

## 关键文件总结

| 文件路径 | 行号 | 功能描述 |
|---------|------|---------|
| `CBWatcherService.java` | 54-96 | 创建通知栏常驻图标和点击事件绑定 |
| `CBWatcherService.java` | 剪贴板监控部分 | 自动监控剪贴板变化并触发 Popup |
| `PopupActivity.java` | 718-783 | Intent 处理和启动逻辑 |
| `AndroidManifest.xml` | 29-51 | PopupActivity 的 Intent Filter 配置 |
| `Constant.java` | - | 定义 `USE_CLIPBOARD_CONTENT_FLAG` 等常量 |

## 设计特点

1. **前台服务保证存活**：使用前台服务确保剪贴板监控不被系统杀死
2. **多种触发方式**：支持通知栏点击、剪贴板监控、文本选择、分享等多种方式
3. **来源识别机制**：通过特殊标记区分不同触发来源，做相应处理
4. **单例模式**：使用 `singleInstance` 启动模式避免重复创建
5. **透明悬浮窗**：透明主题配合 `FLAG_ACTIVITY_REORDER_TO_FRONT` 实现悬浮效果

## 相关文档

- [PopupActivity 业务逻辑分析](./PopupActivity_BusinessLogic_Analysis.md)
- [PopupActivity onCreate 方法分析](./PopupActivity_OnCreate_Method_Analysis.md)
