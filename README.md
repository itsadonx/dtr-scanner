# DTR Scanner

Android app for **Daily Time Record (DTR)** workflows: scan a QR code and save the decoded text together with the device’s current date and time.

## Features

- Live QR scanning (rear camera)
- Each scan is stored locally with timestamp (`yyyy-MM-dd HH:mm:ss`)
- History list with per-item delete and “Clear all”
- Debug APK built automatically on GitHub Actions

## Build APK on GitHub

1. Create a new repository on GitHub and push this project:

   ```bash
   git init
   git add .
   git commit -m "Add DTR QR scanner Android app"
   git branch -M main
   git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
   git push -u origin main
   ```

2. Open the **Actions** tab on GitHub. The **Build APK** workflow runs on every push to `main`.

3. When the run finishes, open the workflow run → **Artifacts** → download `dtr-scanner-debug-apk`.

4. Install on Android: enable “Install unknown apps” for your file manager, then open `app-debug.apk`.

## Local build (optional)

Requirements: Android Studio Ladybug or newer, JDK 17.

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes

- Data is stored only on the device (Room/SQLite). No cloud sync in this version.
- Duplicate scans of the same QR within ~2.5 seconds are ignored to avoid double entries.
