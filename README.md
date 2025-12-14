# 實時時間表 PWA

學校實時時間表應用程式 - 採用 Material You 設計語言，支援離線使用、通知提醒。

## ✨ 功能特色

### 🎨 Material You 設計
- 完整的 Material Design 3 設計系統
- 動態主題色彩（淺色/深色模式）
- 流暢的動畫與轉場效果
- 響應式漣漪效果

### 📱 行動端優化
- 底部導航列（行動裝置）
- 左右滑動切換日期
- 觸覺回饋（震動）
- 安全區域適配（瀏海/打孔螢幕）
- 48px 最小觸控目標

### 🔔 通知提醒
- 課前 5 分鐘提醒
- 節次變更通知
- 特殊時間表提醒
- 可自訂提醒時間

### 📡 離線功能 (PWA)
- Service Worker 快取
- 離線存取時間表
- 離線狀態指示器
- 背景同步
- 可安裝至主畫面

### 🔌 GET API 端點

透過 URL 參數或 JavaScript 呼叫取得時間表資料：

#### URL 參數用法
```
?api=today              - 取得今日時間表
?api=current            - 取得當前節次資訊
?api=date&date=2025-12-15  - 取得指定日期
?api=week               - 取得本週時間表
?api=subjects           - 取得所有科目
?api=timetables         - 取得所有時間表類型

加上 &format=json 可在瀏覽器中顯示原始 JSON
```

#### JavaScript 用法
```javascript
const api = window.TimetableAPI;

// 取得今日時間表
const today = api.getToday();

// 取得當前節次
const current = api.getCurrent();

// 取得指定日期
const specific = api.getByDate('2025-12-15');

// 取得本週
const week = api.getWeek();
```

#### 回應格式範例
```json
{
  "success": true,
  "timestamp": "2025-12-14T10:30:00.000Z",
  "data": {
    "date": "2025-12-15",
    "dayOfWeek": "星期一",
    "dayCycle": 6,
    "timetableType": "normal",
    "isSchoolDay": true,
    "schedule": [
      {
        "type": "period",
        "name": "第1節",
        "start": "08:40",
        "end": "09:20",
        "subject": "ICT WKC 316"
      }
    ]
  }
}
```

## 📂 專案結構

```
timetable/
├── index.html          # 主 HTML 檔案
├── style.css           # Material You 樣式
├── script.js           # 主應用程式邏輯
├── timetable-data.js   # 時間表資料
├── api.js              # GET API 模組
├── notifications.js    # 通知管理模組
├── sw.js               # Service Worker
├── manifest.json       # PWA Manifest
├── icons/              # 應用程式圖示
│   └── icon.svg        # SVG 圖示來源
└── README.md           # 說明文件
```

## 🚀 部署至 GitHub Pages

1. 將專案推送至 GitHub 儲存庫
2. 前往 Settings → Pages
3. Source 選擇 "Deploy from a branch"
4. Branch 選擇 "main" (或 "master")
5. 資料夾選擇 "/ (root)"
6. 點擊 Save

部署完成後，可透過 `https://<username>.github.io/<repo>/` 存取。

## 🛠️ 開發

### 本地開發
使用任何靜態檔案伺服器即可：
```bash
# Python
python -m http.server 8080

# Node.js
npx serve

# VS Code Live Server 擴充功能
```

### 產生 PNG 圖示
使用 SVG 圖示產生各尺寸 PNG：
```bash
# 使用 ImageMagick
convert icons/icon.svg -resize 192x192 icons/icon-192.png
convert icons/icon.svg -resize 512x512 icons/icon-512.png

# 或使用線上工具如 https://realfavicongenerator.net/
```

## 📋 時間表資料更新

編輯 `timetable-data.js` 檔案：

- `TIMETABLE_DATA` - 時間表時段定義
- `SUBJECT_SCHEDULE` - 各 Day Cycle 的科目
- `DAY_ROTATION` - 日期對應的 Day Cycle
- `SPECIAL_DATES` - 特殊時間表日期

## 🎯 瀏覽器支援

- Chrome 80+
- Safari 14+
- Firefox 75+
- Edge 80+
- Samsung Internet 12+

## 📄 授權

MIT License
