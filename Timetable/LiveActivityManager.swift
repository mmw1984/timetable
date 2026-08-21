import Foundation
import ActivityKit

@Observable
@MainActor
final class LiveActivityManager {
    static let shared = LiveActivityManager()

    var isEnabled: Bool {
        didSet { UserDefaults.standard.set(isEnabled, forKey: "liveActivityEnabled") }
    }

    private var currentActivity: Activity<TimetableActivityAttributes>?

    var isRunning: Bool {
        currentActivity != nil
    }

    private init() {
        self.isEnabled = UserDefaults.standard.bool(forKey: "liveActivityEnabled")
    }

    func startActivity(periodName: String, subject: String, endDate: Date, nextPeriodName: String?, nextSubject: String?) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        stopActivity()

        let attributes = TimetableActivityAttributes()
        let state = makeState(
            periodName: periodName,
            subject: subject,
            endDate: endDate,
            nextPeriodName: nextPeriodName,
            nextSubject: nextSubject
        )

        do {
            let content = ActivityContent(state: state, staleDate: endDate)
            let activity = try Activity.request(
                attributes: attributes,
                content: content,
                pushType: nil
            )
            currentActivity = activity
        } catch {}
    }

    func updateActivity(periodName: String, subject: String, endDate: Date, nextPeriodName: String?, nextSubject: String?) {
        guard let activity = currentActivity else { return }

        let state = makeState(
            periodName: periodName,
            subject: subject,
            endDate: endDate,
            nextPeriodName: nextPeriodName,
            nextSubject: nextSubject
        )

        Task {
            let content = ActivityContent(state: state, staleDate: endDate)
            await activity.update(content)
        }
    }

    func stopActivity() {
        guard let activity = currentActivity else { return }
        currentActivity = nil

        Task {
            let state = TimetableActivityAttributes.ContentState(
                periodName: "已結束",
                subject: "",
                endDate: .now,
                nextPeriodName: nil,
                nextSubject: nil,
                accentHex: ThemeManager.shared.darkAccentHex
            )
            let content = ActivityContent(state: state, staleDate: nil)
            await activity.end(content, dismissalPolicy: .immediate)
        }
    }

    func syncWithEngine(_ engine: TimetableEngine) {
        guard isEnabled else { return }

        let period = engine.currentPeriod
        guard period.type != .free && period.type != .none,
              let end = engine.countdownEnd else {
            if isRunning { stopActivity() }
            return
        }

        let nextPeriodName = engine.nextPeriod?.name
        let nextSubject = engine.nextPeriod?.type == .period ? engine.nextPeriod?.subject : nil

        if isRunning {
            updateActivity(
                periodName: period.name,
                subject: period.subject,
                endDate: end,
                nextPeriodName: nextPeriodName,
                nextSubject: nextSubject
            )
        } else {
            startActivity(
                periodName: period.name,
                subject: period.subject,
                endDate: end,
                nextPeriodName: nextPeriodName,
                nextSubject: nextSubject
            )
        }
    }

    private func makeState(periodName: String, subject: String, endDate: Date, nextPeriodName: String?, nextSubject: String?) -> TimetableActivityAttributes.ContentState {
        TimetableActivityAttributes.ContentState(
            periodName: periodName,
            subject: subject,
            endDate: endDate,
            nextPeriodName: nextPeriodName,
            nextSubject: nextSubject,
            accentHex: ThemeManager.shared.darkAccentHex
        )
    }
}
