package com.timetable.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetable.shared.data.model.PeriodInfo
import com.timetable.shared.data.model.ScheduleItem
import com.timetable.shared.data.model.ScheduleItemType
import com.timetable.shared.engine.HomeScheduleState
import com.timetable.shared.engine.isCurrentMerged
import com.timetable.shared.engine.mergeConsecutiveItems
import com.timetable.shared.ui.HomeViewModel
import com.timetable.shared.ui.theme.SubjectColors
import com.timetable.shared.util.isActiveTimedPeriod

/** Breakpoint: >= 840dp lays the summary card and the schedule side by side. */
private val ExpandedLayoutMinWidth = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val mergeConsecutive by viewModel.mergeConsecutive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    val displayItems = remember(scheduleState.scheduleItems, mergeConsecutive) {
        if (mergeConsecutive) mergeConsecutiveItems(scheduleState.scheduleItems)
        else scheduleState.scheduleItems
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Column {
                    Text("時間表", fontWeight = FontWeight.Bold)
                    Text(
                        text = scheduleState.selectedDateDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.prevDay() },
                    enabled = scheduleState.canNavigatePrevious
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一個上課日")
                }
                IconButton(
                    onClick = { viewModel.nextDay() },
                    enabled = scheduleState.canNavigateNext
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一個上課日")
                }
                IconButton(onClick = { viewModel.refresh() }, enabled = !isRefreshing) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新整理")
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (scheduleState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val expanded = maxWidth >= ExpandedLayoutMinWidth
                    if (expanded) {
                        // Tablet / unfolded: summary column beside the schedule list.
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            Column(
                                Modifier
                                    .weight(0.42f)
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DayMetaChips(scheduleState)
                                CurrentPeriodCard(scheduleState, countdown.countdown, countdown.countdownLabel, countdown.countdownProgress)
                                NextPeriodCard(scheduleState.nextPeriod)
                            }
                            Spacer(Modifier.width(16.dp))
                            ScheduleList(
                                displayItems = displayItems,
                                scheduleState = scheduleState,
                                modifier = Modifier.weight(0.58f)
                            )
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            item { DayMetaChips(scheduleState) }
                            item {
                                CurrentPeriodCard(
                                    scheduleState,
                                    countdown.countdown,
                                    countdown.countdownLabel,
                                    countdown.countdownProgress
                                )
                            }
                            item { NextPeriodCard(scheduleState.nextPeriod) }
                            item {
                                Text(
                                    "全日課表",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(displayItems, key = { it.stableKey }) { item ->
                                ScheduleRow(item, isCurrentMerged(item, scheduleState))
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMetaChips(state: HomeScheduleState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.dayCycle?.let { AssistChip(onClick = {}, label = { Text("Day $it") }) }
        if (state.timetableType.displayText.isNotBlank() &&
            state.timetableType != com.timetable.shared.data.model.TimetableType.NORMAL
        ) {
            AssistChip(onClick = {}, label = { Text(state.timetableType.displayText) })
        }
    }
}

@Composable
private fun CurrentPeriodCard(
    state: HomeScheduleState,
    countdown: String,
    countdownLabel: String,
    countdownProgress: Float
) {
    val current = state.currentPeriod
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (current.name.isNotBlank()) current.name else "現在",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = current.subject,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (current.start.isNotBlank() && current.end.isNotBlank()) {
                Text(
                    text = "${current.start} – ${current.end}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            if (state.isViewingToday && isActiveTimedPeriod(current) && countdownLabel.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = countdownLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                LinearProgressIndicator(
                    progress = { countdownProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NextPeriodCard(next: PeriodInfo?) {
    if (next == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "下堂",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = next.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (next.start.isNotBlank()) {
                Text(
                    text = next.start,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ScheduleList(
    displayItems: List<ScheduleItem>,
    scheduleState: HomeScheduleState,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "全日課表",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        items(displayItems, key = { it.stableKey }) { item ->
            ScheduleRow(item, isCurrentMerged(item, scheduleState))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ScheduleRow(item: ScheduleItem, isCurrent: Boolean) {
    val subjectColor = SubjectColors.colorFor(item.subject)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isCurrent) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                    drawCircle(color = subjectColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (item.type == ScheduleItemType.PERIOD) item.subject else item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = "${item.start}–${item.end}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
