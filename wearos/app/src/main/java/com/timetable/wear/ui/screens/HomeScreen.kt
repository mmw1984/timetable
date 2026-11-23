package com.timetable.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timetable.shared.data.local.resolveDenseLayout
import com.timetable.shared.ui.theme.SubjectColors
import com.timetable.wear.ui.theme.denseBodySmall
import com.timetable.wear.ui.theme.denseLabelSmall
import com.timetable.wear.ui.theme.denseLabelSmall11
import com.timetable.wear.ui.theme.denseTitleLarge
import com.timetable.wear.ui.theme.denseTitleSmall
import androidx.hilt.navigation.compose.hiltViewModel
import com.timetable.shared.ui.HomeViewModel
import com.timetable.shared.engine.isCurrentMerged
import com.timetable.shared.engine.mergeConsecutiveItems
import com.timetable.shared.engine.timeToMinutes
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
import com.timetable.shared.util.parseSubject
import com.timetable.shared.data.model.PeriodInfo
import com.timetable.shared.data.model.ScheduleItem
import com.timetable.shared.data.model.ScheduleItemType
import com.timetable.shared.data.model.TimetableType
import com.timetable.shared.engine.CountdownState
import com.timetable.shared.engine.HomeScheduleState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    onOpenWeek: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val denseMode by viewModel.denseMode.collectAsStateWithLifecycle()
    val mergeConsecutive by viewModel.mergeConsecutive.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val useDense = remember(denseMode, configuration.smallestScreenWidthDp, configuration.screenHeightDp) {
        resolveDenseLayout(denseMode, configuration.smallestScreenWidthDp, configuration.screenHeightDp)
    }
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(Unit) { viewModel.start() }

    // Signal time-to-full-display once the bundled/cached data has rendered, so
    // baseline-profile generation covers the full startup journey.
    ReportDrawnWhen { !scheduleState.isLoading }

    val mergedItems = remember(scheduleState.scheduleItems, mergeConsecutive) {
        if (mergeConsecutive) mergeConsecutiveItems(scheduleState.scheduleItems)
        else scheduleState.scheduleItems
    }
    // Merged 連堂 display name for the current card ("第1-2節"); falls back
    // to the single period name when nothing is merged.
    val mergedCurrentName = remember(mergedItems, scheduleState) {
        mergedItems.firstOrNull { isCurrentMerged(it, scheduleState) }?.displayName
            ?: scheduleState.currentPeriod.name
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
                if (useDense) {
                    DenseDateNavRow(
                        state = scheduleState,
                        onPrevious = viewModel::prevDay,
                        onNext = viewModel::nextDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                    )
                } else {
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
                    if (useDense) {
                        item {
                            DenseCurrentNextCard(
                                state = scheduleState,
                                currentName = mergedCurrentName,
                                countdownFlow = viewModel.countdownState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                            )
                        }
                    } else {
                        item {
                            CurrentClassCard(
                                state = scheduleState,
                                currentName = mergedCurrentName,
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
                }

                item {
                    if (useDense) {
                        DenseWeekRow(
                            onClick = onOpenWeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        )
                    } else {
                        WeekOverviewEntryCard(
                            onClick = onOpenWeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    }
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
                    val rowModifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        .padding(vertical = 3.dp)
                    if (useDense) {
                        if (isBreak) {
                            DenseBreakRow(
                                item = item,
                                isCurrent = isCurrent,
                                modifier = rowModifier
                            )
                        } else {
                            DenseScheduleRow(
                                item = item,
                                isCurrent = isCurrent,
                                modifier = rowModifier
                            )
                        }
                    } else if (isBreak) {
                        BreakItemCard(
                            item = item,
                            isCurrent = isCurrent,
                            modifier = rowModifier,
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    } else {
                        ScheduleItemCard(
                            item = item,
                            isCurrent = isCurrent,
                            modifier = rowModifier,
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
    currentName: String,
    countdownFlow: StateFlow<CountdownState>,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
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
                        text = currentName,
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
                if (state.isViewingToday) {
                    CountdownSlot(countdownFlow, state.nextPeriod)
                }
            }
        }
    }
}

@Composable
private fun CountdownSlot(
    countdownFlow: StateFlow<CountdownState>,
    nextPeriod: PeriodInfo?
) {
    // Isolated so the 1-second ticker only recomposes this small slot,
    // not the whole CurrentClassCard.
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    if (countdown.countdownLabel.isNotEmpty()) {
        CountdownInfo(countdown, nextPeriod)
    }
}

@Composable
private fun CountdownInfo(countdown: CountdownState, nextPeriod: PeriodInfo?) {
    // No rings, no countdown label: big MM:SS + what the next class is.
    // "下堂" is for classes only: breaks/lunch/assembly show their own name.
    val nextLabel = nextPeriod?.let { next ->
        when (next.type) {
            PeriodInfo.PeriodType.PERIOD -> "下堂 ${parseSubject(next.subject)}"
            else -> next.name.ifBlank { "休息" }
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
                        // "下一堂" is for classes only: breaks show name/start below.
                        if (period.type == PeriodInfo.PeriodType.PERIOD) "下一堂" else "接下來",
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
            .heightIn(min = 32.dp)
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
                        style = denseLabelSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    Spacer(Modifier.width(8.dp))
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dense layout variants (see Settings → 版面）. Old composables above are
 * untouched so 設定 → 標準 restores the previous UI byte-for-byte.
 */

@Composable
private fun DenseCurrentNextCard(
    state: HomeScheduleState,
    currentName: String,
    countdownFlow: StateFlow<CountdownState>,
    modifier: Modifier
) {
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    val isTimed = state.isViewingToday && countdown.countdownLabel.isNotEmpty()
    val accent = when (state.currentPeriod.type) {
        PeriodInfo.PeriodType.PERIOD -> SubjectColors.colorFor(state.currentPeriod.subject)
        PeriodInfo.PeriodType.BREAK_TIME, PeriodInfo.PeriodType.ASSEMBLY -> SubjectColors.colorFor(state.currentPeriod.name)
        else -> MaterialTheme.colorScheme.primary
    }
    val nextLabel = state.nextPeriod?.let { next ->
        when (next.type) {
            PeriodInfo.PeriodType.PERIOD -> "下堂 ${parseSubject(next.subject)} ${next.start}"
            else -> "下堂 ${next.name} ${next.start}"
        }
    } ?: "已放學"
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
            Column(verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "目前 $currentName",
                        style = denseTitleSmall(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                    if (isTimed) {
                        Text(
                            text = countdown.countdownShort,
                            style = denseTitleSmall(),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = "${state.currentPeriod.subject} · $nextLabel",
                    style = denseBodySmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }

@Composable
private fun DenseDateNavRow(
    state: HomeScheduleState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        DateNavigationControls(state, onPrevious, onNext)
    }
}

@Composable
private fun DenseWeekRow(
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "查看一週",
                    style = denseTitleSmall(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = "未來 7 個上課日概覽",
                    style = denseBodySmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
            Text(
                text = "→",
                style = denseTitleSmall(),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DenseScheduleRow(
    item: ScheduleItem,
    isCurrent: Boolean,
    modifier: Modifier
) {
    val dotColor = when (item.type) {
        ScheduleItemType.PERIOD -> SubjectColors.colorFor(item.subject)
        else -> SubjectColors.colorFor(item.displayName)
    }
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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
                text = item.start,
                style = denseLabelSmall(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.displayName,
                    style = denseLabelSmall(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = item.subject,
                    style = denseBodySmall(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DenseBreakRow(
    item: ScheduleItem,
    isCurrent: Boolean,
    modifier: Modifier
) {
    val dotColor = SubjectColors.colorFor(item.displayName)
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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
