# Fuckissues

一个用于记录问题、解决过程和任务复盘的 Android 应用。

## 功能

- 记录任务和每一步问题/解决办法
- 历史任务、统计页和 Clay/Jelly 风格 WebView UI
- DeepSeek API 配置、模型拉取和任务归因分析
- 任务级 AI 对话，支持流式输出和聊天记录缓存

## 技术栈

- Kotlin
- Jetpack Compose
- WebView
- Room
- DataStore
- Hilt
- Retrofit + OkHttp
- Gradle Kotlin DSL

## 本地运行

1. 用 Android Studio 打开项目根目录。
2. 确认 `local.properties` 中有本机 Android SDK 路径。
3. 执行 Debug 构建：

```bash
./gradlew assembleDebug
```

Windows 可以使用：

```powershell
.\gradlew.bat assembleDebug
```

## API 配置

应用内进入咖啡沉思馆的 API 配置页面，填写：

- Base URL
- API Key
- Model

API Key 只保存在本机 DataStore，不会提交到仓库。
