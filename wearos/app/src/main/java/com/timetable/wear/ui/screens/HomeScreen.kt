package com.timetable.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timetable.wear.ui.theme.SubjectColors
import com.timetable.wear.ui.theme.denseBodySmall
import com.timetable.wear.ui.theme.denseLabelSmall
import com.timetable.wear.ui.theme.denseLabelSmall11
import com.timetable.wear.ui.theme.denseTitleLarge
import com.timetable.wear.ui.theme.denseTitleSmall
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.data.model.ScheduleItem
import com.timetable.wear.data.model.ScheduleItemType
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.engine.CountdownState
import com.timetable.wear.engine.HomeScheduleState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    onOpenWeek: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(Unit) { viewModel.start() }

    // Signal time-to-full-display once the bundled/cached data has rendered, so
    // baseline-profile generation covers the full startup journey.
    ReportDrawnWhen { !scheduleState.isLoading }

    val mergedItems = remember(scheduleState.scheduleItems) {
        mergeConsecutiveItems(scheduleState.scheduleItems)
    }

    ScreenScaffold(
        scrollState = columnState,
        edgeButton = {
            EdgeButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "設定")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                    DateNavigationCard(
                        state = scheduleState,
                        onPrevious = viewModel::prevDay,
                        onNext = viewModel::nextDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
            }

            if (scheduleState.isLoading) {
                item {
                    LoadingCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            } else {
                // Only show current/next when viewing today; "課表預覽" is redundant for other days
                if (scheduleState.isViewingToday) {
                    item {
                        CurrentClassCard(
                            state = scheduleState,
                            countdownFlow = viewModel.countdownState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    }

                    scheduleState.nextPeriod?.let { nextPeriod ->
                        item {
                            NextClassCard(
                                period = nextPeriod,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                                transformation = SurfaceTransformation(transformationSpec)
                            )
                        }
                    }
                }

                item {
                    WeekOverviewEntryCard(
                        onClick = onOpenWeek,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }

                if (mergedItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "今日沒有課程",
                                    style = denseBodySmall(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                items(mergedItems, key = { it.stableKey }) { item ->
                    val isCurrent = isCurrentMerged(item, scheduleState)
                    val isBreak = item.type == ScheduleItemType.BREAK_TIME || item.type == ScheduleItemType.ASSEMBLY
                    if (isBreak) {
                        BreakItemCard(
                            item = item,
                            isCurrent = isCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    } else {
                        ScheduleItemCard(
                            item = item,
                            isCurrent = isCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun DateNavigationCard(
    state: HomeScheduleState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    Card(
        modifier = modifier,
        transformation = transformation,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        // Outer Box fills Card's minHeight (64dp) to allow true vertical centering; Center aligns Row vertically
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
            DateNavigationControls(state, onPrevious, onNext)
        }
    }
}

@Composable
internal fun DateNavigationControls(
    state: HomeScheduleState,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    // Compact single-row switcher: small arrows flanking centered date text, no pill background
    val display = state.selectedDateDisplay.ifBlank { "載入中" }
    val weekday = if (" " in display) display.substringAfterLast(" ") else ""
    val datePart = if (" " in display) display.substringBeforeLast(" ") else display
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = state.canNavigatePrevious,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "上一個上課日",
                modifier = Modifier.size(16.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (weekday.isNotBlank() && weekday != datePart) "$datePart$weekday" else datePart,
                style = denseLabelSmall11(),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Text(
                text = when {
                    state.isLoading -> "正在載入課表"
                    state.timetableType == TimetableType.NONE -> "非上課日"
                    state.isViewingToday -> "今天 · Day ${state.dayCycle ?: "—"}"
                    else -> "Day ${state.dayCycle ?: "—"}"
                },
                style = denseLabelSmall(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        IconButton(
            onClick = onNext,
            enabled = state.canNavigateNext,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "下一個上課日",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LoadingCard(modifier: Modifier, transformation: SurfaceTransformation) {
    Card(modifier = modifier, transformation = transformation) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(
                    text = "正在載入課表",
                    modifier = Modifier.padding(start = 10.dp),
                    style = denseBodySmall()
                )
            }
        }
    }
}

@Composable
private fun CurrentClassCard(
    state: HomeScheduleState,
    countdownFlow: StateFlow<CountdownState>,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    val isTimed = state.isViewingToday && countdown.countdownLabel.isNotEmpty()
    val accent = when (state.currentPeriod.type) {
        PeriodInfo.PeriodType.PERIOD -> SubjectColors.colorFor(state.currentPeriod.subject)
        PeriodInfo.PeriodType.BREAK_TIME, PeriodInfo.PeriodType.ASSEMBLY -> SubjectColors.colorFor(state.currentPeriod.name)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = modifier, transformation = transformation) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 36.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (state.isViewingToday) "目前" else "課表預覽",
                        style = denseLabelSmall(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = state.currentPeriod.name,
                        style = denseTitleSmall(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    Text(
                        text = state.currentPeriod.subject,
                        style = denseBodySmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                if (isTimed) {
                    CountdownInfo(countdown, state.nextPeriod)
                }
            }
        }
    }
}

@Composable
private fun CountdownInfo(countdown: CountdownState, nextPeriod: PeriodInfo?) {
    // No rings, no countdown label: big MM:SS + what the next class is
    val nextLabel = nextPeriod?.let { next ->
        when (next.type) {
            PeriodInfo.PeriodType.PERIOD -> "下堂 ${parseSubject(next.subject)}"
            else -> "下堂 ${next.name}"
        }
    } ?: "已放學"
    Column(
        modifier = Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = countdown.countdownShort,
            style = denseTitleLarge(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = nextLabel,
            style = denseLabelSmall(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun NextClassCard(period: PeriodInfo, modifier: Modifier, transformation: SurfaceTransformation) {
    val dotColor = when (period.type) {
        PeriodInfo.PeriodType.PERIOD -> SubjectColors.colorFor(period.subject)
        else -> SubjectColors.colorFor(period.name)
    }
    Card(modifier = modifier, transformation = transformation) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "下一堂",
                        style = denseLabelSmall(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    text = "${period.name} · ${period.start}",
                    style = denseBodySmall(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = period.subject,
                    style = denseLabelSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun WeekOverviewEntryCard(
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        transformation = transformation
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "查看一週",
                        style = denseTitleSmall(),
                        maxLines = 1
                    )
                    Text(
                        text = "→",
                        style = denseTitleSmall(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "未來 7 個上課日概覽",
                    style = denseBodySmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BreakItemCard(
    item: ScheduleItem,
    isCurrent: Boolean,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val dotColor = SubjectColors.colorFor(item.displayName)
    // Breaks use compact height and vertically centered, keep left-aligned (not horizontal Center)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${item.start}  ${item.displayName}",
                style = denseLabelSmall(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun ScheduleItemCard(
    item: ScheduleItem,
    isCurrent: Boolean,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val dotColor = when (item.type) {
        ScheduleItemType.PERIOD -> SubjectColors.colorFor(item.subject)
        else -> SubjectColors.colorFor(item.displayName)
    }
    val isSingleLine = item.subject.isBlank() || item.subject == item.displayName
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        transformation = transformation
    ) {
        // Box fills Card's enforced 64dp minHeight to truly center vertically;
        // use CenterStart to keep left-aligned horizontally (user wants vertical centering, not horizontal) + weight to fill height
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isSingleLine) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${item.start}  ${item.displayName}",
                        style = denseLabelSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.start,
                        modifier = Modifier.width(42.dp),
                        style = denseLabelSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.displayName,
                            style = denseLabelSmall(),
                            color = if (item.type == ScheduleItemType.PERIOD) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Text(
                            text = item.subject,
                            style = denseBodySmall(),
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

private fun timeToMinutes(time: String): Int {
    if (time.isBlank()) return -1
    val parts = time.split(":")
    if (parts.size < 2) return -1
    val h = parts[0].toIntOrNull() ?: return -1
    val m = parts[1].toIntOrNull() ?: return -1
    return h * 60 + m
}

private fun mergeConsecutiveItems(items: List<ScheduleItem>): List<ScheduleItem> {
    if (items.isEmpty()) return items
    val merged = mutableListOf<ScheduleItem>()
    var current = items[0]
    for (i in 1 until items.size) {
        val next = items[i]
        // Only merge same type + same subject and contiguous time, skip assembly
        val canMerge = current.type == next.type &&
            current.type != ScheduleItemType.ASSEMBLY &&
            current.subject == next.subject &&
            current.subject.isNotBlank() &&
            current.end == next.start
        if (canMerge) {
            val newDisplayName = if (
                current.type == ScheduleItemType.PERIOD &&
                current.periodNumber != null && next.periodNumber != null
            ) {
                // Extract start number from current display (already may be range like "第1-2節")
                val startNum = current.periodNumber
                val endNum = next.periodNumber
                "第${startNum}-${endNum}節"
            } else {
                current.displayName
            }
            current = current.copy(
                displayName = newDisplayName,
                end = next.end,
                // Keep start periodNumber for future merging (e.g., 1-2 + 3 -> 1-3)
                // periodNumber stays as start's number
            )
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

private fun isCurrentMerged(item: ScheduleItem, state: HomeScheduleState): Boolean {
    // Prefer exact stableKey match for non-merged items
    if (item.stableKey == state.currentItemId) return true
    // For merged ranges, check if current period is inside the range
    val cur = state.currentPeriod
    if (cur.start.isBlank() || cur.end.isBlank() || item.start.isBlank() || item.end.isBlank()) return false
    val curStart = timeToMinutes(cur.start)
    val curEnd = timeToMinutes(cur.end)
    val itemStart = timeToMinutes(item.start)
    val itemEnd = timeToMinutes(item.end)
    if (curStart < 0 || curEnd < 0 || itemStart < 0 || itemEnd < 0) return false
    // Current period fully inside merged item range
    return curStart >= itemStart && curEnd <= itemEnd
}
