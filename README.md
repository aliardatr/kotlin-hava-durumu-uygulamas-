# Hava Durumu Uygulaması (Android/kotlin)

Bu proje, OpenWeatherMap API kullanarak anlık ve 5 günlük hava durumu verilerini gösteren modern bir Android uygulamasıdır.

## 🚀 Özellikler
* **Konum Bazlı Veri:** GPS üzerinden otomatik konum bulma.
* **Arka Plan Servisi:** Bildirim çubuğunda sürekli güncel hava durumu.
* **Widget Desteği:** Ana ekranda şık ve dinamik hava durumu kutucuğu.
* **Modern Mimari:** Kotlin, Coroutines, Retrofit ve View Binding kullanılmıştır.
* **Reklam Entegrasyonu:** AdMob (Banner ve Ödüllü Reklam) hazır yapıdadır.

## ⚠️ Önemli Not
Güvenlik nedeniyle API anahtarları ve Reklam kimlikleri kod içerisinden kaldırılmıştır. Kendi anahtarınızla çalıştırmak için `MainActivity` ve `WeatherService` içerisindeki ilgili alanları doldurunuz.

Bu projenin çalışabilmesi için gradle dosyasındaki kütüphaneleri eklemeyi unutmayınız!
BU projeyi çalıştırmak için ide uygulamanızda boş bir proje açın ve bu açılan projeye MainActivity.kt dosyasının olduğu dizine .kt uzantılı tüm dosyaları ekleyin aynı şekilde activity_main.xml dosyasının olduğu dizine AndroidManifest dosyası hariç tüm xml dosyalarını ekleyin, AndroidManifest dosyasını, activity_main.xml dosyasını, MainActivity.kt dosyasını ve build.gradle dosyasını güncelleyin!
