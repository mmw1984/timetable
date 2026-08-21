import WidgetKit
import SwiftUI
import AppIntents

struct UpcomingItem: Hashable {
    let name: String
    let subject: String
    let start: String
    let end: String
    let isCurrent: Bool
}

struct ScheduleEntry: TimelineEntry {
    let date: Date
    let isSchoolDay: Bool
    let dayText: String
    let currentName: String?
    let currentSubject: String?
    let currentEnd: Date?
    let nextName: String?
    let nextSubject: String?
    let upcoming: [UpcomingItem]
    let accentLightHex: String
    let accentDarkHex: String

    func accent(for scheme: ColorScheme) -> Color {
        Color(hex: scheme == .dark ? accentDarkHex : accentLightHex)
    }
}

struct Slot: Hashable {
    let start: String
    let end: String
    let name: String
    let subject: String
}

struct ScheduleProvider: TimelineProvider {
    private let resolver: ScheduleResolver
    private let accentLightHex: String
    private let accentDarkHex: String

    init() {
        let snapshot = SnapshotStore.load()
        self.resolver = ScheduleResolver(snapshot: snapshot)
        self.accentLightHex = snapshot.accentLightHex
        self.accentDarkHex = snapshot.accentDarkHex
    }

    func placeholder(in context: Context) -> ScheduleEntry {
        makeEntry(at: .now)
    }

    func getSnapshot(in context: Context, completion: @escaping (ScheduleEntry) -> Void) {
        completion(makeEntry(at: .now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleEntry>) -> Void) {
        let calendar = Calendar.current
        let now = Date()
        var entries: [ScheduleEntry] = []
        let boundaries = Self.boundaries(after: now, resolver: resolver, calendar: calendar)

        entries.append(makeEntry(at: now))
        for boundary in boundaries.prefix(24) {
            entries.append(makeEntry(at: boundary))
        }

        if let last = entries.last, last.isSchoolDay, !boundaries.isEmpty {
            completion(Timeline(entries: entries, policy: .atEnd))
            return
        }

        let nextMorning = Self.nextSchoolDayMorning(after: now, calendar: calendar)
        entries.append(makeEntry(at: nextMorning))
        completion(Timeline(entries: entries, policy: .after(nextMorning)))
    }

    func makeEntry(at date: Date) -> ScheduleEntry {
        let dateStr = ScheduleResolver.isoString(from: date)
        let type = resolver.timetableType(for: dateStr)
        let dayCycle = resolver.dayRotation[dateStr]

        guard type != .none, let schedule = resolver.schedule(for: type) else {
            return ScheduleEntry(
                date: date,
                isSchoolDay: false,
                dayText: TimetableType.none.displayText,
                currentName: nil,
                currentSubject: nil,
                currentEnd: nil,
                nextName: nil,
                nextSubject: nil,
                upcoming: [],
                accentLightHex: accentLightHex,
                accentDarkHex: accentDarkHex
            )
        }

        let dayText = dayCycle.map { "Day \($0)" } ?? type.displayText
        let timeString = Self.timeString(from: date)

        var slots: [Slot] = []
        if let asm = schedule.preSchoolAssembly {
            slots.append(Slot(start: asm.start, end: asm.end, name: "早會", subject: "Pre-School Assembly"))
        }
        for (i, p) in schedule.periods.enumerated() {
            slots.append(Slot(
                start: p.start,
                end: p.end,
                name: "第\(i + 1)節",
                subject: resolver.subject(day: dayCycle, periodNumber: i + 1)
            ))
        }
        for b in schedule.breaks {
            slots.append(Slot(start: b.start, end: b.end, name: b.name, subject: b.name))
        }
        slots.sort { $0.start < $1.start }

        let current = slots.first { timeString >= $0.start && timeString <= $0.end }
        let next = slots.first { $0.start > timeString }
        let upcoming = slots
            .filter { $0.end >= timeString }
            .prefix(5)
            .map { UpcomingItem(name: $0.name, subject: $0.subject, start: $0.start, end: $0.end, isCurrent: current == $0) }

        return ScheduleEntry(
            date: date,
            isSchoolDay: true,
            dayText: dayText,
            currentName: current?.name,
            currentSubject: current?.subject,
            currentEnd: current.flatMap { Self.date(fromTime: $0.end, on: date) },
            nextName: next?.name,
            nextSubject: next?.subject,
            upcoming: Array(upcoming),
            accentLightHex: accentLightHex,
            accentDarkHex: accentDarkHex
        )
    }

    static func boundaries(after date: Date, resolver: ScheduleResolver, calendar: Calendar) -> [Date] {
        let dateStr = ScheduleResolver.isoString(from: date)
        let type = resolver.timetableType(for: dateStr)
        guard type != .none, let schedule = resolver.schedule(for: type) else { return [] }

        var times: Set<String> = []
        if let asm = schedule.preSchoolAssembly {
            times.insert(asm.start); times.insert(asm.end)
        }
        for p in schedule.periods {
            times.insert(p.start); times.insert(p.end)
        }
        for b in schedule.breaks {
            times.insert(b.start); times.insert(b.end)
        }

        return times.compactMap { Self.date(fromTime: $0, on: date) }
            .filter { $0 > date }
            .sorted()
    }

    static func nextSchoolDayMorning(after date: Date, calendar: Calendar) -> Date {
        var d = calendar.startOfDay(for: date)
        for _ in 0..<14 {
            d = calendar.date(byAdding: .day, value: 1, to: d) ?? d
            let wd = calendar.component(.weekday, from: d)
            if wd != 1 && wd != 7 {
                return calendar.date(bySettingHour: 7, minute: 0, second: 0, of: d) ?? d
            }
        }
        return calendar.date(byAdding: .day, value: 1, to: date) ?? date
    }

    static func timeString(from date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    static func date(fromTime time: String, on day: Date) -> Date? {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        return Calendar.current.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: day)
    }
}

struct CurrentPeriodWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "mmw1984.Timetable.currentPeriod", provider: ScheduleProvider()) { entry in
            CurrentPeriodWidgetView(entry: entry)
        }
        .configurationDisplayName("當前節數")
        .description("顯示目前節數、倒計時同下一節")
        .supportedFamilies([.systemSmall])
    }
}

struct TodayScheduleWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "mmw1984.Timetable.todaySchedule", provider: ScheduleProvider()) { entry in
            TodayScheduleWidgetView(entry: entry)
        }
        .configurationDisplayName("今日時間表")
        .description("顯示今日剩餘嘅課堂安排")
        .supportedFamilies([.systemMedium])
    }
}

struct CurrentPeriodAccessoryWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "mmw1984.Timetable.accessoryPeriod", provider: ScheduleProvider()) { entry in
            AccessoryPeriodView(entry: entry)
        }
        .configurationDisplayName("節數倒計時")
        .description("鎖定畫面顯示目前節數同倒計時")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct CurrentPeriodControl: ControlWidget {
    static let kind = "mmw1984.Timetable.currentPeriodControl"

    var body: some ControlWidgetConfiguration {
        AppIntentControlConfiguration(
            kind: Self.kind,
            provider: ControlProvider()
        ) { value in
            ControlWidgetButton(action: OpenTimetableIntent()) {
                Label(value.label, systemImage: "graduationcap")
            }
        }
        .displayName("當前節數")
        .description("喺控制中心查看目前節數同倒計時")
    }
}

struct ControlValue {
    let label: String
}

struct ControlProvider: AppIntentControlValueProvider {
    func previewValue(configuration: OpenTimetableControlIntent) -> ControlValue {
        ControlValue(label: "第3節 · 09:59")
    }

    func currentValue(configuration: OpenTimetableControlIntent) async throws -> ControlValue {
        let resolver = ScheduleResolver(snapshot: SnapshotStore.load())
        let dateStr = ScheduleResolver.isoString(from: .now)
        let type = resolver.timetableType(for: dateStr)
        guard type != .none, let schedule = resolver.schedule(for: type) else {
            return ControlValue(label: "非上課日")
        }

        let dayCycle = resolver.dayRotation[dateStr]
        let timeString = ScheduleProvider.timeString(from: .now)

        var slots: [Slot] = []
        if let asm = schedule.preSchoolAssembly {
            slots.append(Slot(start: asm.start, end: asm.end, name: "早會", subject: "Pre-School Assembly"))
        }
        for (i, p) in schedule.periods.enumerated() {
            slots.append(Slot(
                start: p.start,
                end: p.end,
                name: "第\(i + 1)節",
                subject: resolver.subject(day: dayCycle, periodNumber: i + 1)
            ))
        }
        for b in schedule.breaks {
            slots.append(Slot(start: b.start, end: b.end, name: b.name, subject: b.name))
        }
        slots.sort { $0.start < $1.start }

        guard let current = slots.first(where: { timeString >= $0.start && timeString <= $0.end }),
              let end = ScheduleProvider.date(fromTime: current.end, on: .now), end > .now else {
            return ControlValue(label: "暫時冇課")
        }

        let remaining = Int(end.timeIntervalSinceNow) / 60
        return ControlValue(label: "\(current.name) · 剩 \(max(0, remaining)) 分鐘")
    }
}

struct OpenTimetableControlIntent: ControlConfigurationIntent {
    static let title: LocalizedStringResource = "打開時間表"
}

struct OpenTimetableIntent: AppIntent {
    static let title: LocalizedStringResource = "打開時間表"
    static let openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        .result()
    }
}

struct CurrentPeriodWidgetView: View {
    let entry: ScheduleEntry
    @Environment(\.colorScheme) private var colorScheme

    private var accent: Color {
        entry.accent(for: colorScheme)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                Text(entry.dayText)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(accent, in: .capsule)
                Spacer()
            }

            Spacer(minLength: 0)

            if entry.isSchoolDay, let name = entry.currentName {
                VStack(alignment: .leading, spacing: 2) {
                    Text(name)
                        .font(.headline)
                        .lineLimit(1)
                    if let subject = entry.currentSubject {
                        Text(subject)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    if let end = entry.currentEnd {
                        Text(end, style: .timer)
                            .font(.title3.weight(.bold))
                            .fontDesign(.rounded)
                            .monospacedDigit()
                            .foregroundStyle(accent)
                    }
                }
            } else if entry.isSchoolDay {
                Text("暫時冇課")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            } else {
                Text("非上課日")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            }

            if let next = entry.nextName {
                Text("下一節 \(next)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .containerBackground(for: .widget) { Color(.systemBackground) }
    }
}

struct TodayScheduleWidgetView: View {
    let entry: ScheduleEntry
    @Environment(\.colorScheme) private var colorScheme

    private var accent: Color {
        entry.accent(for: colorScheme)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.dayText)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(accent, in: .capsule)

                Spacer(minLength: 0)

                if let name = entry.currentName {
                    Text(name)
                        .font(.headline)
                        .lineLimit(1)
                    if let subject = entry.currentSubject {
                        Text(subject)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    if let end = entry.currentEnd {
                        Text(end, style: .timer)
                            .font(.title2.weight(.bold))
                            .fontDesign(.rounded)
                            .monospacedDigit()
                            .foregroundStyle(accent)
                    }
                } else {
                    Text(entry.isSchoolDay ? "暫時冇課" : "非上課日")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .leading, spacing: 5) {
                if entry.upcoming.isEmpty {
                    Text("今日課程已結束")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(entry.upcoming, id: \.self) { item in
                        HStack(spacing: 6) {
                            Text(item.start)
                                .font(.caption2.monospacedDigit())
                                .foregroundStyle(item.isCurrent ? accent : .secondary)
                            Text(item.name)
                                .font(.caption2.weight(item.isCurrent ? .bold : .regular))
                                .foregroundStyle(item.isCurrent ? accent : .primary)
                            Text(item.subject)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .containerBackground(for: .widget) { Color(.systemBackground) }
    }
}

struct AccessoryPeriodView: View {
    let entry: ScheduleEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    if let end = entry.currentEnd {
                        Text(end, style: .timer)
                            .font(.system(size: 13, weight: .bold))
                            .minimumScaleFactor(0.6)
                            .frame(maxWidth: 52)
                    } else {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 16))
                    }
                }
            }
        case .accessoryInline:
            if let name = entry.currentName, let end = entry.currentEnd {
                Text("\(name) · \(Text(end, style: .timer))")
            } else {
                Text(entry.isSchoolDay ? "暫時冇課" : "非上課日")
            }
        default:
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.dayText)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.tertiary)
                if let name = entry.currentName {
                    Text(name)
                        .font(.headline)
                        .lineLimit(1)
                    if let end = entry.currentEnd {
                        Text(end, style: .timer)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                } else {
                    Text(entry.isSchoolDay ? "暫時冇課" : "非上課日")
                        .font(.caption)
                }
            }
        }
    }
}
