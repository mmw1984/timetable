import Foundation

// MARK: - Data Parser

/// Parses the text file formats used in the web version:
/// - days.txt: Day rotation schedule
/// - special-date.txt: Special timetable dates and timetable definitions
/// - timetable.txt: Normal timetable with subject schedule
enum DataParser {

    // MARK: - Parse days.txt → [String: Int]

    /// Parses lines like: "September 03, 2025 (Wednesday): Day 1"
    static func parseDayRotation(_ text: String) -> [String: Int] {
        var result: [String: Int] = [:]
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")

        let lines = text.components(separatedBy: .newlines)
        for line in lines {
            // Match pattern: "Month DD, YYYY (Weekday): Day N"
            guard let colonRange = line.range(of: ": Day ") else { continue }
            let datePart = String(line[line.startIndex..<colonRange.lowerBound])
            let dayPart = String(line[colonRange.upperBound...])

            guard let dayNum = Int(dayPart.trimmingCharacters(in: .whitespaces)) else { continue }

            // Remove the (Weekday) part
            let cleanDate: String
            if let parenRange = datePart.range(of: " (") {
                cleanDate = String(datePart[datePart.startIndex..<parenRange.lowerBound])
            } else {
                cleanDate = datePart
            }

            dateFormatter.dateFormat = "MMMM dd, yyyy"
            if let date = dateFormatter.date(from: cleanDate.trimmingCharacters(in: .whitespaces)) {
                let iso = Self.isoFormatter.string(from: date)
                result[iso] = dayNum
            }
        }
        return result
    }

    // MARK: - Parse special-date.txt → (specialDates, timetables)

    struct SpecialDateParseResult {
        var specialDates: [String: String] = [:]          // "2025-09-03": "A"
        var timetables: [String: TimetableSchedule] = [:] // "specialA": schedule
    }

    static func parseSpecialDates(_ text: String) -> SpecialDateParseResult {
        var result = SpecialDateParseResult()
        let lines = text.components(separatedBy: .newlines)

        var currentTimetableKey: String?
        var currentPeriods: [TimePeriod] = []
        var currentBreaks: [BreakPeriod] = []
        var inDatesSection = false

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            // Detect timetable header: ### Special Timetable A
            if trimmed.hasPrefix("### Special Timetable ") {
                // Save previous timetable
                saveTimetable(key: currentTimetableKey, periods: currentPeriods, breaks: currentBreaks, into: &result)

                let letter = String(trimmed.dropFirst("### Special Timetable ".count)).trimmingCharacters(in: .whitespaces)
                currentTimetableKey = "special\(letter)"
                currentPeriods = []
                currentBreaks = []
                inDatesSection = false
                continue
            }

            // Detect dates section
            if trimmed.hasPrefix("### Dates") {
                saveTimetable(key: currentTimetableKey, periods: currentPeriods, breaks: currentBreaks, into: &result)
                currentTimetableKey = nil
                inDatesSection = true
                continue
            }

            // Parse timetable rows: | Period | Start | End | Duration |
            if currentTimetableKey != nil && trimmed.hasPrefix("|") && !trimmed.contains("---") && !trimmed.lowercased().contains("period") {
                let cols = trimmed.split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }
                guard cols.count >= 3 else { continue }

                let periodName = cols[0]
                let startStr = normalizeTime(cols[1])
                let endStr = normalizeTime(cols[2])

                guard !startStr.isEmpty && !endStr.isEmpty else { continue }

                let isBreak = periodName.lowercased().contains("recess") ||
                              periodName.lowercased().contains("lunch") ||
                              periodName.lowercased().contains("roll-call") ||
                              periodName.lowercased().contains("assembly") && periodName.lowercased() != "pre-school assembly" ||
                              periodName.lowercased().contains("house meeting")

                let isAssemblyBreak = periodName.lowercased().contains("assembly") ||
                                     periodName.lowercased().contains("long assembly")

                if isBreak || isAssemblyBreak {
                    let name = mapBreakName(periodName)
                    currentBreaks.append(BreakPeriod(start: startStr, end: endStr, name: name))
                } else {
                    currentPeriods.append(TimePeriod(start: startStr, end: endStr))
                }
                continue
            }

            // Parse date rows: | Date | Event | Type |
            if inDatesSection && trimmed.hasPrefix("|") && !trimmed.contains("---") && !trimmed.lowercased().contains("dates") {
                let cols = trimmed.split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }
                guard cols.count >= 3 else { continue }

                let dateStr = cols[0]
                let typeStr = cols[cols.count - 1]

                guard typeStr != "Normal" else { continue }

                let dates = parseDateRange(dateStr)
                for d in dates {
                    result.specialDates[d] = typeStr
                }
            }
        }

        // Save last timetable
        saveTimetable(key: currentTimetableKey, periods: currentPeriods, breaks: currentBreaks, into: &result)

        return result
    }

    // MARK: - Parse timetable.txt → (normalSchedule, subjectSchedule)

    struct TimetableParseResult {
        var normalSchedule: TimetableSchedule?
        var subjectSchedule: [Int: [Int: String]] = [:]  // [dayCycle: [periodNumber: subject]]
    }

    static func parseTimetable(_ text: String) -> TimetableParseResult {
        var result = TimetableParseResult()
        let lines = text.components(separatedBy: .newlines)

        var periods: [TimePeriod] = []
        var breaks: [BreakPeriod] = []
        var preSchoolAssembly: TimePeriod?
        var periodIndex = 0

        // Initialize subject schedule for days 1-6
        for d in 1...6 { result.subjectSchedule[d] = [:] }

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("|") && !trimmed.contains("---") && !trimmed.contains(":---") else { continue }

            let cols = trimmed.split(separator: "|").map {
                $0.trimmingCharacters(in: .whitespaces)
                  .replacingOccurrences(of: "**", with: "")
            }

            // Skip header row
            if cols.first?.lowercased() == "period" { continue }
            guard cols.count >= 2 else { continue }

            let timeStr = cols[0]

            // Parse time range
            guard let dashRange = timeStr.range(of: " - ") ?? timeStr.range(of: "-") else { continue }
            let startRaw = String(timeStr[timeStr.startIndex..<dashRange.lowerBound]).trimmingCharacters(in: .whitespaces)
            let endRaw = String(timeStr[dashRange.upperBound...]).trimmingCharacters(in: .whitespaces)

            let start = normalizeTime(startRaw)
            let end = normalizeTime(endRaw)
            guard !start.isEmpty && !end.isEmpty else { continue }

            // Determine row type from second column
            let content = cols.count > 1 ? cols[1] : ""

            if content.lowercased().contains("pre-school assembly") {
                preSchoolAssembly = TimePeriod(start: start, end: end)
            } else if content.lowercased().contains("recess") {
                breaks.append(BreakPeriod(start: start, end: end, name: "小息"))
            } else if content.lowercased().contains("lunch") {
                breaks.append(BreakPeriod(start: start, end: end, name: "午餐"))
            } else {
                // Subject row
                periodIndex += 1
                periods.append(TimePeriod(start: start, end: end))

                // Parse subjects for each day
                for dayIdx in 0..<6 {
                    let colIdx = dayIdx + 1  // cols[0] is time, cols[1..6] are days
                    if colIdx < cols.count {
                        result.subjectSchedule[dayIdx + 1]?[periodIndex] = cols[colIdx]
                    }
                }
            }
        }

        if !periods.isEmpty {
            // Add roll-call break (12:45-12:50) if lunch exists
            if breaks.contains(where: { $0.name == "午餐" }) {
                // Approximate roll-call from lunch end
            }
            result.normalSchedule = TimetableSchedule(
                preSchoolAssembly: preSchoolAssembly,
                periods: periods,
                breaks: breaks
            )
        }

        return result
    }

    // MARK: - Internal Helpers

    private static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    /// Convert "8:30" or "1:15" to "08:30" or "13:15"
    private static func normalizeTime(_ raw: String) -> String {
        let cleaned = raw.trimmingCharacters(in: .whitespaces)
        let parts = cleaned.split(separator: ":").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count == 2 else { return "" }

        var hour = parts[0]
        let minute = parts[1]

        // If hour < 8, it's PM (school hours are 8:xx - 15:xx)
        if hour >= 1 && hour < 8 {
            hour += 12
        }

        return String(format: "%02d:%02d", hour, minute)
    }

    private static func mapBreakName(_ name: String) -> String {
        let lower = name.lowercased()
        if lower.contains("lunch") && lower.contains("house") { return "午餐/社際聚會" }
        if lower.contains("lunch") { return "午餐" }
        if lower.contains("recess") { return "小息" }
        if lower.contains("roll-call") || lower.contains("roll call") { return "點名" }
        if lower.contains("long assembly") { return "長集會" }
        if lower.contains("assembly") { return "集會" }
        if lower.contains("house meeting") { return "午餐/社際聚會" }
        return name
    }

    /// Parse dates like "03 - 10/09/2025" or "12/09/2025" or "19/05 - 04/06/2026"
    private static func parseDateRange(_ raw: String) -> [String] {
        let cleaned = raw.trimmingCharacters(in: .whitespaces)

        // Check for range with " - "
        if let dashRange = cleaned.range(of: " - ") {
            let leftPart = String(cleaned[cleaned.startIndex..<dashRange.lowerBound]).trimmingCharacters(in: .whitespaces)
            let rightPart = String(cleaned[dashRange.upperBound...]).trimmingCharacters(in: .whitespaces)

            // Right part is always a full date: dd/MM/yyyy
            guard let endDate = parseDate(rightPart) else { return [] }

            // Left part might be just "dd" or "dd/MM" or "dd/MM/yyyy"
            let startDate: Date?
            if leftPart.contains("/") {
                // Has month info
                let slashCount = leftPart.filter({ $0 == "/" }).count
                if slashCount == 1 {
                    // dd/MM - borrow year from end date
                    let year = Calendar.current.component(.year, from: endDate)
                    startDate = parseDate("\(leftPart)/\(year)")
                } else {
                    startDate = parseDate(leftPart)
                }
            } else {
                // Just dd - borrow month/year from end date
                let cal = Calendar.current
                let month = cal.component(.month, from: endDate)
                let year = cal.component(.year, from: endDate)
                startDate = parseDate("\(leftPart)/\(String(format: "%02d", month))/\(year)")
            }

            guard let start = startDate else { return [] }

            // Generate all dates in range
            var dates: [String] = []
            var current = start
            let cal = Calendar.current
            while current <= endDate {
                let wd = cal.component(.weekday, from: current)
                if wd != 1 && wd != 7 {
                    dates.append(isoFormatter.string(from: current))
                }
                current = cal.date(byAdding: .day, value: 1, to: current) ?? current.addingTimeInterval(86400)
            }
            return dates
        } else {
            // Single date
            if let date = parseDate(cleaned) {
                return [isoFormatter.string(from: date)]
            }
            return []
        }
    }

    /// Parse "dd/MM/yyyy"
    private static func parseDate(_ raw: String) -> Date? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "dd/MM/yyyy"
        return f.date(from: raw)
    }

    private static func saveTimetable(key: String?, periods: [TimePeriod], breaks: [BreakPeriod], into result: inout SpecialDateParseResult) {
        guard let key, !periods.isEmpty else { return }
        result.timetables[key] = TimetableSchedule(preSchoolAssembly: nil, periods: periods, breaks: breaks)
    }
}
