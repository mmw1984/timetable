import SwiftUI

// MARK: - Conditional modifier

extension View {
    @ViewBuilder
    func `if`<T: View>(_ condition: Bool, transform: (Self) -> T) -> some View {
        if condition { transform(self) } else { self }
    }
}

struct ContentView: View {
    @StateObject private var engine = TimetableEngine()
    @StateObject private var theme = ThemeManager.shared
    @StateObject private var dataSource = DataSourceManager.shared
    @Environment(\.colorScheme) private var colorScheme
    @State private var showSettings = false
    @State private var showFullscreenCountdown = false
    @State private var showFutureDays = false
    @State private var scheduleID = UUID()
    @State private var liveActivityEnabled = false

    private var accentColor: Color {
        theme.selectedPreset.accent(for: colorScheme)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                headerSection
                mainContent
            }
            .padding(24)
        }
        .background(Color(.systemGroupedBackground))
        .tint(accentColor)
        .animation(.easeInOut(duration: 0.3), value: engine.viewMode)
        .animation(.easeInOut(duration: 0.3), value: engine.timetableType)
        .sheet(isPresented: $showSettings) {
            SettingsView(theme: theme, dataSource: dataSource, liveActivityEnabled: $liveActivityEnabled)
                .tint(accentColor)
        }
        .fullScreenCover(isPresented: $showFullscreenCountdown) {
            FullscreenCountdownView(engine: engine, accentColor: accentColor)
        }
        .onChange(of: dataSource.dayRotation) {
            engine.refresh()
        }
        .onChange(of: dataSource.specialDates) {
            engine.refresh()
        }
        .onChange(of: dataSource.subjectSchedule) {
            engine.refresh()
        }
        .onChange(of: engine.currentPeriod) {
            if liveActivityEnabled {
                LiveActivityManager.shared.syncWithEngine(engine)
            }
        }
    }

    // MARK: - Header (single glass card)

    private var headerSection: some View {
        VStack(spacing: 16) {
            // Title row
            HStack {
                Text("實時時間表")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(.primary)

                Spacer()

                datetimeView
            }

            // Tab + nav row
            HStack(spacing: 8) {
                viewTabButton(title: "今日時間表", icon: "calendar", mode: .today)
                viewTabButton(title: "選擇日期", icon: "calendar.badge.clock", mode: .dateSelect)

                // Future days popover button
                Button {
                    showFutureDays.toggle()
                } label: {
                    Image(systemName: "list.number")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: .capsule)
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showFutureDays) {
                    futureDaysPopover
                }

                // Settings button — right next to future days
                Button {
                    showSettings = true
                } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: .capsule)
                }
                .buttonStyle(.plain)

                Spacer()

                // Nav buttons
                Button {
                    withAnimation(.spring(duration: 0.35)) {
                        engine.goToPrevDay()
                        scheduleID = UUID()
                    }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: .capsule)
                }
                .buttonStyle(.plain)

                Button {
                    withAnimation(.spring(duration: 0.35)) {
                        engine.goToNextDay()
                        scheduleID = UUID()
                    }
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: .capsule)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .background(.ultraThinMaterial, in: .rect(cornerRadius: 22))
    }

    @ViewBuilder
    private func viewTabButton(title: String, icon: String, mode: TimetableEngine.ViewMode) -> some View {
        let isActive = engine.viewMode == mode
        Button {
            withAnimation(.spring(duration: 0.3)) {
                if mode == .today {
                    engine.switchToToday()
                } else {
                    engine.viewMode = .dateSelect
                }
                scheduleID = UUID()
            }
        } label: {
            Label(title, systemImage: icon)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundStyle(isActive ? .white : .primary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .if(!isActive) { view in
            view.background(.ultraThinMaterial, in: .capsule)
        }
        .if(isActive) { view in
            view.background(accentColor, in: .capsule)
        }
    }

    private var datetimeView: some View {
        VStack(alignment: .trailing, spacing: 4) {
            Text(TimetableEngine.dateFormatter.string(from: engine.currentDate))
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Text(TimetableEngine.timeFormatter.string(from: engine.currentDate))
                .font(.system(size: 18, weight: .semibold))
                .fontDesign(.rounded)
                .foregroundStyle(accentColor)
                .contentTransition(.numericText())
                .animation(.smooth, value: TimetableEngine.timeFormatter.string(from: engine.currentDate))
        }
    }

    // MARK: - Main Content (two-column: 1fr 1.5fr)

    private var mainContent: some View {
        HStack(alignment: .top, spacing: 24) {
            leftPanel
                .containerRelativeFrame(.horizontal) { length, _ in
                    (length - 72) * (1.0 / 2.5)
                }

            scheduleSection
                .containerRelativeFrame(.horizontal) { length, _ in
                    (length - 72) * (1.5 / 2.5)
                }
        }
    }

    // MARK: - Left Panel

    private var leftPanel: some View {
        VStack(spacing: 16) {
            if engine.viewMode == .today {
                currentStatusCard
                countdownCard
                nextPeriodCard
            } else {
                dateSelectionCard
            }

            if engine.timetableType != .normal && engine.timetableType != .none {
                noticeCard
            }
        }
    }

    private var currentStatusCard: some View {
        GlassCard(title: "當前狀態") {
            VStack(spacing: 8) {
                Text(engine.currentPeriod.name)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(accentColor)

                if !engine.currentPeriod.start.isEmpty {
                    Text("\(engine.currentPeriod.start) - \(engine.currentPeriod.end)")
                        .font(.system(size: 14, weight: .medium))
                        .fontDesign(.rounded)
                        .foregroundStyle(.secondary)
                }

                Text(engine.currentPeriod.subject)
                    .font(.body)
                    .fontWeight(.medium)
                    .foregroundStyle(.primary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var countdownCard: some View {
        GlassCard(title: "倒計時") {
            VStack(spacing: 8) {
                Text(engine.countdownText)
                    .font(.system(size: 32, weight: .bold))
                    .fontDesign(.rounded)
                    .foregroundStyle(accentColor)
                    .contentTransition(.numericText())
                    .animation(.smooth, value: engine.countdownText)

                if !engine.countdownLabel.isEmpty {
                    Text(engine.countdownLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // Fullscreen AOD button — always visible
                Button {
                    showFullscreenCountdown = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 12, weight: .medium))
                        Text("全螢幕")
                            .font(.system(size: 13, weight: .medium))
                    }
                    .foregroundStyle(accentColor)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.ultraThinMaterial, in: .capsule)
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var nextPeriodCard: some View {
        GlassCard(title: "下一堂課") {
            if let next = engine.nextPeriod {
                VStack(spacing: 8) {
                    Text(next.name)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.primary)

                    if !next.start.isEmpty {
                        Text("\(next.start) - \(next.end)")
                            .font(.system(size: 14, weight: .medium))
                            .fontDesign(.rounded)
                            .foregroundStyle(.secondary)
                    }

                    Text(next.subject)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                }
                .frame(maxWidth: .infinity)
            } else {
                Text("今日課程已結束")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var dateSelectionCard: some View {
        GlassCard(title: "選擇日期") {
            VStack(spacing: 16) {
                DatePicker("日期", selection: $engine.selectedDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "zh-HK"))

                HStack(spacing: 12) {
                    Button("查看") {
                        withAnimation(.spring(duration: 0.35)) {
                            engine.viewDate(engine.selectedDate)
                            scheduleID = UUID()
                        }
                    }
                    .buttonStyle(GlassPrimaryButton(accent: accentColor))

                    Button("返回今日") {
                        withAnimation(.spring(duration: 0.35)) {
                            engine.switchToToday()
                            scheduleID = UUID()
                        }
                    }
                    .buttonStyle(GlassSecondaryButton())
                }

                if engine.viewMode == .dateSelect {
                    VStack(spacing: 8) {
                        Text(TimetableEngine.fullDateFormatter.string(from: engine.selectedDate))
                            .font(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)

                        HStack(spacing: 8) {
                            dateTag(engine.dayIndicatorText)
                            dateTag(engine.timetableType.displayText)
                        }
                    }
                    .transition(.opacity.combined(with: .scale(scale: 0.95)))
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func dateTag(_ text: String) -> some View {
        Text(text)
            .font(.caption)
            .fontWeight(.medium)
            .foregroundStyle(.secondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(.ultraThinMaterial, in: .capsule)
    }

    // Notice card — standalone glass with orange tint
    private var noticeCard: some View {
        Text(engine.timetableType.noticeText)
            .font(.body)
            .fontWeight(.medium)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(16)
            .background(.orange, in: .rect(cornerRadius: 20))
            .overlay {
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(.white.opacity(0.2), lineWidth: 1)
            }
    }

    // MARK: - Future Days Popover

    private var futureDaysPopover: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("即將到來的循環日")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.primary)

            if engine.futureDays.isEmpty {
                Text("暫無即將到來的循環日資料")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 10) {
                    ForEach(engine.futureDays, id: \.day) { item in
                        HStack(spacing: 12) {
                            Text("Day \(item.day)")
                                .font(.system(size: 15, weight: .bold))
                                .fontDesign(.rounded)
                                .foregroundStyle(accentColor)
                                .frame(width: 52, alignment: .leading)

                            Text(TimetableEngine.shortDateString(from: item.date))
                                .font(.system(size: 14))
                                .foregroundStyle(.primary)

                            Spacer()
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .padding(20)
        .frame(minWidth: 260)
        .presentationCompactAdaptation(.popover)
    }

    // MARK: - Schedule Section

    private var scheduleSection: some View {
        VStack(spacing: 0) {
            scheduleHeader
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 16)

            scheduleContent
                .id(scheduleID)
                .transition(.asymmetric(
                    insertion: .move(edge: engine.transitionDirection == .backward ? .leading : .trailing)
                        .combined(with: .opacity),
                    removal: .move(edge: engine.transitionDirection == .backward ? .trailing : .leading)
                        .combined(with: .opacity)
                ))
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
        }
        .clipped()
        .background(.ultraThinMaterial, in: .rect(cornerRadius: 22))
    }

    private var scheduleHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(engine.viewMode == .today ? "今日時間表" : "時間表")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.primary)

                if engine.viewMode == .dateSelect {
                    Text(TimetableEngine.fullDateFormatter.string(from: engine.selectedDate))
                        .font(.system(size: 14))
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            Text(engine.dayIndicatorText)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                .background(accentColor, in: .capsule)
        }
    }

    @ViewBuilder
    private var scheduleContent: some View {
        let mergedItems = engine.mergedScheduleItems(merge: dataSource.mergePeriods)
        if mergedItems.isEmpty {
            emptyScheduleView
        } else {
            VStack(spacing: 8) {
                ForEach(mergedItems) { item in
                    ScheduleRowView(
                        item: item,
                        accentColor: accentColor
                    )
                }
            }
        }
    }

    private var emptyScheduleView: some View {
        Text(engine.timetableType == .none ? "今日沒有課程" : "暫無時間表資料")
            .font(.body)
            .fontWeight(.medium)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [6]))
                    .foregroundStyle(Color(.separator))
            )
    }
}

// MARK: - Button Styles

struct GlassPrimaryButton: ButtonStyle {
    let accent: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline)
            .fontWeight(.medium)
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(accent, in: .capsule)
            .opacity(configuration.isPressed ? 0.8 : 1.0)
    }
}

struct GlassSecondaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline)
            .fontWeight(.medium)
            .foregroundStyle(.primary)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(.ultraThinMaterial, in: .capsule)
            .opacity(configuration.isPressed ? 0.7 : 1.0)
    }
}

#Preview {
    ContentView()
}
