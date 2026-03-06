package com.example.d1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherService : Service() {

    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.example.d1.ACTION_SHOW_NOTIFICATION"
        const val ACTION_CANCEL_NOTIFICATION = "com.example.d1.ACTION_CANCEL_NOTIFICATION"
        const val ACTION_REFRESH_NOW = "com.example.d1.ACTION_REFRESH_NOW"
        const val EXTRA_WEATHER_TEXT = "weather_text"
    }

    private val TAG = "WeatherService"
    private val channelId = "weather_channel"
    private val NOTIF_ID = 1

    private val PREFS = "weather_prefs"
    private val PREF_KEY_LAST = "last_weather"
    private val PREF_KEY_LAST_TITLE = "last_weather_title"
    private val PREF_KEY_CITY = "current_city"
    private val PREF_KEY_LAST_UPDATE = "last_update_millis"

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var tickerJob: Job? = null
    private var notificationShown = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Arka planda periyodik güncelleme döngüsü
        serviceScope.launch {
            try {
                while (true) {
                    if (notificationShown) {
                        val prefs = applicationContext.getSharedPreferences(PREFS, MODE_PRIVATE)
                        val city = prefs.getString(PREF_KEY_CITY, "Gaziantep") ?: "Gaziantep"
                        refreshWeatherData(city)
                    }
                    delay(5 * 60 * 1000) // 5 dakika bekle
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Döngü hatası", t)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_SHOW_NOTIFICATION -> {
                notificationShown = true
                // CRASH ÇÖZÜMÜ: Veriyi beklemeden HEMEN bildirimi başlat
                startForegroundSafe("Hava Durumu", "Yükleniyor...")

                // Sonra veriyi çek
                serviceScope.launch {
                    val prefs = applicationContext.getSharedPreferences(PREFS, MODE_PRIVATE)
                    val city = prefs.getString(PREF_KEY_CITY, "Gaziantep") ?: "Gaziantep"
                    refreshWeatherData(city)
                }
            }

            ACTION_REFRESH_NOW -> {
                // NOT: Eğer MainActivity sadece Widget güncellemesi için broadcast yolluyorsa
                // burası çalışmaz (ki doğrusu odur). Ama yine de servis tetiklenirse çökmesin.
                if (notificationShown) {
                    startForegroundSafe("Hava Durumu", "Güncelleniyor...")
                }

                val city = intent?.getStringExtra("city")
                serviceScope.launch {
                    val prefs = applicationContext.getSharedPreferences(PREFS, MODE_PRIVATE)
                    val targetCity = city ?: prefs.getString(PREF_KEY_CITY, "Gaziantep") ?: "Gaziantep"
                    refreshWeatherData(targetCity)
                }
            }

            ACTION_CANCEL_NOTIFICATION -> {
                notificationShown = false
                stopTicker()
                stopForegroundSafe()
            }
        }
        return START_STICKY
    }

    // Yardımcı Fonksiyon: Çökmeden Bildirim Başlatır
    private fun startForegroundSafe(title: String, content: String) {
        try {
            val notification = createNotification(title, content, System.currentTimeMillis())
            startForeground(NOTIF_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground hatası", e)
        }
    }

    private fun stopForegroundSafe() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground hatası", e)
        }
    }

    private suspend fun refreshWeatherData(city: String) {
        try {
            val payload = fetchWeatherData(city)
            val cleanContent = sanitizeContent(payload.content)
            val now = System.currentTimeMillis()

            val prefs = applicationContext.getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.edit {
                putString(PREF_KEY_LAST_TITLE, payload.title)
                putString(PREF_KEY_LAST, cleanContent)
                putLong(PREF_KEY_LAST_UPDATE, now)
                putString(PREF_KEY_CITY, city)
            }

            // Widget'a haber ver
            val widgetUpdateIntent = Intent(applicationContext, WeatherWidgetProvider::class.java).apply {
                this.action = WeatherWidgetProvider.ACTION_WIDGET_UPDATE
            }
            sendBroadcast(widgetUpdateIntent)

            // Bildirimi Güncelle (Sadece kullanıcı bildirimi açtıysa)
            if (notificationShown) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, createNotification(payload.title, cleanContent, now))
                startTickerIfNeeded()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Veri yenileme hatası", t)
        }
    }

    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val prefs = applicationContext.getSharedPreferences(PREFS, MODE_PRIVATE)
            while (notificationShown) {
                try {
                    val title = prefs.getString(PREF_KEY_LAST_TITLE, "Hava Durumu") ?: "Hava Durumu"
                    val content = prefs.getString(PREF_KEY_LAST, " ") ?: " "
                    val lastUpdate = prefs.getLong(PREF_KEY_LAST_UPDATE, 0L)
                    nm.notify(NOTIF_ID, createNotification(title, content, lastUpdate))
                } catch (t: Throwable) { }
                delay(60_000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotification(title: String, content: String, whenMillis: Long?): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val safeContent = if (content.isBlank()) " " else content
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(safeContent)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // İkon yoksa varsayılan
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // SENİN ORİJİNAL KODUNDAKİ DETAYLI ZAMAN HESAPLAMASI
        if (whenMillis != null && whenMillis > 0L) {
            val rel = relativeTimeString(whenMillis)
            val bigText = "$safeContent\n\nGüncellendi: $rel"
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(safeContent))
        }

        builder.setShowWhen(false)
        builder.setUsesChronometer(false)

        return builder.build()
    }

    // SENİN ORİJİNAL KODUNDAKİ DETAYLI ZAMAN HESAPLAMA FONKSİYONU
    private fun relativeTimeString(timeMillis: Long): String {
        val diff = System.currentTimeMillis() - timeMillis
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "şimdi"
            minutes < 60 -> if (minutes == 1L) "1 dakika önce" else "$minutes dakika önce"
            hours < 24 -> if (hours == 1L) "1 saat önce" else "$hours saat önce"
            days < 7 -> if (days == 1L) "1 gün önce" else "$days gün önce"
            else -> {
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("tr"))
                sdf.format(java.util.Date(timeMillis))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Hava Durumu Kanalı", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // SENİN ORİJİNAL KODUNDAKİ DETAYLI TEMİZLEME FONKSİYONU
    private fun sanitizeContent(content: String): String {
        return content
            .replace(Regex("\\b\\d+\\s*(sn|s|saniye|dk|dak|dakika|saat|gün|hafta|ay|yıl)\\s*önce\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\-–—•·]\\s*\\d+\\s*(sn|s|saniye|dk|dak|dakika|saat|gün|hafta|ay|yıl)\\s*önce", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?(güncelleme|son güncelleme|updated|update).*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)güncelleme\\s*[:\\-–—]?\\s*\\d+\\s*(sn|s|saniye|dk|dak|dakika|saat|gün|hafta|ay|yıl)\\s*önce"), "")
            .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), "")
            .replace(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"), "")
            .trim()
    }

    private data class NotificationPayload(val title: String, val content: String)

    private suspend fun fetchWeatherData(city: String): NotificationPayload {
        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherServiceApi::class.java)
            val response = service.getCurrentWeather(city, "BURAYA_API_KEY_GELECEK", "metric", "tr")

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && !body.weather.isNullOrEmpty()) {
                    val location = if (!body.name.isNullOrEmpty()) body.name!! else city
                    val temp = body.main?.temp ?: Double.NaN
                    val humidity = body.main?.humidity ?: -1
                    val desc = body.weather?.firstOrNull()?.description ?: ""

                    // Orijinal koddaki başlık formatı
                    val title = " " + location.split("\\s+".toRegex()).first()
                    val content = "🌡 ${temp}°C • 💧 ${humidity}% • ${desc}"
                    NotificationPayload(title, content)
                } else {
                    NotificationPayload(city, "$city: Veri alınamadı")
                }
            } else {
                NotificationPayload(city, "$city: API hatası: ${response.code()}")
            }
        } catch (e: Exception) {
            NotificationPayload(city, "$city: Hata: ${e.localizedMessage ?: "Bilinmeyen hata"}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        tickerJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}