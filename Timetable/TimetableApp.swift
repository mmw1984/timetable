import SwiftUI

@main
struct TimetableApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(nil) // Respect system setting (supports dark mode)
        }
    }
}
