# Silver TV (TVfS) Development Rules

## Senior Accessibility & UI Principles
1. **Typography**: Always maintain minimum `22sp` text size across all screens (`38sp` primary headings, `30sp` card titles, `24sp` metadata).
2. **Focus Visibility**: Every interactive surface must render a solid `4dp` Solar Yellow (`#FFD54F`) focus border with a subtle scale factor.
3. **Viewport Constraints**: Target viewport is `960x540dp` (`1920x1080` @ `320dpi`). Ensure zero vertical clipping by restricting text overflow to single-line ellipses inside grid cards.
4. **Remote Control Ergonomics**:
   - Back single-tap toggles drawers/overlays without interrupting video playback.
   - Back double-tap prompts "Returning to Main Menu" with "Keep Watching" focused by default.
5. **Git & CI Workflow**:
   - Feature branches must pass GitHub Actions `build-and-test` before merging to `main`.
   - Main branch commits automatically trigger Render static site updates and debug APK builds.
