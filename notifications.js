/**
 * Notifications Module
 * Handles local notifications for class reminders
 */

class NotificationManager {
  constructor() {
    this.permission = ('Notification' in window) ? Notification.permission : 'denied';
    this.enabled = localStorage.getItem('notifications-enabled') === 'true';
    this.reminderMinutes = parseInt(localStorage.getItem('reminder-minutes')) || 5;
    this.scheduledNotifications = new Map();
    this.checkInterval = null;
  }

  /**
   * Initialize notification system
   */
  async init() {
    // Check if notifications are supported
    if (!('Notification' in window)) {
      console.log('[Notifications] Not supported in this browser');
      return false;
    }

    // Update permission status
    this.permission = Notification.permission;

    // If enabled, start checking for notifications
    if (this.enabled && this.permission === 'granted') {
      this.startNotificationChecker();
    }

    return true;
  }

  /**
   * Request notification permission
   */
  async requestPermission() {
    if (!('Notification' in window)) {
      return { success: false, reason: 'not-supported' };
    }

    if (this.permission === 'granted') {
      return { success: true, permission: 'granted' };
    }

    if (this.permission === 'denied') {
      return { success: false, reason: 'denied', message: '通知權限已被拒絕，請在瀏覽器設定中開啟' };
    }

    try {
      const result = await Notification.requestPermission();
      this.permission = result;
      
      if (result === 'granted') {
        this.showTestNotification();
        return { success: true, permission: 'granted' };
      } else {
        return { success: false, reason: result };
      }
    } catch (error) {
      console.error('[Notifications] Permission request failed:', error);
      return { success: false, reason: 'error', message: error.message };
    }
  }

  /**
   * Enable notifications
   */
  async enable() {
    const permResult = await this.requestPermission();
    
    if (permResult.success) {
      this.enabled = true;
      localStorage.setItem('notifications-enabled', 'true');
      this.startNotificationChecker();
      return { success: true };
    }
    
    return permResult;
  }

  /**
   * Disable notifications
   */
  disable() {
    this.enabled = false;
    localStorage.setItem('notifications-enabled', 'false');
    this.stopNotificationChecker();
    this.clearAllScheduled();
    return { success: true };
  }

  /**
   * Set reminder time (minutes before class)
   */
  setReminderTime(minutes) {
    this.reminderMinutes = Math.max(1, Math.min(30, minutes));
    localStorage.setItem('reminder-minutes', this.reminderMinutes.toString());
  }

  /**
   * Get notification settings
   */
  getSettings() {
    return {
      supported: 'Notification' in window,
      permission: this.permission,
      enabled: this.enabled,
      reminderMinutes: this.reminderMinutes
    };
  }

  /**
   * Show a test notification
   */
  showTestNotification() {
    if (this.permission !== 'granted') return;

    this.show({
      title: '通知已啟用 ✓',
      body: '您將在課堂開始前收到提醒',
      icon: './icons/icon-192.png',
      tag: 'test-notification'
    });
  }

  /**
   * Show a notification
   */
  show(options) {
    if (this.permission !== 'granted') {
      console.log('[Notifications] Permission not granted');
      return null;
    }

    const notification = new Notification(options.title, {
      body: options.body,
      icon: options.icon || './icons/icon-192.png',
      badge: options.badge || './icons/icon-72.png',
      tag: options.tag || 'timetable-notification',
      requireInteraction: options.requireInteraction || false,
      silent: options.silent || false,
      vibrate: options.vibrate || [200, 100, 200]
    });

    notification.onclick = () => {
      window.focus();
      notification.close();
      if (options.onClick) options.onClick();
    };

    return notification;
  }

  /**
   * Start checking for upcoming classes
   */
  startNotificationChecker() {
    if (this.checkInterval) {
      clearInterval(this.checkInterval);
    }

    // Check every 30 seconds
    this.checkInterval = setInterval(() => {
      this.checkUpcomingClasses();
    }, 30000);

    // Also check immediately
    this.checkUpcomingClasses();
  }

  /**
   * Stop notification checker
   */
  stopNotificationChecker() {
    if (this.checkInterval) {
      clearInterval(this.checkInterval);
      this.checkInterval = null;
    }
  }

  /**
   * Check for upcoming classes and schedule notifications
   */
  checkUpcomingClasses() {
    if (!this.enabled || this.permission !== 'granted') return;

    const api = window.TimetableAPI;
    if (!api) return;

    const current = api.getCurrent();
    if (!current.success || !current.data.nextPeriod) return;

    const next = current.data.nextPeriod;
    if (next.type !== 'period') return; // Only notify for actual classes

    const now = new Date();
    const [hours, minutes] = next.start.split(':').map(Number);
    const startTime = new Date();
    startTime.setHours(hours, minutes, 0, 0);

    const minutesUntilStart = Math.floor((startTime - now) / 60000);

    // Check if we should notify
    if (minutesUntilStart > 0 && minutesUntilStart <= this.reminderMinutes) {
      const notificationKey = `${next.start}-${next.name}`;
      
      // Don't send duplicate notifications
      if (!this.scheduledNotifications.has(notificationKey)) {
        this.scheduledNotifications.set(notificationKey, true);
        
        this.show({
          title: `${next.name} 即將開始`,
          body: `${next.subject}\n${minutesUntilStart} 分鐘後開始 (${next.start})`,
          tag: `class-reminder-${next.start}`,
          requireInteraction: true
        });

        // Clear the key after the class starts
        setTimeout(() => {
          this.scheduledNotifications.delete(notificationKey);
        }, (minutesUntilStart + 1) * 60000);
      }
    }
  }

  /**
   * Schedule a notification for a specific time
   */
  scheduleNotification(time, options) {
    const now = Date.now();
    const targetTime = time.getTime();
    const delay = targetTime - now;

    if (delay <= 0) return null;

    const timeoutId = setTimeout(() => {
      this.show(options);
    }, delay);

    return timeoutId;
  }

  /**
   * Clear all scheduled notifications
   */
  clearAllScheduled() {
    this.scheduledNotifications.clear();
  }

  /**
   * Notify about special schedule
   */
  notifySpecialSchedule(timetableType, typeName) {
    if (!this.enabled || this.permission !== 'granted') return;

    if (timetableType !== 'normal' && timetableType !== 'none') {
      this.show({
        title: '今日特殊時間表',
        body: typeName,
        tag: 'special-schedule',
        requireInteraction: false
      });
    }
  }

  /**
   * Notify about period change
   */
  notifyPeriodChange(fromPeriod, toPeriod) {
    if (!this.enabled || this.permission !== 'granted') return;

    if (toPeriod && toPeriod.type === 'period') {
      this.show({
        title: `${toPeriod.name} 開始`,
        body: toPeriod.subject,
        tag: 'period-change',
        requireInteraction: false
      });
    }
  }
}

// Create global notification manager instance
window.NotificationManager = new NotificationManager();
