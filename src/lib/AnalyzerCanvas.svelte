<script>
  import { onMount } from 'svelte';
  import { bandCenters, sample } from './engine.js';
  import { SignalState } from './signalState.js';
  import {
    PAD, DB_TOP, DB_BOT, dbY, fX, computeBarLayout,
    drawBackground, drawGrid,
    drawBars, drawHoldTicks, drawReference,
    drawTransferCurve, drawTransferLabels,
    drawPeakMarkers, drawLocateLine, drawHoverTooltip, drawZoneStrip,
  } from './draw.js';

  let {
    micOn = true,
    soloOn = true,
    frac = 3,
    smoothing = 0.62,
    avgN = 4,
    peakHold = false,
    holdReset = 0,
    markers = true,
    markerSource = 'mic',
    ring = { active: false, fc: 2500, t0: 0 },
    micVoice = { g: 0, tilt: 0 },
    soloVoice = { g: 0, tilt: 0 },
    micSource = null,
    soloSource = null,
    showTransfer = false,
    captureNonce = 0,
    onCapture,
    reference = null,
    showReference = true,
    locateFreq = null,
    onStats,
  } = $props();

  let wrapEl, canvasEl;

  const sig = new SignalState();
  let W = 0, H = 0, dpr = 1;
  let lastReport = 0, lastCapture = 0, holdResetStamp = 0;
  const mouse = { x: 0, y: 0, inside: false };

  onMount(() => {
    const t0 = performance.now();

    const onMove = (e) => {
      const r = canvasEl.getBoundingClientRect();
      const p = e.touches ? e.touches[0] : e;
      mouse.x = p.clientX - r.left; mouse.y = p.clientY - r.top; mouse.inside = true;
    };
    const onLeave = () => { mouse.inside = false; };
    canvasEl.addEventListener('pointermove', onMove);
    canvasEl.addEventListener('pointerdown', onMove);
    canvasEl.addEventListener('pointerleave', onLeave);

    const id = setInterval(frame, 22);

    function frame() {
      const cv = canvasEl, el = wrapEl;
      if (!cv || !el) return;
      const cw = el.clientWidth, ch = el.clientHeight;
      if (cw === 0 || ch === 0) return;
      if (cw !== W || ch !== H) {
        const d = Math.min(window.devicePixelRatio || 1, 2);
        cv.width = Math.round(cw * d); cv.height = Math.round(ch * d);
        cv.style.width = cw + 'px'; cv.style.height = ch + 'px';
        W = cw; H = ch; dpr = d;
      }

      const ctx = cv.getContext('2d');
      const t = (performance.now() - t0) / 1000;
      const plotW = W - PAD.l - PAD.r;
      const plotH = H - PAD.t - PAD.b;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, W, H);

      // Signal data
      const centers = bandCenters(frac);
      sig.ensureBands(frac, centers.length);
      if (holdReset !== holdResetStamp) { holdResetStamp = holdReset; sig.resetHold(); }

      const simData = sample(centers, t, ring);
      const liveMic  = micSource?.readBands(centers, frac);
      const liveSolo = soloSource?.readBands(centers, frac);
      const mvc = liveMic  ? { g: 0, tilt: 0 } : (micVoice  || { g: 0, tilt: 0 });
      const svc = liveSolo ? { g: 0, tilt: 0 } : (soloVoice || { g: 0, tilt: 0 });
      sig.update(liveMic ?? simData.mic, liveSolo ?? simData.solo, mvc, svc, centers, smoothing, avgN);

      if (onStats && t - lastReport > 0.12) {
        lastReport = t;
        const s = sig.getStats(centers);
        if (micSource) s.micRmsDbfs = micSource.readRMS();
        onStats(s);
      }
      if (captureNonce !== lastCapture) {
        lastCapture = captureNonce;
        if (captureNonce > 0 && onCapture)
          onCapture({ centers: Array.from(centers), mic: Array.from(sig.dispMic), solo: Array.from(sig.dispSolo) });
      }

      // Draw
      drawBackground(ctx, W, H);
      drawGrid(ctx, W, plotW, plotH);

      const { xs, barW } = computeBarLayout(centers, plotW);
      const baseY = PAD.t + plotH;
      const aT = showTransfer ? 0.3 : 0.5;
      const aB = showTransfer ? 0.05 : 0.07;
      const tfShown = showTransfer && micOn && soloOn;

      ctx.save();
      ctx.beginPath(); ctx.rect(PAD.l, PAD.t, plotW, plotH); ctx.clip();

      if (soloOn) drawBars(ctx, sig.dispSolo, xs, barW, plotH, baseY, 245, 165, 36, aT, aB);
      if (micOn)  drawBars(ctx, sig.dispMic,  xs, barW, plotH, baseY, 34, 211, 238, aT, aB);
      if (peakHold) {
        if (soloOn) drawHoldTicks(ctx, sig.holdSolo, xs, barW, plotH, 'rgba(245,165,36,0.85)');
        if (micOn)  drawHoldTicks(ctx, sig.holdMic,  xs, barW, plotH, 'rgba(34,211,238,0.9)');
      }
      if (reference && showReference) drawReference(ctx, reference, plotW, plotH, micOn, soloOn);
      if (tfShown) drawTransferCurve(ctx, centers, sig.dispMic, sig.dispSolo, W, plotW, plotH);

      ctx.restore();

      if (tfShown) drawTransferLabels(ctx, W, plotW, plotH);
      if (markers) {
        const src = markerSource === 'solo' ? sig.dispSolo : sig.dispMic;
        const on  = markerSource === 'solo' ? soloOn : micOn;
        const col = markerSource === 'solo' ? '#f5a524' : '#22d3ee';
        if (on) drawPeakMarkers(ctx, src, centers, plotW, plotH, W, col);
      }
      if (locateFreq) drawLocateLine(ctx, locateFreq, W, plotW, plotH);
      if (mouse.inside) drawHoverTooltip(ctx, mouse, centers, xs, sig.dispMic, sig.dispSolo, W, plotW, plotH, micOn, soloOn);
      drawZoneStrip(ctx, plotW, plotH, W, H);
    }

    return () => {
      clearInterval(id);
      canvasEl.removeEventListener('pointermove', onMove);
      canvasEl.removeEventListener('pointerdown', onMove);
      canvasEl.removeEventListener('pointerleave', onLeave);
    };
  });
</script>

<div class="analyzer-wrap" bind:this={wrapEl}>
  <canvas class="analyzer-canvas" bind:this={canvasEl}></canvas>
</div>

<style>
  .analyzer-wrap {
    width: 100%; height: 100%;
    border: 1px solid var(--line);
    border-radius: 12px; overflow: hidden; position: relative;
    background: #080b11;
    box-shadow: inset 0 0 0 1px rgba(255,255,255,.02), 0 10px 40px rgba(0,0,0,.4);
  }
  .analyzer-canvas { display: block; cursor: crosshair; touch-action: none; }
</style>
