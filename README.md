# 离线条码工具 Android 源码

这是一个离线安卓扫码/条码生成工具，风格为护眼暖色主题。

## 功能

- 扫描一维码、二维码
- 显示扫描值并自动复制
- 手动输入内容生成二维码或 Code128 一维码
- 保存生成图片到相册
- 分享生成图片
- 本地历史记录，最多保存 100 条
- 全部功能离线运行，不需要服务器

## 技术栈

- Android 原生 Java
- Gradle Android Plugin
- ZXing / JourneyApps `zxing-android-embedded`
- SharedPreferences 本地保存历史

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

前提：本机已安装 JDK 17、Android SDK、Gradle 或使用 Android Studio 自带 Gradle。

```bash
cd barcode-offline-android
gradle :app:assembleDebug
```

或在 Android Studio 中生成 Gradle Wrapper 后执行：

```bash
./gradlew :app:assembleDebug
```

## 权限说明

- CAMERA：扫码需要相机权限
- VIBRATE：扫码成功震动提示
- WRITE_EXTERNAL_STORAGE：仅 Android 9 及以下保存图片需要；Android 10+ 使用 MediaStore

## 后续可增强

- 历史搜索
- 收藏常用码
- 连续扫码模式
- 文件导出 CSV
- 更精细的条码格式选择，如 EAN-13、CODE-39
