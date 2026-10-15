# SubtitleOverlayMVP

Version 1.2.0.

Based on stable v0.3.4.2 and the verified v1.1.1 synchronization fixes. Preserves the Accessibility → MediaSession sync architecture, dual SRT subtitles, subtitle timing controls, custom fonts, positioning, and overlay behavior.

## v1.2.0 UI improvements

- Visible pressed / hover / focus states for buttons.
- Primary, secondary, danger, and settings actions are visually separated.
- Color selection uses a dedicated color dialog with RGB controls instead of fixed color buttons.
- Permission status indicators show whether Overlay, Notifications/Media Session, and Accessibility are enabled.
- Selected subtitle files are persisted so changing fonts or recreating the Activity does not lose the selected subtitles.
- Font changes now update an already-running overlay immediately.
- Light and dark UI themes follow the Android device system theme.
- Overlay control buttons also have clearer pressed/hover states.

Build with GitHub Actions using the included workflow.


## v1.2.2 sync reliability changes
- Poll active Android MediaSessions every 500 ms so the overlay receives fresh playback position even when the session does not emit frequent callbacks.
- Refresh the active session list periodically and prioritize a playing session over a stale paused session.
- Permit direct MEDIA takeover when Accessibility samples are absent or stale.
- Use the same source-selection decision for displayed status and subtitle timing.
- Keep the last valid fallback anchor; do not derive video time from device uptime.
