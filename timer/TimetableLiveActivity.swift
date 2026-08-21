import WidgetKit
import SwiftUI
import ActivityKit

struct TimetableLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TimetableActivityAttributes.self) { context in
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
                        countdownText(context.state.endDate)
                            .font(.system(size: 20, weight: .bold))
                            .fontDesign(.rounded)
                            .foregroundStyle(Color(hex: context.state.accentHex))
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
                countdownText(context.state.endDate)
                    .font(.system(size: 12, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(Color(hex: context.state.accentHex))
            } minimal: {
                countdownText(context.state.endDate)
                    .font(.system(size: 11, weight: .bold))
                    .fontDesign(.rounded)
            }
        }
    }

    private func countdownText(_ endDate: Date) -> Text {
        Text(endDate, style: .timer)
    }

    private func lockScreenView(context: ActivityViewContext<TimetableActivityAttributes>) -> some View {
        let accent = Color(hex: context.state.accentHex)
        return HStack(spacing: 16) {
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
                Text(context.state.endDate, style: .timer)
                    .font(.system(size: 24, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(accent)
                    .monospacedDigit()
                    .frame(maxWidth: 90, alignment: .trailing)

                if let nextName = context.state.nextPeriodName {
                    Text("下一節: \(nextName)")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(16)
    }
}
