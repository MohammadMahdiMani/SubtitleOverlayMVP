# SubtitleOverlayMVP v0.3.3

Built from the working v0.3.2 project.

Changes:
- Overlay controls auto-hide after 3 seconds but can always be reopened with the small `≡` handle at the top-right.
- Added subtitle position selection: Bottom / Lower / Center / Upper / Top.
- Added TTF/OTF custom font import. The font is copied into the app's private storage and can be selected as `Custom`.
- Added `Pos` to the floating controls so position can be cycled while the overlay is running.
- Improved SRT text cleanup for ASS/SSA override tags such as `{\\an8}`, `{\\pos(...)}`, `{\\c&H...&}`, `{\\i1}` and common ASS escape sequences.
- Existing working MediaSession + Accessibility sync behavior is retained.
