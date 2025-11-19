# AstralEbook

Generator EPUB/PDF sederhana berbasis Compose untuk Android. Aplikasi ini memungkinkan Anda memilih file teks/DOCX sebagai isi buku, menambahkan sampul, mengatur metadata, tema, ukuran halaman, serta font kustom sebelum mengekspor dokumen.

## Format teks khusus untuk file `.txt`
Saat sumber body berupa file teks biasa, Anda bisa menambahkan gaya sederhana langsung dari isi file:

| Contoh | Hasil |
| --- | --- |
| `*teks miring*` | Huruf miring |
| `**teks tebal**` | Huruf tebal |
| `***teks tebal miring***` | Huruf tebal + miring |

Tulisan tanpa penutup (`*`) dianggap apa adanya. Gunakan pasangan tanda bintang yang sama untuk membuka dan menutup gaya agar parser dapat mengenali formatnya.

## Font kustom
1. Masuk ke bagian **Fonts** pada layar utama.
2. Pilih jenis teks (Title, Heading, Body), ganti opsi menjadi **Custom**, lalu tekan tombol **Pilih font**.
3. Pilih berkas `.ttf` atau `.otf` melalui Storage Access Framework.
4. Aplikasi akan menyimpan izin baca dan menyematkan font tersebut ke dalam PDF maupun EPUB.

Anda dapat menekan tombol **Hapus** untuk kembali ke font bawaan kapan saja.
