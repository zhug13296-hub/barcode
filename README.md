# 离线条码工具 Android 源码

这是一个离线安卓扫码/条码生成工具，风格为护眼暖色主题。

## 功能

- 扫描一维码、二维码
- 显示扫描值并自动复制
- 手动输入内容生成二维码或 Code128 一维码
- 保存生成图片到相册
- 分享生成图片
- 本地历史记录，支持搜索、筛选和 CSV/JSON 导出
- 批量扫码和相册图片识别
- 全部功能离线运行，不需要服务器

## 技术栈

- Android 原生 Java
- Gradle Android Plugin
- ZXing / JourneyApps `zxing-android-embedded`
- SQLite 本地保存历史

## 目录

```text
barcode-offline-android/
  settings.gradle
  build.gradle
  app/
    build.gradle
    src/main/AndroidManifest.xml
    src/main/java/com/example/barcodeoffline/MainActivity.java
    src/main/res/values/styles.xml
```

## 用 Android Studio 打包 APK

1. 打开 Android Studio
2. File → Open → 选择本目录 `barcode-offline-android`
3. 等待 Gradle 同步完成
4. 菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)
5. APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 命令行打包

前提：本机已安装 JDK 17 和 Android SDK。命令行构建前需确保 `ANDROID_HOME` 指向 Android SDK 目录，或在项目根目录创建不提交的 `local.properties`：

```properties
sdk.dir=C:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
```

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME="C:\Path\To\JDK17"
$env:ANDROID_HOME="C:\Users\<你的用户名>\AppData\Local\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

macOS/Linux 示例：

```bash
cd barcode-offline-android
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

## 权限说明

- CAMERA：扫码需要相机权限
- VIBRATE：扫码成功震动提示
- WRITE_EXTERNAL_STORAGE：仅 Android 9 及以下保存图片需要；Android 10+ 使用 MediaStore

## 后续可增强

- 收藏常用码
- Android 13+ Photo Picker
- 历史列表改为 RecyclerView
- 结构化解析增加更多类型
