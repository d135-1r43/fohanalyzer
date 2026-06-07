# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

FOHanalyzer is a real-time dual-channel RTA (Real-Time Analyzer) web app for Front of House engineers. It displays two overlaid frequency spectrum traces — a measurement mic and a console solo bus — to compare room response vs. source signal. Designed for iPad landscape use.

## Commands

```bash
npm run dev        # Vite dev server (hot reload)
npm run build      # Production build → dist/
npm run preview    # Serve the production build locally
```

No test or lint scripts are configured. Docker deployment uses nginx to serve the static build:
```bash
docker compose up  # Runs ghcr.io/d135-1r43/fohanalyzer:<version>
```

## Architecture

**Entry point**: `src/main.js` → mounts `App.svelte` into `#app`

**Three layers:**

1. **Audio engine** (`src/lib/`)
   - `engine.js` — simulation: generates beat-synchronized frequency-domain data with Gaussian bumps for kick/snare/bass/hat/lead; also exports band-center computation for log-spaced octave fractions
   - `audioInput.js` — Web Audio API wrapper: `getUserMedia` → `AnalyserNode` (FFT size 16384) → `readBands()` interpolates FFT bins into log-spaced band values

2. **State** (`App.svelte`)
   - All application state lives here as Svelte 5 `$state` runes
   - Key state: `micOn/soloOn` (visibility), `frac` (octave resolution: 1/1→1/24), `smoothing`, `avgN`, `micChan/soloChan` (channel presets with gain+tilt voicing), `micSource/soloSource` (live Web Audio instances or null for simulation), `ring` (ring-out assist), `reference` (captured snapshot)
   - Live input is connected/disconnected via `$effect` watching channel selection

3. **Rendering** (`src/lib/AnalyzerCanvas.svelte`)
   - Canvas 2D animation loop (~22ms): reads simulated or live band data → applies voicing (gain+tilt, sim only) → IIR averaging → fast-attack/slow-release smoothing → peak hold → draws
   - Draw order: background → dB grid → frequency grid → zone color strip → bars (solo behind, mic in front, with glow+bright caps) → peak ticks → transfer function overlay (mic−solo, ±24 dB) → reference ghost → peak markers with note names → ring-out highlight → hover cursor

**Other components**: `SourceCard.svelte` (per-source controls), `ChannelSelect.svelte`, `Toggle.svelte`, `Segmented.svelte`, `Meter.svelte`

## Key Conventions

**Signal processing:**
- Frequency bands are log-spaced; resolution set by `frac` (1=one per octave, 12=twelve per octave, etc.)
- Smoothing uses separate fast-attack/slow-release EMA coefficients
- Peak hold decays at 0.42 dB/frame after manual release

**Voicing presets** (per channel): `{ g: <gain dB>, tilt: <dB/octave> }` applied only to simulated sources, not live audio

**Color system**: Cyan `#22d3ee` = measurement mic, Amber `#f5a524` = solo bus, Lime `#a3e635` = interactive/highlight

**Framework**: Svelte 5 runes syntax (`$state`, `$props`, `$derived`, `$effect`) — not the legacy Options API
