# Changelog

Notable changes to FOHanalyzer, newest first. Each version links to its GitHub release, which
carries the `.dmg`. Dependency bumps are summarised rather than listed one by one.

## [Unreleased]

### Added

- A spectrograph: a scrolling time × frequency view on the RTA's frequency axis, newest at the
  top. The new **View** section on the rail switches between the RTA, a split with the
  spectrograph under the RTA, and the spectrograph alone. It shows the mic, the solo bus or
  Mic−Solo, over 10, 30 or 60 seconds, with an adjustable colour range. A located ring is
  marked down through it too.
- The SPL meter keeps a record instead of one passing number. It shows the Leq over a
  window of 10 s, 1, 5, 15 or 60 minutes, the Fast level, and the maximum and peak since a
  reset. While the meter is calibrated, every second
  is logged to a CSV in `~/Documents/FOHanalyzer/SPL logs`, one file per session. Levels
  are unweighted for now and labelled Z (`LZeq · 5 min`). The MCP `get_stats_history` tool
  reports the Leq with its window and weighting.

### Changed

- The window is split by what you do: the **rail only shows** (levels, loudest band, SPL
  meter, logged rings, and what the plot is computed with), **Preferences holds every
  setting** (now with Display, Analysis and General pages, plus the solo trim, the SPL nudge
  and the Leq window), and a **strip above the plot** has what you press during a show —
  the view, traces and overlays, *Capture reference*, *Detect ring* and a *Reset* menu.
- The header no longer repeats the readouts; it shows the wordmark and the live status.

### Removed

- The *Inject feedback* practice mode. Ring-out assist only shows when the mic is a live input.

### Fixed

- The window lagged and could stall: drawing the plot kept the GPU busy for longer than a
  frame. It now runs at a steady 60 fps on far less CPU, and the spectrum is computed on the
  audio thread instead of the UI thread.
- A live input could show nothing after a restart: the card named the right channel, but the
  analyser was reading a different one. Input changes are now applied in the order they were
  made, so the channel shown is always the one being read.

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

[Unreleased]: https://github.com/d135-1r43/fohanalyzer-java/compare/fohanalyzer-java-2.6.0...HEAD
[2.6.0]: https://github.com/d135-1r43/fohanalyzer-java/releases/tag/fohanalyzer-java-2.6.0
[2.5.0]: https://github.com/d135-1r43/fohanalyzer-java/releases/tag/fohanalyzer-java-2.5.0
[2.4.0]: https://github.com/d135-1r43/fohanalyzer-java/releases/tag/fohanalyzer-java-2.4.0
