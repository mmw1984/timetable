/**
 * Timetable API Module
 * Provides GET-like API interface for timetable data
 * Can be used via URL parameters or JavaScript calls
 */

class TimetableAPI {
  constructor() {
    this.timetableData = window.TIMETABLE_DATA;
    this.subjectSchedule = window.SUBJECT_SCHEDULE;
    this.dayRotation = window.DAY_ROTATION;
    this.specialDates = window.SPECIAL_DATES;
  }

  /**
   * GET /api/today - Get today's timetable
   * @returns {Object} Today's complete timetable data
   */
  getToday() {
    const today = this.formatDateToISO(new Date());
    return this.getByDate(today);
  }

  /**
   * GET /api/date/:date - Get timetable for a specific date
   * @param {string} dateStr - Date in YYYY-MM-DD format
   * @returns {Object} Timetable data for the specified date
   */
  getByDate(dateStr) {
    const date = new Date(dateStr + 'T00:00:00');
    
    if (isNaN(date.getTime())) {
      return this.errorResponse('Invalid date format. Use YYYY-MM-DD');
    }

    const dayOfWeek = date.getDay();
    const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
    const dayCycle = this.dayRotation[dateStr] || null;
    const specialType = this.specialDates[dateStr] || null;
    
    let timetableType = 'normal';
    if (specialType) {
      timetableType = `special${specialType}`;
    } else if (isWeekend || !dayCycle) {
      timetableType = 'none';
    } else if (dayOfWeek === 5) {
      timetableType = 'specialB';
    }

    const isSchoolDay = timetableType !== 'none';
    const timetable = this.timetableData[timetableType];

    const response = {
      success: true,
      timestamp: new Date().toISOString(),
      data: {
        date: dateStr,
        dayOfWeek: this.getDayOfWeekName(dayOfWeek),
        dayOfWeekNumber: dayOfWeek,
        dayCycle: dayCycle,
        timetableType: timetableType,
        timetableTypeName: this.getTimetableTypeName(timetableType),
        isSchoolDay: isSchoolDay,
        isWeekend: isWeekend
      }
    };

    if (isSchoolDay && timetable) {
      response.data.schedule = this.buildSchedule(timetable, dayCycle);
      response.data.periods = timetable.periods.length;
      response.data.breaks = timetable.breaks.length;
      
      if (timetable.preSchoolAssembly) {
        response.data.preSchoolAssembly = timetable.preSchoolAssembly;
      }
    } else {
      response.data.schedule = [];
      response.data.message = isWeekend ? '週末休息' : '非上課日';
    }

    return response;
  }

  /**
   * GET /api/current - Get current period information
   * @returns {Object} Current period data
   */
  getCurrent() {
    const now = new Date();
    const today = this.formatDateToISO(now);
    const currentTime = this.formatTimeForComparison(now);
    
    const todayData = this.getByDate(today);
    
    if (!todayData.success || !todayData.data.isSchoolDay) {
      return {
        success: true,
        timestamp: new Date().toISOString(),
        data: {
          date: today,
          currentTime: currentTime,
          status: 'no-school',
          currentPeriod: null,
          nextPeriod: null,
          message: todayData.data?.message || '今日沒有課程'
        }
      };
    }

    const schedule = todayData.data.schedule;
    let currentPeriod = null;
    let nextPeriod = null;
    let status = 'free';

    for (const item of schedule) {
      if (this.isTimeInRange(currentTime, item.start, item.end)) {
        currentPeriod = item;
        status = item.type;
        break;
      }
    }

    for (const item of schedule) {
      if (item.start > currentTime) {
        nextPeriod = item;
        break;
      }
    }

    // Calculate remaining time
    let remainingSeconds = null;
    if (currentPeriod) {
      const endTime = this.parseTimeToDate(currentPeriod.end);
      remainingSeconds = Math.max(0, Math.floor((endTime - now) / 1000));
    }

    return {
      success: true,
      timestamp: new Date().toISOString(),
      data: {
        date: today,
        currentTime: currentTime,
        dayCycle: todayData.data.dayCycle,
        status: status,
        currentPeriod: currentPeriod,
        nextPeriod: nextPeriod,
        remainingSeconds: remainingSeconds,
        remainingFormatted: remainingSeconds ? this.formatSeconds(remainingSeconds) : null
      }
    };
  }

  /**
   * GET /api/week - Get current week's timetable
   * @returns {Object} Week's timetable data
   */
  getWeek() {
    const today = new Date();
    const dayOfWeek = today.getDay();
    const monday = new Date(today);
    monday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1));

    const weekData = [];
    for (let i = 0; i < 7; i++) {
      const date = new Date(monday);
      date.setDate(monday.getDate() + i);
      const dateStr = this.formatDateToISO(date);
      weekData.push(this.getByDate(dateStr));
    }

    return {
      success: true,
      timestamp: new Date().toISOString(),
      data: {
        weekStart: this.formatDateToISO(monday),
        weekEnd: this.formatDateToISO(new Date(monday.getTime() + 6 * 24 * 60 * 60 * 1000)),
        days: weekData.map(d => d.data)
      }
    };
  }

  /**
   * GET /api/subjects - Get all subjects
   * @returns {Object} Subject schedule data
   */
  getSubjects() {
    return {
      success: true,
      timestamp: new Date().toISOString(),
      data: {
        dayCycles: Object.keys(this.subjectSchedule),
        schedule: this.subjectSchedule
      }
    };
  }

  /**
   * GET /api/timetables - Get all timetable types
   * @returns {Object} Available timetable types
   */
  getTimetables() {
    const types = Object.keys(this.timetableData).map(key => ({
      id: key,
      name: this.getTimetableTypeName(key),
      periodsCount: this.timetableData[key].periods.length,
      breaksCount: this.timetableData[key].breaks.length,
      hasAssembly: !!this.timetableData[key].preSchoolAssembly
    }));

    return {
      success: true,
      timestamp: new Date().toISOString(),
      data: {
        types: types
      }
    };
  }

  // Helper methods
  buildSchedule(timetable, dayCycle) {
    const schedule = [];

    if (timetable.preSchoolAssembly) {
      schedule.push({
        type: 'assembly',
        name: '朝會',
        displayName: '朝會',
        start: timetable.preSchoolAssembly.start,
        end: timetable.preSchoolAssembly.end,
        subject: 'Pre-School Assembly',
        duration: this.calculateDuration(timetable.preSchoolAssembly.start, timetable.preSchoolAssembly.end)
      });
    }

    timetable.periods.forEach((period, index) => {
      const periodNumber = index + 1;
      const subject = this.subjectSchedule[dayCycle]?.[periodNumber] || '課程';
      schedule.push({
        type: 'period',
        name: `第${periodNumber}節`,
        displayName: `P${periodNumber}`,
        periodNumber: periodNumber,
        start: period.start,
        end: period.end,
        subject: subject,
        duration: this.calculateDuration(period.start, period.end)
      });
    });

    timetable.breaks.forEach(breakTime => {
      schedule.push({
        type: 'break',
        name: breakTime.name,
        displayName: breakTime.name,
        start: breakTime.start,
        end: breakTime.end,
        subject: breakTime.name,
        duration: this.calculateDuration(breakTime.start, breakTime.end)
      });
    });

    schedule.sort((a, b) => a.start.localeCompare(b.start));
    return schedule;
  }

  calculateDuration(start, end) {
    const [startH, startM] = start.split(':').map(Number);
    const [endH, endM] = end.split(':').map(Number);
    return (endH * 60 + endM) - (startH * 60 + startM);
  }

  formatDateToISO(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
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

  isTimeInRange(current, start, end) {
    return current >= start && current <= end;
  }

  formatSeconds(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  getDayOfWeekName(day) {
    const names = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
    return names[day];
  }

  getTimetableTypeName(type) {
    const names = {
      'normal': '正常時間表',
      'specialA': '特殊時間表A - 學期初',
      'specialB': '特殊時間表B',
      'specialC': '特殊時間表C - 長集會',
      'specialD': '特殊時間表D - 社際聚會',
      'specialE': '特殊時間表E - 資訊日準備',
      'none': '非上課日'
    };
    return names[type] || type;
  }

  errorResponse(message) {
    return {
      success: false,
      timestamp: new Date().toISOString(),
      error: {
        message: message
      }
    };
  }
}

// Create global API instance
window.TimetableAPI = new TimetableAPI();

// Handle URL query parameters for GET requests
function handleURLParams() {
  const params = new URLSearchParams(window.location.search);
  const apiEndpoint = params.get('api');
  
  if (apiEndpoint) {
    const api = window.TimetableAPI;
    if (!api) {
      console.error('[API] TimetableAPI not available');
      return;
    }
    let response;

    switch (apiEndpoint) {
      case 'today':
        response = api.getToday();
        break;
      case 'current':
        response = api.getCurrent();
        break;
      case 'week':
        response = api.getWeek();
        break;
      case 'subjects':
        response = api.getSubjects();
        break;
      case 'timetables':
        response = api.getTimetables();
        break;
      case 'date':
        const date = params.get('date');
        if (date) {
          response = api.getByDate(date);
        } else {
          response = api.errorResponse('Missing date parameter');
        }
        break;
      default:
        response = api.errorResponse(`Unknown endpoint: ${apiEndpoint}`);
    }

    // If raw JSON is requested
    if (params.get('format') === 'json') {
      document.body.innerHTML = `<pre>${JSON.stringify(response, null, 2)}</pre>`;
      document.body.style.fontFamily = 'monospace';
      document.body.style.whiteSpace = 'pre-wrap';
    }

    // Store response for programmatic access
    window.apiResponse = response;
  }
}

// Run after DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', handleURLParams);
} else {
  handleURLParams();
}

/**
 * Usage Examples:
 * 
 * JavaScript:
 * const api = window.TimetableAPI;
 * const today = api.getToday();
 * const current = api.getCurrent();
 * const specific = api.getByDate('2025-12-15');
 * const week = api.getWeek();
 * 
 * URL Parameters (GET-like interface):
 * ?api=today              - Get today's timetable
 * ?api=current            - Get current period info
 * ?api=date&date=2025-12-15  - Get specific date
 * ?api=week               - Get current week
 * ?api=subjects           - Get all subjects
 * ?api=timetables         - Get all timetable types
 * 
 * Add &format=json to get raw JSON output in browser
 */
