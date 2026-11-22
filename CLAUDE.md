# FOHanalyzer

## UI layout: see, set, do

Every control and readout has exactly one home, decided by what the engineer does with it:

- **See — the right rail** (`ui.ControlRail`). Live readouts and the state in effect, each
  shown once. Read only: no toggles, steppers, sliders or segmented controls. The one
  interaction is clicking a logged ring to locate it on the plot. A setting the rail depends
  on is shown as text (e.g. `trim −44.0 dB`, `LZeq · 5 min`), never as its control.
- **Set — Preferences, ⌘,** (`ui.PreferencesWindow`). Everything that is a setting: inputs,
  solo trim, SPL calibration and Leq window, display and analysis options, the MCP server,
  resetting saved settings.
- **Do — the strip above the plot** (`ui.ActionStrip`). Momentary actions and view switches
  pressed during a show: the view (RTA / Split / Spectro), which traces and overlays are
  drawn, capture reference, detect ring, and the resets.
- **The header** carries identity and status only (wordmark, LIVE, sample rate, clock), no
  readouts — they would repeat the rail.

When adding something, put it where this says, not where there happens to be room. If it
does not fit any of the three, ask rather than bending the rule.

## Build

`mvn test` runs the tests and the formatter check; `mvn net.revelc.code.formatter:formatter-maven-plugin:format`
fixes formatting. `mvn -q -o javafx:run` runs the app.
