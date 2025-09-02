// 實時時間表應用程式主邏輯
class TimetableApp {
    constructor() {
        this.currentDate = null;
        this.currentTime = null;
        this.timetableType = null;
        this.dayCycle = null;
        this.currentPeriodInfo = null;
        this.nextPeriodInfo = null;
        
        this.init();
    }
    
    init() {
        this.updateDateTime();
        this.updateTimetable();
        
        // 每秒更新时间和倒计时
        setInterval(() => {
            this.updateDateTime();
            this.updateCountdown();
        }, 1000);
        
        // 每分钟更新时间表状态
        setInterval(() => {
            this.updateTimetable();
        }, 60000);
        
        this.updateLastUpdateTime();
    }
    
    updateDateTime() {
        const now = new Date();
        this.currentDate = this.formatDate(now);
        this.currentTime = this.formatTime(now);
        
        document.getElementById('current-date').textContent = this.currentDate;
        document.getElementById('current-time').textContent = this.currentTime;
    }
    
    formatDate(date) {
        const options = { 
            year: 'numeric', 
            month: 'long', 
            day: 'numeric', 
            weekday: 'long' 
        };
        return date.toLocaleDateString('zh-HK', options);
    }
    
    formatTime(date) {
        return date.toLocaleTimeString('zh-HK', { 
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }
    
    updateTimetable() {
        const today = new Date().toISOString().split('T')[0];
        this.timetableType = getTimetableType(today);
        this.dayCycle = getDayCycle(today);
        
        this.updateCurrentPeriod();
        this.updateTodaySchedule();
        this.updateSpecialInfo();
    }
    
    updateCurrentPeriod() {
        const now = new Date();
        const currentTimeStr = this.formatTimeForComparison(now);
        
        let timetable;
        switch(this.timetableType) {
            case 'A': timetable = TIMETABLE_DATA.specialA; break;
            case 'B': timetable = TIMETABLE_DATA.specialB; break;
            case 'C': timetable = TIMETABLE_DATA.specialC; break;
            case 'D': timetable = TIMETABLE_DATA.specialD; break;
            case 'E': timetable = TIMETABLE_DATA.specialE; break;
            default: timetable = TIMETABLE_DATA.normal; break;
        }
        
        // 检查是否在预校朝会时间
        if (this.timetableType === 'normal' && timetable.preSchoolAssembly) {
            const assembly = timetable.preSchoolAssembly;
            if (this.isTimeInRange(currentTimeStr, assembly.start, assembly.end)) {
                this.currentPeriodInfo = {
                    type: 'assembly',
                    name: assembly.name,
                    start: assembly.start,
                    end: assembly.end,
                    subject: assembly.name
                };
                this.updateCurrentPeriodDisplay();
                this.findNextPeriod(currentTimeStr, timetable);
                return;
            }
        }
        
        // 检查是否在课程时间
        for (let i = 0; i < timetable.periods.length; i++) {
            const period = timetable.periods[i];
            if (this.isTimeInRange(currentTimeStr, period.start, period.end)) {
                const subject = this.dayCycle ? SUBJECT_SCHEDULE[this.dayCycle]?.[i + 1] : '課程';
                
                this.currentPeriodInfo = {
                    type: 'period',
                    name: `第${i + 1}節`,
                    start: period.start,
                    end: period.end,
                    subject: subject || '課程'
                };
                this.updateCurrentPeriodDisplay();
                this.findNextPeriod(currentTimeStr, timetable);
                return;
            }
        }
        
        // 检查是否在小息或午餐时间
        for (const breakTime of timetable.breaks) {
            if (this.isTimeInRange(currentTimeStr, breakTime.start, breakTime.end)) {
                this.currentPeriodInfo = {
                    type: 'break',
                    name: breakTime.name,
                    start: breakTime.start,
                    end: breakTime.end,
                    subject: breakTime.name
                };
                this.updateCurrentPeriodDisplay();
                this.findNextPeriod(currentTimeStr, timetable);
                return;
            }
        }
        
        // 如果不在任何时间段内
        this.currentPeriodInfo = {
            type: 'free',
            name: '空堂時間',
            start: '',
            end: '',
            subject: '目前沒有課程'
        };
        this.updateCurrentPeriodDisplay();
        this.findNextPeriod(currentTimeStr, timetable);
    }
    
    findNextPeriod(currentTimeStr, timetable) {
        const allTimeSlots = [];
        
        // 添加预校朝会
        if (this.timetableType === 'normal' && timetable.preSchoolAssembly) {
            allTimeSlots.push({
                ...timetable.preSchoolAssembly,
                type: 'assembly',
                periodNumber: 0
            });
        }
        
        // 添加所有课程
        timetable.periods.forEach((period, index) => {
            allTimeSlots.push({
                ...period,
                type: 'period',
                periodNumber: index + 1
            });
        });
        
        // 添加所有休息时间
        timetable.breaks.forEach(breakTime => {
            allTimeSlots.push({
                ...breakTime,
                type: 'break',
                periodNumber: 0
            });
        });
        
        // 按开始时间排序
        allTimeSlots.sort((a, b) => a.start.localeCompare(b.start));
        
        // 查找下一个时间段
        for (const slot of allTimeSlots) {
            if (slot.start > currentTimeStr) {
                let subject = slot.name;
                if (slot.type === 'period' && this.dayCycle) {
                    subject = SUBJECT_SCHEDULE[this.dayCycle]?.[slot.periodNumber] || '課程';
                }
                
                this.nextPeriodInfo = {
                    type: slot.type,
                    name: slot.type === 'period' ? `第${slot.periodNumber}節` : slot.name,
                    start: slot.start,
                    end: slot.end,
                    subject: subject
                };
                this.updateNextPeriodDisplay();
                return;
            }
        }
        
        this.nextPeriodInfo = null;
        this.updateNextPeriodDisplay();
    }
    
    updateCurrentPeriodDisplay() {
        const periodElement = document.getElementById('current-period');
        const periodName = periodElement.querySelector('.period-name');
        const periodTime = periodElement.querySelector('.period-time');
        const subjectInfo = periodElement.querySelector('.subject-info');
        
        if (this.currentPeriodInfo) {
            periodName.textContent = this.currentPeriodInfo.name;
            periodTime.textContent = `${this.currentPeriodInfo.start} - ${this.currentPeriodInfo.end}`;
            subjectInfo.textContent = this.currentPeriodInfo.subject;
            
            // 设置状态样式
            periodElement.className = `period-info ${this.currentPeriodInfo.type}`;
        }
    }
    
    updateNextPeriodDisplay() {
        const nextPeriodElement = document.getElementById('next-period-info');
        const nextPeriodName = nextPeriodElement.querySelector('.next-period-name');
        const nextPeriodTime = nextPeriodElement.querySelector('.next-period-time');
        const nextSubjectInfo = nextPeriodElement.querySelector('.next-subject-info');
        
        if (this.nextPeriodInfo) {
            nextPeriodName.textContent = this.nextPeriodInfo.name;
            nextPeriodTime.textContent = `${this.nextPeriodInfo.start} - ${this.nextPeriodInfo.end}`;
            nextSubjectInfo.textContent = this.nextPeriodInfo.subject;
        } else {
            nextPeriodName.textContent = '今日課程已結束';
            nextPeriodTime.textContent = '';
            nextSubjectInfo.textContent = '';
        }
    }
    
    updateCountdown() {
        if (!this.currentPeriodInfo || !this.currentPeriodInfo.end) {
            document.querySelector('.countdown-display').textContent = '--:--:--';
            document.querySelector('.countdown-label').textContent = '';
            return;
        }
        
        const now = new Date();
        const endTime = this.parseTimeToDate(this.currentPeriodInfo.end);
        const timeDiff = endTime - now;
        
        if (timeDiff <= 0) {
            document.querySelector('.countdown-display').textContent = '00:00:00';
            document.querySelector('.countdown-label').textContent = '時間已到';
            return;
        }
        
        const hours = Math.floor(timeDiff / (1000 * 60 * 60));
        const minutes = Math.floor((timeDiff % (1000 * 60 * 60)) / (1000 * 60));
        const seconds = Math.floor((timeDiff % (1000 * 60)) / 1000);
        
        const timeString = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
        
        document.querySelector('.countdown-display').textContent = timeString;
        
        let label = '';
        if (this.currentPeriodInfo.type === 'period') {
            label = '下課倒計時';
        } else if (this.currentPeriodInfo.type === 'break') {
            label = '小息結束倒計時';
        } else if (this.currentPeriodInfo.type === 'assembly') {
            label = '朝會結束倒計時';
        }
        
        document.querySelector('.countdown-label').textContent = label;
    }
    
    updateTodaySchedule() {
        const container = document.getElementById('schedule-container');
        const dayCycleElement = document.getElementById('day-cycle');
        
        // 更新Day周期显示
        if (this.dayCycle) {
            dayCycleElement.textContent = `(Day ${this.dayCycle})`;
        } else {
            dayCycleElement.textContent = '';
        }
        
        // 获取当前时间表
        let timetable;
        switch(this.timetableType) {
            case 'A': timetable = TIMETABLE_DATA.specialA; break;
            case 'B': timetable = TIMETABLE_DATA.specialB; break;
            case 'C': timetable = TIMETABLE_DATA.specialC; break;
            case 'D': timetable = TIMETABLE_DATA.specialD; break;
            case 'E': timetable = TIMETABLE_DATA.specialE; break;
            default: timetable = TIMETABLE_DATA.normal; break;
        }
        
        container.innerHTML = '';
        
        // 添加预校朝会（仅正常时间表）
        if (this.timetableType === 'normal' && timetable.preSchoolAssembly) {
            this.addScheduleItem(container, '朝會', timetable.preSchoolAssembly, 'Pre-School Assembly');
        }
        
        // 合并课程和休息时间并排序
        const allItems = [];
        
        // 添加课程
        timetable.periods.forEach((period, index) => {
            const subject = this.dayCycle ? SUBJECT_SCHEDULE[this.dayCycle]?.[index + 1] : '課程';
            allItems.push({
                ...period,
                name: `第${index + 1}節`,
                subject: subject || '課程',
                type: 'period'
            });
        });
        
        // 添加休息时间
        timetable.breaks.forEach(breakTime => {
            allItems.push({
                ...breakTime,
                subject: breakTime.name,
                type: 'break'
            });
        });
        
        // 按开始时间排序
        allItems.sort((a, b) => a.start.localeCompare(b.start));
        
        // 显示所有项目
        allItems.forEach(item => {
            this.addScheduleItem(container, item.name, item, item.subject);
        });
    }
    
    addScheduleItem(container, name, timeInfo, subject) {
        const item = document.createElement('div');
        item.className = `schedule-item ${timeInfo.type || 'period'}`;
        
        const now = new Date();
        const currentTimeStr = this.formatTimeForComparison(now);
        
        // 检查是否为当前时间段
        if (this.isTimeInRange(currentTimeStr, timeInfo.start, timeInfo.end)) {
            item.classList.add('current');
        }
        
        item.innerHTML = `
            <div class="time">${timeInfo.start} - ${timeInfo.end}</div>
            <div class="period">${name}</div>
            <div class="subject">${subject}</div>
        `;
        
        container.appendChild(item);
    }
    
    updateSpecialInfo() {
        const infoElement = document.getElementById('special-timetable-info');
        
        let specialInfo = '';
        switch(this.timetableType) {
            case 'A':
                specialInfo = '特殊時間表A - 學期初安排 (3-10/9)';
                break;
            case 'B':
                specialInfo = '特殊時間表B - 開學禮/中華文化週/復活節崇拜/頒獎典禮準備/學習雙週';
                break;
            case 'C':
                specialInfo = '特殊時間表C - 福音聚會/跳繩比賽/歡送中六畢業生/SA諮詢會';
                break;
            case 'D':
                specialInfo = '特殊時間表D - 社際聚會';
                break;
            case 'E':
                specialInfo = '特殊時間表E - 資訊日準備';
                break;
            default:
                const date = new Date();
                if (date.getDay() === 5) {
                    specialInfo = '星期五使用特殊時間表B';
                } else {
                    specialInfo = '正常時間表';
                }
        }
        
        infoElement.innerHTML = `<div class="special-notice">${specialInfo}</div>`;
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
    
    updateLastUpdateTime() {
        const now = new Date();
        document.getElementById('last-update').textContent = now.toLocaleString('zh-HK');
    }
}

// 初始化應用程式
document.addEventListener('DOMContentLoaded', () => {
    new TimetableApp();
});