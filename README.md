# RetroBit OD

Always On Display bergaya **retro pixel** untuk **Infinix GT 30 Pro** (dan HP AMOLED
Android 10+ lain) — dibuat native Kotlin, tanpa root, tanpa accessibility service,
dan bisa menampilkan **kartu identitas ASN** di layar AOD.

Panel GT 30 Pro adalah AMOLED 1.5K 1224×2720 dengan dukungan Always-On Display bawaan
XOS 15. Aplikasi ini tidak bisa memanggil mode ambient asli sistem (butuh izin
signature OEM), jadi RetroBit OD memakai jalur yang tersedia untuk aplikasi pihak
ketiga: menggambar sendiri jam/kartu di atas keyguard dengan piksel hitam pekat
(piksel AMOLED yang tidak dipakai benar-benar mati), plus mode Daydream untuk saat
dicharge.

> ⚠️ Status: kode lengkap & siap di-build, tetapi **belum dikompilasi** di lingkungan
> ini (sandbox tidak punya JDK/Android SDK). Buka di Android Studio, sync, lalu
> jalankan — perbaiki error kecil kalau ada (mis. versi AGP/Kotlin yang diminta
> Android Studio kamu lebih baru).

---

## Fitur

**Rendering (custom Canvas, tanpa aset font)**
- Font bitmap 5×7 buatan sendiri + mode **LED 7-segmen** (`PixelFont.kt`), semua
  digambar sebagai kotak piksel → tajam di density apa pun, ukuran APK kecil.
- 6 warna aksen (phosphor hijau, amber CRT, cyan, magenta, putih, merah), glow ala CRT,
  garis scanline, jam detik (opsional), tanggal (mengikuti bahasa sistem), baterai,
  jumlah notifikasi.
- 3 tata letak: **Jam di atas**, **Jam di tengah**, **Kartu penuh**.
- Aman burn-in: posisi digeser 2 dp tiap 1/2/5 menit (`burnInMinutes`), latar selalu
  `#000000`, dan hanya redraw ketika kontennya berubah (biasanya 1× per menit).
- Menghindari punch-hole kamera & sudut layan lewat `DisplayCutout` + inset status bar.

**Kartu ASN di AOD** (`ui/CardActivity.kt` + `render/AmbientRenderer.kt`)
- 2 mode: **data** (Nama, NIP/NUPTK, Pangkat/Golongan, Jabatan, Unit kerja, Instansi,
  catatan) yang dinormalisasi jadi teks pixel + pas foto + QR, atau **gambar kartu utuh**
  (screenshot e-Karpeg / foto kartu) yang tinggal dicocokkan ke bingkai.
- QR dibuat lokal dengan ZXing (isi bebas: NIP, link verifikasi, dsb.) → praktis untuk
  scan di gate/absensi tanpa membuka HP.
- Privasi (opsional): aktifkan *Sensor kartu diburamkan otomatis* kalau AOD sering
  terlihat orang lain — kartu jadi placeholder terkunci, dan terbuka sendiri saat
  dicharge atau selama 20 detik setelah kamu menekan tile / tombol di app.

**Cara tampil (user bisa pilih di Setelan → Mode render)**
| Mode | Cara kerja | Catatan |
|---|---|---|
| **Overlay** (default) | `TYPE_APPLICATION_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED` + `FLAG_TURN_SCREEN_ON` + `FLAG_KEEP_SCREEN_ON`, brightness ditekan | butuh izin “tampilkan di atas aplikasi lain”; sentuhan tembus (bisa buka shade/scan fingerprint) |
| **Kompatibilitas (Activity)** | `AmbientActivity` dengan `setShowWhenLocked(true)` + `setTurnScreenOn(true)`; fallback otomatis kalau overlay ditolak ROM | dipakai kalau XOS memblokir overlay; geser ke atas = tutup |
| **Screensaver (Daydream)** | `DreamService` (`setFullscreen`, `setInteractive(false)`, `setScreenBright(false)`) | paling hemat & stabil, tapi hanya aktif saat dicharge/idle; nyalakan di Setelan → Layar → Screensaver |

**Otomatisasi & baterai**
- Jadwal (mulai–selesai, boleh melewati tengah malam, pilihan hari), hanya saat dicharge,
  ambang baterai, tahan saat suhu ≥ 42 °C, sensor proximity (kantong), durasi tampil
  (peek 5–120 dtk atau nyala terus), brightness 0–25 % (opsi “paksa lewat sistem”
  memakai `WRITE_SETTINGS` untuk ROM yang mengabaikan brightness overlay).
- Toggle lewat **Quick Settings tile**, auto-start ulang setelah reboot, dan tombol uji
  10 detik dari dalam app.

---

## Struktur

```
app/src/main/java/dev/retrobit/od/
├── RetroApp.kt                    init channel notifikasi + pulihkan layanan
├── data/Prefs.kt                  satu-satunya sumber preferensi (SharedPreferences)
├── data/IdCard.kt                 model kartu ASN (JSON) + CardStore (cache bitmap/QR)
├── render/PixelFont.kt            font bitmap 5×7 + LED 7-segmen
├── render/AmbientRenderer.kt      semua layout AOD digambar di Canvas
├── render/RenderState.kt          snapshot jam/baterai/notifikasi/geseran anti burn-in
├── ambient/AmbientView.kt           View kanvas (dipakai overlay, activity, dream, preview)
├── ambient/AmbientService.kt        FGS: receiver layar, jadwal, jendela overlay, timeout
├── ambient/AmbientActivity.kt       jalur kompatibilitas keyguard
├── ambient/AmbientController.kt     kebijakan: kapan boleh tampil / matikan
├── system/PowerWatch.kt             baca baterai (sticky) + ProximityWatch
├── service/AmbientDreamService.kt   Daydream
├── service/NotificationMonitor.kt   penghitung notifikasi (tidak menyimpan isi)
├── service/AmbientTileService.kt    Quick Settings tile
├── receiver/BootReceiver.kt         nyalakan lagi setelah reboot/update
└── ui/                            MainActivity (dashboard), StyleActivity,
                                   AutomationActivity, CardActivity
```

## Build & install

1. **Android Studio** (Ladybug atau lebih baru) → *Open* → pilih folder ini.
   `gradle/wrapper/gradle-wrapper.properties` sudah mengarah ke Gradle 8.9; kalau wrapper
   jar belum ada, Android Studio akan men-downloads-nya sendiri (atau jalankan
   `gradle wrapper` dari CLI).
2. Versi yang dipakai proyek ini: AGP **8.6.1**, Kotlin **2.0.21**, JDK **17**,
   compileSdk **35**, minSdk **29**, targetSdk **35**. Kalau Android Studio menawarkan
   upgrade AGP/Kotlin, terima saja — kodenya tidak memakai API eksperimental.
3. *Run* ke GT 30 Pro (USB debugging), atau `./gradlew assembleDebug` lalu
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Setelan wajib di Infinix GT 30 Pro (XOS 15)

XOS agresif mematikan proses latar. Buka **RetroBit OD → Izin yang dibutuhkan** dan
aktifkan semuanya:

1. **Tampilkan di atas aplikasi lain** → wajib untuk mode Overlay.
2. **Abaikan optimasi baterai** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
3. **Auto-start**: Setelan → Aplikasi → RetroBit OD → izinkan *Auto-start* /
   *Allow background activity*; kalau ada di Phone Master, matikan pembatasan latar.
4. Opsional: **Akses notifikasi** (kalau mau jumlah notifikasi muncul) dan
   **Ubah pengaturan sistem** (kalau mau brightness dipaksa turun saat AOD).
5. Setel **Setelan → Layar → Time-out layar** ke 15–30 detik supaya layar cepat tidur
   lagi setelah mode peek selesai (tanpa ini, HP menunggu timeout sistem dulu).
6. Matikan AOD bawaan XOS (Setelan → Layar → Always On Display → Off) supaya tidak ada
   dua jam yang bertumpuk.

## Catatan teknis penting

- Aplikasi pihak ketiga **tidak bisa** masuk ke mode Doze/AOD asli panel
  (`CONTROL_DISPLAY_IN_AMBIENT_MODE` itu izin signature). Mode "Overlay" = layar
  dibiarkan nyala pada brightness minimum dengan isi hitam pekat; konsumsi riilnya
  kecil tapi tidak nol. Kalau mau 0% risiko boros: pakai **mode Screensaver** (hanya saat
  dicharge) atau **peek 10 dtk**.
- Tidak ada `LiveLockScreenService` di rilis awal. API live-lock-screen tidak dijamin
  muncul di pemilih wallpaper XOS, dan implementasinya butuh `WallpaperService` engine
  khusus — ada di roadmap (lihat bawah). Untuk sementara, `AmbientActivity`
  (`showWhenLocked`) yang menutupi kebutuhan keyguard.
- Data kartu & foto hanya masuk `SharedPreferences` + URI persistable milik app.
  Tidak ada izin internet di manifest → tidak bisa kirim ke mana-mana.

## Roadmap

- [ ] Mode **Live Lock Screen** (`LiveLockScreenService`) + uji di XOS/stock.
- [ ] Preset tambahan: jam analog pixel, marquee, matrix rain, skin “Game Boy”.
- [ ] Widget layar utama & app shortcut untuk reveal kartu.
- [ ] Material You (`dynamicColor`) untuk warna aksen.
- [ ] i18n lengkap (base English + `values-id`) kalau mau naik ke Google Play.
- [ ] Opsi “kartu hanya di NFC/BLE wilayah kantor” (geofence halus, tanpa izin lokasi
      permanen).

## Lisensi

Belum ditentukan — sesuaikan sebelum mempublikasikan.
