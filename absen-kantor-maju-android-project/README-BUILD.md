# Cara membangun APK Absen Kantor Maju

Proyek ini sudah lengkap dan tidak punya dependensi tambahan. Pilih salah satu cara:

## A. Android Studio (paling mudah)
1. Install Android Studio, lalu **File > Open** dan pilih folder proyek ini.
2. Tunggu sinkronisasi Gradle selesai (otomatis mengunduh SDK yang kurang).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## B. Baris perintah
Syarat: JDK 17, Android SDK (platform 34 + build-tools 34.0.0), Gradle 8.9 atau lebih baru.

    echo "sdk.dir=/jalur/ke/Android/Sdk" > local.properties
    gradle assembleDebug

## C. GitHub Actions (tanpa install apa pun di komputer)
1. Buat repositori GitHub, unggah seluruh isi folder ini (termasuk `.github/`).
2. Buka tab **Actions > Build APK > Run workflow**.
3. Setelah hijau, unduh APK dari bagian **Artifacts**.

## Memasang ke HP
Salin APK ke HP, buka, dan izinkan "Install dari sumber tidak dikenal" bila diminta.
Saat pertama dibuka, izinkan akses **Kamera** dan **Lokasi**.

## Untuk rilis (Play Store atau distribusi resmi)
APK debug cukup untuk dipasang langsung. Untuk rilis, buat keystore sendiri dan tanda tangani
dengan `assembleRelease`; simpan keystore dengan aman karena tidak bisa dipulihkan.
