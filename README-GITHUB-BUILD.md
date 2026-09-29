# Build the APK without Android Studio

This project includes a GitHub Actions workflow that builds the debug APK for you in the cloud.

## Steps

1. Create a free GitHub account if you do not already have one.
2. Create a **new empty repository** on GitHub, for example `SubtitleOverlayMVP`.
3. Upload the **contents of this folder** to the repository (the `app` folder, `build.gradle`, `settings.gradle`, `.github`, etc.). Do not upload the outer ZIP folder as an extra level.
4. Open the repository's **Actions** tab.
5. Select **Build APK** and press **Run workflow** (or push to `main`; it also builds automatically).
6. Wait for the green checkmark.
7. Open the completed workflow run and scroll to **Artifacts**.
8. Download `SubtitleOverlayMVP-debug-apk` and extract `app-debug.apk`.
9. Copy the APK to your Android tablet and install it.

The workflow uses GitHub-hosted Linux build machines, JDK 17, Gradle 8.9 and the Android SDK already available on the runner. No Android Studio is required on your computer.
