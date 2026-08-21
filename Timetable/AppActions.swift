import SwiftUI

@Observable
@MainActor
final class AppActions {
    static let shared = AppActions()

    var showSettings = false
    var showFullscreen = false
    var showFutureDays = false

    private init() {}
}
