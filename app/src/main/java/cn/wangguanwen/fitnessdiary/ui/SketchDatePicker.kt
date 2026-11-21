@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cn.wangguanwen.fitnessdiary.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun SketchDatePicker(onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val selectable = remember(today) { object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L)) <= today
        override fun isSelectableYear(year: Int) = year <= today.year
    } }
    val state = rememberDatePickerState(initialSelectedDateMillis = today.toEpochDay() * 86_400_000L,
        yearRange = 1900..today.year, selectableDates = selectable)
    val selected = state.selectedDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)) }
    DatePickerDialog(onDismissRequest = onDismiss, shape = SketchShape,
        colors = DatePickerDefaults.colors(containerColor = Paper.copy(alpha = .97f)),
        confirmButton = { TextButton(onClick = { selected?.let(onSelect) }, enabled = selected != null) { Text("记录这一天") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Paper.copy(alpha = .97f)),
            title = { Text("补记或查看过去的记录", Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp), color = Muted) },
            headline = { Text(selected?.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")) ?: "选择日期",
                Modifier.padding(start = 24.dp, bottom = 12.dp, end = 24.dp), style = MaterialTheme.typography.headlineSmall) })
    }
}
