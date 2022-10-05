package com.example.server.meal

import android.util.Log
import com.example.server.meal.api.MealApi
import com.example.server.meal.pojo.MealResponse

class RemoteMealDataSourceImpl(private val api: MealApi) : RemoteMealDataSource {
    override suspend fun getMeals(year: Int, month: Int): MealResponse {
        return api.getMeals(year, month).apply {
            Log.d("RemoteMealDataSourceImpl", "meal $year $month: ${response.map { it.ymd }}")
        }
    }
}