package com.example.d1

import com.google.gson.annotations.SerializedName

// Tüm değişkenlerin yanına '?' ekledik, böylece veri eksik gelse bile çökmez.
data class ForecastResponse(
    val list: List<ForecastItem>? = emptyList(),
    val city: CityInfo? = null
)

data class ForecastItem(
    val dt: Long? = 0L,
    val main: Main? = null, // Main sınıfı zaten WeatherResponse içinde tanımlı
    val weather: List<Weather>? = emptyList(), // Weather sınıfı WeatherResponse içinde tanımlı
    val wind: Wind? = null,
    @SerializedName("dt_txt")
    val dtTxt: String? = "" // Tarih boş gelirse çökmesin
)

data class CityInfo(
    val name: String? = null,
    val country: String? = null,
    val sunrise: Long? = 0L,
    val sunset: Long? = 0L
)