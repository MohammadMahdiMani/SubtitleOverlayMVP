# SubtitleOverlayMVP

Version 1.1.2.

Base: verified v1.1.1 synchronization behavior (Accessibility → MediaSession handoff/seek handling).

Included changes:
- v1.2 UI improvements consolidated onto the v1.1.1 base.
- Primary/secondary SRT selections persist across Activity recreation and font changes.
- Main app and running overlay share SettingsStore as the single source of truth.
- Appearance changes from the main app are reflected immediately in the running overlay.
- Timing/speed/offset changes are reflected immediately in the overlay.
- Dual subtitle settings update live.
- Color selection uses an RGB color dialog instead of fixed color choices.
- Buttons have clear pressed/hover/focus states.
- Permission status indicators cover Overlay, Notifications/Media Session, and Accessibility.
- UI follows the Android device light/dark mode.
- Background fit-to-subtitle, padding, and opacity controls.
- Overlay controls are grouped into Appearance and Timing/Playback rows.
- Overlay controls auto-hide can be set to 3 seconds, 5 seconds, 10 seconds, or Always visible.
- The overlay itself also exposes the auto-hide setting through its Hide button.
- Overlay Color/BG buttons open a color dialog.

Build with GitHub Actions using the included workflow.
