# PRD — Simpan (Android: YouTube/YouTube Music → MP3)

Versi: 1.0 · Status: **untuk disetujui sebelum coding** · Bahasa: Indonesia

---

## 1. Gambaran umum

Aplikasi Android native yang menerima satu link YouTube / YouTube Music, mengambil audio terbaik, mengubahnya jadi MP3, dan menyimpannya ke folder `Downloads`. Semua pemrosesan terjadi **di perangkat** — tidak ada server, tidak ada akun, tidak ada upload.

Nama aplikasi: **Simpan**. ApplicationId: `com.za.sidqi.simpan` (final, tidak diubah setelah build pertama).

Rujukan visual: `Desain_v4_Malam_Radio_Tipografi.png` (tema gelap, aksi merah → kuning → hijau).

---

## 2. Tujuan & Non-tujuan

**Tujuan**
- Satu alur: tempel link → Unduh MP3 → selesai → riwayat.
- Berjalan tanpa PC, tanpa login, tanpa koneksi ke service pihak ketiga selain situs sumbernya.
- Bisa dibatalkan, tetap jalan saat aplikasi ditutup, dan hasilnya terlihat di aplikasi Downloads sistem.

**Non-tujuan (tidak di MVP)**
- Playlist / antrean banyak unduhan bersamaan.
- Login, cookies, akun premium, atau bypass pembatasan/DRM.
- Pilihan format selain MP3, pemotongan lagu, tag ID3 editor.
- Cloud sync, statistik, iklan, telemetry.
- Dukungan semua situs yang dikenal yt-dlp — hanya YouTube & YouTube Music yang dijamin.

---

## 3. Arsitektur

```
UI (Jetpack Compose, Material 3 dark)
  └─ SimpanViewModel (StateFlow<UiState>)
       └─ DownloadService (foreground service)
            └─ YoutubeDL (youtubedl-android 0.18.1)
                 ├─ getInfo(url)        → judul, durasi
                 └─ execute(request)    → unduh + konversi MP3
       └─ Room (riwayat)
       └─ MediaStore.scan(file)         → muncul di Downloads
```

- Seluruh panggilan `YoutubeDL` berada di `Dispatchers.IO`, bukan di komposisi.
- Satu unduhan aktif pada satu waktu. Permintaan kedua ditolak dengan pesan "Masih ada unduhan berjalan".
- Service foreground wajib agar proses tidak mati saat UI ditutup; notifikasi menampilkan judul + progres + aksi **Batal**.

---

## 4. Kontrak dependency (terverifikasi)

Sumber: README resmi `yausername/youtubedl-android`; ketersediaan artifact dicek 22 Nov 2026 via `repo1.maven.org` → **HTTP 200** untuk `library` dan `ffmpeg` versi 0.18.1.

```kotlin
val youtubedlAndroid = "0.18.1"
repositories { mavenCentral() }
dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
}
```

`aria2c` **tidak** dipakai (tidak perlu untuk single stream audio).

Kewajiban konfigurasi dari README:
- `android:extractNativeLibs="true"` di manifest.
- `abiFilters`: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` — atau ABI splits untuk APK lebih kecil.
- Android 10 (API 29): `android:requestLegacyExternalStorage="true"`.
- API 30+: akses langsung hanya ke `Download/` dan `Documents/`, jadi output wajib di bawah `Download/Simpan/`.

API yang dipakai (dari README & contoh Seal):
- `YoutubeDL.getInstance().init(context)` dan `FFmpeg.getInstance().init(context)` di `Application.onCreate()`.
- `YoutubeDLRequest(url)` + `addOption(key, value)`.
- `YoutubeDL.getInstance().execute(request, callback, processId)` — callback `(progress: Float, eta: Long)`.
- `YoutubeDL.destroyProcessById(processId)` untuk **Batal**.
- `YoutubeDL.getInstance().getInfo(url)` untuk metadata awal.
- `YoutubeDL.CanceledException` harus ditangani sebagai pembatalan, bukan error.

Perintah unduhan yang dipakai:

```
-f bestaudio
-x
--audio-format mp3
--audio-quality 0
-o /sdcard/Download/Simpan/%(title)s.%(ext)s
--no-playlist
```

**Terverifikasi sebagian (22 Nov 2026):** `libffmpeg.zip.so` (arm64-v8a) dari artifact 0.18.1 mengandung referensi `libmp3lame.so`, sehingga encoder MP3 tersedia di bundled ffmpeg. **Masih wajib diuji di HP** apakah `--audio-format mp3` benar menghasilkan file `.mp3` yang bisa diputar. Jika gagal, fallback: `-x --audio-format m4a` lalu beri tahu pengguna, atau bundled ffmpeg lain.

---

## 5. Model data lokal (Room)

Satu entitas, tanpa FTS, tanpa relasi:

```
DownloadRecord
  id: Long                @PrimaryKey(autoGenerate = true)
  url: String
  title: String
  fileName: String
  outputPath: String
  durationSec: Int
  sizeBytes: Long
  status: String          QUEUED | RUNNING | DONE | FAILED | CANCELED
  progress: Float
  createdAt: Long
  finishedAt: Long?
  errorMessage: String?
```

DAO: `observeAll(): Flow<List<DownloadRecord>>`, `upsert`, `delete`. Tidak ada migrasi di MVP — hapus data app saat versi skipped.

---

## 6. Status & error mapping

| Kondisi | Pesan UI | Bisa retry | Aksi |
|---|---|---|---|
| URL kosong / bukan http(s) | "Link tidak valid" | Ya | Tetap di layar, fokus ke input |
| Host selain youtube.com / music.youtube.com | "Hanya link YouTube yang didukung" | Ya | Tolak sebelum memanggil library |
| Video private / tidak ada | "Video tidak tersedia" | Ya | Tampilkan di riwayat sebagai FAILED |
| Network mati / timeout | "Koneksi terputus" | Ya (manual) | Tombol "Coba lagi" di riwayat |
| Bot-check / 403 | "YouTube menolak permintaan. Coba lagi nanti" | Ya (manual) | Jangan auto-retry berulang |
| Encoder MP3 tidak ada | "MP3 tidak didukung perangkat ini" | Tidak | Fallback m4a + notifikasi |
| Penyimpanan penuh | "Storage penuh" | Ya (manual) | Hapus file lama dari riwayat |
| Pengguna tekan Batal | "Dibatalkan" | Ya | `destroyProcessById`, status CANCELED |

Aturan retry: **tidak ada auto-retry**. Semua kegagalan setelah request dikirim harus dimulai ulang oleh pengguna.

---

## 7. UI/UX

Berdasarkan mockup yang disetujui (`Malam Radio`):

- Layar awal: label merah "Tempel linknya", input URL + tombol TEMPEL, CTA merah "↓ UNDUH MP3", catatan kualitas, kartu riwayat kosong.
- Proses: kartu berjudul lagu, angka progres besar, meter bersegmen, teks "1,2 / 4,4 MB", tombol "BATALKAN". Aksen **kuning**.
- Selesai: banner hijau "Tersimpan di Downloads", daftar riwayat 2 baris dengan ikon putar & bagikan. Aksen **hijau mint**.
- Tipografi: **Fraunces** (judul/brand), **IBM Plex Mono** (label & data), **DM Sans** (tubuh). Ketiganya didaftarkan sebagai font resource Compose.
- Warna: latar `#0B0D0B`, permukaan `#171B17`, ink `#F3F0E7`, coral `#FF7654`, amber `#F3C45C`, mint `#7CE0B4`.
- Target sentuh minimal 44 dp; ukuran teks data minimal 12 sp; judul lagu maksimal 2 baris.
- Perubahan warna per tahap adalah bagian dari status, bukan dekorasi.

---

## 8. Izin & privasi

- `INTERNET`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS` (Android 13+), `WAKE_LOCK`.
- Tidak meminta izin baca/tulis storage umum. Output ditulis ke `Download/Simpan/` dan didaftarkan via MediaStore.
- Tidak menyimpan cookie, token, riwayat ke cloud, atau data pribadi selain URL yang dimasukkan pengguna.
- Riwayat lokal bisa dihapus per item.
- Pemakaian ditujukan untuk konten yang boleh diunduh pengguna; tidak ada fitur bypass DRM atau pembatasan akun.

---

## 9. Build & delivery (kontrak mesin ini)

- `JAVA_HOME=~/jdk21`, `ANDROID_HOME=~/Android/Sdk`, tanpa `local.properties`.
- Versi wajib di-pin dari cache lokal: Gradle 8.14.3-bin, AGP 8.11.1, Kotlin 2.1.20, Compose BOM 2025.05.00, activity-compose 1.10.1, lifecycle 2.9.1, core-ktx 1.16.0.
- Salin `gradlew` + wrapper dari `~/Desktop/ArtifactChat`; jangan bootstrap dari nol.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m`, workers 2, parallel false.
- Tidak ada `/dev/kvm`: **tidak ada emulator**. Uji di HP pengguna.
- Build release via `terminal(background=true, notify_on_complete=true)`; perkiraan 10–15 menit + unduhan dependency.
- Verifikasi APK: `aapt dump badging` + `apksigner verify --print-certs`; kirim sebagai `MEDIA:/abs/path.apk` dengan applicationId.
- Jika applicationId sama tapi berbeda signing dengan APK lama, pengguna harus uninstall dulu.

---

## 10. Fase implementasi

| Fase | Isi | Keluar |
|---|---|---|
| 0 — Spike | scaffold minimal + init library + satu unduhan MP3 nyata | Bukti encoder MP3 jalan di HP |
| 1 — Fondasi | Application init, service foreground, notifikasi, izin | Unduhan jalan saat UI ditutup |
| 2 — Engine | `getInfo`, perintah unduhan, progres, batal, error mapping | Alur backend stabil |
| 3 — UI | Komposisi sesuai mockup, tiga state, animasi transisi warna | Layar sesuai desain |
| 4 — Riwayat | Room, putar via intent, bagikan file, hapus item | Riwayat fungsional |
| 5 — Final | Font resource, polish, sign release, verifikasi APK | APK siap kirim |

MVP = fase 0–4. Fase 5 hanya setelah pengujian HP selesai.

---

## 11. Verifikasi wajib sebelum coding

1. `curl -I` ke kedua artifact Maven Central — **sudah dilakukan**, HTTP 200.
2. Periksa cache Gradle lokal untuk versi di §9; jika tidak ada, unduh lebih dulu.
3. **Spike encoder MP3**: jalankan perintah §4 di HP;cek file hasil benar-benar `.mp3` dan bisa diputar. Jika gagal, terapkan fallback m4a sebelum menulis UI.
4. Verifikasi file muncul di aplikasi Downloads sistem (MediaStore scan).
5. Verifikasi `destroyProcessById` benar-benar menghentikan proses.
6. Verifikasi notifikasi tetap tampil saat aplikasi ditutup.
7. Verifikasi perilaku di Android 10 (legacy storage) dan Android 13+ (POST_NOTIFICATIONS).

---

## 12. Kriteria penerimaan

- Link `https://music.youtube.com/watch?v=JaWEMiFf0-A` menghasilkan file MP3 di `Download/Simpan/`.
- Progres naik dari 0 → 100, warna berubah merah → kuning → hijau.
- Tombol Batal menghentikan unduhan dan tidak meninggalkan file setengah jadi.
- Riwayat menampilkan judul, durasi, ukuran, dan bisa dibuka/dibagikan.
- Link invalid/private menampilkan pesan yang sesuai, bukan crash.
- APK terpasang di HP tanpa uninstall (jika signing pertama kali).

---

## 13. Sumber

- https://github.com/yausername/youtubedl-android — README resmi (instalasi, init, API, batas storage)
- https://github.com/JunkFood02/Seal — contoh pemakaian nyata (`Downloader.kt`, cancel, notifikasi)
- https://repo1.maven.org/maven2/io/github/junkfood02/youtubedl-android/ — verifikasi artifact 0.18.1
- https://github.com/yt-dlp/yt-dlp — dokumentasi opsi `-x`, `--audio-format`, `--audio-quality`
- Mockup: `/home/trfth/Desktop/Desain_v4_Malam_Radio_Tipografi.png`
