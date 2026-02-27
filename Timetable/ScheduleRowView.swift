import SwiftUI

struct ScheduleRowView: View {
    let item: MergedScheduleItem
    var accentColor: Color = .accentColor

    @Environment(\.colorScheme) private var colorScheme

    private var isBreak: Bool {
        item.type == .breakTime
    }

    private var isAssembly: Bool {
        item.type == .assembly
    }

    var body: some View {
        HStack(spacing: 12) {
            // Time column
            VStack(spacing: 2) {
                Text(item.start)
                Text(item.end)
            }
            .font(.system(size: 12, weight: .medium))
            .fontDesign(.rounded)
            .foregroundStyle(labelColor)
            .frame(width: 48, alignment: .center)

            // Period name
            Text(item.displayName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(labelColor)
                .frame(width: 60, alignment: .leading)

            // Subject
            Text(item.subject)
                .font(.system(size: 14))
                .foregroundStyle(labelColor)
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, rowVerticalPadding)
        .background(rowBackgroundColor, in: .rect(cornerRadius: 10))
    }

    // MARK: - Layout

    /// Merged items keep same visual height per span
    private var rowVerticalPadding: CGFloat {
        if item.spanCount > 1 {
            return CGFloat(item.spanCount) * 14
        }
        return 14
    }

    // MARK: - Colors

    private var rowBackgroundColor: Color {
        if item.isCurrent {
            return accentColor
        } else if isBreak {
            return .orange
        } else if isAssembly {
            return accentColor.opacity(0.15)
        } else {
            return Color(.systemBackground).opacity(colorScheme == .dark ? 0.15 : 0.5)
        }
    }

    private var labelColor: Color {
        (item.isCurrent || isBreak) ? .white : .primary
    }
}
