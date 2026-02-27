import SwiftUI

struct FullscreenCountdownView: View {
    @ObservedObject var engine: TimetableEngine
    var accentColor: Color
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()

            VStack(spacing: 40) {
                Spacer()

                // Current time
                Text(TimetableEngine.timeFormatter.string(from: engine.currentDate))
                    .font(.system(size: 80, weight: .thin))
                    .fontDesign(.rounded)
                    .foregroundStyle(.white.opacity(0.6))

                // Current period info
                VStack(spacing: 12) {
                    Text(engine.currentPeriod.name)
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.8))

                    if engine.currentPeriod.type == .period {
                        Text(engine.currentPeriod.subject)
                            .font(.system(size: 22, weight: .regular))
                            .foregroundStyle(.white.opacity(0.6))
                    }
                }

                // Countdown
                Text(engine.countdownText)
                    .font(.system(size: 120, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(accentColor)
                    .contentTransition(.numericText())
                    .animation(.smooth, value: engine.countdownText)

                if !engine.countdownLabel.isEmpty {
                    Text(engine.countdownLabel)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(.white.opacity(0.5))
                }

                // Next period
                if let next = engine.nextPeriod {
                    VStack(spacing: 8) {
                        Text("下一節")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(.white.opacity(0.4))

                        Text(next.name)
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.7))

                        if next.type == .period {
                            Text(next.subject)
                                .font(.system(size: 18))
                                .foregroundStyle(.white.opacity(0.5))
                        }

                        Text("\(next.start) - \(next.end)")
                            .font(.system(size: 16, weight: .medium))
                            .fontDesign(.rounded)
                            .foregroundStyle(.white.opacity(0.4))
                    }
                    .padding(.top, 20)
                }

                Spacer()

                // Dismiss button
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(.white.opacity(0.3))
                }
                .padding(.bottom, 40)
            }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
    }
}
