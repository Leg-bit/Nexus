# NEXUS Media

Native Android video & music player (Kotlin + Jetpack Compose + Media3 + Room).
Dark AMOLED, Midnight Blue + Electric Blue. Reads the real device storage (MediaStore + Storage Access Framework). No fake/hardcoded library.

## Fitur yang sudah ada
- Scan MediaStore otomatis + rescan manual + auto-refresh saat ada file baru
- Folder custom via SAF (pilih folder), sembunyikan folder
- Musik: lagu / album / artis, antrian, shuffle, repeat, sleep timer, equalizer (band, bass, surround, preset, profil kustom)
- Mode player: Cover, Vinyl (berputar), Lirik (.lrc/.txt pilihan user, sinkron), Compact
- Mini player + full player, background playback (MediaSessionService + notifikasi), restore antrian terakhir
- Video: fullscreen landscape, gesture (kecerahan, volume, geser seek, double tap +/-10 dtk), kecepatan, aspect ratio, subtitle eksternal (.srt/.vtt/.ass), Picture-in-Picture, resume posisi terakhir
- Playlist, riwayat, lanjut menonton, deteksi duplikat, pencarian (Room)

## Build
Android Studio: buka folder ini -> Run.  
CLI: `gradle assembleDebug` (Gradle 8.9, JDK 17).  
GitHub Actions: lihat `.github/workflows/build.yml`, APK ada di tab Actions -> Artifacts.

## Catatan arsitektur
Paket: `core`, `data`, `media`, `player`, `features`, `ui`, `di`.
DI: Hilt menyediakan `AppContainer` (singleton) yang memegang database, repository, scanner, player, dan equalizer.

## Tambahan v0.2
- Hilt (DI) + `@HiltViewModel`
- Preview thumbnail saat menggeser timeline video
- Audio latar untuk video (tombol headphone di player video) lewat MediaSession + notifikasi
- Halaman detail audio/video (menu ⋮ atau tekan lama kartu video)
- Filter kategori storage: Music, Movies, Download, DCIM, Recordings, Kustom
- Navigation Drawer (tombol menu di Beranda)
- Lirik otomatis dari file `<lagu>.lrc` / `.txt` di folder yang sama (Android 10 ke bawah), atau pilih manual
- Akses media parsial Android 14
- Tes: unit test (`gradle testDebugUnitTest`) dan instrumented test Room DAO (`gradle connectedDebugAndroidTest`, perlu emulator/perangkat)
