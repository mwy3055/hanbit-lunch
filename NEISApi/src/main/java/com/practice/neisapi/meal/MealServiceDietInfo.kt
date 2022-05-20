package com.practice.neisapi.meal

import com.practice.neisapi.meal.pojo.MealResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface MealServiceDietInfo {

    @GET("mealServiceDietInfo")
    suspend fun getMealOfMonth(
        @Query("KEY") key: String = "f276e815f7554497bb3a4b8a697aaf9b",
        @Query("Type") type: String = "json",
        @Query("pIndex") pageIndex: Int = 1,
        @Query("pSize") pageSize: Int = 100,
        @Query("ATPT_OFCDC_SC_CODE") officeCode: String,
        @Query("SD_SCHUL_CODE") schoolCode: String,
        @Query("MLSV_YMD") mealYearMonth: String,
    ): MealResponse
}

const val seoulOfficeCode = "B10"
const val hanbitSchoolCode = "7010578"