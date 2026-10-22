# Subtitle Overlay v1.3.2 — Overlay UI Restoration

This ZIP contains the complete Android project source for building with GitHub Actions.

## Build
1. Upload/replace the repository source with the contents of this ZIP.
2. Open the **Actions** tab and run **Build APK**.
3. Download the generated debug APK artifact after the workflow succeeds.

## Changes in v1.3.2
- Restored the overlay auto-hide timeout selector in the app settings (3 seconds, 5 seconds, 10 seconds, or always visible).
- Restored the overlay Hide control, which cycles through the auto-hide settings.
- Split overlay controls into separate design and timing/action rows.
- Removed persistent speed and offset buttons from the overlay toolbar; those settings remain in the Sync dialog and are available only in Manual Override.
- Added colored overlay button styles with pressed, selected, hovered, focused, and disabled states. Exit is red, Sync is blue, Play/Pause is green, and auto-hide is purple.
- Changed manual time input to a numeric keyboard. Numeric-only input is supported: `1234` means `12:34`, `11234` means `1:12:34`, and `123456` means `12:34:56`; colon-formatted times remain accepted too.
- Pinned START OVERLAY and STOP OVERLAY buttons to the bottom of the main screen so they remain available while scrolling.

Build and device validation have not yet been confirmed in this environment.
