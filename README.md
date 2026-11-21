# Simpan — catatan build

- ApplicationId: `com.za.sidqi.simpan`
- Package: `/home/trfth/Desktop/SimpanApp`
- PRD: `/home/trfth/Desktop/PRD_Simpan.md`

## Build

```bash
cd /home/trfth/Desktop/SimpanApp
JAVA_HOME=~/jdk21 ANDROID_HOME=~/Android/Sdk ./gradlew assembleRelease --no-daemon
```

Hasil: `app/build/outputs/apk/release/app-release.apk`

## Signing

Keystore: `simpan-release.jks` di root project (**tidak ikut ter-commit**; letakkan manual sebelum rebuild).
- Store password: `simpan123`
- Key alias: `simpan`
- Key password: `simpan123`

**Simpan keystore ini.** Build berikutnya harus memakai key yang sama, kalau tidak APK tidak bisa menggantikan yang terpasang (harus uninstall dulu).

## Dependency khusus

`youtubedl-android` 0.18.1 (library + ffmpeg, total ~200 MB) disimpan di repo Maven lokal `/home/trfth/.m2local` supaya build tidak mengunduh ulang. Jangan hapus folder itu saat build.

- ABI phone-only: `armeabi-v7a`, `arm64-v8a` (x86 dihapus; APK 200 MB → 103 MB).
- `packaging.jniLibs.useLegacyPackaging = true` wajib karena `extractNativeLibs=true`.

## Yang belum teruji

Tidak ada emulator di mesin ini. Elemen yang wajib diuji di HP:
1. Hasil benar-benar `.mp3` dan bisa diputar.
2. File muncul di aplikasi Downloads sistem.
3. Tombol Batal menghentikan proses.
4. Notifikasi tetap muncul saat aplikasi ditutup.
