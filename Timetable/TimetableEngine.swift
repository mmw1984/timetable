import Foundation
import Combine

// MARK: - Current Period Info

struct PeriodInfo: Equatable {
    enum PeriodType: Equatable {
        case period, breakTime, assembly, free, none
    }
    let type: PeriodType
    let name: String
    let start: String
    let end: String
    let subject: String
}

// MARK: - Timetable Engine

@MainActor
final class TimetableEngine: ObservableObject {

    // MARK: Published state

    @Published var currentDate: Date = .now
    @Published var currentPeriod: PeriodInfo = PeriodInfo(type: .free, name: "載入中...", start: "", end: "", subject: "正在載入...")
    @Published var nextPeriod: PeriodInfo?
    @Published var countdownText: String = "--:--:--"
    @Published var countdownLabel: String = ""
    @Published var timetableType: TimetableType = .none
    @Published var dayCycle: Int?
    @Published var scheduleItems: [ScheduleItem] = []
    @Published var currentItemID: UUID?

    // Date selection mode
    @Published var viewMode: ViewMode = .today
    @Published var selectedDate: Date = .now

    // Future day rotation
    @Published var futureDays: [(date: Date, day: Int)] = []

    // Transition direction for animations
    @Published var transitionDirection: TransitionDirection = .none

    enum ViewMode: Equatable {
        case today, dateSelect
    }

    enum TransitionDirection {
        case none, forward, backward
    }

    private var timer: Timer?
    private let calendar = Calendar.current

    // Live data source reference
    private var ds: DataSourceManager { DataSourceManager.shared }

    // MARK: - Formatters (shared)

    static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh-HK")
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh-HK")
        f.dateFormat = "yyyy年M月d日 EEEE"
        return f
    }()

    static let fullDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh-HK")
        f.dateFormat = "yyyy年M月d日 EEEE"
        return f
    }()

    private static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    // MARK: - Lifecycle

    init() {
        refresh()
        updateFutureDays()
        startTimer()
    }

    deinit {
        timer?.invalidate()
    }

    // MARK: - Timer

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor [weak self] in
                self?.tick()
            }
        }
    }

    private func tick() {
        currentDate = .now
        if viewMode == .today {
            updateCurrentPeriod()
            updateCountdown()
        }
    }

    // MARK: - Public API

    func refresh() {
        currentDate = .now
        if viewMode == .today {
            let todayStr = Self.isoString(from: .now)
            timetableType = timetableTypeForDate(todayStr)
            dayCycle = ds.dayRotation[todayStr]
            updateCurrentPeriod()
            updateScheduleItems(dateStr: todayStr, highlightCurrent: true)
            updateCountdown()
        } else {
            let dateStr = Self.isoString(from: selectedDate)
            timetableType = timetableTypeForDate(dateStr)
            dayCycle = ds.dayRotation[dateStr]
            updateScheduleItems(dateStr: dateStr, highlightCurrent: false)
        }
        updateFutureDays()
    }

    func switchToToday() {
        transitionDirection = .none
        viewMode = .today
        selectedDate = .now
        refresh()
    }

    func viewDate(_ date: Date) {
        viewMode = .dateSelect
        selectedDate = date
        let dateStr = Self.isoString(from: date)
        timetableType = timetableTypeForDate(dateStr)
        dayCycle = ds.dayRotation[dateStr]
        updateScheduleItems(dateStr: dateStr, highlightCurrent: false)
    }

    func goToPrevDay() {
        transitionDirection = .backward
        var d = selectedDate
        repeat {
            d = calendar.date(byAdding: .day, value: -1, to: d) ?? d
        } while isWeekend(d)
        viewDate(d)
    }

    func goToNextDay() {
        transitionDirection = .forward
        var d = selectedDate
        repeat {
            d = calendar.date(byAdding: .day, value: 1, to: d) ?? d
        } while isWeekend(d)
        viewDate(d)
    }

    // MARK: - Timetable type detection (uses live data)

    func timetableTypeForDate(_ dateStr: String) -> TimetableType {
        if let special = ds.specialDates[dateStr] {
            switch special {
            case "A": return .specialA
            case "B": return .specialB
            case "C": return .specialC
            case "D": return .specialD
            case "E": return .specialE
            default:  return .normal
            }
        }

        if isNonSchoolDay(dateStr) { return .none }

        // Friday → specialB
        if let date = Self.isoDate(from: dateStr) {
            let weekday = Calendar.current.component(.weekday, from: date)
            if weekday == 6 { return .specialB }
        }

        return .normal
    }

    // MARK: - Future Day Rotation

    func updateFutureDays() {
        var result: [(date: Date, day: Int)] = []
        let today = Date.now
        var d = today

        // Find the next occurrence of each Day 1-6
        var foundDays: Set<Int> = []
        let maxSearch = 60 // search up to 60 days ahead

        for _ in 0..<maxSearch {
            d = calendar.date(byAdding: .day, value: 1, to: d) ?? d
            let dateStr = Self.isoString(from: d)
            if let day = ds.dayRotation[dateStr], !foundDays.contains(day) {
                result.append((date: d, day: day))
                foundDays.insert(day)
                if foundDays.count == 6 { break }
            }
        }

        result.sort { $0.day < $1.day }
        futureDays = result
    }

    // MARK: - Current period logic

    private func updateCurrentPeriod() {
        let todayStr = Self.isoString(from: .now)
        let type = timetableTypeForDate(todayStr)
        self.timetableType = type
        self.dayCycle = ds.dayRotation[todayStr]

        guard type != .none, let schedule = ds.schedule(for: type) else {
            currentPeriod = PeriodInfo(type: .none, name: "今日沒有課程", start: "", end: "", subject: "非上課日")
            nextPeriod = nil
            return
        }

        let now = currentTimeString()
        currentPeriod = findCurrentPeriod(time: now, schedule: schedule)
        nextPeriod = findNextPeriod(time: now, schedule: schedule)
        updateScheduleItems(dateStr: todayStr, highlightCurrent: true)
    }

    private func findCurrentPeriod(time: String, schedule: TimetableSchedule) -> PeriodInfo {
        if let asm = schedule.preSchoolAssembly, isInRange(time, asm.start, asm.end) {
            return PeriodInfo(type: .assembly, name: "早會", start: asm.start, end: asm.end, subject: "Pre-School Assembly")
        }

        for (i, p) in schedule.periods.enumerated() {
            if isInRange(time, p.start, p.end) {
                let subject = ds.subjectSchedule[dayCycle ?? 0]?[i + 1] ?? "課程"
                return PeriodInfo(type: .period, name: "第\(i + 1)節", start: p.start, end: p.end, subject: subject)
            }
        }

        for b in schedule.breaks {
            if isInRange(time, b.start, b.end) {
                return PeriodInfo(type: .breakTime, name: b.name, start: b.start, end: b.end, subject: b.name)
            }
        }

        return PeriodInfo(type: .free, name: "空堂時間", start: "", end: "", subject: "目前沒有課程")
    }

    private func findNextPeriod(time: String, schedule: TimetableSchedule) -> PeriodInfo? {
        var slots: [(start: String, end: String, type: PeriodInfo.PeriodType, name: String, subject: String)] = []

        if let asm = schedule.preSchoolAssembly {
            slots.append((asm.start, asm.end, .assembly, "早會", "Pre-School Assembly"))
        }
        for (i, p) in schedule.periods.enumerated() {
            let subject = ds.subjectSchedule[dayCycle ?? 0]?[i + 1] ?? "課程"
            slots.append((p.start, p.end, .period, "第\(i + 1)節", subject))
        }
        for b in schedule.breaks {
            slots.append((b.start, b.end, .breakTime, b.name, b.name))
        }

        slots.sort { $0.start < $1.start }

        for s in slots where s.start > time {
            return PeriodInfo(type: s.type, name: s.name, start: s.start, end: s.end, subject: s.subject)
        }
        return nil
    }

    // MARK: - Countdown

    func updateCountdown() {
        guard currentPeriod.type != .free && currentPeriod.type != .none,
              !currentPeriod.end.isEmpty else {
            countdownText = "--:--:--"
            countdownLabel = ""
            return
        }

        let now = Date.now

        if currentPeriod.type == .period,
           let startDate = todayDate(from: currentPeriod.start),
           now < startDate.addingTimeInterval(60) {
            countdownText = "--:--:--"
            countdownLabel = "上課 1 分鐘後開始倒計時"
            return
        }

        guard let endDate = todayDate(from: currentPeriod.end) else {
            countdownText = "--:--:--"
            countdownLabel = ""
            return
        }

        let diff = endDate.timeIntervalSince(now)
        if diff <= 0 {
            countdownText = "00:00:00"
            countdownLabel = "時間已到"
            return
        }

        let h = Int(diff) / 3600
        let m = (Int(diff) % 3600) / 60
        let s = Int(diff) % 60
        countdownText = String(format: "%02d:%02d:%02d", h, m, s)

        switch currentPeriod.type {
        case .period:    countdownLabel = "下課倒計時"
        case .breakTime: countdownLabel = "小息結束倒計時"
        case .assembly:  countdownLabel = "早會結束倒計時"
        default:         countdownLabel = ""
        }
    }

    // MARK: - Merged countdown for consecutive same-subject periods

    /// Returns the end time considering merged consecutive periods with same subject
    func mergedCountdownEnd() -> String? {
        guard currentPeriod.type == .period else { return nil }
        let currentSubject = currentPeriod.subject

        // Find current and next periods
        let sortedPeriods = scheduleItems.filter {
            if case .period = $0.type { return true }
            return false
        }.sorted { $0.start < $1.start }

        guard let currentIdx = sortedPeriods.firstIndex(where: { isInRange(currentTimeString(), $0.start, $0.end) }) else {
            return nil
        }

        var endTime = sortedPeriods[currentIdx].end
        var nextIdx = currentIdx + 1

        while nextIdx < sortedPeriods.count {
            let next = sortedPeriods[nextIdx]
            // Check if there's only a break (no subject change) between them
            if next.subject == currentSubject {
                // Check no break interrupts continuity - next period starts where a break ends
                endTime = next.end
                nextIdx += 1
            } else {
                break
            }
        }

        return endTime == currentPeriod.end ? nil : endTime
    }

    // MARK: - Schedule items

    private func updateScheduleItems(dateStr: String, highlightCurrent: Bool) {
        guard timetableType != .none, let schedule = ds.schedule(for: timetableType) else {
            scheduleItems = []
            currentItemID = nil
            return
        }

        var items: [ScheduleItem] = []

        if let asm = schedule.preSchoolAssembly {
            items.append(ScheduleItem(type: .assembly, displayName: "早會", subject: "Pre-School Assembly", start: asm.start, end: asm.end))
        }

        let dc = ds.dayRotation[dateStr]
        for (i, p) in schedule.periods.enumerated() {
            let subject = ds.subjectSchedule[dc ?? 0]?[i + 1] ?? "課程"
            items.append(ScheduleItem(type: .period(number: i + 1), displayName: "第\(i + 1)節", subject: subject, start: p.start, end: p.end))
        }

        for b in schedule.breaks {
            items.append(ScheduleItem(type: .breakTime, displayName: b.name, subject: b.name, start: b.start, end: b.end))
        }

        items.sort { $0.start < $1.start }
        scheduleItems = items

        if highlightCurrent {
            let now = currentTimeString()
            currentItemID = items.first(where: { isInRange(now, $0.start, $0.end) })?.id
        } else {
            currentItemID = nil
        }
    }

    // MARK: - Build merged schedule items for display

    func mergedScheduleItems(merge: Bool) -> [MergedScheduleItem] {
        guard merge else {
            return scheduleItems.map {
                MergedScheduleItem(
                    id: $0.id,
                    type: $0.type,
                    displayName: $0.displayName,
                    subject: $0.subject,
                    start: $0.start,
                    end: $0.end,
                    spanCount: 1,
                    isCurrent: $0.id == currentItemID
                )
            }
        }

        var result: [MergedScheduleItem] = []
        var i = 0
        let items = scheduleItems

        while i < items.count {
            let item = items[i]

            // Only merge period types with same subject
            if case .period = item.type {
                var endIdx = i
                var spanCount = 1

                while endIdx + 1 < items.count {
                    let next = items[endIdx + 1]
                    // Skip intervening breaks, check if next period has same subject
                    if next.type == .breakTime {
                        // Check if the period after the break has same subject
                        if endIdx + 2 < items.count,
                           case .period = items[endIdx + 2].type,
                           items[endIdx + 2].subject == item.subject {
                            // Include the break in the merge, skip break and merge next period
                            endIdx += 2
                            spanCount += 1
                        } else {
                            break
                        }
                    } else if case .period = next.type, next.subject == item.subject {
                        endIdx += 1
                        spanCount += 1
                    } else {
                        break
                    }
                }

                if spanCount > 1 {
                    // Check if any of the merged periods is current
                    let mergedIsCurrent = (i...endIdx).contains(where: { items[$0].id == currentItemID })
                    let firstPeriod = item
                    let lastPeriod = items[endIdx]

                    // Display name: e.g. "第1-2節"
                    var firstNum = 0
                    var lastNum = 0
                    if case .period(let n) = firstPeriod.type { firstNum = n }
                    if case .period(let n) = lastPeriod.type { lastNum = n }
                    let displayName = firstNum == lastNum ? "第\(firstNum)節" : "第\(firstNum)-\(lastNum)節"

                    result.append(MergedScheduleItem(
                        id: firstPeriod.id,
                        type: firstPeriod.type,
                        displayName: displayName,
                        subject: firstPeriod.subject,
                        start: firstPeriod.start,
                        end: lastPeriod.end,
                        spanCount: spanCount,
                        isCurrent: mergedIsCurrent
                    ))
                    i = endIdx + 1
                    continue
                }
            }

            // Non-merged item
            result.append(MergedScheduleItem(
                id: item.id,
                type: item.type,
                displayName: item.displayName,
                subject: item.subject,
                start: item.start,
                end: item.end,
                spanCount: 1,
                isCurrent: item.id == currentItemID
            ))
            i += 1
        }

        return result
    }

    // MARK: - Helpers

    func currentTimeString() -> String {
        let c = calendar.dateComponents([.hour, .minute], from: .now)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    private func isInRange(_ time: String, _ start: String, _ end: String) -> Bool {
        time >= start && time <= end
    }

    private func isWeekend(_ date: Date) -> Bool {
        let wd = calendar.component(.weekday, from: date)
        return wd == 1 || wd == 7
    }

    func isNonSchoolDay(_ dateStr: String) -> Bool {
        guard let date = Self.isoDate(from: dateStr) else { return false }
        let wd = calendar.component(.weekday, from: date)
        if wd == 1 || wd == 7 { return true }
        let hasRotation = ds.dayRotation[dateStr] != nil
        let hasSpecial = ds.specialDates[dateStr] != nil
        return !hasRotation && !hasSpecial
    }

    static func isoString(from date: Date) -> String {
        isoFormatter.string(from: date)
    }

    static func isoDate(from str: String) -> Date? {
        isoFormatter.date(from: str)
    }

    func todayDate(from timeStr: String) -> Date? {
        let parts = timeStr.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        return calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: .now)
    }

    // MARK: - Display helpers

    var dayIndicatorText: String {
        if timetableType == .none { return "非上課日" }
        if let dc = dayCycle { return "Day \(dc)" }
        return "無循環日"
    }

    var scheduleTitleText: String {
        if viewMode == .today {
            return timetableType == .none ? "今日無課程" : "今日時間表"
        } else {
            let formatted = Self.dateFormatter.string(from: selectedDate)
            return timetableType == .none ? "\(formatted) 無課程" : "\(formatted) 時間表"
        }
    }

    /// Short date string for future day display
    static func shortDateString(from date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh-HK")
        f.dateFormat = "M月d日(EEE)"
        return f.string(from: date)
    }
}

// MARK: - Merged Schedule Item

struct MergedScheduleItem: Identifiable {
    let id: UUID
    let type: ScheduleItemType
    let displayName: String
    let subject: String
    let start: String
    let end: String
    let spanCount: Int
    let isCurrent: Bool
}
