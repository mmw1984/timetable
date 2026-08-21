import SwiftUI

struct ContentView: View {
    @State private var engine = TimetableEngine.shared
    private var theme = ThemeManager.shared
    private var dataSource = DataSourceManager.shared
    @State private var actions = AppActions.shared
    private var liveActivity = LiveActivityManager.shared

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase

    private var accentColor: Color {
        theme.selectedPreset.accent(for: colorScheme)
    }

    var body: some View {
        VStack(spacing: 24) {
            headerSection
            mainContent
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(.systemGroupedBackground))
        .tint(accentColor)
        .animation(.easeInOut(duration: 0.3), value: engine.viewMode)
        .animation(.easeInOut(duration: 0.3), value: engine.timetableType)
        .animation(.spring(duration: 0.35), value: engine.selectedDate)
        .sheet(isPresented: $actions.showSettings) {
            SettingsView(theme: theme, dataSource: dataSource, liveActivity: liveActivity)
                .tint(accentColor)
        }
        .fullScreenCover(isPresented: $actions.showFullscreen) {
            FullscreenCountdownView(engine: engine, accentColor: accentColor)
        }
        .onChange(of: dataSource.dayRotation) { engine.refresh() }
        .onChange(of: dataSource.specialDates) { engine.refresh() }
        .onChange(of: dataSource.subjectSchedule) { engine.refresh() }
        .onChange(of: theme.selectedPresetID) { dataSource.pushSnapshot() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                engine.refresh()
                if liveActivity.isEnabled && liveActivity.isRunning {
                    LiveActivityManager.shared.syncWithEngine(engine)
                }
            }
        }
    }

    // MARK: - Header (single glass card)

    private var headerSection: some View {
        VStack(spacing: 16) {
            HStack(alignment: .top) {
                Text("實時時間表")
                    .font(.title.weight(.bold))
                    .foregroundStyle(.primary)

                Spacer()

                datetimeView
            }

            controlRow
        }
        .padding(20)
        .background(.ultraThinMaterial, in: .rect(cornerRadius: 22))
    }

    private var controlRow: some View {
        HStack(spacing: 8) {
            viewTabButton(title: "今日時間表", icon: "calendar", mode: .today)
            viewTabButton(title: "選擇日期", icon: "calendar.badge.clock", mode: .dateSelect)

            Button {
                actions.showFutureDays.toggle()
            } label: {
                Label("循環日", systemImage: "list.number")
            }
            .buttonStyle(.glass)
            .labelStyle(.iconOnly)
            .popover(isPresented: $actions.showFutureDays) {
                futureDaysPopover
            }

            Button {
                actions.showSettings = true
            } label: {
                Label("設定", systemImage: "gearshape")
            }
            .buttonStyle(.glass)
            .labelStyle(.iconOnly)

            Spacer()

            Button {
                withAnimation(.spring(duration: 0.35)) {
                    engine.goToPrevDay()
                }
            } label: {
                Image(systemName: "chevron.left")
            }
            .buttonStyle(.glass)
            .labelStyle(.iconOnly)

            Button {
                withAnimation(.spring(duration: 0.35)) {
                    engine.goToNextDay()
                }
            } label: {
                Image(systemName: "chevron.right")
            }
            .buttonStyle(.glass)
            .labelStyle(.iconOnly)
        }
    }

    @ViewBuilder
    private func viewTabButton(title: String, icon: String, mode: TimetableEngine.ViewMode) -> some View {
        let isActive = engine.viewMode == mode
        let button = Button {
            withAnimation(.spring(duration: 0.3)) {
                if mode == .today {
                    engine.switchToToday()
                } else {
                    engine.viewMode = .dateSelect
                }
            }
        } label: {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.medium))
        }

        if isActive {
            button.buttonStyle(.glassProminent)
        } else {
            button.buttonStyle(.glass)
        }
    }

    private var datetimeView: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            VStack(alignment: .trailing, spacing: 4) {
                Text(TimetableEngine.dateFormatter.string(from: context.date))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text(TimetableEngine.timeFormatter.string(from: context.date))
                    .font(.title3.weight(.semibold))
                    .fontDesign(.rounded)
                    .monospacedDigit()
                    .foregroundStyle(accentColor)
                    .contentTransition(.numericText())
                    .animation(.smooth, value: context.date.timeIntervalSinceReferenceDate)
            }
        }
    }

    // MARK: - Main Content (adaptive columns)

    @ViewBuilder
    private var mainContent: some View {
        if horizontalSizeClass == .compact {
            ScrollView {
                VStack(spacing: 24) {
                    leftPanel
                    scheduleSection(scrollable: false)
                }
            }
        } else {
            HStack(alignment: .top, spacing: 24) {
                leftPanel
                    .containerRelativeFrame(.horizontal) { length, _ in
                        max(280, (length - 72) * 0.38)
                    }

                scheduleSection(scrollable: true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
            .frame(maxHeight: .infinity, alignment: .top)
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
                    .font(.title2.weight(.bold))
                    .foregroundStyle(accentColor)

                if !engine.currentPeriod.start.isEmpty {
                    Text("\(engine.currentPeriod.start) - \(engine.currentPeriod.end)")
                        .font(.subheadline.weight(.medium))
                        .fontDesign(.rounded)
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }

                Text(engine.currentPeriod.subject)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var countdownCard: some View {
        GlassCard(title: "倒計時") {
            VStack(spacing: 8) {
                Group {
                    if let end = engine.countdownEnd, !engine.countdownDelayed {
                        Text(timerInterval: Date.now...end, countsDown: true)
                            .monospacedDigit()
                    } else {
                        Text("--:--:--")
                            .monospacedDigit()
                    }
                }
                .font(.largeTitle.weight(.bold))
                .fontDesign(.rounded)
                .foregroundStyle(accentColor)
                .contentTransition(.numericText())
                .animation(.smooth, value: engine.countdownEnd)

                if !engine.countdownLabel.isEmpty {
                    Text(engine.countdownLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                Button {
                    actions.showFullscreen = true
                } label: {
                    Label("全螢幕", systemImage: "arrow.up.left.and.arrow.down.right")
                        .font(.footnote.weight(.medium))
                }
                .buttonStyle(.glass)
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
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.primary)

                    if !next.start.isEmpty {
                        Text("\(next.start) - \(next.end)")
                            .font(.subheadline.weight(.medium))
                            .fontDesign(.rounded)
                            .monospacedDigit()
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
                        }
                    }
                    .buttonStyle(.glassProminent)
                    .tint(accentColor)

                    Button("返回今日") {
                        withAnimation(.spring(duration: 0.35)) {
                            engine.switchToToday()
                        }
                    }
                    .buttonStyle(.glass)
                }

                if engine.viewMode == .dateSelect {
                    VStack(spacing: 8) {
                        Text(TimetableEngine.fullDateFormatter.string(from: engine.selectedDate))
                            .font(.body.weight(.semibold))
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
            .font(.caption.weight(.medium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(.ultraThinMaterial, in: .capsule)
    }

    private var noticeCard: some View {
        Text(engine.timetableType.noticeText)
            .font(.body.weight(.medium))
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
                .font(.headline)

            if engine.futureDays.isEmpty {
                Text("暫無即將到來的循環日資料")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 10) {
                    ForEach(engine.futureDays, id: \.day) { item in
                        HStack(spacing: 12) {
                            Text("Day \(item.day)")
                                .font(.subheadline.weight(.bold))
                                .fontDesign(.rounded)
                                .foregroundStyle(accentColor)
                                .frame(width: 52, alignment: .leading)

                            Text(TimetableEngine.shortDateString(from: item.date))
                                .font(.subheadline)
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

    private func scheduleSection(scrollable: Bool) -> some View {
        VStack(spacing: 0) {
            scheduleHeader
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 16)

            if scrollable {
                ScrollView {
                    animatedScheduleContent
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                }
            } else {
                animatedScheduleContent
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
            }
        }
        .frame(maxHeight: scrollable ? .infinity : nil)
        .clipped()
        .background(.ultraThinMaterial, in: .rect(cornerRadius: 22))
    }

    private var animatedScheduleContent: some View {
        Group {
            if engine.viewMode == .today {
                scheduleContent
            } else {
                scheduleContent
                    .id(ScheduleResolver.isoString(from: engine.selectedDate))
            }
        }
        .transition(.asymmetric(
            insertion: .move(edge: engine.transitionDirection == .backward ? .leading : .trailing)
                .combined(with: .opacity),
            removal: .move(edge: engine.transitionDirection == .backward ? .trailing : .leading)
                .combined(with: .opacity)
        ))
    }

    private var scheduleHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(engine.viewMode == .today ? "今日時間表" : "時間表")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.primary)

                if engine.viewMode == .dateSelect {
                    Text(TimetableEngine.fullDateFormatter.string(from: engine.selectedDate))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            Text(engine.dayIndicatorText)
                .font(.caption.weight(.semibold))
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
            LazyVStack(spacing: 8) {
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
            .font(.body.weight(.medium))
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

#Preview {
    ContentView()
}
