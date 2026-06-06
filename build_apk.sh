#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if command -v ./gradlew >/dev/null 2>&1 && [ -x ./gradlew ]; then
  ./gradlew :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle :app:assembleDebug
else
  echo "未找到 gradle。请用 Android Studio 打开本项目，或安装 Gradle/JDK/Android SDK 后再执行。" >&2
  exit 1
fi
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "APK 已生成: $PWD/$APK"
else
  echo "构建结束，但未找到 APK: $APK" >&2
  exit 2
fi
