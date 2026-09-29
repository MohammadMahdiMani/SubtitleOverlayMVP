# Subtitle Overlay MVP v0.1
Android tablet prototype: choose SRT, grant overlay permission, start overlay. Optional Accessibility service tries to read a visible MM:SS/HH:MM:SS player time from the active window. If the browser does not expose time, use the +/- 0.5s controls and the internal timer.

Build in Android Studio with JDK 17. `./gradlew assembleDebug` after generating the Gradle wrapper or using Android Studio's Gradle.
