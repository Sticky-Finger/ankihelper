# AnkiHelper 运行编译指南

## 前置要求

1. **JDK 11** - 安装并配置环境变量
2. **Android SDK** - 编译 SDK 29，最低支持 SDK 19
3. **Gradle** - 项目使用 Gradle 5.1.1

## 环境配置

### 1. 设置 JAVA_HOME

```bash
# 示例（根据实际 JDK 安装路径调整）
export JAVA_HOME=/media/wpw/D/ubuntu_docs/APPs/JDK/jdk-11.0.31+11
export PATH=$JAVA_HOME/bin:$PATH
```

建议将以上配置添加到 `~/.bashrc` 或 `~/.zshrc` 中永久生效。

### 2. 修复 gradlew 行尾符（Windows 兼容性问题）

如果执行 `./gradlew` 时出现 `env: $'bash\r': No such file or directory` 错误：

```bash
sed -i 's/\r$//' gradlew
chmod +x gradlew
```

## 构建步骤

使用已安装的全局 Gradle：

```bash
# 构建 Debug APK
gradle assembleDebug

# 安装到已连接的设备
gradle installDebug

# 运行单元测试
gradle testDebugUnitTest
```

> **注意：** Gradle Wrapper（`./gradlew`）因默认下载源问题在实践中无法正常工作，请使用全局 Gradle 构建。

## Gradle 下载加速

如果 Gradle 5.1.1 下载缓慢，可修改 `gradle/wrapper/gradle-wrapper.properties`：

```properties
# 原始
distributionUrl=https\://services.gradle.org/distributions/gradle-5.1.1-all.zip

# 改为腾讯镜像
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-5.1.1-all.zip
```

## 输出文件位置

构建成功后，APK 文件位于：

```
app/build/outputs/apk/debug/app-debug.apk
```

## 通过 ADB 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 注意事项

1. **Maven 镜像** - 项目已配置阿里云镜像，在国内下载依赖更快
2. **AnkiDroid 集成** - 如需使用 AnkiDroid 相关功能，需先安装 AnkiDroid 应用
3. **权限要求** - 剪贴板监控服务需要 FOREGROUND_SERVICE 权限
