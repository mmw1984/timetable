import SwiftUI

struct FullscreenCountdownView: View {
    @Bindable var engine: TimetableEngine
    var accentColor: Color
    @Environment(\.dismiss) private var dismiss

    @ScaledMetric(relativeTo: .largeTitle) private var clockSize: CGFloat = 80
    @ScaledMetric(relativeTo: .largeTitle) private var countdownSize: CGFloat = 120

    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()

            TimelineView(.periodic(from: .now, by: 1)) { context in
                VStack(spacing: 40) {
                    Spacer()

                    Text(TimetableEngine.timeFormatter.string(from: context.date))
                        .font(.system(size: clockSize, weight: .thin))
                        .fontDesign(.rounded)
                        .monospacedDigit()
                        .foregroundStyle(.white.opacity(0.6))

                    VStack(spacing: 12) {
                        Text(engine.currentPeriod.name)
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.8))

                        if engine.currentPeriod.type == .period {
                            Text(engine.currentPeriod.subject)
                                .font(.title3)
                                .foregroundStyle(.white.opacity(0.6))
                        }
                    }

                    Group {
                        if let end = engine.countdownEnd, !engine.countdownDelayed {
                            Text(timerInterval: Date.now...end, countsDown: true)
                                .monospacedDigit()
                        } else {
                            Text("--:--:--")
                                .monospacedDigit()
                        }
                    }
                    .font(.system(size: countdownSize, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(accentColor)
                    .contentTransition(.numericText())
                    .animation(.smooth, value: engine.countdownEnd)
                    .minimumScaleFactor(0.3)
                    .lineLimit(1)

                    if !engine.countdownLabel.isEmpty {
                        Text(engine.countdownLabel)
                            .font(.title3.weight(.medium))
                            .foregroundStyle(.white.opacity(0.5))
                    }

                    if let next = engine.nextPeriod {
                        VStack(spacing: 8) {
                            Text("下一節")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.white.opacity(0.4))

                            Text(next.name)
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.7))

                            if next.type == .period {
                                Text(next.subject)
                                    .font(.body)
                                    .foregroundStyle(.white.opacity(0.5))
                            }

                            Text("\(next.start) - \(next.end)")
                                .font(.subheadline.weight(.medium))
                                .fontDesign(.rounded)
                                .monospacedDigit()
                                .foregroundStyle(.white.opacity(0.4))
                        }
                        .padding(.top, 20)
                    }

                    Spacer()

                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.largeTitle)
                            .foregroundStyle(.white.opacity(0.3))
                    }
                    .buttonStyle(.glass)
                    .padding(.bottom, 40)
                }
            }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
    }
}
