package com.example.d1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ForecastAdapter(private val forecastList: List<ForecastItem>?) :
    RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvTemp: TextView = view.findViewById(R.id.tvTemp)
        val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        val tvDesc: TextView = view.findViewById(R.id.tvDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val item = forecastList?.get(position) ?: return

        // 1. Sıcaklık
        val temp = item.main?.temp?.toInt() ?: 0
        holder.tvTemp.text = "$temp°C"

        // 2. Saat ve Gece/Gündüz Hesaplama
        var isNight = false // Varsayılan gündüz

        val rawDate = item.dtTxt ?: ""
        if (rawDate.isNotEmpty()) {
            try {
                val date =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(rawDate)
                if (date != null) {
                    val formatter = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
                    holder.tvTime.text = formatter.format(date)

                    // Saati kontrol et (06:00 - 18:00 arası Gündüz, kalanı Gece)
                    val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
                    val hour = hourFormat.format(date).toInt()
                    isNight = (hour < 6 || hour >= 18)
                } else {
                    holder.tvTime.text = "--:--"
                }
            } catch (e: Exception) {
                holder.tvTime.text = "--:--"
            }
        } else {
            holder.tvTime.text = "--:--"
        }

        // 3. Açıklama
        val weatherList = item.weather
        val desc = if (!weatherList.isNullOrEmpty()) {
            weatherList[0].description ?: ""
        } else {
            ""
        }

        holder.tvDesc.text =
            desc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // 4. İkon Seçimi (Gece/Gündüz Ayarlı)
        val iconRes = getWeatherIconResource(desc, isNight)
        holder.imgIcon.setImageResource(iconRes)
    }

    override fun getItemCount(): Int = forecastList?.size ?: 0

    // Gece/Gündüz Destekli İkon Fonksiyonu
    private fun getWeatherIconResource(text: String, isNight: Boolean): Int {
        val t = text.lowercase(Locale.getDefault())

        // Eğer GECE ise
        if (isNight) {
            return when {
                t.contains("kar") || t.contains("snow") -> R.drawable.krl
                t.contains("yağmur") || t.contains("rain") -> R.drawable.ygmr
                t.contains("kapalı") || t.contains("closed") -> R.drawable.kapblt
                t.contains("sis") || t.contains("fog") -> R.drawable.sisicon
                t.contains("bulut") || t.contains("cloud") -> R.drawable.blt
                t.contains("gök gürültülü") || t.contains("thunderstorm") -> R.drawable.ggy
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
                t.contains("gök gürültülü") || t.contains("thunderstorm") -> R.drawable.ggy
                t.contains("parçalı bulutlu") || t.contains("broken clouds") -> R.drawable.pb
                t.contains("güneş") || t.contains("clear") || t.contains("açık") -> R.drawable.gns
                else -> R.drawable.gns
            }
        }
    }
}