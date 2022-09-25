package com.practice.hanbitlunch.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.domain.date.DayType
import com.example.domain.date.calculateDayType
import com.example.domain.date.toKor
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.practice.hanbitlunch.theme.HanbitCalendarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*

@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    calendarState: CalendarState = rememberCalendarState(),
    onDateClick: (LocalDate) -> Unit = {},
    onSwiped: (YearMonth) -> Unit = {},
    isLight: Boolean = MaterialTheme.colors.isLight
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colors.surface)
    ) {
        CalendarDays(days = calendarDays(), isLight = isLight)
        SwipeableCalendarDates(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface),
            calendarState = calendarState,
            onDateClick = onDateClick,
            onSwiped = onSwiped,
            isLight = isLight
        )
    }
}

private fun calendarDays(): List<DayOfWeek> {
    val values = DayOfWeek.values().toList()
    Collections.rotate(values, 1)
    return values
}

@Composable
private fun CalendarDays(
    days: List<DayOfWeek>,
    modifier: Modifier = Modifier,
    isLight: Boolean = true,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier,
    ) {
        items(items = days, key = { it.ordinal }) {
            CalendarDay(day = it, isLight = isLight)
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun SwipeableCalendarDates(
    modifier: Modifier = Modifier,
    calendarState: CalendarState = rememberCalendarState(),
    onDateClick: (LocalDate) -> Unit = {},
    onSwiped: (YearMonth) -> Unit = {},
    isLight: Boolean = true,
) {
    // shows from a year ago to a year after
    val itemCount = 13
    val firstItemIndex = itemCount / 2
    val pagerState = rememberPagerState(initialPage = firstItemIndex)

    val currentYearMonth = LocalDate.now().let {
        YearMonth(it.year, it.monthValue)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
            val offset = pageIndex - firstItemIndex
            val (newYear, newMonth) = currentYearMonth.offset(offset)
            calendarState.apply {
                year = newYear
                month = newMonth
            }
            onSwiped(YearMonth(newYear, newMonth))
        }
    }

    HorizontalPager(
        count = itemCount,
        state = pagerState,
        modifier = modifier,
    ) { index ->
        val shownYearMonth = currentYearMonth.offset(index - firstItemIndex)
        CalendarDates(
            yearMonth = shownYearMonth,
            selectedDate = calendarState.selectedDate,
            onDateClick = {
                calendarState.selectedDate = it
                onDateClick(it)
            },
            isLight = isLight
        )
    }
}

/**
 * 달력 맨 윗 줄의 요일
 */
@Composable
private fun CalendarDay(
    day: DayOfWeek,
    modifier: Modifier = Modifier,
    isLight: Boolean = true,
) {
    CalendarElement(
        text = day.toKor(),
        modifier = modifier,
        textColor = day.color(isLight),
    )
}

private val WeekdayColorOnLight = Color(0xFF000000)
private val WeekDayColorOnDark = Color(0xFFFFFFFF)
private val SaturdayColor = Color(0xFF5151FF)
private val HolidayColor = Color(0xFFFF5F5F)

@Composable
private fun DayOfWeek.color(isLight: Boolean = true) = when (this) {
    DayOfWeek.SUNDAY -> HolidayColor
    DayOfWeek.SATURDAY -> SaturdayColor
    else -> if (isLight) WeekdayColorOnLight else WeekDayColorOnDark
}

@Composable
private fun CalendarDates(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    onDateClick: (LocalDate) -> Unit = {},
    isLight: Boolean = true,
) {
    val dates = CalendarRow.getInstance(yearMonth).dates
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier,
    ) {
        items(items = dates, key = { it.hashCode() }) { date ->
            CalendarDate(
                date = date,
                onClick = { clickedDate ->
                    onDateClick(clickedDate)
                },
                currentMonth = yearMonth.month,
                isSelected = (date == selectedDate),
                isLight = isLight
            )
        }
    }
}

/**
 * 실제 날짜
 */
@Composable
private fun CalendarDate(
    date: LocalDate,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    currentMonth: Int = date.monthValue,
    isSelected: Boolean = false,
    isLight: Boolean = true
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colors.secondary else MaterialTheme.colors.surface,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing,
        )
    )
    val text = if (date == LocalDate.MAX) "" else date.dayOfMonth.toString()
    CalendarElement(
        text = text,
        modifier = modifier
            .clip(shape = CircleShape)
            .background(color = background)
            .clickable { onClick(date) },
        textColor = date.color(
            isSelected = isSelected,
            isLight = isLight,
            currentMonth = currentMonth
        ),
        textStyle = MaterialTheme.typography.body2,
    )
}

@Composable
private fun CalendarElement(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    textStyle: TextStyle = MaterialTheme.typography.body1,
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .then(modifier)
            .aspectRatio(1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            color = textColor,
            style = textStyle,
        )
    }
}

private val WeekDayOverMonthColor = Color(0xFF999999)

@Composable
private fun LocalDate.color(
    isSelected: Boolean = false,
    isLight: Boolean = false,
    currentMonth: Int = this.monthValue
) = when (calculateDayType(currentMonth)) {
    DayType.Weekday -> if (isLight || isSelected) WeekdayColorOnLight else WeekDayColorOnDark
    DayType.WeekdayOverMonth -> WeekDayOverMonthColor
    DayType.Saturday -> SaturdayColor
    DayType.SaturdayOverMonth -> Color(0xFF9999FF)
    DayType.Holiday -> HolidayColor
    DayType.HolidayOverMonth -> Color(0xFFFF9999)
}

@Preview(showBackground = true)
@Composable
private fun CalendarDayPreview() {
    HanbitCalendarTheme(darkTheme = true) {
        CalendarDay(
            day = DayOfWeek.MONDAY,
            modifier = Modifier
                .size(50.dp)
                .background(MaterialTheme.colors.surface),
            isLight = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarDatePreview() {
    HanbitCalendarTheme {
        CalendarDate(
            date = LocalDate.of(2022, 8, 12),
            onClick = {},
            modifier = Modifier.size(50.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarDatePreview_Selected() {
    HanbitCalendarTheme {
        CalendarDate(
            date = LocalDate.of(2022, 8, 17),
            onClick = {},
            isSelected = true,
            modifier = Modifier.size(50.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarPreview() {
    val calendarState = rememberCalendarState(
        year = 2022,
        month = 8,
        selectedDate = LocalDate.of(2022, 8, 12)
    )
    HanbitCalendarTheme(darkTheme = true) {
        Column {
            Calendar(calendarState = calendarState)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SwipeableCalendarDatesPreview() {
    HanbitCalendarTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            SwipeableCalendarDates(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colors.surface),
                calendarState = rememberCalendarState()
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}