# PWA 測試與驗證指南

## 快速測試清單

### 基本功能測試

#### 1. Service Worker 註冊 ✓
```
測試步驟：
1. 打開瀏覽器開發者工具 (F12)
2. 切換到 Console 標籤
3. 查找日誌：[App] Service Worker registered

預期結果：
✅ Console 顯示註冊成功
✅ Application → Service Workers 顯示活躍狀態
```

#### 2. 快取功能 ✓
```
測試步驟：
1. 首次訪問應用
2. 開發者工具 → Application → Cache Storage
3. 查看 timetable-v1 快取

預期結果：
✅ 快取包含 /, index.html, timetable-data.js, manifest.json
✅ 刷新頁面幾乎瞬間加載
```

#### 3. 離線支援 ✓
```
測試步驟：
1. 正常訪問應用
2. 開發者工具 → Network → Offline (勾選)
3. 刷新頁面

預期結果：
✅ 應用正常顯示
✅ 右上角顯示 🔴 離線模式
✅ 所有功能可用
```

#### 4. 通知功能 ✓
```
測試步驟：
1. 點擊頂部 🔔 按鈕
2. 允許通知權限
3. 查看 Console 日誌

預期結果：
✅ 顯示確認通知
✅ Console 顯示排程資訊
✅ 按鈕變為實心 🔔
```

#### 5. PWA 安裝 ✓
```
測試步驟：
1. 等待 3 秒
2. 底部出現安裝提示
3. 點擊「安裝」

預期結果：
✅ 顯示系統安裝對話框
✅ 應用添加到主屏幕/應用列表
✅ 可以獨立啟動
```

### 進階功能測試

#### 6. 通知排程 ✓
```
測試步驟：
1. 啟用通知後查看 Console
2. 找到 [Notification] Scheduled 日誌
3. 記錄排程時間

驗證項目：
✅ 今日每節課都有排程
✅ 時間為課前 5 分鐘
✅ 包含正確的課程資訊
```

#### 7. 自動滾動 ✓
```
測試步驟：
1. 在上課時間打開應用
2. 觀察時間表顯示

預期結果：
✅ 自動滾動到當前課程
✅ 當前課程高亮顯示
✅ 滾動平滑
```

#### 8. 主題切換 ✓
```
測試步驟：
1. 點擊 🌙 按鈕
2. 切換深色模式
3. 刷新頁面

預期結果：
✅ 主題正確切換
✅ 設置被記住
✅ 所有元素適配主題
```

## 瀏覽器特定測試

### Chrome/Edge (桌面)

#### 安裝測試
```
1. 地址欄右側出現 ⊕ 圖標
2. 點擊圖標安裝
3. 檢查應用列表
```

#### 通知測試
```
1. 設置 → 網站設置 → 通知
2. 確認應用有通知權限
3. 等待測試通知
```

### Firefox (桌面)

#### 安裝測試
```
1. 選單 → 安裝此網站
2. 檢查應用圖標
3. 獨立啟動測試
```

#### 通知測試
```
1. 工具 → 頁面資訊 → 權限
2. 確認通知已允許
3. 測試通知接收
```

### Safari (iOS)

#### 安裝測試
```
1. 分享按鈕 (⬆️)
2. 加入主屏幕
3. 自定義名稱（可選）
4. 從主屏幕啟動
```

#### 通知測試
```
⚠️ 重要：必須從主屏幕啟動才能使用通知

1. 從主屏幕圖標打開應用
2. 啟用通知
3. 設置 → 通知 → 檢查應用權限
```

### Android Chrome

#### 安裝測試
```
1. 等待底部提示彈出
2. 點擊「安裝」
3. 或選單 → 安裝應用程式
4. 檢查應用抽屜
```

#### 通知測試
```
1. 啟用通知
2. 設置 → 應用和通知 → 檢查權限
3. 鎖定螢幕測試通知
```

## 自動化測試腳本

### Service Worker 檢查
```javascript
// 在 Console 執行
navigator.serviceWorker.getRegistration().then(reg => {
  if (reg) {
    console.log('✅ Service Worker 已註冊');
    console.log('狀態:', reg.active ? '活躍' : '未活躍');
  } else {
    console.log('❌ Service Worker 未註冊');
  }
});
```

### 快取檢查
```javascript
// 在 Console 執行
caches.keys().then(keys => {
  console.log('快取版本:', keys);
  return caches.open('timetable-v1');
}).then(cache => {
  return cache.keys();
}).then(requests => {
  console.log('✅ 快取資源:', requests.map(r => r.url));
});
```

### 通知權限檢查
```javascript
// 在 Console 執行
console.log('通知權限:', Notification.permission);
if (Notification.permission === 'granted') {
  console.log('✅ 通知已允許');
} else if (Notification.permission === 'denied') {
  console.log('❌ 通知被拒絕');
} else {
  console.log('⚠️ 通知未請求');
}
```

### 離線測試
```javascript
// 在 Console 執行
console.log('在線狀態:', navigator.onLine ? '✅ 在線' : '🔴 離線');

// 模擬離線
window.dispatchEvent(new Event('offline'));

// 模擬在線
window.dispatchEvent(new Event('online'));
```

## 性能測試

### Lighthouse 審計
```
1. 開發者工具 → Lighthouse
2. 選擇：
   - ✓ Performance
   - ✓ Progressive Web App
   - ✓ Best Practices
   - ✓ Accessibility
   - ✓ SEO
3. 選擇裝置：Mobile/Desktop
4. 點擊「Generate report」

目標分數：
- Performance: 95+
- PWA: 100
- Accessibility: 95+
- Best Practices: 95+
- SEO: 100
```

### 網絡速度測試
```
1. Network → No throttling (基準)
2. Network → Fast 3G (模擬慢網)
3. Network → Offline (離線測試)

測量：
- 首次加載時間
- 快取加載時間
- 離線可用性
```

### 快取效果測試
```javascript
// 測量加載時間
performance.mark('start');
window.addEventListener('load', () => {
  performance.mark('end');
  performance.measure('loadTime', 'start', 'end');
  const measure = performance.getEntriesByName('loadTime')[0];
  console.log('頁面加載時間:', measure.duration.toFixed(2), 'ms');
});
```

## 常見問題排查

### 問題 1: Service Worker 未註冊
```
檢查項目：
□ 是否使用 HTTPS (或 localhost)
□ sw.js 文件是否存在
□ Console 是否有錯誤
□ 瀏覽器是否支援 Service Worker

解決方案：
1. 確認使用 HTTPS
2. 檢查 sw.js 路徑
3. 查看 Console 錯誤
4. 嘗試清除瀏覽器快取
```

### 問題 2: 通知不工作
```
檢查項目：
□ 通知權限是否允許
□ Service Worker 是否運行
□ iOS 是否從主屏幕啟動
□ 系統通知設置

解決方案：
1. 重新請求通知權限
2. 檢查 Service Worker 狀態
3. iOS 從主屏幕圖標啟動
4. 檢查系統通知設置
```

### 問題 3: 無法離線使用
```
檢查項目：
□ 是否至少在線訪問過一次
□ Service Worker 是否正常
□ 快取是否成功

解決方案：
1. 在線訪問一次以建立快取
2. 檢查 Cache Storage
3. 重新註冊 Service Worker
```

### 問題 4: 無法安裝 PWA
```
檢查項目：
□ manifest.json 是否正確
□ 是否使用 HTTPS
□ Service Worker 是否註冊
□ 是否滿足安裝條件

解決方案：
1. 驗證 manifest.json
2. 確認 HTTPS 連接
3. 檢查 Service Worker
4. 查看 Application → Manifest
```

## 部署前檢查清單

### 必需項目
- [ ] HTTPS 已配置
- [ ] manifest.json 路徑正確
- [ ] Service Worker 路徑正確
- [ ] 所有資源可訪問
- [ ] 跨域資源已配置 (如需)

### 配置檢查
- [ ] start_url 正確
- [ ] scope 正確
- [ ] 圖標格式正確
- [ ] 主題顏色設置
- [ ] 應用名稱正確

### 功能驗證
- [ ] Service Worker 註冊成功
- [ ] 快取策略工作
- [ ] 通知功能正常
- [ ] 離線支援可用
- [ ] 安裝流程順暢

### 性能驗證
- [ ] Lighthouse PWA: 100/100
- [ ] 快取加載 < 100ms
- [ ] 離線完全可用
- [ ] 無 Console 錯誤

### 文檔完整
- [ ] README.md 已更新
- [ ] PWA_FEATURES.md 存在
- [ ] 使用說明清晰
- [ ] 故障排除指南

## 用戶驗收測試 (UAT)

### 場景測試

#### 場景 1: 新用戶首次使用
```
步驟：
1. 清除所有網站數據
2. 訪問應用
3. 觀察首次體驗

檢查點：
✓ 頁面快速加載
✓ Service Worker 自動註冊
✓ 3 秒後顯示安裝提示
✓ 可以正常使用所有功能
```

#### 場景 2: 啟用通知
```
步驟：
1. 點擊通知按鈕
2. 允許權限
3. 等待排程完成

檢查點：
✓ 顯示確認通知
✓ Console 顯示排程日誌
✓ 按鈕狀態更新
✓ 設置被記住
```

#### 場景 3: 日常使用
```
步驟：
1. 第二天打開應用
2. 檢查通知是否重新排程
3. 查看時間表更新

檢查點：
✓ 快速加載（快取）
✓ 自動重新排程通知
✓ 數據是最新的
✓ 離線可用
```

#### 場景 4: 離線情況
```
步驟：
1. 正常使用應用
2. 關閉網絡連接
3. 繼續使用

檢查點：
✓ 顯示離線指示器
✓ 應用繼續工作
✓ 所有功能可用
✓ 數據準確
```

## 監控指標

### 關鍵指標
- Service Worker 註冊率: > 95%
- PWA 安裝率: > 30%
- 通知啟用率: > 50%
- 離線使用率: 追蹤
- 快取命中率: > 90%

### 用戶行為
- 每日活躍用戶
- 通知點擊率
- 安裝後留存率
- 離線訪問頻率
- 平均使用時長

### 技術指標
- Service Worker 錯誤率
- 通知送達率
- 快取更新成功率
- 頁面加載時間
- 錯誤日誌數量

## 結論

完整測試以上所有項目，確保：
1. ✅ 所有基本功能正常
2. ✅ 各瀏覽器兼容性良好
3. ✅ 性能達標
4. ✅ 用戶體驗流暢
5. ✅ 離線完全可用

---

**測試人員**: ___________
**測試日期**: ___________
**測試環境**: ___________
**測試結果**: ☐ 通過 ☐ 失敗
**備註**: ___________
