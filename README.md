# OpenClaw Android Dashboard

OpenClaw 的原生 Android 儀表板應用程式。

## 功能

- 連接到您的 OpenClaw Web Dashboard
- 支援 Hugging Face 私有 Space 認證
- Sessions 管理與 Token 統計
- 即時聊天介面
- 已連線裝置監控
- Material 3 動態主題

## 建置

### 本地建置
```bash
./gradlew assembleDebug
```

### GitHub Actions
專案包含自動化建置流程，推送到 `main` 分支後會自動建置 Debug APK。

## 使用方式

1. 安裝 APK
2. 輸入您的 Dashboard URL（例如：`https://your-space.hf.space`）
3. 輸入 Hugging Face Token（私有 Space 必填）
4. 開始管理您的 AI 代理！

## 授權

MIT License
