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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timetable.shared.data.repository.TimetableRepository
import com.timetable.shared.engine.TimetableEngine
import com.timetable.shared.ui.theme.SubjectColors
import com.timetable.shared.util.parseSubject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val ExpandedGridMinWidth = 840.dp

@HiltViewModel
class WeekGridViewModel @Inject constructor(
    repository: TimetableRepository,
    engine: TimetableEngine
) : ViewModel() {
    val data = repository.data

    init {
        viewModelScope.launch { engine.ensureReady() }
    }
}

/**
 * Full Day 1–6 subject grid. Compact screens page through one day at a time;
 * medium/expanded screens (tablet, unfolded foldable, landscape) show the
 * whole grid side by side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    modifier: Modifier = Modifier,
    viewModel: WeekGridViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d/M", Locale.TAIWAN) }

    // Next upcoming date for each Day cycle, for column headers.
    val nextDateForDay = remember(data.dayRotation, today) {
        data.dayRotation.entries
            .mapNotNull { (dateStr, day) -> runCatching { LocalDate.parse(dateStr) to day }.getOrNull() }
            .filter { it.first >= today }
            .sortedBy { it.first }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, dates) -> dates.first() }
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = { Text("本週課表", fontWeight = FontWeight.Bold) }
        )
        if (data.subjectSchedule.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxPeriods = data.subjectSchedule.values.maxOfOrNull { it.keys.maxOrNull() ?: 0 } ?: 0
            if (maxWidth >= ExpandedGridMinWidth) {
                FullWeekGrid(data.subjectSchedule, nextDateForDay, dateFormatter, maxPeriods)
            } else {
                DayTabsGrid(data.subjectSchedule, nextDateForDay, dateFormatter, maxPeriods)
            }
        }
    }
}

@Composable
private fun FullWeekGrid(
    subjectSchedule: Map<Int, Map<Int, String>>,
    nextDateForDay: Map<Int, LocalDate>,
    dateFormatter: DateTimeFormatter,
    maxPeriods: Int
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row {
                Spacer(Modifier.width(44.dp))
                for (day in 1..6) {
                    DayHeaderCell(
                        day = day,
                        date = nextDateForDay[day],
                        dateFormatter = dateFormatter,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        items(maxPeriods) { index ->
            val period = index + 1
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "P$period",
                    modifier = Modifier
                        .width(44.dp)
                        .align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                for (day in 1..6) {
                    SubjectCell(
                        subject = subjectSchedule[day]?.get(period),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayTabsGrid(
    subjectSchedule: Map<Int, Map<Int, String>>,
    nextDateForDay: Map<Int, LocalDate>,
    dateFormatter: DateTimeFormatter,
    maxPeriods: Int
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize()) {
    SecondaryTabRow(selectedTabIndex = selectedDay - 1) {
        for (day in 1..6) {
            Tab(
                selected = selectedDay == day,
                onClick = { selectedDay = day },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Day $day")
                        nextDateForDay[day]?.let {
                            Text(
                                dateFormatter.format(it),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        items(maxPeriods) { index ->
            val period = index + 1
            val subject = subjectSchedule[selectedDay]?.get(period)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "P$period",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = subject ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (subject == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            SubjectColors.colorFor(subject)
                        }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
}

@Composable
private fun DayHeaderCell(
    day: Int,
    date: LocalDate?,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Day $day",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = date?.let { dateFormatter.format(it) } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubjectCell(subject: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(horizontal = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (subject == null) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = subject?.let { parseSubject(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (subject == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    SubjectColors.colorFor(subject)
                },
                maxLines = 1
            )
            if (subject != null && subject != parseSubject(subject)) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
