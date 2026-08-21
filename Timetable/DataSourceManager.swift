import Foundation
import WidgetKit

@Observable
@MainActor
final class DataSourceManager {

    static let shared = DataSourceManager()

    var dayRotation: [String: Int] = TimetableDefaults.dayRotation
    var specialDates: [String: String] = TimetableDefaults.specialDates
    var subjectSchedule: [Int: [Int: String]] = TimetableDefaults.subjectSchedule
    var customTimetables: [String: TimetableSchedule] = [:]
    var normalSchedule: TimetableSchedule? = nil

    var daysURL: String {
        didSet { UserDefaults.standard.set(daysURL, forKey: "daysURL") }
    }
    var specialDatesURL: String {
        didSet { UserDefaults.standard.set(specialDatesURL, forKey: "specialDatesURL") }
    }
    var timetableURL: String {
        didSet { UserDefaults.standard.set(timetableURL, forKey: "timetableURL") }
    }

    var lastDaysUpdate: Date?
    var lastSpecialUpdate: Date?
    var lastTimetableUpdate: Date?

    var mergePeriods: Bool {
        didSet { UserDefaults.standard.set(mergePeriods, forKey: "mergePeriods") }
    }

    var isLoading = false
    var errorMessage: String?

    private init() {
        let base = "https://raw.githubusercontent.com/mmw1984/timetable/refs/heads/main/"
        self.daysURL = UserDefaults.standard.string(forKey: "daysURL") ?? "\(base)days.txt"
        self.specialDatesURL = UserDefaults.standard.string(forKey: "specialDatesURL") ?? "\(base)special-date.txt"
        self.timetableURL = UserDefaults.standard.string(forKey: "timetableURL") ?? "\(base)timetable.txt"

        self.mergePeriods = UserDefaults.standard.bool(forKey: "mergePeriods")

        self.lastDaysUpdate = UserDefaults.standard.object(forKey: "lastDaysUpdate") as? Date
        self.lastSpecialUpdate = UserDefaults.standard.object(forKey: "lastSpecialUpdate") as? Date
        self.lastTimetableUpdate = UserDefaults.standard.object(forKey: "lastTimetableUpdate") as? Date

        loadCachedData()
    }

    func updateAllFromURLs() async {
        isLoading = true
        errorMessage = nil

        async let d = fetchAndParseDays()
        async let s = fetchAndParseSpecialDates()
        async let t = fetchAndParseTimetable()

        let results = await (d, s, t)

        var errors: [String] = []
        if let e = results.0 { errors.append("循環日: \(e)") }
        if let e = results.1 { errors.append("特殊日期: \(e)") }
        if let e = results.2 { errors.append("時間表: \(e)") }

        if !errors.isEmpty {
            errorMessage = errors.joined(separator: "\n")
        } else {
            pushSnapshot()
        }

        isLoading = false
    }

    func importDaysFile(_ text: String) {
        let parsed = DataParser.parseDayRotation(text)
        if !parsed.isEmpty {
            dayRotation = parsed
            cacheData("days", text)
            lastDaysUpdate = .now
            UserDefaults.standard.set(lastDaysUpdate, forKey: "lastDaysUpdate")
            pushSnapshot()
        }
    }

    func importSpecialDatesFile(_ text: String) {
        let parsed = DataParser.parseSpecialDates(text)
        if !parsed.specialDates.isEmpty {
            specialDates = parsed.specialDates
        }
        if !parsed.timetables.isEmpty {
            customTimetables = parsed.timetables
        }
        cacheData("special", text)
        lastSpecialUpdate = .now
        UserDefaults.standard.set(lastSpecialUpdate, forKey: "lastSpecialUpdate")
        pushSnapshot()
    }

    func importTimetableFile(_ text: String) {
        let parsed = DataParser.parseTimetable(text)
        if let schedule = parsed.normalSchedule {
            normalSchedule = schedule
        }
        if !parsed.subjectSchedule.isEmpty {
            subjectSchedule = parsed.subjectSchedule
        }
        cacheData("timetable", text)
        lastTimetableUpdate = .now
        UserDefaults.standard.set(lastTimetableUpdate, forKey: "lastTimetableUpdate")
        pushSnapshot()
    }

    func resetToDefaults() {
        dayRotation = TimetableDefaults.dayRotation
        specialDates = TimetableDefaults.specialDates
        subjectSchedule = TimetableDefaults.subjectSchedule
        customTimetables = [:]
        normalSchedule = nil
        clearCache()
        lastDaysUpdate = nil
        lastSpecialUpdate = nil
        lastTimetableUpdate = nil
        UserDefaults.standard.removeObject(forKey: "lastDaysUpdate")
        UserDefaults.standard.removeObject(forKey: "lastSpecialUpdate")
        UserDefaults.standard.removeObject(forKey: "lastTimetableUpdate")
        pushSnapshot()
    }

    func makeResolver() -> ScheduleResolver {
        ScheduleResolver(
            dayRotation: dayRotation,
            specialDates: specialDates,
            subjectSchedule: subjectSchedule,
            customTimetables: customTimetables,
            normalSchedule: normalSchedule
        )
    }

    func pushSnapshot() {
        let theme = ThemeManager.shared
        let snapshot = TimetableSnapshot(
            dayRotation: dayRotation,
            specialDates: specialDates,
            subjectSchedule: subjectSchedule,
            customTimetables: customTimetables,
            normalSchedule: normalSchedule,
            accentLightHex: theme.lightAccentHex,
            accentDarkHex: theme.darkAccentHex,
            generatedAt: .now
        )
        SnapshotStore.save(snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func fetchAndParseDays() async -> String? {
        guard let url = URL(string: daysURL) else { return "無效 URL" }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let text = String(data: data, encoding: .utf8) else { return "無法解碼" }
            importDaysFile(text)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    private func fetchAndParseSpecialDates() async -> String? {
        guard let url = URL(string: specialDatesURL) else { return "無效 URL" }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let text = String(data: data, encoding: .utf8) else { return "無法解碼" }
            importSpecialDatesFile(text)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    private func fetchAndParseTimetable() async -> String? {
        guard let url = URL(string: timetableURL) else { return "無效 URL" }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let text = String(data: data, encoding: .utf8) else { return "無法解碼" }
            importTimetableFile(text)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    private var cacheDir: URL? {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
    }

    private func cacheData(_ key: String, _ text: String) {
        guard let dir = cacheDir else { return }
        let file = dir.appendingPathComponent("cached_\(key).txt")
        try? text.write(to: file, atomically: true, encoding: .utf8)
    }

    private func loadCachedData() {
        guard let dir = cacheDir else { return }

        if let text = try? String(contentsOf: dir.appendingPathComponent("cached_days.txt"), encoding: .utf8) {
            let parsed = DataParser.parseDayRotation(text)
            if !parsed.isEmpty { dayRotation = parsed }
        }

        if let text = try? String(contentsOf: dir.appendingPathComponent("cached_special.txt"), encoding: .utf8) {
            let parsed = DataParser.parseSpecialDates(text)
            if !parsed.specialDates.isEmpty { specialDates = parsed.specialDates }
            if !parsed.timetables.isEmpty { customTimetables = parsed.timetables }
        }

        if let text = try? String(contentsOf: dir.appendingPathComponent("cached_timetable.txt"), encoding: .utf8) {
            let parsed = DataParser.parseTimetable(text)
            if let schedule = parsed.normalSchedule { normalSchedule = schedule }
            if !parsed.subjectSchedule.isEmpty { subjectSchedule = parsed.subjectSchedule }
        }
    }

    private func clearCache() {
        guard let dir = cacheDir else { return }
        for name in ["cached_days.txt", "cached_special.txt", "cached_timetable.txt"] {
            try? FileManager.default.removeItem(at: dir.appendingPathComponent(name))
        }
    }
}
