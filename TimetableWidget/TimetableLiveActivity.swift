import WidgetKit
import SwiftUI
import ActivityKit

// MARK: - Live Activity Widget

struct TimetableLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TimetableActivityAttributes.self) { context in
            // Lock screen / banner presentation
            lockScreenView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.state.periodName)
                            .font(.system(size: 14, weight: .semibold))
                        if !context.state.subject.isEmpty {
                            Text(context.state.subject)
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(formatCountdown(context.state.countdownSeconds))
                            .font(.system(size: 20, weight: .bold))
                            .fontDesign(.rounded)
                            .foregroundStyle(.cyan)
                        Text("剩餘")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if let nextName = context.state.nextPeriodName {
                        HStack {
                            Text("下一節: \(nextName)")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                            if let nextSubject = context.state.nextSubject {
                                Text("· \(nextSubject)")
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
            } compactLeading: {
                Text(context.state.periodName)
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
            } compactTrailing: {
                Text(formatCountdown(context.state.countdownSeconds))
                    .font(.system(size: 12, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(.cyan)
            } minimal: {
                Text(formatCountdown(context.state.countdownSeconds))
                    .font(.system(size: 11, weight: .bold))
                    .fontDesign(.rounded)
            }
        }
    }

    @ViewBuilder
    private func lockScreenView(context: ActivityViewContext<TimetableActivityAttributes>) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(context.state.periodName)
                    .font(.system(size: 16, weight: .semibold))
                if !context.state.subject.isEmpty {
                    Text(context.state.subject)
                        .font(.system(size: 14))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                Text(formatCountdown(context.state.countdownSeconds))
                    .font(.system(size: 24, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(.cyan)

                if let nextName = context.state.nextPeriodName {
                    Text("下一節: \(nextName)")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(16)
    }

    private func formatCountdown(_ seconds: Int) -> String {
        if seconds <= 0 { return "00:00" }
        let m = seconds / 60
        let s = seconds % 60
        if m >= 60 {
            let h = m / 60
            let rm = m % 60
            return String(format: "%d:%02d:%02d", h, rm, s)
        }
        return String(format: "%02d:%02d", m, s)
    }
}
