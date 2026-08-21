import Foundation

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

@Observable
@MainActor
final class TimetableEngine {

    static let shared = TimetableEngine()

    var currentPeriod: PeriodInfo = PeriodInfo(type: .free, name: "載入中...", start: "", end: "", subject: "正在載入...")
    var nextPeriod: PeriodInfo?
    var timetableType: TimetableType = .none
    var dayCycle: Int?
    var scheduleItems: [ScheduleItem] = []
    var currentItemID: String?

    var countdownEnd: Date?
    var countdownDelayed: Bool = false
    var countdownLabel: String = ""

    var viewMode: ViewMode = .today
    var selectedDate: Date = .now

    var futureDays: [(date: Date, day: Int)] = []

    var transitionDirection: TransitionDirection = .none

    enum ViewMode: Equatable {
        case today, dateSelect
    }

    enum TransitionDirection {
        case none, forward, backward
    }

    private var resolver: ScheduleResolver
    private var boundaryTask: Task<Void, Never>?
    private let calendar = Calendar.current

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

    static let shortDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh-HK")
        f.dateFormat = "M月d日(EEE)"
        return f
    }()

    init() {
        self.resolver = DataSourceManager.shared.makeResolver()
        if ProcessInfo.processInfo.environment["TIMETABLE_FORCE_DATE_SELECT"] == "1" {
            viewMode = .dateSelect
            selectedDate = calendar.date(byAdding: .day, value: 2, to: .now) ?? .now
        }
        refresh()
        startBoundaryLoop()
    }

    func startBoundaryLoop() {
        boundaryTask?.cancel()
        boundaryTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(15))
                guard let self, !Task.isCancelled else { return }
                self.tickBoundary()
            }
        }
    }

    private func tickBoundary() {
        guard viewMode == .today else { return }
        let before = currentPeriod
        updateCurrentPeriod()
        updateCountdown()
        if before != currentPeriod {
            LiveActivityManager.shared.syncWithEngine(self)
        }
    }

    // MARK: - Public API

    func refresh() {
        resolver = DataSourceManager.shared.makeResolver()
        if viewMode == .today {
            updateCurrentPeriod()
            updateCountdown()
        } else {
            let dateStr = ScheduleResolver.isoString(from: selectedDate)
            timetableType = resolver.timetableType(for: dateStr)
            dayCycle = resolver.dayRotation[dateStr]
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
        let dateStr = ScheduleResolver.isoString(from: date)
        timetableType = resolver.timetableType(for: dateStr)
        dayCycle = resolver.dayRotation[dateStr]
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

    // MARK: - Current period logic

    private func updateCurrentPeriod() {
        let todayStr = ScheduleResolver.isoString(from: .now)
        let type = resolver.timetableType(for: todayStr)
        timetableType = type
        dayCycle = resolver.dayRotation[todayStr]

        guard type != .none, let schedule = resolver.schedule(for: type) else {
            currentPeriod = PeriodInfo(type: .none, name: "今日沒有課程", start: "", end: "", subject: "非上課日")
            nextPeriod = nil
            scheduleItems = []
            currentItemID = nil
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
                let subject = resolver.subject(day: dayCycle, periodNumber: i + 1)
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
            let subject = resolver.subject(day: dayCycle, periodNumber: i + 1)
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

    private func updateCountdown() {
        guard currentPeriod.type != .free && currentPeriod.type != .none,
              !currentPeriod.end.isEmpty else {
            countdownEnd = nil
            countdownDelayed = false
            countdownLabel = ""
            return
        }

        let now = Date.now

        if currentPeriod.type == .period,
           let startDate = todayDate(from: currentPeriod.start),
           now < startDate.addingTimeInterval(60) {
            countdownEnd = startDate.addingTimeInterval(60)
            countdownDelayed = true
            countdownLabel = "上課 1 分鐘後開始倒計時"
            return
        }

        guard let endDate = todayDate(from: currentPeriod.end) else {
            countdownEnd = nil
            countdownDelayed = false
            countdownLabel = ""
            return
        }

        if endDate <= now {
            countdownEnd = nil
            countdownDelayed = false
            countdownLabel = ""
            return
        }

        countdownEnd = endDate
        countdownDelayed = false

        switch currentPeriod.type {
        case .period:    countdownLabel = "下課倒計時"
        case .breakTime: countdownLabel = "小息結束倒計時"
        case .assembly:  countdownLabel = "早會結束倒計時"
        default:         countdownLabel = ""
        }
    }

    // MARK: - Schedule items

    private func updateScheduleItems(dateStr: String, highlightCurrent: Bool) {
        guard timetableType != .none, let schedule = resolver.schedule(for: timetableType) else {
            scheduleItems = []
            currentItemID = nil
            return
        }

        var items: [ScheduleItem] = []

        if let asm = schedule.preSchoolAssembly {
            items.append(ScheduleItem(type: .assembly, displayName: "早會", subject: "Pre-School Assembly", start: asm.start, end: asm.end))
        }

        let dc = resolver.dayRotation[dateStr]
        for (i, p) in schedule.periods.enumerated() {
            let subject = resolver.subject(day: dc, periodNumber: i + 1)
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

    // MARK: - Merged schedule items for display

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

            if case .period = item.type {
                var endIdx = i
                var spanCount = 1

                while endIdx + 1 < items.count {
                    let next = items[endIdx + 1]
                    if next.type == .breakTime {
                        if endIdx + 2 < items.count,
                           case .period = items[endIdx + 2].type,
                           items[endIdx + 2].subject == item.subject {
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
                    let mergedIsCurrent = (i...endIdx).contains(where: { items[$0].id == currentItemID })
                    let firstPeriod = item
                    let lastPeriod = items[endIdx]

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

    // MARK: - Future Day Rotation

    func updateFutureDays() {
        var result: [(date: Date, day: Int)] = []
        var d = Date.now

        var foundDays: Set<Int> = []
        let maxSearch = 60

        for _ in 0..<maxSearch {
            d = calendar.date(byAdding: .day, value: 1, to: d) ?? d
            let dateStr = ScheduleResolver.isoString(from: d)
            if let day = resolver.dayRotation[dateStr], !foundDays.contains(day) {
                result.append((date: d, day: day))
                foundDays.insert(day)
                if foundDays.count == 6 { break }
            }
        }

        result.sort { $0.day < $1.day }
        futureDays = result
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

    static func shortDateString(from date: Date) -> String {
        shortDateFormatter.string(from: date)
    }
}

struct MergedScheduleItem: Identifiable {
    let id: String
    let type: ScheduleItemType
    let displayName: String
    let subject: String
    let start: String
    let end: String
    let spanCount: Int
    let isCurrent: Bool
}
