# FOHanalyzer

[![CI](https://github.com/d135-1r43/fohanalyzer/actions/workflows/ci.yml/badge.svg)](https://github.com/d135-1r43/fohanalyzer/actions/workflows/ci.yml)
[![Release](https://github.com/d135-1r43/fohanalyzer/actions/workflows/release.yml/badge.svg)](https://github.com/d135-1r43/fohanalyzer/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/d135-1r43/fohanalyzer?cacheSeconds=0)](https://github.com/d135-1r43/fohanalyzer/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-22c55e)](LICENSE)
[![Svelte](https://img.shields.io/badge/Svelte-5-FF3E00?logo=svelte&logoColor=white)](https://svelte.dev)
[![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)](https://vitejs.dev)
[![Live](https://img.shields.io/badge/live-fohanalyzer.com-22d3ee)](https://fohanalyzer.com)

Real-time dual-channel RTA for Front of House engineers. Overlays a measurement mic trace against a console solo bus so you can compare room response to source signal directly in the browser — no install required.

Designed for iPad landscape. Also works on any modern desktop browser.

---


## Usage

Open the app and select a source for each channel:

- **Measurement Mic** (cyan) — point this at your measurement microphone input
- **Solo Bus** (amber) — point this at your console's PFL/solo output

Both channels can run in **simulation mode** (generated test signal) or **live mode** (real audio input via your browser's microphone permission). Switch between them using the channel selector in each source card.

When a live device is selected, a channel stepper (`‹ Ch 1 / 2 ›`) appears below the device picker, letting you route the left or right channel of the input independently to each analyzer trace.

### Controls

| Control | Description |
|---|---|
| Resolution | Octave fraction: 1/1 through 1/24 bands |
| Smoothing | EMA time constant — higher = slower response |
| Avg | Number of frames to average |
| Peak hold | Latch peaks; click to release |
| Transfer fn | Overlay mic−solo difference (±24 dB) |
| Ref capture | Snapshot the current trace as a ghost reference |
| Ring-out | Highlight a ringing frequency on the analyzer |

---

## Caveats

### Browsers cap audio inputs at 2 channels

Browsers enforce a 2-channel (stereo) limit on `getUserMedia` regardless of your interface's actual channel count. A Focusrite 18i20 with 18 inputs will still only appear as a stereo device. The channel stepper gives you Ch 1 or Ch 2 — that's the ceiling the browser allows.

**Workarounds:**

- **Your interface's mixer software** (e.g. Focusrite Control) — route the two physical channels you want to Mix 1 L/R. The browser sees them as left and right. No extra software needed.
- **[BlackHole](https://existential.audio/blackhole/)** (free) — virtual audio driver. Create a stereo BlackHole device fed from any two channels of your interface, then select it in FOHanalyzer.
- **[Loopback](https://rogueamoeba.com/loopback/)** (~$99) — same idea with a GUI for complex routing.

### HTTPS required for microphone access

Browsers only grant microphone permission on `localhost` or an HTTPS origin. A plain `http://` deployment will silently fail to enumerate or connect audio devices.

### Safari / iOS

Safari supports the Web Audio API but may limit FFT resolution and channel count more aggressively than Chrome or Firefox. Chrome on desktop gives the best results.

---

## Building

Requires Node.js 18+.

```bash
npm install
npm run dev       # Dev server with hot reload → http://localhost:5173
npm run build     # Production build → dist/
npm run preview   # Serve the production build locally
npm test          # Run the test suite
```

### Docker

The production build is served as a static site behind nginx.

```bash
docker compose up
```

The `docker-compose.yml` pulls a versioned image from GHCR. To build your own:

```bash
docker build -t fohanalyzer .
docker run -p 8080:80 fohanalyzer
```

### Self-hosting

Deploy the contents of `dist/` to any static host (nginx, Caddy, S3, Netlify, etc.). No server-side component required. Ensure HTTPS is configured so microphone access works.

---

## Stack

- [Svelte 5](https://svelte.dev) — UI and state (runes syntax)
- [Vite](https://vite.dev) — build tooling
- Web Audio API — FFT via `AnalyserNode` (FFT size 16384)
- Canvas 2D — rendering loop at ~22 ms/frame

## License

MIT
