# Code Editor RH

android code editor dengan fitur:

- full screen editor + tab
- buka file via saf
- buat project di `/sdcard/codeEditorRH/projectName/`
- akses storage pakai manage all files
- custom syntax highlight (format `.sxh` version 1)
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

## format .sxh (version 1)

contoh ada di `app/src/main/assets/syntaxHighlight/`

```
version:1
name:JavaScript
extensions:js,jsx
keywords:var,let,const,...
colors:
  keyword:#FF7B72
  string:#A5D6FF
rules:
  string: ".*?"|'.*?'
```
