import Foundation
import ActivityKit

// MARK: - Timetable Live Activity Attributes

struct TimetableActivityAttributes: ActivityAttributes {
    /// Static data that doesn't change during the activity
    struct ContentState: Codable, Hashable {
        var periodName: String
        var subject: String
        var endTime: String
        var countdownSeconds: Int
        var nextPeriodName: String?
        var nextSubject: String?
    }
}

// MARK: - Live Activity Manager

@MainActor
final class LiveActivityManager {
    static let shared = LiveActivityManager()

    private var currentActivity: Activity<TimetableActivityAttributes>?
    private var updateTimer: Timer?

    private init() {}

    var isRunning: Bool {
        currentActivity != nil
    }

    func startActivity(periodName: String, subject: String, endTime: String, countdownSeconds: Int, nextPeriodName: String?, nextSubject: String?) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        // End existing activity first
        stopActivity()

        let attributes = TimetableActivityAttributes()
        let state = TimetableActivityAttributes.ContentState(
            periodName: periodName,
            subject: subject,
            endTime: endTime,
            countdownSeconds: countdownSeconds,
            nextPeriodName: nextPeriodName,
            nextSubject: nextSubject
        )

        do {
            let content = ActivityContent(state: state, staleDate: nil)
            let activity = try Activity.request(
                attributes: attributes,
                content: content,
                pushType: nil
            )
            currentActivity = activity
            startUpdateTimer()
        } catch {
            print("Failed to start live activity: \(error)")
        }
    }

    func updateActivity(periodName: String, subject: String, endTime: String, countdownSeconds: Int, nextPeriodName: String?, nextSubject: String?) {
        guard let activity = currentActivity else { return }

        let state = TimetableActivityAttributes.ContentState(
            periodName: periodName,
            subject: subject,
            endTime: endTime,
            countdownSeconds: countdownSeconds,
            nextPeriodName: nextPeriodName,
            nextSubject: nextSubject
        )

        Task {
            let content = ActivityContent(state: state, staleDate: nil)
            await activity.update(content)
        }
    }

    func stopActivity() {
        updateTimer?.invalidate()
        updateTimer = nil

        guard let activity = currentActivity else { return }
        Task {
            let state = TimetableActivityAttributes.ContentState(
                periodName: "已結束",
                subject: "",
                endTime: "",
                countdownSeconds: 0,
                nextPeriodName: nil,
                nextSubject: nil
            )
            let content = ActivityContent(state: state, staleDate: nil)
            await activity.end(content, dismissalPolicy: .immediate)
        }
        currentActivity = nil
    }

    /// Updates the live activity from the engine's current state
    func syncWithEngine(_ engine: TimetableEngine) {
        let period = engine.currentPeriod
        guard period.type != .free && period.type != .none else {
            if isRunning { stopActivity() }
            return
        }

        let countdownSeconds = calculateCountdown(endTime: period.end)

        let nextPeriodName = engine.nextPeriod?.name
        let nextSubject = engine.nextPeriod?.type == .period ? engine.nextPeriod?.subject : nil

        if isRunning {
            updateActivity(
                periodName: period.name,
                subject: period.subject,
                endTime: period.end,
                countdownSeconds: countdownSeconds,
                nextPeriodName: nextPeriodName,
                nextSubject: nextSubject
            )
        } else {
            startActivity(
                periodName: period.name,
                subject: period.subject,
                endTime: period.end,
                countdownSeconds: countdownSeconds,
                nextPeriodName: nextPeriodName,
                nextSubject: nextSubject
            )
        }
    }

    private func calculateCountdown(endTime: String) -> Int {
        let parts = endTime.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return 0 }

        let calendar = Calendar.current
        guard let endDate = calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: .now) else {
            return 0
        }

        return max(0, Int(endDate.timeIntervalSince(.now)))
    }

    private func startUpdateTimer() {
        updateTimer?.invalidate()
        updateTimer = Timer.scheduledTimer(withTimeInterval: 15.0, repeats: true) { [weak self] _ in
            guard self != nil else { return }
            // Updates are driven by syncWithEngine calls from ContentView
        }
    }
}
