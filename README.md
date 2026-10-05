# Invoice Maker by RizogLabs

Proyek Android native (Kotlin + Jetpack Compose) berdasarkan PRD dan lima layar HTML yang dilampirkan. Antarmuka memakai Bahasa Indonesia, palet teal/amber, navigasi bawah, kartu dashboard, daftar invoice, katalog, dan pengaturan.

## Yang sudah dibuat

- Dashboard ringkas dengan total belum lunas, jumlah status, invoice terbaru, dan aksi buat invoice.
- Penyimpanan lokal SQLite untuk profil usaha, klien, produk, dan invoice. Data invoice menyimpan snapshot nama klien dan item.
- Tambah/cari/edit/hapus klien dan produk, termasuk SKU, deskripsi, harga, unit, pajak, dan catatan klien. Penghapusan klien yang sudah dipakai invoice diblokir.
- Buat, edit, duplikasi, cari, dan filter invoice; nomor `INV-YYYY-NNNN` unik dibuat dalam transaksi database. Status dapat diubah, dan jatuh tempo dihitung dari tanggal lokal.
- Kalkulasi subtotal, diskon, tarif pajak produk, ongkir, dan total dengan rupiah integer.
- Pembuatan PDF satu halaman dan pembagian melalui Android Sharesheet menggunakan content URI.
- Pengaturan profil usaha tersimpan lokal.

## Membuka proyek

Buka folder ini di Android Studio yang memiliki JDK 17 dan Android SDK Platform 35. Gradle akan meminta sinkronisasi plugin dan dependensi Compose. Minimum SDK disetel ke Android 8.0 (API 26). Lingkungan Codex tidak memiliki Android SDK/Gradle/ADB; build debug APK dilakukan di GitHub Actions.

## Status verifikasi

| Area | Status | Catatan |
| --- | --- | --- |
| Sumber proyek Android | PASS | Struktur Gradle Kotlin DSL dan source Kotlin tersedia. |
| Build APK debug | PASS | GitHub Actions run #6 berhasil dan mengunggah artifact APK. |
| Instalasi/jalankan di emulator atau perangkat | NOT RUN | Belum ada uji runtime di emulator atau perangkat fisik. |
| Data invoice lokal dan PDF | NOT RUN | Perlu build dan pemeriksaan pada perangkat/emulator. |
| RizogKey Engine 1.0.0 Protocol V1 | BLOCKED | SDK/integration pack, API contract, error mapping, konfigurasi produk, dan kredensial uji tidak ada pada lampiran. UI dengan sengaja tidak mengarang Installation Code, status ACTIVE, aktivasi, revalidasi, atau offline grace. |
| Multi-item invoice, pengurutan daftar, preview/print, backup/restore | Belum diimplementasikan | Perlu penyelesaian sebelum klaim MVP sesuai seluruh PRD. |

Jangan gunakan untuk distribusi produksi sebelum build, alur penting, dan integrasi lisensi resmi diverifikasi. Tidak ada perubahan pada proyek RupKas.

