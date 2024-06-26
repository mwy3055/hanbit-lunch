package com.practice.main.main

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsk.ktx.date.Date
import com.practice.combine.LoadMonthlyDataUseCase
import com.practice.combine.MonthlyData
import com.practice.designsystem.calendar.core.YearMonth
import com.practice.designsystem.calendar.core.getFirstWeekday
import com.practice.designsystem.calendar.core.yearMonth
import com.practice.domain.Memo
import com.practice.domain.School
import com.practice.domain.meal.Meal
import com.practice.domain.schedule.Schedule
import com.practice.firebase.BlindarFirebase
import com.practice.firebase.BlindarUserStatus
import com.practice.main.main.state.DailyData
import com.practice.main.main.state.MainUiMode
import com.practice.main.main.state.MainUiState
import com.practice.main.main.state.toUiMode
import com.practice.main.state.UiMeals
import com.practice.main.state.UiMemo
import com.practice.main.state.UiMemos
import com.practice.main.state.UiSchedules
import com.practice.main.state.toMealUiState
import com.practice.main.state.toMemo
import com.practice.main.state.toUiMemo
import com.practice.main.state.toUiSchedule
import com.practice.preferences.PreferencesRepository
import com.practice.util.date.DateUtil
import com.practice.work.BlindarWorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// TODO: 빠른 보기에서 영양/메모 팝업 대신 내비게이션 화면으로 가도록 수정
// TODO: UiState 구조를 Flow로 싹 뜯어고치기 (메모 화면에서 돌아왔을 때 메모가 업데이트되지 않는 문제 해결)

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val loadMonthlyDataUseCase: LoadMonthlyDataUseCase,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState: MutableState<MainUiState>
    val uiState: State<MainUiState>
        get() = _uiState

    // For internal use only
    private var state: MainUiState
        get() = _uiState.value
        set(value) {
            _uiState.value = value
        }

    private val selectedDateFlow: MutableStateFlow<Date>

    private var loadMonthlyDataJob: Job? = null
    private var initialWorkCount: Int? = null

    init {
        val current = Date.now()
        val userId = getCurrentUserId()
        _uiState = mutableStateOf(MainUiState.EMPTY.copy(userId = userId))
        selectedDateFlow = MutableStateFlow(current)
    }

    private fun getCurrentUserId(): String {
        return when (val currentlyLoggedInUser = BlindarFirebase.getBlindarUser()) {
            is BlindarUserStatus.LoginUser -> currentlyLoggedInUser.user.uid
            is BlindarUserStatus.NotLoggedIn -> ""
        }
    }

    /**
     * init 블럭에서 실행하지 않은 이유는 [IllegalStateException]이 발생하기 때문이다.
     * 아직 UI에 반영되지 않은 값을 참조하기 때문에 예외가 발생한다.
     */
    fun onLaunch() {
        viewModelScope.launch {
            collectPreferences()
        }
    }

    /**
     * Kotlin Flow의 combine 함수를 본따 작성했다.
     */
    private fun updateUiState(
        userId: String = state.userId,
        yearMonth: YearMonth = state.yearMonth,
        selectedDate: Date = state.selectedDate,
        monthlyData: List<DailyData> = state.monthlyDataState,
        selectedMealIndex: Int = state.selectedMealIndex,
        isLoading: Boolean = state.isLoading,
        selectedSchool: School = state.selectedSchool,
        isMealDialogVisible: Boolean = state.isMealDialogVisible,
        isScheduleDialogVisible: Boolean = state.isScheduleDialogVisible,
        mainUiMode: MainUiMode = state.mainUiMode
    ) {
        val isCollectNeeded =
            userId != state.userId || yearMonth != state.yearMonth || selectedSchool != state.selectedSchool || loadMonthlyDataJob == null
        synchronized(state) {
            state = state.copy(
                userId = userId,
                year = yearMonth.year,
                month = yearMonth.month,
                monthlyDataState = monthlyData,
                selectedMealIndex = selectedMealIndex,
                selectedDate = selectedDate,
                isLoading = isLoading,
                selectedSchool = selectedSchool,
                isMealDialogVisible = isMealDialogVisible,
                isScheduleDialogVisible = isScheduleDialogVisible,
                mainUiMode = mainUiMode,
            )
        }
        if (isCollectNeeded) {
            startCollectMonthlyDataJob(userId, selectedSchool.schoolCode, yearMonth)
        }
    }

    fun onDateClick(clickedDate: Date) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "clicked date: $clickedDate")
        updateUiState(
            yearMonth = clickedDate.yearMonth,
            selectedDate = clickedDate,
            selectedMealIndex = 0,
        )
    }

    fun onRefreshIconClick(context: Context) {
        BlindarWorkManager.setOneTimeFetchDataWork(
            context = context,
            clearMealDatabase = true,
            clearScheduleDatabase = true,
        )
    }

    private fun startCollectMonthlyDataJob(userId: String, schoolCode: Int, yearMonth: YearMonth) {
        loadMonthlyDataJob?.cancel()

        val (queryYear, queryMonth) = yearMonth
        loadMonthlyDataJob = viewModelScope.launch {
            loadMonthlyDataUseCase.loadData(userId, schoolCode, queryYear, queryMonth)
                .collectLatest {
                    updateUiState(monthlyData = parseDailyState(it))
                }
        }
    }

    fun onMainScreenModeSet(mainUiMode: MainUiMode) {
        mainUiMode.toMainScreenMode()?.let { mainScreenMode ->
            viewModelScope.launch {
                preferencesRepository.updateMainScreenMode(mainScreenMode)
            }
        }
    }

    fun onMealTimeClick(index: Int) {
        updateUiState(selectedMealIndex = index)
    }

    private fun parseDailyState(monthlyData: MonthlyData): List<DailyData> {
        val allDates = mutableSetOf<Date>().apply {
            addAll(monthlyData.meals.map { Date(it.year, it.month, it.day) })
            addAll(monthlyData.schedules.map { Date(it.year, it.month, it.day) })
            addAll(monthlyData.memos.map { Date(it.year, it.month, it.day) })
        }
        val newDailyData = allDates.map { date ->
            val uiMeals = monthlyData.getMeals(date)
            val uiSchedules = monthlyData.getSchedule(date)
            val uiMemos = monthlyData.getMemo(date)
            DailyData(
                schoolCode = monthlyData.schoolCode,
                date = date,
                uiMeals = uiMeals,
                uiSchedules = uiSchedules,
                uiMemos = uiMemos,
            )
        }.sorted()
        return newDailyData
    }

    fun onSwiped(yearMonth: YearMonth) = viewModelScope.launch {
        if (yearMonth != state.yearMonth) {
            val firstWeekday = yearMonth.getFirstWeekday()
            updateUiState(yearMonth = yearMonth, selectedDate = firstWeekday)
        }
    }

    private suspend fun collectPreferences() {
        preferencesRepository.userPreferencesFlow.collectLatest {
            if (initialWorkCount == null) {
                initialWorkCount = it.runningWorksCount
            }

            updateUiState(
                isLoading = (it.runningWorksCount != initialWorkCount),
                selectedSchool = School(
                    name = it.schoolName,
                    schoolCode = it.schoolCode,
                ),
                mainUiMode = it.mainScreenMode.toUiMode(),
            )
        }
    }

    fun getContentDescription(date: Date): String {
        val dailyState = state.monthlyDataState.find { it.date == date }

        val isSelectedString = if (date == state.selectedDate) "선택됨" else ""
        val isTodayString = if (date == DateUtil.today()) "오늘" else ""
        val dailyStateString = if (dailyState != null) {
            "식단: ${dailyState.uiMeals.description}\n학사일정:${dailyState.uiSchedules.description}\n메모: ${dailyState.uiMemos.description}"
        } else {
            ""
        }

        return listOf(isSelectedString, isTodayString, dailyStateString)
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    fun getClickLabel(date: Date): String =
        if (date == state.selectedDate) "" else "식단 및 학사일정 보기"

    fun onMealDialog() {
        updateUiState(isMealDialogVisible = true)
    }

    fun onMealDialogClose() {
        updateUiState(isMealDialogVisible = false)
    }

    fun onScheduleDialogOpen() {
        updateUiState(isScheduleDialogVisible = true)
    }

    fun onScheduleDialogClose() {
        updateUiState(isScheduleDialogVisible = false)
    }

    companion object {
        private const val TAG = "MainScreenViewModel"
    }
}

private fun MonthlyData.getMeals(date: Date): UiMeals {
    val meals = meals.filter { it.dateEquals(date) }.map { it.toMealUiState() }
    Log.d("MainScreenModel", "$date: $meals")
    return UiMeals(meals)
}

private fun MonthlyData.getSchedule(date: Date): UiSchedules {
    return try {
        val uiSchedules = schedules.filter { it.dateEquals(date) }.map { it.toUiSchedule() }
        UiSchedules(
            date = date,
            uiSchedules = uiSchedules.toPersistentList(),
        )
    } catch (e: NoSuchElementException) {
        UiSchedules.EmptyUiSchedules
    }
}

private fun MonthlyData.getMemo(date: Date): UiMemos {
    return try {
        val memos = memos.filter { it.dateEquals(date) }.map { it.toUiMemo() }
        UiMemos(
            date = date,
            memos = memos.toPersistentList(),
        )
    } catch (e: NoSuchElementException) {
        UiMemos.EmptyUiMemos
    }
}

private fun Meal.dateEquals(date: Date) =
    this.year == date.year && this.month == date.month && this.day == date.dayOfMonth

private fun Schedule.dateEquals(date: Date) =
    this.year == date.year && this.month == date.month && this.day == date.dayOfMonth

private fun Memo.dateEquals(date: Date) =
    this.year == date.year && this.month == date.month && this.day == date.dayOfMonth