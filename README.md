# Code Editor RH

android code editor dengan fitur:

- full screen editor + tab
- buka file via saf
- buat project di `/sdcard/codeEditorRH/projectName/`
- akses storage pakai manage all files
- custom syntax highlight (format `.sxh` version 3)
- preview html via localhost (nanohttpd)
- support relative asset html
- ui rounded khas ios + material icons

## identitas

- package: `rh.code.editor`
- label: Code Editor
- version: 1.0 (versionCode 1)

## build

project ini dirancang untuk di-build lewat github actions.

1. push ke github
2. workflow `Build APK` akan otomatis jalan
3. download artifact `CodeEditorRH-debug`

atau build lokal:

```bash
./gradlew assembleDebug
```

## permission

aplikasi membutuhkan **kelola semua file** (manage external storage) untuk fitur project di `/sdcard/codeEditorRH/`.

## format .sxh (version 3)

format berbasis kurawal, 1 file bisa berisi banyak bahasa.

contoh ada di `app/src/main/assets/syntaxHighlight/`

```
// sxh v3

lang JavaScript {
  ext js jsx mjs cjs

  color keyword #FF7B72
  color string #A5D6FF
  color comment #8B949E
  color number #79C0FF
  color default #E6EDF3

  keyword {
    var let const function return if else for while
    true false null undefined
  }

  match string /"(?:[^"\\]|\\.)*"/
  match string /'(?:[^'\\]|\\.)*'/
  match comment /\/\/.*$/
  match number /\b\d+(\.\d+)?\b/
}

lang HTML {
  ext html htm

  color tag #7EE787
  color string #A5D6FF
  color comment #8B949E
  color default #E6EDF3

  match comment /<!--[\s\S]*?-->/
  match tag /<\/?[A-Za-z][A-Za-z0-9]*/

  region {
    start <script
    end </script>
    lang JavaScript
  }

  region {
    start <style
    end </style>
    lang CSS
  }
}
```

aturan:
- `lang Nama { ... }` = definisi bahasa
- `ext a b c` = ekstensi file
- `color nama #hex` = warna
- `keyword { ... }` = daftar keyword (bisa multi baris, dipisah spasi)
- `match nama /regex/` = pola regex
- `region { start ... end ... lang ... }` = blok bahasa lain (misal script di html)
- keyword hanya diwarnai kalau posisi masih default (jadi gak nimpah string/comment)
- region start boleh tanpa `>` biar support atribut (`<script type="module">`)
