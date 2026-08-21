import Foundation

struct TimePeriod: Identifiable, Codable, Hashable {
    var start: String
    var end: String

    var id: String { "\(start)-\(end)" }
}

struct BreakPeriod: Identifiable, Codable, Hashable {
    var start: String
    var end: String
    var name: String

    var id: String { "\(start)-\(end)-\(name)" }
}

struct TimetableSchedule: Codable, Hashable {
    var preSchoolAssembly: TimePeriod?
    var periods: [TimePeriod]
    var breaks: [BreakPeriod]
}

enum ScheduleItemType: Equatable, Hashable {
    case assembly
    case period(number: Int)
    case breakTime
}

struct ScheduleItem: Identifiable, Hashable {
    let type: ScheduleItemType
    let displayName: String
    let subject: String
    let start: String
    let end: String

    var id: String {
        "\(type)-\(start)-\(end)"
    }

    init(type: ScheduleItemType, displayName: String, subject: String, start: String, end: String) {
        self.type = type
        self.displayName = displayName
        self.subject = subject
        self.start = start
        self.end = end
    }
}

enum TimetableType: String, Codable, Hashable {
    case normal
    case specialA
    case specialB
    case specialC
    case specialD
    case specialE
    case none

    var displayText: String {
        switch self {
        case .normal:   return "正常時間表"
        case .specialA: return "特殊時間表A"
        case .specialB: return "特殊時間表B"
        case .specialC: return "特殊時間表C"
        case .specialD: return "特殊時間表D"
        case .specialE: return "特殊時間表E"
        case .none:     return "非上課日"
        }
    }

    var noticeText: String {
        switch self {
        case .normal:   return "正常時間表"
        case .specialA: return "特殊時間表A - 學期初安排"
        case .specialB: return "特殊時間表B"
        case .specialC: return "特殊時間表C"
        case .specialD: return "特殊時間表D"
        case .specialE: return "特殊時間表E"
        case .none:     return "今日無課程"
        }
    }
}
