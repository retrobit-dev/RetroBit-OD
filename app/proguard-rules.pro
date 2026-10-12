# Component yang di-instantiate oleh sistem (bukan oleh kode aplikasi) harus dipertahankan.
-keep class dev.retrobit.od.ambient.AmbientService { *; }
-keep class dev.retrobit.od.ambient.AmbientActivity { *; }
-keep class dev.retrobit.od.service.** { *; }
-keep class dev.retrobit.od.receiver.** { *; }

# ZXing
-dontwarn com.google.zxing.**
