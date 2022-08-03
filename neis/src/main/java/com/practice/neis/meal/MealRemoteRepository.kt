package com.practice.neis.meal

import com.practice.neis.meal.pojo.MealModel

class MealRemoteRepository(private val mealRemoteDataSource: MealRemoteDataSource) {

    suspend fun getMealData(year: Int, month: Int): List<MealModel> {
        return try {
            mealRemoteDataSource.getMeals(year, month)
        } catch (e: MealDataSourceException) {
            throw MealRemoteRepositoryException(e.message)
        }
    }

}

class MealRemoteRepositoryException(override val message: String) : Exception(message)