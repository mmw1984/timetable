import AppIntents
import WidgetKit

struct ViewTodayTimetableIntent: AppIntent {
    static let title: LocalizedStringResource = "查看今日時間表"
    static let description = IntentDescription("打開 app 並顯示今日時間表")
    static let openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        await MainActor.run {
            TimetableEngine.shared.switchToToday()
        }
        return .result()
    }
}

struct ViewDateTimetableIntent: AppIntent {
    static let title: LocalizedStringResource = "查看指定日期時間表"
    static let description = IntentDescription("打開 app 並顯示指定日期嘅時間表")
    static let openAppWhenRun = true

    @Parameter(title: "日期")
    var date: Date

    static var parameterSummary: some ParameterSummary {
        Summary("查看 \(\.$date) 嘅時間表")
    }

    func perform() async throws -> some IntentResult {
        await MainActor.run {
            TimetableEngine.shared.viewDate(date)
        }
        return .result()
    }
}

struct RefreshTimetableDataIntent: AppIntent {
    static let title: LocalizedStringResource = "更新時間表資料"
    static let description = IntentDescription("從網絡下載最新嘅循環日、特殊日期同時間表資料")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        await DataSourceManager.shared.updateAllFromURLs()
        return .result(dialog: "時間表資料已更新")
    }
}

struct ToggleLiveActivityIntent: AppIntent {
    static let title: LocalizedStringResource = "切換即時動態"
    static let description = IntentDescription("開啟或關閉鎖定畫面倒計時")
    static let openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        await MainActor.run {
            let manager = LiveActivityManager.shared
            manager.isEnabled.toggle()
            if !manager.isEnabled {
                manager.stopActivity()
            } else {
                manager.syncWithEngine(TimetableEngine.shared)
            }
        }
        return .result()
    }
}

struct TimetableAppShortcuts: AppShortcutsProvider {
    @AppShortcutsBuilder
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ViewTodayTimetableIntent(),
            phrases: [
                "打開\(.applicationName)時間表",
                "睇下\(.applicationName)今日時間表",
                "Show \(.applicationName) timetable"
            ],
            shortTitle: "今日時間表",
            systemImageName: "calendar"
        )

        AppShortcut(
            intent: RefreshTimetableDataIntent(),
            phrases: [
                "更新\(.applicationName)資料",
                "Refresh \(.applicationName)"
            ],
            shortTitle: "更新資料",
            systemImageName: "arrow.clockwise"
        )
    }
}
