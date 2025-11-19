# AstralEbook

Generator PDF sederhana berbasis Compose untuk Android. Aplikasi ini memungkinkan Anda memilih file teks/DOCX sebagai isi buku, menambahkan sampul (dengan opsi full-bleed atau tetap memakai margin), mengatur metadata, tema, ukuran halaman, serta font kustom sebelum mengekspor dokumen.

## Format teks khusus untuk file `.txt`
Saat sumber body berupa file teks biasa, Anda bisa menambahkan gaya sederhana langsung dari isi file:

| Contoh | Hasil |
| --- | --- |
| `*teks miring*` | Huruf miring |
| `**teks tebal**` | Huruf tebal |
| `***teks tebal miring***` | Huruf tebal + miring |
| `__teks bergaris bawah__` atau `[u]teks bergaris bawah[/u]` | Huruf bergaris bawah |
| `~~teks dicoret~~` atau `[s]teks dicoret[/s]` | Huruf dicoret |
| `[center]Paragraf ini rata tengah[/center]` | Paragraf rata tengah |
| `[align=right]Paragraf ini rata kanan[/align]` | Paragraf rata kanan |
| `[left]Paragraf ini rata kiri[/left]` | Paragraf rata kiri (mengabaikan indent bawaan) |

Tulisan tanpa penutup (`*`) dianggap apa adanya. Gunakan pasangan tanda bintang yang sama untuk membuka dan menutup gaya agar parser dapat mengenali formatnya.

Penanda `[center]...[/center]`, `[left]...[/left]`, `[right]...[/right]`, dan `[align=justify]...[/align]` berlaku untuk satu paragraf (dipisahkan oleh baris kosong). Anda dapat mengombinasikannya dengan gaya lain seperti tebal/miring untuk teks pada paragraf tersebut.

## Font kustom
1. Masuk ke bagian **Fonts** pada layar utama.
2. Pilih jenis teks (Title, Heading, Body), ganti opsi menjadi **Custom**, lalu tekan tombol **Pilih font**.
3. Pilih berkas `.ttf` atau `.otf` melalui Storage Access Framework.
4. Aplikasi akan menyimpan izin baca dan menyematkan font tersebut ke dalam PDF maupun EPUB.

Anda dapat menekan tombol **Hapus** untuk kembali ke font bawaan kapan saja.
