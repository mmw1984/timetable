import SwiftUI

struct ColorPreset: Identifiable, Codable, Equatable {
    let id: String
    let name: String
    let lightAccentHex: String
    let darkAccentHex: String

    var lightAccent: Color { Color(hex: lightAccentHex) }
    var darkAccent: Color { Color(hex: darkAccentHex) }

    func accent(for scheme: ColorScheme) -> Color {
        scheme == .dark ? darkAccent : lightAccent
    }
}

@Observable
@MainActor
final class ThemeManager {

    static let shared = ThemeManager()

    var selectedPresetID: String {
        didSet { UserDefaults.standard.set(selectedPresetID, forKey: "selectedPresetID") }
    }

    var selectedPreset: ColorPreset {
        Self.presets.first(where: { $0.id == selectedPresetID }) ?? Self.presets[0]
    }

    var lightAccentHex: String { selectedPreset.lightAccentHex }
    var darkAccentHex: String { selectedPreset.darkAccentHex }

    private init() {
        self.selectedPresetID = UserDefaults.standard.string(forKey: "selectedPresetID") ?? "blue"
    }

    static let presets: [ColorPreset] = [
        ColorPreset(id: "blue",        name: "經典藍",   lightAccentHex: "#007AFF", darkAccentHex: "#0A84FF"),
        ColorPreset(id: "indigo",      name: "靛藍",     lightAccentHex: "#5856D6", darkAccentHex: "#5E5CE6"),
        ColorPreset(id: "purple",      name: "紫色",     lightAccentHex: "#AF52DE", darkAccentHex: "#BF5AF2"),
        ColorPreset(id: "pink",        name: "粉紅",     lightAccentHex: "#FF2D55", darkAccentHex: "#FF375F"),
        ColorPreset(id: "red",         name: "紅色",     lightAccentHex: "#FF3B30", darkAccentHex: "#FF453A"),
        ColorPreset(id: "orange",      name: "橙色",     lightAccentHex: "#FF9500", darkAccentHex: "#FF9F0A"),
        ColorPreset(id: "tangerine",   name: "橘橙",     lightAccentHex: "#FF6723", darkAccentHex: "#FF6F31"),
        ColorPreset(id: "peach",       name: "蜜桃橙",   lightAccentHex: "#FF8C69", darkAccentHex: "#FF9A76"),
        ColorPreset(id: "apricot",     name: "杏橙",     lightAccentHex: "#E8842C", darkAccentHex: "#F09035"),
        ColorPreset(id: "amber",       name: "琥珀",     lightAccentHex: "#FFBF00", darkAccentHex: "#FFC914"),
        ColorPreset(id: "yellow",      name: "黃色",     lightAccentHex: "#FFCC00", darkAccentHex: "#FFD60A"),
        ColorPreset(id: "green",       name: "綠色",     lightAccentHex: "#34C759", darkAccentHex: "#30D158"),
        ColorPreset(id: "teal",        name: "藍綠",     lightAccentHex: "#5AC8FA", darkAccentHex: "#64D2FF"),
        ColorPreset(id: "mint",        name: "薄荷",     lightAccentHex: "#00C7BE", darkAccentHex: "#63E6E2"),
        ColorPreset(id: "brown",       name: "棕色",     lightAccentHex: "#A2845E", darkAccentHex: "#AC8E68"),
        ColorPreset(id: "graphite",    name: "石墨",     lightAccentHex: "#8E8E93", darkAccentHex: "#98989D"),
    ]
}
