# AnkiHelper 生产版本构建指南

本文档说明如何构建已签名的 Release APK（即可分发、可安装到设备的生产版本）。如仅需调试版本，请参考 [How-to-run.md](./How-to-run.md)。

## 前置要求

1. **JDK 11** — 与 Debug 构建相同
2. **Android SDK** — 需包含 `build-tools/29.0.2`（提供 `apksigner` 与 `zipalign`）
3. **Gradle 5.1.1** — 使用全局 Gradle（不要用 `./gradlew`，原因见 How-to-run.md）
4. **Debug 构建环境已就绪** — 即 How-to-run.md 中的环境变量、`gradlew` 行尾符修复等步骤已完成

## 一次性配置（仅首次构建需要）

生产版本必须签名。下述三步只需在首次配置时执行一次。

### 1. 生成签名 keystore

```bash
# 在项目根目录执行
keytool -genkeypair \
  -keystore ankihelper.jks \
  -alias ankihelper \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass <你的密码> -keypass <你的密码> \
  -noprompt \
  -dname "CN=AnkiHelper, OU=Dev, O=Personal, L=Beijing, ST=Beijing, C=CN"
```

参数说明：
- `-validity 36500` — 100 年有效期，避免证书过期
- `-storepass / -keypass` — **请使用强密码**，文档示例的 `123456` 仅供演示
- `-dname` — 证书主体信息，可按需修改

生成后会在项目根目录得到 `ankihelper.jks`。**此文件务必妥善备份**，丢失后已发布的同款 app 将无法再升级。

### 2. 创建 `keystore.properties`

在项目根目录新建 `keystore.properties`：

```properties
storeFile=ankihelper.jks
storePassword=<你的密码>
keyAlias=ankihelper
keyPassword=<你的密码>
```

> **重要**：`storeFile` 是相对于**项目根目录**的路径（不是 `app/` 目录）。`app/build.gradle` 中已用 `rootProject.file()` 解析。

### 3. 确保 `.gitignore` 已排除敏感文件

`.gitignore` 应包含以下条目，避免 keystore 与密码进入版本库：

```gitignore
keystore.properties
*.jks
*.keystore
```

### 4. 确认 `app/build.gradle` 已配置签名

项目已在 `android {}` 块内配置：

- `signingConfigs.release` — 从 `keystore.properties` 读取凭据
- `buildTypes.release.signingConfig` — 指向上面的 release 配置

若文件不存在 `keystore.properties`，签名配置会自动跳过，不影响 Debug 构建。

## 构建签名 Release APK

```bash
# 在项目根目录执行
gradle assembleRelease
```

构建成功后会输出 `BUILD SUCCESSFUL`。

## 输出文件位置

```
app/build/outputs/apk/release/Anki 划词助手 2.30.7-release.apk
```

文件名格式为 `Anki 划词助手 <versionName>-release.apk`，由 `app/build.gradle` 中的 `archivesBaseName` 决定。

> 同时会生成一个 `*-release-unsigned.apk`，可忽略（属于中间产物）。

## 验证签名

```bash
# 使用 Android SDK 自带的 apksigner 验证
apksigner verify --verbose \
  "app/build/outputs/apk/release/Anki 划词助手 2.30.7-release.apk"
```

期望输出（关键字段）：

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
```

> **v3 scheme 为 false 不影响使用**：v1+v2 已覆盖 Android 全部版本。

## 安装到设备

```bash
adb install -r "app/build/outputs/apk/release/Anki 划词助手 2.30.7-release.apk"
```

## 注意事项

1. **签名冲突** — 如果设备上已安装其他签名的同款 app（例如旧的开源版本），`adb install` 会失败。需先卸载：
   ```bash
   adb uninstall com.mmjang.ankihelper
   ```

2. **META-INF 警告无害** — `apksigner verify` 可能输出大量 `WARNING: META-INF/xxx not protected by signature`，这些是依赖库自带的元数据文件（`.version`、`.kotlin_module`、`README` 等），不影响功能与安装。

3. **keystore 备份** — `ankihelper.jks` 一旦丢失，已发布版本无法被任何新版本升级。建议多处异地备份。

4. **密码安全** — 不要把真实密码提交到 git，`keystore.properties` 与 `*.jks` 已通过 `.gitignore` 排除。

## 故障排查

### 1. `Keystore file '.../app/ankihelper.jks' not found`

**原因**：`app/build.gradle` 中 `storeFile` 用 `file(...)` 解析时相对于 `app/` 目录，找不到根目录的 jks。

**解决**：确认 build.gradle 中使用的是 `rootProject.file(...)` 而非 `file(...)`。

### 2. `Execution failed for task ':app:validateSigningRelease'`

通常是 `keystore.properties` 路径或密码不对。检查：

- 文件是否在**项目根目录**（不是 `app/` 目录）
- 密码是否与生成 keystore 时一致
- 别名（`keyAlias`）是否与 `-alias` 参数一致

### 3. `adb install` 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / `INSTALL_FAILED_VERIFICATION_FAILURE`

设备上已存在不同签名的同款 app。先 `adb uninstall com.mmjang.ankihelper` 再装。

### 4. 构建仍然输出 `*-release-unsigned.apk` 而非签名包

确认 `app/build.gradle` 中：

```gradle
buildTypes {
    release {
        signingConfig signingConfigs.release   // ← 这一行必须存在
        ...
    }
}
```

## 参考资料

- 环境配置、Debug 构建、Gradle 加速等基础内容：[How-to-run.md](./How-to-run.md)
- 项目版本信息（`VERSION_NAME` / `VERSION_CODE`）：根目录 `build.gradle`
