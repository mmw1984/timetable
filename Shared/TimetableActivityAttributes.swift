import Foundation
import ActivityKit

nonisolated struct TimetableActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var periodName: String
        var subject: String
        var endDate: Date
        var nextPeriodName: String?
        var nextSubject: String?
        var accentHex: String
    }
}
