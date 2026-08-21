import SwiftUI

@main
struct TimetableApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(nil)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        DataSourceManager.shared.pushSnapshot()
                    }
                }
        }
        .commands {
            TimetableCommands()
        }
    }
}

@MainActor
struct TimetableCommands: Commands {
    var body: some Commands {
        CommandMenu("時間表") {
            Button("上一日") {
                TimetableEngine.shared.goToPrevDay()
            }
            .keyboardShortcut(.leftArrow, modifiers: .command)

            Button("下一日") {
                TimetableEngine.shared.goToNextDay()
            }
            .keyboardShortcut(.rightArrow, modifiers: .command)

            Button("返回今日") {
                TimetableEngine.shared.switchToToday()
            }
            .keyboardShortcut("t", modifiers: .command)

            Divider()

            Button("全螢幕倒計時") {
                AppActions.shared.showFullscreen = true
            }
            .keyboardShortcut("f", modifiers: [.command, .shift])
            .disabled(TimetableEngine.shared.currentPeriod.type == .none)
        }

        CommandGroup(replacing: .appSettings) {
            Button("設定") {
                AppActions.shared.showSettings = true
            }
            .keyboardShortcut(",", modifiers: .command)
        }
    }
}
