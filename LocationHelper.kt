package com.example.d1

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Location
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource

class LocationHelper(private val activity: Activity) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)

    // Bu fonksiyon başarı durumunda (enlem, boylam) döndürür
    @SuppressLint("MissingPermission") // İzni MainActivity'de kontrol edeceğiz
    fun getCurrentLocation(onLocationRetrieved: (Double, Double) -> Unit) {

        // Pil tasarrufu yerine yüksek doğruluk için token
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                // Konum bulundu, MainActivity'e gönder
                onLocationRetrieved(location.latitude, location.longitude)
            } else {
                Toast.makeText(activity, "Konum alınamadı, GPS açık mı?", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(activity, "Konum hatası: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}