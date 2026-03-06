package com.example.d1

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        // Servis veriyi çekip kaydettikten sonra bu sinyali gönderir
        const val ACTION_WIDGET_UPDATE = "com.example.d1.ACTION_WIDGET_UPDATE"

        // Widget üzerindeki yenileme butonuna basılınca
        private const val ACTION_WIDGET_REFRESH_CLICK = "com.example.d1.ACTION_WIDGET_REFRESH_CLICK"

        private const val PREFS = "weather_prefs"
        private const val PREF_KEY_LAST = "last_weather"
        private const val PREF_KEY_CITY = "current_city"
        // Zaman ve Gece/Gündüz bilgisini okumak için anahtarlar
        private const val PREF_KEY_LAST_UPDATE = "last_update_millis"
        private const val PREF_KEY_LAST_IS_NIGHT = "last_is_night"
    }

    // 1. SİSTEM OTOMATİK GÜNCELLEMESİ (30 Dakikada bir çalışır)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Süre dolunca servise "Git yeni veri al" diyoruz
        triggerServiceUpdate(context)
    }

    // 2. Sinyalleri Yakala
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        // A) Servisten "Veri Hazır" sinyali geldiğinde -> Arayüzü, SAATİ ve İKONU Güncellenecek
        if (ACTION_WIDGET_UPDATE == action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            // Tüm widgetları yeni verilerle çiz
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
        // B) Kullanıcı butona bastığında -> Servisi Manuel Tetikle
        else if (ACTION_WIDGET_REFRESH_CLICK == action) {
            triggerServiceUpdate(context)
        }
    }

    // Servisi başlatan yardımcı fonksiyon
    private fun triggerServiceUpdate(context: Context) {
        val serviceIntent = Intent(context, WeatherService::class.java).apply {
            this.action = WeatherService.ACTION_REFRESH_NOW
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Widget Arayüzünü Çizen Fonksiyon
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Verileri Çek
        val fullText = prefs.getString(PREF_KEY_LAST, "") ?: ""
        val city = prefs.getString(PREF_KEY_CITY, "Şehir?") ?: "Şehir?"
        val lastUpdateMillis = prefs.getLong(PREF_KEY_LAST_UPDATE, 0L)
        // Gece mi Gündüz mü bilgisini çek (Varsayılan: false/gündüz)
        val isNight = prefs.getBoolean(PREF_KEY_LAST_IS_NIGHT, false)

        // Metni temizle (Şehir ismini ayırıp sadece dereceyi al)
        val weatherOnlyText = if (fullText.contains("\n")) {
            fullText.substringAfter("\n").trim()
        } else {
            fullText
        }

        val parts = weatherOnlyText.split("•")
        var tempText = ""
        var descText = ""

        if (parts.size >= 3) {
            tempText = parts[0].replace("🌡", "").trim()
            descText = parts[2].trim()
        } else {
            if (fullText.isBlank()) {
                tempText = "--"
                descText = "Yükleniyor..."
            } else {
                tempText = fullText
            }
        }

        val views = RemoteViews(context.packageName, R.layout.weather_widget_layout)

        views.setTextViewText(R.id.widget_tv_city, city)
        views.setTextViewText(R.id.widget_tv_temp, tempText)
        views.setTextViewText(R.id.widget_tv_desc, descText)

        // SAATİ FORMATLA VE YAZDIR
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = if (lastUpdateMillis > 0) sdf.format(Date(lastUpdateMillis)) else "--:--"
        views.setTextViewText(R.id.widget_tv_last_update, "Son: $timeStr")

        // İKONU SEÇ (Gece/Gündüz bilgisine göre)
        val iconRes = getWeatherIconResource(weatherOnlyText, isNight)
        views.setImageViewResource(R.id.widget_img_icon, iconRes)

        // Tıklama 1: Uygulamayı Aç (Ana Gövdeye Tıklayınca)
        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Tıklama 2: Manuel Yenileme Butonu (Eğer layout'unda buton varsa)
        val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_REFRESH_CLICK
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // İkon Seçme Mantığı (Gece/Gündüz destekli)
    private fun getWeatherIconResource(text: String, isNight: Boolean): Int {
        val t = text.lowercase(Locale.getDefault())

        // Eğer gece ise
        if (isNight) {
            return when {
                t.contains("kar") || t.contains("snow") -> R.drawable.krl
                t.contains("yağmur") || t.contains("rain") -> R.drawable.ygmr
                t.contains("kapalı") || t.contains("closed") -> R.drawable.kapblt
                t.contains("sis") || t.contains("fog") -> R.drawable.sisicon
                t.contains("bulut") || t.contains("cloud") -> R.drawable.blt
                t.contains("gök gürültülü yağmur") || t.contains("thunderstorm") -> R.drawable.ggy
                t.contains("parçalı bulutlu") || t.contains("broken clouds") -> R.drawable.ayblt
                t.contains("ay") || t.contains("clear") || t.contains("açık") -> R.drawable.ay2
                else -> R.drawable.ay2
            }
        }
        // Gündüz ise
        else {
            return when {
                t.contains("kar") || t.contains("snow") -> R.drawable.krl
                t.contains("yağmur") || t.contains("rain") -> R.drawable.ygmr
                t.contains("kapalı") || t.contains("closed") -> R.drawable.kapblt
                t.contains("sis") || t.contains("fog") -> R.drawable.sisicon
                t.contains("bulut") || t.contains("cloud") -> R.drawable.blt
                t.contains("gök gürültülü yağmur") || t.contains("thunderstorm") -> R.drawable.ggy
                t.contains("parçalı bulutlu") || t.contains("broken clouds") -> R.drawable.pb
                t.contains("güneş") || t.contains("clear") || t.contains("açık") -> R.drawable.gns
                else -> R.drawable.gns
            }
        }
    }
}