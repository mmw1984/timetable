import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @Bindable var theme: ThemeManager
    @Bindable var dataSource: DataSourceManager
    @Bindable var liveActivity: LiveActivityManager
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var showFileImporter = false
    @State private var importTarget: ImportTarget = .days
    @State private var showResetConfirm = false
    @State private var liveActivityTestActive = false

    enum ImportTarget { case days, special, timetable }

    var body: some View {
        NavigationStack {
            List {
                colorSection
                displaySection
                dataSourceSection
                dataURLSection
                aboutSection
            }
            .navigationTitle("設定")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.plainText],
                allowsMultipleSelection: false
            ) { result in
                handleFileImport(result)
            }
            .confirmationDialog("確認重置", isPresented: $showResetConfirm) {
                Button("重置為預設值", role: .destructive) {
                    dataSource.resetToDefaults()
                }
            } message: {
                Text("這將清除所有從網絡或檔案匯入的資料，恢復為內建預設值。")
            }
        }
        .presentationDetents([.large])
    }

    // MARK: - Color Section

    private var colorSection: some View {
        Section {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 56))], spacing: 12) {
                ForEach(ThemeManager.presets) { preset in
                    colorSwatch(preset)
                }
            }
            .padding(.vertical, 8)
        } header: {
            Label("主題色彩", systemImage: "paintpalette")
        } footer: {
            Text("選擇主題色彩，所有強調色將隨之改變")
        }
    }

    private func colorSwatch(_ preset: ColorPreset) -> some View {
        let isSelected = theme.selectedPresetID == preset.id
        let color = preset.accent(for: colorScheme)

        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                theme.selectedPresetID = preset.id
            }
        } label: {
            VStack(spacing: 4) {
                Circle()
                    .fill(color)
                    .frame(width: 40, height: 40)
                    .overlay {
                        if isSelected {
                            Image(systemName: "checkmark")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)
                        }
                    }
                    .overlay {
                        Circle()
                            .strokeBorder(isSelected ? color : .clear, lineWidth: 3)
                            .frame(width: 48, height: 48)
                    }

                Text(preset.name)
                    .font(.caption2)
                    .foregroundStyle(isSelected ? color : .secondary)
            }
        }
        .buttonStyle(.plain)
    }

    // MARK: - Display Section

    private var displaySection: some View {
        Section {
            Toggle("合併相同科目連堂", isOn: $dataSource.mergePeriods)

            Toggle(isOn: $liveActivity.isEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("即時動態")
                    Text("在鎖定畫面和動態島顯示倒計時（無聲）")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .onChange(of: liveActivity.isEnabled) {
                if !liveActivity.isEnabled {
                    liveActivity.stopActivity()
                    liveActivityTestActive = false
                } else if let engine = currentEngineIfRunning {
                    liveActivity.syncWithEngine(engine)
                }
            }

            if liveActivity.isEnabled {
                Button {
                    if liveActivityTestActive {
                        liveActivity.stopActivity()
                        liveActivityTestActive = false
                    } else {
                        liveActivity.startActivity(
                            periodName: "第3節（測試）",
                            subject: "MATH YPC 405",
                            endDate: .now.addingTimeInterval(600),
                            nextPeriodName: "小息",
                            nextSubject: nil
                        )
                        liveActivityTestActive = true
                    }
                } label: {
                    HStack {
                        Label(
                            liveActivityTestActive ? "停止測試即時動態" : "測試即時動態",
                            systemImage: liveActivityTestActive ? "stop.circle" : "play.circle"
                        )
                        Spacer()
                        if liveActivityTestActive {
                            Text("運行中")
                                .font(.caption)
                                .foregroundStyle(.green)
                        }
                    }
                }
            }
        } header: {
            Label("顯示設定", systemImage: "rectangle.grid.1x2")
        } footer: {
            Text("合併：連續相同科目的課堂將合併為一個顯示，但保持相同的視覺高度\n即時動態不會發出任何聲音或震動")
        }
    }

    @MainActor
    private var currentEngineIfRunning: TimetableEngine? {
        TimetableEngine.shared.currentPeriod.type == .period ? TimetableEngine.shared : nil
    }

    // MARK: - Data Source Section

    private var dataSourceSection: some View {
        Section {
            dataRow(
                title: "循環日資料",
                subtitle: "days.txt",
                lastUpdate: dataSource.lastDaysUpdate,
                importAction: { importTarget = .days; showFileImporter = true }
            )
            dataRow(
                title: "特殊日期資料",
                subtitle: "special-date.txt",
                lastUpdate: dataSource.lastSpecialUpdate,
                importAction: { importTarget = .special; showFileImporter = true }
            )
            dataRow(
                title: "時間表資料",
                subtitle: "timetable.txt",
                lastUpdate: dataSource.lastTimetableUpdate,
                importAction: { importTarget = .timetable; showFileImporter = true }
            )

            Button {
                Task { await dataSource.updateAllFromURLs() }
            } label: {
                HStack {
                    Label("從網絡更新全部", systemImage: "arrow.clockwise")
                    Spacer()
                    if dataSource.isLoading {
                        ProgressView()
                    }
                }
            }
            .disabled(dataSource.isLoading)

            Button("重置為預設值", role: .destructive) {
                showResetConfirm = true
            }

            if let error = dataSource.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        } header: {
            Label("資料來源", systemImage: "doc.text")
        } footer: {
            Text("匯入本地檔案或從網絡 URL 下載最新資料")
        }
    }

    private func dataRow(title: String, subtitle: String, lastUpdate: Date?, importAction: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body)
                HStack(spacing: 4) {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let date = lastUpdate {
                        Text("· \(date.formatted(.relative(presentation: .named)))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Spacer()
            Button {
                importAction()
            } label: {
                Image(systemName: "square.and.arrow.down")
            }
        }
    }

    // MARK: - URL Section

    private var dataURLSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("循環日 URL")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("days.txt URL", text: $dataSource.daysURL)
                    .textContentType(.URL)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .font(.caption)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("特殊日期 URL")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("special-date.txt URL", text: $dataSource.specialDatesURL)
                    .textContentType(.URL)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .font(.caption)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("時間表 URL")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("timetable.txt URL", text: $dataSource.timetableURL)
                    .textContentType(.URL)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .font(.caption)
            }
        } header: {
            Label("網絡資料 URL", systemImage: "link")
        }
    }

    // MARK: - About

    private var aboutSection: some View {
        Section {
            LabeledContent("版本", value: "1.0")
            LabeledContent("資料來源", value: "timetable.mmw1984.com")
        } header: {
            Label("關於", systemImage: "info.circle")
        }
    }

    // MARK: - File import handler

    private func handleFileImport(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result, let url = urls.first else { return }

        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }

        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return }

        switch importTarget {
        case .days:      dataSource.importDaysFile(text)
        case .special:   dataSource.importSpecialDatesFile(text)
        case .timetable: dataSource.importTimetableFile(text)
        }
    }
}
