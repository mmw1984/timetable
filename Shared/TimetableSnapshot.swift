import Foundation

struct TimetableSnapshot: Codable {
    var dayRotation: [String: Int]
    var specialDates: [String: String]
    var subjectSchedule: [Int: [Int: String]]
    var customTimetables: [String: TimetableSchedule]
    var normalSchedule: TimetableSchedule?
    var accentLightHex: String
    var accentDarkHex: String
    var generatedAt: Date

    static func fallback() -> TimetableSnapshot {
        TimetableSnapshot(
            dayRotation: TimetableDefaults.dayRotation,
            specialDates: TimetableDefaults.specialDates,
            subjectSchedule: TimetableDefaults.subjectSchedule,
            customTimetables: [:],
            normalSchedule: nil,
            accentLightHex: "#007AFF",
            accentDarkHex: "#0A84FF",
            generatedAt: .distantPast
        )
    }
}

enum SnapshotStore {
    static let appGroupID = "group.mmw1984.Timetable"
    static let fileName = "timetable-snapshot.json"

    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID)
    }

    static func save(_ snapshot: TimetableSnapshot) {
        guard let dir = containerURL else { return }
        let url = dir.appendingPathComponent(fileName)
        do {
            let data = try JSONEncoder().encode(snapshot)
            try data.write(to: url, options: .atomic)
        } catch {}
    }

    static func load() -> TimetableSnapshot {
        guard let dir = containerURL else { return .fallback() }
        let url = dir.appendingPathComponent(fileName)
        guard let data = try? Data(contentsOf: url) else { return .fallback() }
        return (try? JSONDecoder().decode(TimetableSnapshot.self, from: data)) ?? .fallback()
    }

    static var isAvailable: Bool {
        containerURL != nil
    }
}
