/**
 * Timetable Application - Main Script
 * Material You Design with PWA Support
 */

class TimetableApp {
  constructor() {
    this.currentViewMode = 'today';
    this.selectedDate = null;
    this.theme = localStorage.getItem('theme') || this.getSystemTheme();
    this.realTimeUpdateInterval = null;
    this.timetableUpdateInterval = null;
    this.isPageVisible = true;
    this.lastUpdateTime = Date.now();
    this.animationFrameId = null;
    this.touchStartX = 0;
    this.touchStartY = 0;
    this.isOnline = navigator.onLine;
    this.previousPeriod = null;
    
    this.init();
  }

  async init() {
    // Register Service Worker
    await this.registerServiceWorker();
    
    // Initialize UI
    this.initTheme();
    this.initViewSwitcher();
    this.initDateSelection();
    this.initNavigationButtons();
    this.initBottomNavigation();
    this.initPageVisibility();
    this.initMobileSupport();
    this.initSwipeGestures();
    this.initOnlineStatus();
    this.initNotifications();
    this.initSnackbar();
    
    // Start updates
    this.updateDateTime();
    this.updateTimetable();
    this.startRealTimeUpdates();
    
    // Update timetable every minute
    this.timetableUpdateInterval = setInterval(() => {
      if (this.currentViewMode === 'today' && this.isPageVisible) {
        this.updateTimetable();
      }
    }, 60000);
    
    // Check URL parameters for view
    this.checkURLParams();
    
    // Clean up on page unload
    window.addEventListener('beforeunload', () => {
      this.cleanup();
    });
  }

  // ========== SERVICE WORKER ==========
  async registerServiceWorker() {
    if ('serviceWorker' in navigator) {
      try {
        const registration = await navigator.serviceWorker.register('./sw.js');
        console.log('[SW] Registration successful:', registration.scope);
        
        // Check for updates
        registration.addEventListener('updatefound', () => {
          const newWorker = registration.installing;
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              this.showSnackbar('有新版本可用', '重新載入', () => window.location.reload());
            }
          });
        });
      } catch (error) {
        console.error('[SW] Registration failed:', error);
      }
    }
  }

  // ========== THEME ==========
  getSystemTheme() {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  initTheme() {
    const themeToggle = document.getElementById('theme-toggle');
    this.applyTheme();
    
    themeToggle.addEventListener('click', () => {
      this.theme = this.theme === 'light' ? 'dark' : 'light';
      localStorage.setItem('theme', this.theme);
      this.applyTheme();
      this.showSnackbar(this.theme === 'dark' ? '已切換至深色模式' : '已切換至淺色模式');
    });

    // Listen for system theme changes
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      if (!localStorage.getItem('theme')) {
        this.theme = e.matches ? 'dark' : 'light';
        this.applyTheme();
      }
    });
  }

  applyTheme() {
    document.body.setAttribute('data-theme', this.theme);
    const themeIcon = document.getElementById('theme-icon');
    themeIcon.textContent = this.theme === 'light' ? 'dark_mode' : 'light_mode';
    
    // Update meta theme-color
    const themeColor = this.theme === 'dark' ? '#D0BCFF' : '#6750A4';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', themeColor);
  }

  // ========== VIEW SWITCHER ==========
  initViewSwitcher() {
    // Desktop segmented button
    const viewButtons = document.querySelectorAll('.segmented-button__item');
    viewButtons.forEach(button => {
      button.addEventListener('click', () => {
        this.switchView(button.dataset.view);
      });
    });
  }

  initBottomNavigation() {
    // Mobile bottom navigation
    const bottomNavItems = document.querySelectorAll('.bottom-nav__item[data-view]');
    bottomNavItems.forEach(item => {
      item.addEventListener('click', () => {
        this.switchView(item.dataset.view);
      });
    });

    // Navigation prev/next
    document.getElementById('nav-prev')?.addEventListener('click', () => this.goToPrevDay());
    document.getElementById('nav-next')?.addEventListener('click', () => this.goToNextDay());
  }

  switchView(viewMode) {
    this.currentViewMode = viewMode;
    
    // Update desktop segmented button
    document.querySelectorAll('.segmented-button__item').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.view === viewMode);
    });

    // Update mobile bottom navigation
    document.querySelectorAll('.bottom-nav__item[data-view]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.view === viewMode);
    });

    const dateSelection = document.getElementById('date-selection');
    const statusCards = document.querySelectorAll('.current-status-card, .countdown-card, .next-period-card');

    if (viewMode === 'today') {
      dateSelection.classList.remove('active');
      statusCards.forEach(card => card.style.display = 'block');
      document.getElementById('schedule-title').textContent = '今日時間表';
      this.updateTimetable();
    } else {
      dateSelection.classList.add('active');
      statusCards.forEach(card => card.style.display = 'none');
      this.updateSelectedDateInfo();
    }
  }

  // ========== DATE SELECTION ==========
  initDateSelection() {
    const datePicker = document.getElementById('date-picker');
    const viewSelectedDateBtn = document.getElementById('view-selected-date');
    const backToTodayBtn = document.getElementById('back-to-today');

    if (datePicker && !datePicker.value) {
      datePicker.value = this.getTodayDateString();
    }

    viewSelectedDateBtn?.addEventListener('click', () => {
      this.viewSelectedDate();
    });

    backToTodayBtn?.addEventListener('click', () => {
      this.switchView('today');
    });

    datePicker?.addEventListener('change', () => {
      this.updateSelectedDateInfo();
    });
  }

  initNavigationButtons() {
    document.getElementById('prev-day-btn')?.addEventListener('click', (e) => {
      e.preventDefault();
      this.goToPrevDay();
    });
    
    document.getElementById('next-day-btn')?.addEventListener('click', (e) => {
      e.preventDefault();
      this.goToNextDay();
    });
  }

  viewSelectedDate() {
    const datePicker = document.getElementById('date-picker');
    const selectedDate = datePicker.value;
    
    if (!selectedDate) {
      this.showSnackbar('請選擇一個日期');
      return;
    }

    this.selectedDate = selectedDate;
    this.updateTimetableForDate(selectedDate);
    document.getElementById('selected-date-info').style.display = 'block';
  }

  goToPrevDay() {
    if (this.currentViewMode !== 'date-select') {
      this.switchView('date-select');
    }
    const datePicker = document.getElementById('date-picker');
    let currentDate = datePicker.value || this.getTodayDateString();
    const date = new Date(currentDate + 'T00:00:00');
    if (isNaN(date.getTime())) return;
    
    date.setDate(date.getDate() - 1);
    while (this.isWeekendDate(date)) {
      date.setDate(date.getDate() - 1);
    }
    
    const prevDate = this.formatDateToISO(date);
    datePicker.value = prevDate;
    this.selectedDate = prevDate;
    this.viewSelectedDate();
    this.updateSelectedDateInfo();
  }

  goToNextDay() {
    if (this.currentViewMode !== 'date-select') {
      this.switchView('date-select');
    }
    const datePicker = document.getElementById('date-picker');
    let baseDate = datePicker.value || this.getTodayDateString();
    let dateArr = baseDate.split('-');
    let dateObj = new Date(Number(dateArr[0]), Number(dateArr[1]) - 1, Number(dateArr[2]));
    if (isNaN(dateObj.getTime())) return;
    
    dateObj.setDate(dateObj.getDate() + 1);
    while (this.isWeekendDate(dateObj)) {
      dateObj.setDate(dateObj.getDate() + 1);
    }
    
    const nextDate = this.formatDateToISO(dateObj);
    datePicker.value = nextDate;
    this.selectedDate = nextDate;
    this.viewSelectedDate();
    this.updateSelectedDateInfo();
  }

  updateSelectedDateInfo() {
    const datePicker = document.getElementById('date-picker');
    const selectedDate = datePicker.value;
    if (!selectedDate) return;

    const date = new Date(selectedDate + 'T00:00:00');
    const timetableType = this.getTimetableTypeForDate(selectedDate);
    const dayCycle = this.calculateDayCycle(selectedDate);

    document.getElementById('selected-date-display').textContent = 
      date.toLocaleDateString('zh-HK', { 
        year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' 
      });
    
    document.getElementById('selected-day-cycle').textContent = 
      dayCycle ? `Day ${dayCycle}` : '無循環日';
    
    document.getElementById('selected-timetable-type').textContent = 
      this.getTimetableTypeText(timetableType);
  }

  // ========== SWIPE GESTURES ==========
  initSwipeGestures() {
    const container = document.querySelector('.app-container');
    let startX = 0;
    let startY = 0;
    let isDragging = false;

    container.addEventListener('touchstart', (e) => {
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      isDragging = true;
    }, { passive: true });

    container.addEventListener('touchmove', (e) => {
      if (!isDragging) return;
      
      const currentX = e.touches[0].clientX;
      const currentY = e.touches[0].clientY;
      const diffX = currentX - startX;
      const diffY = currentY - startY;

      // Only show indicator for horizontal swipes
      if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 50) {
        if (diffX > 0) {
          document.getElementById('swipe-left')?.classList.add('show');
          document.getElementById('swipe-right')?.classList.remove('show');
        } else {
          document.getElementById('swipe-right')?.classList.add('show');
          document.getElementById('swipe-left')?.classList.remove('show');
        }
      }
    }, { passive: true });

    container.addEventListener('touchend', (e) => {
      if (!isDragging) return;
      isDragging = false;
      
      const endX = e.changedTouches[0].clientX;
      const endY = e.changedTouches[0].clientY;
      const diffX = endX - startX;
      const diffY = endY - startY;

      // Hide indicators
      document.getElementById('swipe-left')?.classList.remove('show');
      document.getElementById('swipe-right')?.classList.remove('show');

      // Minimum swipe distance
      if (Math.abs(diffX) > 100 && Math.abs(diffX) > Math.abs(diffY)) {
        if (diffX > 0) {
          this.goToPrevDay();
          this.triggerHaptic();
        } else {
          this.goToNextDay();
          this.triggerHaptic();
        }
      }
    }, { passive: true });
  }

  triggerHaptic() {
    if ('vibrate' in navigator) {
      navigator.vibrate(10);
    }
  }

  // ========== ONLINE STATUS ==========
  initOnlineStatus() {
    const updateOnlineStatus = () => {
      this.isOnline = navigator.onLine;
      const indicator = document.getElementById('offline-indicator');
      
      if (!this.isOnline) {
        indicator?.classList.add('show');
        this.showSnackbar('已進入離線模式');
      } else {
        indicator?.classList.remove('show');
        if (!this.isOnline) {
          this.showSnackbar('已恢復連線');
        }
      }
    };

    window.addEventListener('online', updateOnlineStatus);
    window.addEventListener('offline', updateOnlineStatus);
    updateOnlineStatus();
  }

  // ========== NOTIFICATIONS ==========
  initNotifications() {
    const notificationManager = window.NotificationManager;
    if (!notificationManager) return;

    notificationManager.init();
    
    const settings = notificationManager.getSettings();
    this.updateNotificationUI(settings);

    // Notification toggle button
    document.getElementById('notification-toggle')?.addEventListener('click', async () => {
      const settings = notificationManager.getSettings();
      if (settings.enabled) {
        notificationManager.disable();
        this.showSnackbar('通知已關閉');
      } else {
        const result = await notificationManager.enable();
        if (result.success) {
          this.showSnackbar('通知已開啟');
        } else {
          this.showSnackbar(result.message || '無法開啟通知');
        }
      }
      this.updateNotificationUI(notificationManager.getSettings());
    });

    // FAB for mobile
    document.getElementById('fab-notifications')?.addEventListener('click', async () => {
      const settings = notificationManager.getSettings();
      if (settings.enabled) {
        notificationManager.disable();
        this.showSnackbar('通知已關閉');
      } else {
        const result = await notificationManager.enable();
        if (result.success) {
          this.showSnackbar('通知已開啟');
        } else {
          this.showSnackbar(result.message || '無法開啟通知');
        }
      }
      this.updateNotificationUI(notificationManager.getSettings());
    });

    // Permission banner
    if (settings.supported && settings.permission === 'default') {
      document.getElementById('notification-banner')?.classList.add('show');
    }

    document.getElementById('enable-notifications')?.addEventListener('click', async () => {
      const result = await notificationManager.enable();
      document.getElementById('notification-banner')?.classList.remove('show');
      if (result.success) {
        this.showSnackbar('通知已開啟');
        this.updateNotificationUI(notificationManager.getSettings());
      }
    });

    document.getElementById('dismiss-notification-banner')?.addEventListener('click', () => {
      document.getElementById('notification-banner')?.classList.remove('show');
    });
  }

  updateNotificationUI(settings) {
    const icon = document.getElementById('notification-icon');
    const fab = document.getElementById('fab-notifications');
    
    if (settings.enabled && settings.permission === 'granted') {
      icon.textContent = 'notifications_active';
      fab?.querySelector('.material-symbols-rounded').textContent = 'notifications_active';
    } else {
      icon.textContent = 'notifications_off';
      fab?.querySelector('.material-symbols-rounded').textContent = 'notifications_off';
    }
  }

  // ========== SNACKBAR ==========
  initSnackbar() {
    document.getElementById('snackbar-action')?.addEventListener('click', () => {
      this.hideSnackbar();
    });
  }

  showSnackbar(message, actionText = '關閉', actionCallback = null) {
    const snackbar = document.getElementById('snackbar');
    const messageEl = document.getElementById('snackbar-message');
    const actionEl = document.getElementById('snackbar-action');
    
    messageEl.textContent = message;
    actionEl.textContent = actionText;
    
    actionEl.onclick = () => {
      if (actionCallback) actionCallback();
      this.hideSnackbar();
    };
    
    snackbar.classList.add('show');
    
    // Auto hide after 4 seconds
    clearTimeout(this.snackbarTimeout);
    this.snackbarTimeout = setTimeout(() => {
      this.hideSnackbar();
    }, 4000);
  }

  hideSnackbar() {
    document.getElementById('snackbar')?.classList.remove('show');
  }

  // ========== PAGE VISIBILITY ==========
  initPageVisibility() {
    const handleVisibilityChange = () => {
      const wasVisible = this.isPageVisible;
      this.isPageVisible = !document.hidden;
      
      if (this.isPageVisible && !wasVisible) {
        this.stopRealTimeUpdates();
        this.startRealTimeUpdates();
        this.updateDateTime();
        if (this.currentViewMode === 'today') {
          this.updateTimetable();
        }
      } else if (!this.isPageVisible && wasVisible) {
        this.stopRealTimeUpdates();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
  }

  initMobileSupport() {
    const touchEvents = ['touchstart', 'touchmove', 'touchend'];
    touchEvents.forEach(eventType => {
      document.addEventListener(eventType, () => {
        this.lastUpdateTime = Date.now();
        if (this.isPageVisible && !this.realTimeUpdateInterval && !this.animationFrameId) {
          this.startRealTimeUpdates();
        }
      }, { passive: true });
    });

    // Timer health check
    setInterval(() => {
      if (this.isPageVisible && this.currentViewMode === 'today') {
        const now = Date.now();
        if (now - this.lastUpdateTime > 5000) {
          this.stopRealTimeUpdates();
          this.startRealTimeUpdates();
        }
      }
    }, 5000);
  }

  startRealTimeUpdates() {
    if (!this.isPageVisible) return;
    this.stopRealTimeUpdates();
    
    this.realTimeUpdateInterval = setInterval(() => {
      if (this.isPageVisible) {
        this.lastUpdateTime = Date.now();
        this.updateDateTime();
        if (this.currentViewMode === 'today') {
          this.updateCountdown();
        }
      }
    }, 1000);

    // RAF fallback for mobile
    if (/iPad|iPhone|iPod|Android/i.test(navigator.userAgent)) {
      const animationUpdate = () => {
        if (this.isPageVisible) {
          const now = Date.now();
          if (now - this.lastUpdateTime >= 1000) {
            this.lastUpdateTime = now;
            this.updateDateTime();
            if (this.currentViewMode === 'today') {
              this.updateCountdown();
            }
          }
          this.animationFrameId = requestAnimationFrame(animationUpdate);
        }
      };
      this.animationFrameId = requestAnimationFrame(animationUpdate);
    }
  }

  stopRealTimeUpdates() {
    if (this.realTimeUpdateInterval) {
      clearInterval(this.realTimeUpdateInterval);
      this.realTimeUpdateInterval = null;
    }
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
  }

  cleanup() {
    this.stopRealTimeUpdates();
    if (this.timetableUpdateInterval) {
      clearInterval(this.timetableUpdateInterval);
    }
  }

  checkURLParams() {
    const params = new URLSearchParams(window.location.search);
    const view = params.get('view');
    if (view === 'today' || view === 'date-select') {
      this.switchView(view);
    }
  }

  // ========== DATETIME ==========
  updateDateTime() {
    const now = new Date();
    this.lastUpdateTime = Date.now();
    
    document.getElementById('current-date').textContent = now.toLocaleDateString('zh-HK', { 
      month: 'long', day: 'numeric', weekday: 'long' 
    });
    document.getElementById('current-time').textContent = now.toLocaleTimeString('zh-HK', { 
      hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  // ========== TIMETABLE ==========
  updateTimetable() {
    const today = this.getTodayDateString();
    
    this.timetableType = this.getTimetableTypeForDate(today);
    this.dayCycle = this.calculateDayCycle(today);
    
    this.updateCurrentPeriod();
    this.updateScheduleDisplay();
    this.updateSpecialNotice();
    
    // Notify about special schedule on first load
    if (!this.hasNotifiedSpecialSchedule && window.NotificationManager) {
      this.hasNotifiedSpecialSchedule = true;
      window.NotificationManager.notifySpecialSchedule(
        this.timetableType, 
        this.getTimetableTypeText(this.timetableType)
      );
    }
  }

  updateTimetableForDate(dateStr) {
    this.timetableType = this.getTimetableTypeForDate(dateStr);
    this.dayCycle = this.calculateDayCycle(dateStr);
    
    this.updateScheduleDisplayForDate(dateStr);
    this.updateSpecialNotice();
  }

  getTimetableTypeForDate(dateStr) {
    if (SPECIAL_DATES[dateStr]) {
      return `special${SPECIAL_DATES[dateStr]}`;
    }

    if (this.isNonSchoolDay(dateStr)) {
      return 'none';
    }

    const date = new Date(dateStr + 'T00:00:00');
    if (!isNaN(date.getTime()) && date.getDay() === 5) {
      return 'specialB';
    }

    return 'normal';
  }

  getTimetableTypeText(timetableType) {
    const types = {
      'specialA': '特殊時間表A - 學期初',
      'specialB': '特殊時間表B',
      'specialC': '特殊時間表C - 長集會',
      'specialD': '特殊時間表D - 社際聚會',
      'specialE': '特殊時間表E - 資訊日準備',
      'none': '非上課日'
    };
    return types[timetableType] || '正常時間表';
  }

  calculateDayCycle(dateStr) {
    return DAY_ROTATION[dateStr] || null;
  }

  isNonSchoolDay(dateStr) {
    const date = new Date(dateStr + 'T00:00:00');
    if (isNaN(date.getTime())) return false;

    const dayOfWeek = date.getDay();
    if (dayOfWeek === 0 || dayOfWeek === 6) return true;

    return !DAY_ROTATION[dateStr] && !SPECIAL_DATES[dateStr];
  }

  updateCurrentPeriod() {
    if (this.timetableType === 'none') {
      this.currentPeriodInfo = {
        type: 'none',
        name: '今日沒有課程',
        start: '',
        end: '',
        subject: '非上課日'
      };
      this.nextPeriodInfo = null;
      this.updateCurrentDisplay();
      this.updateNextDisplay();
      this.updateCountdown();
      return;
    }

    const now = new Date();
    const currentTimeStr = this.formatTimeForComparison(now);
    const timetable = TIMETABLE_DATA[this.timetableType];
    
    if (!timetable) {
      this.currentPeriodInfo = {
        type: 'free',
        name: '時間表資料缺失',
        start: '',
        end: '',
        subject: '請稍後再試'
      };
      this.nextPeriodInfo = null;
      this.updateCurrentDisplay();
      this.updateNextDisplay();
      this.updateCountdown();
      return;
    }
    
    const previousPeriodInfo = this.currentPeriodInfo;
    this.currentPeriodInfo = this.findCurrentPeriod(currentTimeStr, timetable);
    this.nextPeriodInfo = this.findNextPeriod(currentTimeStr, timetable);
    
    // Notify on period change
    if (previousPeriodInfo && 
        previousPeriodInfo.name !== this.currentPeriodInfo.name && 
        window.NotificationManager) {
      window.NotificationManager.notifyPeriodChange(previousPeriodInfo, this.currentPeriodInfo);
    }
    
    this.updateCurrentDisplay();
    this.updateNextDisplay();
    this.updateCountdown();
  }

  findCurrentPeriod(currentTimeStr, timetable) {
    // Check periods
    for (let i = 0; i < timetable.periods.length; i++) {
      const period = timetable.periods[i];
      if (this.isTimeInRange(currentTimeStr, period.start, period.end)) {
        const subject = SUBJECT_SCHEDULE[this.dayCycle]?.[i + 1] || '課程';
        return {
          type: 'period',
          name: `第${i + 1}節`,
          start: period.start,
          end: period.end,
          subject: subject
        };
      }
    }

    // Check breaks
    for (const breakTime of timetable.breaks) {
      if (this.isTimeInRange(currentTimeStr, breakTime.start, breakTime.end)) {
        return {
          type: 'break',
          name: breakTime.name,
          start: breakTime.start,
          end: breakTime.end,
          subject: breakTime.name
        };
      }
    }

    // Check assembly
    if (timetable.preSchoolAssembly && 
        this.isTimeInRange(currentTimeStr, timetable.preSchoolAssembly.start, timetable.preSchoolAssembly.end)) {
      return {
        type: 'assembly',
        name: '朝會',
        start: timetable.preSchoolAssembly.start,
        end: timetable.preSchoolAssembly.end,
        subject: 'Pre-School Assembly'
      };
    }

    return {
      type: 'free',
      name: '空堂時間',
      start: '',
      end: '',
      subject: '目前沒有課程'
    };
  }

  findNextPeriod(currentTimeStr, timetable) {
    const allTimeSlots = [];

    if (timetable.preSchoolAssembly) {
      allTimeSlots.push({
        ...timetable.preSchoolAssembly,
        type: 'assembly',
        periodNumber: 0
      });
    }

    timetable.periods.forEach((period, index) => {
      allTimeSlots.push({
        ...period,
        type: 'period',
        periodNumber: index + 1
      });
    });

    timetable.breaks.forEach(breakTime => {
      allTimeSlots.push({
        ...breakTime,
        type: 'break',
        periodNumber: 0
      });
    });

    allTimeSlots.sort((a, b) => a.start.localeCompare(b.start));

    for (const slot of allTimeSlots) {
      if (slot.start > currentTimeStr) {
        let subject = slot.name;
        if (slot.type === 'period') {
          subject = SUBJECT_SCHEDULE[this.dayCycle]?.[slot.periodNumber] || '課程';
        }

        return {
          type: slot.type,
          name: slot.type === 'period' ? `第${slot.periodNumber}節` : 
                slot.type === 'assembly' ? '朝會' : slot.name,
          start: slot.start,
          end: slot.end,
          subject: subject
        };
      }
    }

    return null;
  }

  updateCurrentDisplay() {
    if (!this.currentPeriodInfo) return;

    document.getElementById('current-period-name').textContent = this.currentPeriodInfo.name;
    const periodTime = (this.currentPeriodInfo.start && this.currentPeriodInfo.end)
      ? `${this.currentPeriodInfo.start} - ${this.currentPeriodInfo.end}`
      : '--:-- - --:--';
    document.getElementById('current-period-time').textContent = periodTime;
    document.getElementById('current-subject').textContent = this.currentPeriodInfo.subject;
  }

  updateNextDisplay() {
    if (this.timetableType === 'none') {
      document.getElementById('next-period-name').textContent = '今日沒有課程';
      document.getElementById('next-period-time').textContent = '';
      document.getElementById('next-subject').textContent = '';
      return;
    }

    if (this.nextPeriodInfo) {
      document.getElementById('next-period-name').textContent = this.nextPeriodInfo.name;
      document.getElementById('next-period-time').textContent = 
        `${this.nextPeriodInfo.start} - ${this.nextPeriodInfo.end}`;
      document.getElementById('next-subject').textContent = this.nextPeriodInfo.subject;
    } else {
      document.getElementById('next-period-name').textContent = '今日課程已結束';
      document.getElementById('next-period-time').textContent = '';
      document.getElementById('next-subject').textContent = '';
    }
  }

  updateCountdown() {
    if (!this.currentPeriodInfo || !this.currentPeriodInfo.end) {
      document.getElementById('countdown-display').textContent = '--:--:--';
      document.getElementById('countdown-label').textContent = '';
      return;
    }

    const now = new Date();
    const endTime = this.parseTimeToDate(this.currentPeriodInfo.end);
    const timeDiff = endTime - now;

    if (timeDiff <= 0) {
      document.getElementById('countdown-display').textContent = '00:00:00';
      document.getElementById('countdown-label').textContent = '時間已到';
      return;
    }

    const hours = Math.floor(timeDiff / (1000 * 60 * 60));
    const minutes = Math.floor((timeDiff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((timeDiff % (1000 * 60)) / 1000);

    document.getElementById('countdown-display').textContent = 
      `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    
    const labels = {
      'period': '下課倒計時',
      'break': '小息結束倒計時',
      'assembly': '朝會結束倒計時'
    };
    document.getElementById('countdown-label').textContent = labels[this.currentPeriodInfo.type] || '';
  }

  updateScheduleDisplay() {
    const container = document.getElementById('schedule-grid');
    const dayIndicator = document.getElementById('day-indicator');
    const scheduleTitle = document.getElementById('schedule-title');
    
    if (this.timetableType === 'none') {
      dayIndicator.textContent = '非上課日';
      scheduleTitle.textContent = '今日無課程';
      this.renderEmptySchedule(container, '今日沒有課程');
      return;
    }

    dayIndicator.textContent = this.dayCycle ? `Day ${this.dayCycle}` : '無循環日';

    const timetable = TIMETABLE_DATA[this.timetableType];
    if (!timetable) {
      scheduleTitle.textContent = '時間表載入失敗';
      this.renderEmptySchedule(container, '暫無時間表資料');
      return;
    }

    const allItems = this.buildScheduleItems(timetable);
    this.renderScheduleItems(container, allItems, true);
  }

  updateScheduleDisplayForDate(dateStr) {
    const container = document.getElementById('schedule-grid');
    const dayIndicator = document.getElementById('day-indicator');
    const scheduleTitle = document.getElementById('schedule-title');
    
    const date = new Date(dateStr + 'T00:00:00');
    const formattedDate = date.toLocaleDateString('zh-HK', { 
      month: 'long', day: 'numeric', weekday: 'long' 
    });
    
    dayIndicator.textContent = this.dayCycle ? `Day ${this.dayCycle}` : '無循環日';
    scheduleTitle.textContent = `${formattedDate} 時間表`;

    if (this.timetableType === 'none') {
      dayIndicator.textContent = '非上課日';
      scheduleTitle.textContent = `${formattedDate} 無課程`;
      this.renderEmptySchedule(container, '該日沒有課程');
      return;
    }
    
    const timetable = TIMETABLE_DATA[this.timetableType];
    if (!timetable) {
      scheduleTitle.textContent = `${formattedDate} 時間表暫未可用`;
      this.renderEmptySchedule(container, '暫無時間表資料');
      return;
    }

    const allItems = this.buildScheduleItems(timetable);
    this.renderScheduleItems(container, allItems, false);
  }

  buildScheduleItems(timetable) {
    const allItems = [];

    if (timetable.preSchoolAssembly) {
      allItems.push({
        ...timetable.preSchoolAssembly,
        type: 'assembly',
        displayName: '朝會',
        subject: 'Pre-School Assembly'
      });
    }

    timetable.periods.forEach((period, index) => {
      const subject = SUBJECT_SCHEDULE[this.dayCycle]?.[index + 1] || '課程';
      allItems.push({
        ...period,
        type: 'period',
        displayName: `第${index + 1}節`,
        subject: subject
      });
    });

    timetable.breaks.forEach(breakTime => {
      allItems.push({
        ...breakTime,
        type: 'break',
        displayName: breakTime.name,
        subject: breakTime.name
      });
    });

    allItems.sort((a, b) => a.start.localeCompare(b.start));
    return allItems;
  }

  renderScheduleItems(container, items, highlightCurrent) {
    container.innerHTML = '';
    const now = new Date();
    const currentTimeStr = this.formatTimeForComparison(now);

    items.forEach(item => {
      const itemElement = document.createElement('div');
      itemElement.className = `schedule-item ${item.type}`;
      
      if (highlightCurrent && this.isTimeInRange(currentTimeStr, item.start, item.end)) {
        itemElement.classList.add('current');
      }

      itemElement.innerHTML = `
        <div class="item-time">${item.start}<br>${item.end}</div>
        <div class="item-period">${item.displayName}</div>
        <div class="item-subject">${item.subject}</div>
      `;

      container.appendChild(itemElement);
    });
  }

  renderEmptySchedule(container, message) {
    container.innerHTML = `
      <div class="schedule-empty">
        <span class="material-symbols-rounded">event_busy</span>
        <span>${message}</span>
      </div>
    `;
  }

  updateSpecialNotice() {
    const noticeBanner = document.getElementById('special-notice-banner');
    const noticeElement = document.getElementById('special-notice');
    
    const notices = {
      'specialA': '特殊時間表A - 學期初安排',
      'specialB': '特殊時間表B',
      'specialC': '特殊時間表C - 長集會',
      'specialD': '特殊時間表D - 社際聚會',
      'specialE': '特殊時間表E - 資訊日準備',
      'none': '今日無課程'
    };
    
    noticeElement.textContent = notices[this.timetableType] || '正常時間表';
  }

  // ========== UTILITIES ==========
  getTodayDateString() {
    return this.formatDateToISO(new Date());
  }

  formatDateToISO(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  isWeekendDate(date) {
    const day = date.getDay();
    return day === 0 || day === 6;
  }

  isTimeInRange(current, start, end) {
    return current >= start && current <= end;
  }

  formatTimeForComparison(date) {
    return date.toTimeString().substring(0, 5);
  }

  parseTimeToDate(timeStr) {
    const today = new Date();
    const [hours, minutes] = timeStr.split(':');
    today.setHours(parseInt(hours), parseInt(minutes), 0, 0);
    return today;
  }
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  window.app = new TimetableApp();
});
