package ru.ordercontrol.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.R
import ru.ordercontrol.domain.WorkSchedule
import ru.ordercontrol.ui.DisplayFormat
import ru.ordercontrol.ui.update.UpdateSection
import ru.ordercontrol.widget.WidgetRefresh

private val ALL_DAYS = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var startMinute by remember { mutableIntStateOf(WorkSchedule.DEFAULT_START_MINUTE) }
    var endMinute by remember { mutableIntStateOf(WorkSchedule.DEFAULT_END_MINUTE) }
    var days by remember { mutableStateOf(WorkSchedule.DEFAULT_DAYS) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val invalidDaysMessage = stringResource(R.string.settings_invalid)

    LaunchedEffect(Unit) {
        val schedule = AppGraph.settings.currentSchedule()
        startMinute = schedule.startMinute
        endMinute = schedule.endMinute
        days = schedule.days
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimeField(
                label = stringResource(R.string.settings_from),
                minuteOfDay = startMinute,
                onChange = { startMinute = it },
                modifier = Modifier.weight(1f),
            )
            TimeField(
                label = stringResource(R.string.settings_to),
                minuteOfDay = endMinute,
                onChange = { endMinute = it },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.settings_days),
            style = MaterialTheme.typography.titleMedium,
        )

        ALL_DAYS.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        days = if (day in days) days - day else days + day
                        error = null
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = day in days,
                    onCheckedChange = { checked ->
                        days = if (checked) days + day else days - day
                        error = null
                    },
                )
                Text(
                    text = stringResource(day.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        UpdateSection()

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (days.isEmpty()) {
                    error = invalidDaysMessage
                    return@Button
                }
                scope.launch {
                    AppGraph.settings.setSchedule(
                        WorkSchedule(
                            startMinute = startMinute,
                            endMinute = endMinute,
                            days = days,
                        ),
                    )
                    WidgetRefresh.updateAll(context)
                    onBack()
                }
            },
            enabled = loaded,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onChange(hour * 60 + minute) },
                WorkSchedule.hourOf(minuteOfDay),
                WorkSchedule.minuteOf(minuteOfDay),
                true,
            ).show()
        },
        modifier = modifier,
    ) {
        Text("$label ${DisplayFormat.minuteOfDay(minuteOfDay)}")
    }
}

private fun DayOfWeek.labelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_monday
    DayOfWeek.TUESDAY -> R.string.day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday
    DayOfWeek.THURSDAY -> R.string.day_thursday
    DayOfWeek.FRIDAY -> R.string.day_friday
    DayOfWeek.SATURDAY -> R.string.day_saturday
    DayOfWeek.SUNDAY -> R.string.day_sunday
}
