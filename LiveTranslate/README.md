# Live Translate (English -> Indonesia)

Overlay terjemahan langsung di atas aplikasi lain. Mendengar suara lewat mikrofon.

## Build APK (tanpa install apa pun): GitHub Actions
1. Buat repo baru di github.com, unggah SELURUH isi folder ini (termasuk folder .github).
2. Buka tab Actions -> "Build APK" -> tunggu hijau (3-6 menit).
3. Buka run tersebut, unduh artifact "LiveTranslate-apk" (zip berisi app-debug.apk).
4. Pasang APK di ponsel (izinkan "install dari sumber tidak dikenal").

## Build lewat Android Studio
Buka folder ini -> tunggu sync -> Build > Build APK(s).

## Pemakaian
1. Buka app, ketuk tombol 1 dan izinkan overlay.
2. Ketuk tombol 2, izinkan mikrofon, lalu putar video bahasa Inggris.
3. Kotak terjemahan melayang di layar; geser untuk memindahkan, ketuk "tutup" untuk berhenti.
Pertama kali butuh internet untuk mengunduh model terjemahan (~30 MB); setelah itu terjemahan offline.
