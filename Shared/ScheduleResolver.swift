import Foundation

struct ScheduleResolver: Sendable {
    var dayRotation: [String: Int] = TimetableDefaults.dayRotation
    var specialDates: [String: String] = TimetableDefaults.specialDates
    var subjectSchedule: [Int: [Int: String]] = TimetableDefaults.subjectSchedule
    var customTimetables: [String: TimetableSchedule] = [:]
    var normalSchedule: TimetableSchedule?

    init(
        dayRotation: [String: Int] = TimetableDefaults.dayRotation,
        specialDates: [String: String] = TimetableDefaults.specialDates,
        subjectSchedule: [Int: [Int: String]] = TimetableDefaults.subjectSchedule,
        customTimetables: [String: TimetableSchedule] = [:],
        normalSchedule: TimetableSchedule? = nil
    ) {
        self.dayRotation = dayRotation
        self.specialDates = specialDates
        self.subjectSchedule = subjectSchedule
        self.customTimetables = customTimetables
        self.normalSchedule = normalSchedule
    }

    init(snapshot: TimetableSnapshot) {
        self.init(
            dayRotation: snapshot.dayRotation,
            specialDates: snapshot.specialDates,
            subjectSchedule: snapshot.subjectSchedule,
            customTimetables: snapshot.customTimetables,
            normalSchedule: snapshot.normalSchedule
        )
    }

    func schedule(for type: TimetableType) -> TimetableSchedule? {
        if type == .normal, let custom = normalSchedule {
            return custom
        }
        if let custom = customTimetables[type.rawValue] {
            return custom
        }
        return TimetableDefaults.schedule(for: type)
    }

    func timetableType(for dateStr: String) -> TimetableType {
        if let special = specialDates[dateStr] {
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

        if let date = Self.isoDate(from: dateStr) {
            let weekday = Calendar.current.component(.weekday, from: date)
            if weekday == 6 { return .specialB }
        }

        return .normal
    }

    func isNonSchoolDay(_ dateStr: String) -> Bool {
        guard let date = Self.isoDate(from: dateStr) else { return false }
        let wd = Calendar.current.component(.weekday, from: date)
        if wd == 1 || wd == 7 { return true }
        let hasRotation = dayRotation[dateStr] != nil
        let hasSpecial = specialDates[dateStr] != nil
        return !hasRotation && !hasSpecial
    }

    func subject(day: Int?, periodNumber: Int) -> String {
        subjectSchedule[day ?? 0]?[periodNumber] ?? "課程"
    }

    static func isoString(from date: Date) -> String {
        isoFormatter.string(from: date)
    }

    static func isoDate(from str: String) -> Date? {
        isoFormatter.date(from: str)
    }

    static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
}
