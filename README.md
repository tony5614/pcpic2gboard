# pc2gboard

> 透過 Wi-Fi，將電腦剪貼簿的圖片即時推送到 Android 手機的 Gboard 輸入法。

截圖後直接在手機上貼上，不需要 USB、不需要雲端，零感知完成。

---

## ✨ 功能

- 偵測電腦剪貼簿有新圖片時，自動透過 Wi-Fi 推送到手機
- 手機背景常駐服務，24 小時待命，無需手動操作
- 圖片寫入手機剪貼簿後可直接在 Gboard 貼上
- 透過 UDP 廣播自動找到手機 IP，無需手動設定

---

## 🛠️ 架構

```
電腦端 (Python)                     手機端 (Android)
─────────────────                   ──────────────────
send_paste.py                       SocketService (前台服務)
  │                                   │
  ├─ 偵測剪貼簿有新圖片               ├─ UDP 監聽廣播，回報 IP
  ├─ UDP 廣播找手機 IP                ├─ TCP 接收圖片
  └─ TCP 推送圖片 → → → → → → → →  └─ 寫入剪貼簿
```

---

## 📋 需求

**電腦端**
- Python 3.x
- 套件：`Pillow`

```bash
pip install Pillow
```

**手機端**
- Android 8.0+
- Gboard 輸入法
- 與電腦在同一個 Wi-Fi 網路

---

## 🚀 使用方式

### 手機端

1. 安裝 APK
2. 開啟 App，依提示授權以下權限：
   - 通知權限
   - 懸浮視窗權限
   - 允許背景耗電（不受電池優化限制）
3. 關閉 App，服務會繼續在背景執行

**Vivo 手機額外設定（必做）：**

設定 → 應用程式管理 → pc2gboard → 裝置管理：
- 自動啟動 → 開
- 關聯啟動 → 開
- 背景彈出介面 → 開
- 背景耗電管理 → 允許背景耗電

### 電腦端

```bash
python send_paste.py
```

在電腦上複製任意圖片（截圖、Ctrl+C 複製圖片），幾秒內手機即可貼上。

---

## 📁 專案結構

```
pc2gboard/
├── app/src/main/java/com/example/pc2gboard/
│   ├── MainActivity.java       # 啟動服務、請求權限
│   ├── SocketService.java      # 核心服務：UDP 廣播 + TCP 接收 + 寫入剪貼簿
│   └── BootReceiver.java       # 開機自動啟動服務
├── send_paste.py               # 電腦端推送腳本
└── README.md
```

---

## ⚙️ 技術細節

| 項目 | 說明 |
|------|------|
| 手機發現 | UDP 廣播 Port 9528 |
| 圖片傳輸 | TCP Socket Port 9527 |
| 圖片格式 | PNG |
| 服務類型 | Android Foreground Service (dataSync) |
| 剪貼簿寫入 | ClipData URI via FileProvider |

---

## 🔒 權限說明

| 權限 | 用途 |
|------|------|
| `INTERNET` | TCP/UDP 通訊 |
| `CHANGE_WIFI_MULTICAST_STATE` | 接收 UDP 廣播封包 |
| `FOREGROUND_SERVICE` | 背景常駐服務 |
| `SYSTEM_ALERT_WINDOW` | 允許從背景啟動 |
| `POST_NOTIFICATIONS` | 顯示常駐通知 |
| `RECEIVE_BOOT_COMPLETED` | 開機自動啟動 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 不受電池優化影響 |

---

## 📄 License

MIT
