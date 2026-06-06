# FOHanalyzer 2.0

A real-time dual spectrum analyzer for Front of House engineers. Built with Svelte 5 + Vite.

Designed for iPad landscape at the console — shows two overlaid RTA traces (measurement mic + solo bus) so you can compare room response to the console signal at a glance.

## Features

- **Dual RTA bars** — cyan for measurement mic, amber for solo bus, with glowing fills and bright level caps
- **Log 20 Hz–20 kHz** axis with dB grid and frequency zone color strip (Sub → Air)
- **Octave resolution** — 1/1, 1/3, 1/6, 1/12, 1/24
- **Smoothing + averaging** — configurable response time
- **Peak hold** with manual reset
- **Top-offender markers** — labels the 3 loudest peaks with frequency and note name
- **Hover cursor** — shows freq, note, per-source dB, and Δ between sources
- **Transfer function** overlay (Mic − Solo) on a ±24 dB axis
- **Reference capture** — snapshot both traces as a dashed ghost for A/B comparison before/after EQ
- **Ring-out assist** — injects a simulated monitor feedback ring; logs each event with a GEQ suggestion; tap a log entry to locate it on the analyzer
- **Channel selectors** — patch each source to its console input (with per-channel voicing)

> Signal is simulated — realistic live audio driven by a beat-based engine. Wire to Web Audio for real mic/console input.

## Dev

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```
