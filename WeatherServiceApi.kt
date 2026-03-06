package com.example.d1

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherServiceApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang") lang: String
    ): Response<WeatherResponse>

    // YENİ: Koordinat ile arama (Bunu ekle)
    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCoord(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang") lang: String
    ): Response<WeatherResponse>

    // 1. Şehir ismi ile 5 günlük tahmin
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang") lang: String
    ): Response<ForecastResponse> // Dikkat: Dönüş tipi ForecastResponse oldu!

    // 2. Koordinat ile 5 günlük tahmin
    @GET("data/2.5/forecast")
    suspend fun getForecastByCoord(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang") lang: String
    ): Response<ForecastResponse>
}


