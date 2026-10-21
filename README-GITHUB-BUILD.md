# Subtitle Overlay v1.3.1 — Manual Timing Controls Gate

This ZIP contains the complete Android project source for building with GitHub Actions.

## Build
1. Upload/replace the repository source with the contents of this ZIP.
2. Open the **Actions** tab and run **Build APK**.
3. Download the generated debug APK artifact after the workflow succeeds.

## Changes in v1.3.1
- Playback speed and subtitle offsets are only editable while Manual Override is enabled.
- Automatic ACCESS/MEDIA/HOLD time is used as-is; manual timing scale and offsets are not applied to automatic sources.
- Manual playback speed is shared between the overlay Sync dialog and the app's timing section.
- Overlay timing controls are disabled in automatic mode and refreshed when settings change.

Build and device validation have not yet been confirmed in this environment.
