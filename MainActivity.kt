package com.example.d1

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val PREFS = "weather_prefs"
    private val PREF_KEY_LAST = "last_weather"
    private val PREF_KEY_CITY = "current_city"
    private val PREF_KEY_LAST_IS_NIGHT = "last_is_night"

    private val PREF_KEY_LAST_UPDATE = "last_update_millis"

    // Helper ve View Tanımları
    private lateinit var locationHelper: LocationHelper
    private lateinit var rvForecast: androidx.recyclerview.widget.RecyclerView

    private lateinit var tvWeather: TextView
    private lateinit var etCity: EditText
    private lateinit var btnFetch: Button

    // --- AYARLAR İÇİN TANIMLAMALAR ---
    private lateinit var btnSettings: ImageButton
    private lateinit var settingsPanel: LinearLayout
    private lateinit var panelOverlay: View
    private var isSettingsOpen = false

    // Reklamlar
    private lateinit var rewardedAd: RewardedAd
    private lateinit var adView: AdView
    private lateinit var adView2: AdView

    // Detay TextView Tanımları (Rüzgar, Nem vs.)
    private lateinit var tvFeelsLike: TextView
    private lateinit var tvWind: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvSunTime: TextView
    private lateinit var tvSunLabel: TextView

    private val adRefreshHandler = Handler(Looper.getMainLooper())
    private val adRefreshIntervalMs = 15_000L
    private val adRefreshRunnable = object : Runnable {
        override fun run() {
            try {
                val adRequest = AdRequest.Builder().build()
                adView.loadAd(adRequest)
            } catch (_: Exception) { }
            adRefreshHandler.postDelayed(this, adRefreshIntervalMs)
        }
    }

    private val mainScope = MainScope()
    private val networkErrorHandler = CoroutineExceptionHandler { _, throwable ->
        runOnUiThread {
            // Hata olursa sessiz kal
        }
    }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msg = if (granted) "İzin verildi." else "İzin verilmedi."
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkLocationPermissionAndFetch()
        } else {
            Toast.makeText(this, "Konum izni reddedildi", Toast.LENGTH_SHORT).show()
        }
    }


    // Bu sınıf sayesinde verileri kaybetmeden taşıyoruz
    private data class WeatherFetchResult(
        val display: String,
        val isNight: Boolean,
        val locationName: String?,
        val body: WeatherResponse?, // Hava durumu verisinin tamamı
        val coords: Pair<Double, Double>? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Önce butonu tanıtalım (LinearLayout tıklanabilir yaptık)
        val btnShareApp = findViewById<LinearLayout>(R.id.btnShareApp)

        // 2. Tıklama özelliğini verelim
        btnShareApp.setOnClickListener {
            // Paylaşma panelini açan kod
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Harika Hava Durumu Uygulaması")
                putExtra(Intent.EXTRA_TEXT, "Bu uygulamayı denemelisin! https://play.google.com/store/apps/details?id=com.example.d1")
            }
            startActivity(Intent.createChooser(shareIntent, "Paylaş"))

            // Tıkladıktan sonra paneli kapatalım
            closeSettingsPanel()
        }

        // 1. View Bağlamaları
        locationHelper = LocationHelper(this)

        rvForecast = findViewById(R.id.rvForecast)
        rvForecast.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )

        tvWeather = findViewById(R.id.tvWeather)
        etCity = findViewById(R.id.etCity)
        btnFetch = findViewById(R.id.btnFetch)

        // Ayarlar Paneli Bağlamaları
        btnSettings = findViewById(R.id.btnSettings)
        settingsPanel = findViewById(R.id.settingsPanel)
        panelOverlay = findViewById(R.id.panelOverlay)

        // Detay Kutuları Bağlamaları
        tvFeelsLike = findViewById(R.id.tvFeelsLike)
        tvWind = findViewById(R.id.tvWind)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvSunTime = findViewById(R.id.tvSunTime)
        tvSunLabel = findViewById(R.id.tvSunLabel)

        val btnShowNotification = findViewById<Button>(R.id.btnShowNotification)
        val btnCancelNotification = findViewById<Button>(R.id.btnCancelNotification)
        val btnGps = findViewById<ImageButton>(R.id.btnGps)

        // Ayar paneli başlangıçta solda gizli olsun
        settingsPanel.viewTreeObserver.addOnGlobalLayoutListener {
            if (!isSettingsOpen) {
                settingsPanel.translationX = -settingsPanel.width.toFloat()
            }
        }

        // 2. Reklamları Başlat
        MobileAds.initialize(this) {}
        loadRewardedAd()

        adView = findViewById(R.id.adView)
        adView.loadAd(AdRequest.Builder().build())

        adView2 = findViewById(R.id.adView2)
        adView2.loadAd(AdRequest.Builder().build())

        // 3. Hafızadan Son Verileri Çek
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getString(PREF_KEY_LAST, null)
        val savedCity = prefs.getString(PREF_KEY_CITY, null)
        val lastIsNight = prefs.getBoolean(PREF_KEY_LAST_IS_NIGHT, isNightLocal())

        if (!last.isNullOrBlank() && !last.contains("hava durumu burada", ignoreCase = true)) {
            if (savedCity != null && !last.contains(savedCity, ignoreCase = true)) {
                tvWeather.text = "      $savedCity\n$last"
            } else {
                tvWeather.text = last
            }
            applyBackgroundForWeatherText(last, lastIsNight)
        } else {
            tvWeather.text = "Hava durumu burada görünecek"
        }

        // 4. Otomatik Güncelleme
        if (!savedCity.isNullOrBlank()) {
            fetchWeatherForCity(savedCity)
        }

        // 5. Tıklama Olayları
        etCity.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnFetch.performClick()
                true
            } else false
        }

        btnFetch.setOnClickListener {
            val cityInput = etCity.text.toString().trim()
            if (cityInput.isEmpty()) {
                Toast.makeText(this, "Lütfen şehir girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchWeatherForCity(cityInput)
        }

        btnGps.setOnClickListener {
            checkLocationPermissionAndFetch()
        }

        // --- AYARLAR MANTIĞI ---
        btnSettings.setOnClickListener {
            if (isSettingsOpen) closeSettingsPanel() else openSettingsPanel()
        }

        panelOverlay.setOnClickListener {
            closeSettingsPanel()
        }

        btnShowNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            val intent = Intent(this, WeatherService::class.java).apply {
                action = WeatherService.ACTION_SHOW_NOTIFICATION
            }
            ContextCompat.startForegroundService(this, intent)

            if (::rewardedAd.isInitialized) {
                rewardedAd.show(this) {}
            }
        }

        btnCancelNotification.setOnClickListener {
            val intent = Intent(this, WeatherService::class.java).apply {
                action = WeatherService.ACTION_CANCEL_NOTIFICATION
            }
            startService(intent)
        }
    }

    // --- PANEL ANİMASYON FONKSİYONLARI ---
    private fun openSettingsPanel() {
        panelOverlay.visibility = View.VISIBLE
        panelOverlay.bringToFront()
        settingsPanel.bringToFront()
        btnSettings.bringToFront()

        btnSettings.animate().rotation(180f).setDuration(400).start()
        settingsPanel.animate().translationX(0f).setDuration(400).start()

        isSettingsOpen = true
    }

    private fun closeSettingsPanel() {
        btnSettings.animate().rotation(0f).setDuration(400).start()
        settingsPanel.animate()
            .translationX(-settingsPanel.width.toFloat())
            .setDuration(400)
            .withEndAction { panelOverlay.visibility = View.GONE }
            .start()

        isSettingsOpen = false
    }

    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            tvWeather.text = "Konum aranıyor..."
            locationHelper.getCurrentLocation { lat, lon ->
                fetchWeatherByCoordinates(lat, lon)
            }
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Koordinat ile Veri Çekme
    private fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        tvWeather.text = "Konum bulundu, yükleniyor..."
        mainScope.launch(networkErrorHandler) {
            // Triple yerine WeatherFetchResult kullanıyoruz
            val result = withContext(Dispatchers.IO) {
                try {
                    val retrofit = Retrofit.Builder()
                        .baseUrl("https://api.openweathermap.org/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    val api = retrofit.create(WeatherServiceApi::class.java)
                    // buraya kendi api anahtarını gir
                    val resp = api.getCurrentWeatherByCoord(lat, lon, "BURAYA_API_KEY_GELECEK", "metric", "tr")

                    if (!resp.isSuccessful) return@withContext WeatherFetchResult("API hatası", isNightLocal(), null, null)

                    val body = resp.body()
                    if (body == null || body.weather.isNullOrEmpty()) return@withContext WeatherFetchResult("Veri yok", isNightLocal(), null, null)

                    val locationName = body.name ?: "Konum"
                    val temp = body.main?.temp ?: Double.NaN
                    val humidity = body.main?.humidity ?: -1
                    val desc = body.weather.firstOrNull()?.description ?: ""

                    val display = "      $locationName\n🌡 $temp°C • 💧%$humidity • $desc"

                    val dtSec = body.dt ?: (System.currentTimeMillis() / 1000L)
                    val sunriseSec = body.sys?.sunrise ?: 0L
                    val sunsetSec = body.sys?.sunset ?: 0L
                    val night = if (sunriseSec > 0) isNightFromApi(dtSec, sunriseSec, sunsetSec, body.timezone ?: 0) else isNightLocal()

                    // Body'yi de paketleyip gönderiyoruz!
                    WeatherFetchResult(display, night, locationName, body)
                } catch (e: Exception) {
                    WeatherFetchResult("Hata", isNightLocal(), null, null)
                }
            }

            withContext(Dispatchers.Main) {
                val display = result.display
                val night = result.isNight
                val cityName = result.locationName
                val body = result.body // Artık body burada mevcut!

                tvWeather.text = display
                if (cityName != null && cityName != "Konum") etCity.setText(cityName)

                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putString(PREF_KEY_LAST, display)
                    if (cityName != null) putString(PREF_KEY_CITY, cityName)
                    putBoolean(PREF_KEY_LAST_IS_NIGHT, night)
                    putLong(PREF_KEY_LAST_UPDATE, System.currentTimeMillis())
                }

                if (!display.contains("yükleniyor", ignoreCase = true) && !display.contains("Hata", ignoreCase = true)) {
                    applyBackgroundForWeatherText(display, night)

                    // 3 Saatlik Tahminleri Çek
                    fetchForecast(lat, lon)

                    // Detayları Doldur
                    if (body != null) updateWeatherDetails(body)
                }
            }

            val widgetUpdateIntent = Intent(this@MainActivity, WeatherWidgetProvider::class.java).apply {
                action = WeatherWidgetProvider.ACTION_WIDGET_UPDATE
            }
            sendBroadcast(widgetUpdateIntent)
        }
    }

    // Şehir İsmi ile Veri Çekme
    private fun fetchWeatherForCity(city: String) {
        tvWeather.text = "Yükleniyor..."
        mainScope.launch(networkErrorHandler) {
            val result = withContext(Dispatchers.IO) {
                try {
                    val retrofit = Retrofit.Builder()
                        .baseUrl("https://api.openweathermap.org/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    val api = retrofit.create(WeatherServiceApi::class.java)
                    // buraya kendi APİ anahtarını gir
                    val resp = api.getCurrentWeather(city, "BURAYA_API_KEY_GELECEK", "metric", "tr")

                    if (!resp.isSuccessful) return@withContext WeatherFetchResult("API Hatası", isNightLocal(), null, null)

                    val body = resp.body()
                    if (body == null || body.weather.isNullOrEmpty()) return@withContext WeatherFetchResult("Veri Yok", isNightLocal(), null, null)

                    val finalCityName = body.name ?: city
                    val temp = body.main?.temp ?: Double.NaN
                    val humidity = body.main?.humidity ?: -1
                    val desc = body.weather.firstOrNull()?.description ?: ""
                    val display = "      $finalCityName\n🌡 $temp°C • 💧%$humidity • $desc"

                    val dtSec = body.dt ?: (System.currentTimeMillis() / 1000L)
                    val sunriseSec = body.sys?.sunrise ?: 0L
                    val sunsetSec = body.sys?.sunset ?: 0L
                    val night = if (sunriseSec > 0) isNightFromApi(dtSec, sunriseSec, sunsetSec, body.timezone ?: 0) else isNightLocal()

                    val lat = body.coord?.lat
                    val lon = body.coord?.lon
                    val coords = if (lat != null && lon != null) Pair(lat, lon) else null

                    // Body ve Koordinatları paketle
                    WeatherFetchResult(display, night, null, body, coords)
                } catch (e: Exception) {
                    WeatherFetchResult("Hata", isNightLocal(), null, null)
                }
            }

            withContext(Dispatchers.Main) {
                val display = result.display
                val night = result.isNight
                val body = result.body
                val coords = result.coords

                tvWeather.text = display

                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putString(PREF_KEY_LAST, display)
                    putString(PREF_KEY_CITY, city)
                    putBoolean(PREF_KEY_LAST_IS_NIGHT, night)
                    putLong(PREF_KEY_LAST_UPDATE, System.currentTimeMillis())
                }

                if (!display.contains("yükleniyor", ignoreCase = true) && !display.contains("Hata", ignoreCase = true)) {
                    applyBackgroundForWeatherText(display, night)
                    if (coords != null) {
                        fetchForecast(coords.first, coords.second)
                    }
                    // Detayları Doldur
                    if (body != null) updateWeatherDetails(body)
                }
            }

            val widgetUpdateIntent = Intent(this@MainActivity, WeatherWidgetProvider::class.java).apply {
                action = WeatherWidgetProvider.ACTION_WIDGET_UPDATE
            }
            sendBroadcast(widgetUpdateIntent)
        }
    }

    // Detay verilerini ekrana basan fonksiyon
    private fun updateWeatherDetails(body: WeatherResponse) {
        // 1. Hissedilen
        val feelsLike = body.main?.feelsLike?.toInt() ?: 0
        tvFeelsLike.text = "$feelsLike°C"

        // 2. Rüzgar
        val windSpeed = body.wind?.speed ?: 0.0
        tvWind.text = "${windSpeed} m/s"

        // 3. Nem
        val humidity = body.main?.humidity ?: 0
        tvHumidity.text = "%$humidity"

        // 4. Gün Doğumu / Batımı
        val now = System.currentTimeMillis() / 1000
        val sunrise = body.sys?.sunrise ?: 0L
        val sunset = body.sys?.sunset ?: 0L

        val isDay = now in sunrise..sunset
        val targetTime = if (isDay) sunset else sunrise
        val label = if (isDay) "Gün Batımı" else "Gün Doğumu"

        tvSunLabel.text = label

        val date = java.util.Date((targetTime) * 1000L)
        val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        tvSunTime.text = sdf.format(date)
    }

    private fun fetchForecast(lat: Double, lon: Double) {
        mainScope.launch(networkErrorHandler) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.openweathermap.org/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(WeatherServiceApi::class.java)

                // // buraya kendi openweather api keyini gir
                val resp = api.getForecastByCoord(lat, lon, "BURAYA_API_KEY_GELECEK", "metric", "tr")
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body != null) {
                        val adapter = ForecastAdapter(body.list)
                        rvForecast.adapter = adapter
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        // bu google test idsi kendi id ni girmeyi unutma
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { }
        })
    }

    override fun onResume() {
        super.onResume()
        try { adView.resume(); adView2.resume() } catch (_: Exception) {}

        // Uygulamaya her girdiğinde veya arka plandan döndüğünde Widget'a "Güncellen" sinyali gönderir
        val widgetUpdateIntent = Intent(this, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_WIDGET_UPDATE
        }
        sendBroadcast(widgetUpdateIntent)
    }

    override fun onPause() {
        try { adView.pause(); adView2.pause() } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        try { adView.destroy(); adView2.destroy() } catch (_: Exception) {}
        super.onDestroy()
        mainScope.cancel()
    }

    private fun isNightLocal(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return (hour < 6 || hour >= 18)
    }

    private fun isNightFromApi(dtSec: Long, sunriseSec: Long, sunsetSec: Long, timezoneOffsetSec: Int): Boolean {
        val localNow = dtSec + timezoneOffsetSec
        val localSunrise = sunriseSec + timezoneOffsetSec
        val localSunset = sunsetSec + timezoneOffsetSec
        return (localNow < localSunrise) || (localNow >= localSunset)
    }

    private fun applyBackgroundForWeatherText(text: String, isNight: Boolean) {
        if (text.isBlank() || text.contains("hava durumu burada", ignoreCase = true)) return
        if (isNight) applyNightBackgroundForWeatherText(text) else applyDayBackgroundForWeatherText(text)
    }

    private fun applyDayBackgroundForWeatherText(text: String) {
        val t = text.lowercase(Locale.getDefault())
        val drawableRes = when {
            t.contains("kar") || t.contains("snow") -> R.drawable.karli
            t.contains("kapalı") || t.contains("closed") -> R.drawable.bult
            t.contains("yağmur") || t.contains("rain") -> R.drawable.yaggun
            t.contains("sis") || t.contains("fog") -> R.drawable.sisicon
            t.contains("bulut") || t.contains("cloud") -> R.drawable.bult
            t.contains("güneş") || t.contains("clear") -> R.drawable.gunack
            else -> R.drawable.gunack
        }
        setRootBackgroundSafe(drawableRes)
    }

    private fun applyNightBackgroundForWeatherText(text: String) {
        val t = text.lowercase(Locale.getDefault())
        val drawableRes = when {
            t.contains("kar") || t.contains("snow") -> R.drawable.gecekar
            t.contains("kapalı") || t.contains("closed") -> R.drawable.bult
            t.contains("yağmur") || t.contains("rain") -> R.drawable.yaggece
            t.contains("sis") || t.contains("fog") -> R.drawable.sis
            t.contains("bulut") || t.contains("cloud") -> R.drawable.bult
            t.contains("güneş") || t.contains("clear") -> R.drawable.geceackh
            else -> R.drawable.geceackh
        }
        setRootBackgroundSafe(drawableRes)
    }

    private fun setRootBackgroundSafe(drawableRes: Int) {
        try {
            findViewById<ImageView>(R.id.bgImage).setImageResource(drawableRes)
        } catch (_: Exception) { }
    }
}