# Changelog

Notable changes to FOHanalyzer, newest first. Each version links to its GitHub release, which
carries the `.dmg`. Dependency bumps are summarised rather than listed one by one.

## [2.6.0] — 2026-10-18

### Added

- An optional MCP server, read-only and only reachable from this machine. AI assistants such
  as Claude Code, Claude Desktop or ChatGPT can use it to read the ring-out history, the
  readouts and the current band levels. Switch it on in Preferences, which also shows how to
  connect each assistant.
- A solo trim in the Solo Bus card offsets the solo trace so it lines up with the mic. **Match**
  lines up the averages in one click.
- A ± nudge on the SPL meter for when it drifts from a hand-held meter.
- **Cmd+,** opens the preferences window.

### Changed

- The SPL meter can be hidden in Preferences. It stays hidden until the mic is calibrated, and
  when it is shown uncalibrated it is labelled dBFS instead of SPL.

### Dependencies

- SLF4J updated.

## [2.5.0] — 2026-09-27

### Added

- The `.dmg` has a volume icon drawn from the logo.

### Changed

- The preferences window fits its content and can no longer be resized.
- When SPL calibration is disabled because the mic is simulated, the UI says why.

### Fixed

- The control rail no longer scrolls sideways.

### Dependencies

- JavaFX, SLF4J, JUnit and the Maven plugins updated.
- Release notes are now built from commits rather than pull requests.

## [2.4.0] — 2026-08-09

The first release of the JavaFX version, shipped as a self-contained macOS bundle.

### Added

- FOHanalyzer rewritten as a JavaFX desktop app.
- Spectral analysis on TarsosDSP, with ring detection.
- Live inputs open with all of the device's channels.
- The solo bus is read as a stereo pair and merged in the power domain.
- Settings are kept between launches, including where the preferences window was left.
- Controls you set once moved from the rail into the preferences window.
- The version shown in the app comes from the jar manifest.
- A self-contained macOS `.dmg` that bundles its own Java runtime, built and released by
  GitHub Actions.

### Fixed

- An unconnected source no longer stops the display from rendering.
- Capture failures are logged with their full exception.
- The Gatekeeper instructions work on macOS 15 and later.

### Changed

- Runs on Java 25 LTS and JavaFX 26.
- Logs through SLF4J instead of the console.
- Licensed under GPL-3.0-or-later.

[2.6.0]: https://github.com/d135-1r43/fohanalyzer-java/releases/tag/fohanalyzer-java-2.6.0
[2.5.0]: https://github.com/d135-1r43/fohanalyzer-java/releases/tag/fohanalyzer-java-2.5.0
[2.4.0]: https://github.com/d135-1r43/fohanalyzer-java/releases/tag/fohanalyzer-java-2.4.0
