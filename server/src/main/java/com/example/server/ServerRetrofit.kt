package com.example.server

import retrofit2.Converter
import retrofit2.Retrofit

object ServerRetrofit {
    fun getRetrofit(factory: Converter.Factory): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_URL)
        .addConverterFactory(factory)
        .build()

}