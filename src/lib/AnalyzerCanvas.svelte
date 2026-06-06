<script>
  import { onMount } from 'svelte';
  import { freqNorm, normFreq, bandCenters, noteName, sample, zones, FMIN, FMAX } from './engine.js';

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
    showTransfer = false,
    captureNonce = 0,
    onCapture,
    reference = null,
    showReference = true,
    locateFreq = null,
    onStats,
  } = $props();

  const DB_TOP = -6, DB_BOT = -90;
  const PAD = { l: 50, r: 16, t: 18, b: 50 };

  const FREQ_MAJ = [
    [20, '20'], [50, '50'], [100, '100'], [200, '200'], [500, '500'],
    [1000, '1k'], [2000, '2k'], [5000, '5k'], [10000, '10k'], [20000, '20k'],
  ];
  const FREQ_MIN = [30, 40, 70, 300, 400, 700, 3000, 4000, 7000, 15000];

  let wrapEl, canvasEl;

  const st = {
    frac: 0, avgMic: null, avgSolo: null,
    dispMic: null, dispSolo: null, holdMic: null, holdSolo: null,
    mouse: { x: 0, y: 0, inside: false },
    W: 0, H: 0, dpr: 1,
    lastReport: 0, lastCapture: 0, holdReset: 0,
  };

  function dbY(v, plotH) { return PAD.t + (DB_TOP - v) / (DB_TOP - DB_BOT) * plotH; }
  function fX(f, plotW) { return PAD.l + freqNorm(f) * plotW; }

  function dot(ctx, x, y, c) {
    ctx.beginPath(); ctx.arc(x, y, 3, 0, Math.PI * 2);
    ctx.fillStyle = c; ctx.shadowColor = c; ctx.shadowBlur = 6; ctx.fill(); ctx.shadowBlur = 0;
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  onMount(() => {
    const t0 = performance.now();

    const onMove = (e) => {
      const r = canvasEl.getBoundingClientRect();
      const p = e.touches ? e.touches[0] : e;
      st.mouse = { x: p.clientX - r.left, y: p.clientY - r.top, inside: true };
    };
    const onLeave = () => { st.mouse.inside = false; };
    canvasEl.addEventListener('pointermove', onMove);
    canvasEl.addEventListener('pointerdown', onMove);
    canvasEl.addEventListener('pointerleave', onLeave);

    const id = setInterval(frame, 22);

    function frame() {
      const cv = canvasEl;
      const el = wrapEl;
      if (!cv || !el) return;

      const cw = el.clientWidth, ch = el.clientHeight;
      if (cw === 0 || ch === 0) return;
      if (cw !== st.W || ch !== st.H) {
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        cv.width = Math.round(cw * dpr); cv.height = Math.round(ch * dpr);
        cv.style.width = cw + 'px'; cv.style.height = ch + 'px';
        st.W = cw; st.H = ch; st.dpr = dpr;
      }

      const ctx = cv.getContext('2d');
      const t = (performance.now() - t0) / 1000;
      const W = st.W, H = st.H, dpr = st.dpr;
      const plotW = W - PAD.l - PAD.r;
      const plotH = H - PAD.t - PAD.b;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, W, H);

      const bg = ctx.createLinearGradient(0, 0, 0, H);
      bg.addColorStop(0, '#0b1019'); bg.addColorStop(1, '#080b11');
      ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);

      const centers = bandCenters(frac);
      const n = centers.length;
      if (st.frac !== frac || !st.dispMic || st.dispMic.length !== n) {
        st.frac = frac;
        st.avgMic = new Float32Array(n).fill(-65);
        st.avgSolo = new Float32Array(n).fill(-65);
        st.dispMic = new Float32Array(n).fill(-65);
        st.dispSolo = new Float32Array(n).fill(-65);
        st.holdMic = new Float32Array(n).fill(-95);
        st.holdSolo = new Float32Array(n).fill(-95);
      }
      if (holdReset !== st.holdReset) {
        st.holdReset = holdReset;
        st.holdMic.fill(-95); st.holdSolo.fill(-95);
      }

      const { mic, solo } = sample(centers, t, ring);
      const mvc = micVoice || { g: 0, tilt: 0 };
      const svc = soloVoice || { g: 0, tilt: 0 };
      const ca = avgN > 1 ? 1 - 1 / avgN : 0;
      const cs = smoothing;
      for (let i = 0; i < n; i++) {
        const lf = Math.log2(centers[i] / 1000);
        const mt = mic[i] + mvc.g + mvc.tilt * lf;
        const sv = solo[i] + svc.g + svc.tilt * lf;
        st.avgMic[i] = ca > 0 ? st.avgMic[i] * ca + mt * (1 - ca) : mt;
        st.avgSolo[i] = ca > 0 ? st.avgSolo[i] * ca + sv * (1 - ca) : sv;
        st.dispMic[i] = st.dispMic[i] * cs + st.avgMic[i] * (1 - cs);
        st.dispSolo[i] = st.dispSolo[i] * cs + st.avgSolo[i] * (1 - cs);
        st.holdMic[i] = Math.max(st.holdMic[i] - 0.42, st.dispMic[i]);
        st.holdSolo[i] = Math.max(st.holdSolo[i] - 0.42, st.dispSolo[i]);
      }

      if (onStats && t - st.lastReport > 0.12) {
        st.lastReport = t;
        let pMic = -200, pIdx = 0;
        let sMic = 0, sSolo = 0;
        for (let i = 0; i < n; i++) {
          if (st.dispMic[i] > pMic) { pMic = st.dispMic[i]; pIdx = i; }
          sMic += st.dispMic[i]; sSolo += st.dispSolo[i];
        }
        onStats({
          peakFreq: centers[pIdx], micPeak: pMic,
          micAvg: sMic / n, soloAvg: sSolo / n,
          soloPeak: Array.from(st.dispSolo).reduce((a, b) => Math.max(a, b), -200),
        });
      }

      if (captureNonce !== st.lastCapture) {
        st.lastCapture = captureNonce;
        if (captureNonce > 0 && onCapture) {
          onCapture({ centers: Array.from(centers), mic: Array.from(st.dispMic), solo: Array.from(st.dispSolo) });
        }
      }

      // grid — dB lines
      ctx.lineWidth = 1;
      ctx.font = "11px 'IBM Plex Mono', monospace";
      for (let db = DB_TOP; db >= DB_BOT; db -= 12) {
        const y = Math.round(dbY(db, plotH)) + 0.5;
        ctx.strokeStyle = 'rgba(255,255,255,0.05)';
        ctx.beginPath(); ctx.moveTo(PAD.l, y); ctx.lineTo(W - PAD.r, y); ctx.stroke();
        ctx.fillStyle = '#4d5a6a'; ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
        ctx.fillText(db, PAD.l - 8, y);
      }
      // freq minor
      ctx.strokeStyle = 'rgba(255,255,255,0.035)';
      FREQ_MIN.forEach((f) => {
        const x = Math.round(fX(f, plotW)) + 0.5;
        ctx.beginPath(); ctx.moveTo(x, PAD.t); ctx.lineTo(x, PAD.t + plotH); ctx.stroke();
      });
      // freq major
      ctx.textAlign = 'center'; ctx.textBaseline = 'top';
      FREQ_MAJ.forEach(([f, label]) => {
        const x = Math.round(fX(f, plotW)) + 0.5;
        ctx.strokeStyle = 'rgba(255,255,255,0.08)';
        ctx.beginPath(); ctx.moveTo(x, PAD.t); ctx.lineTo(x, PAD.t + plotH); ctx.stroke();
        ctx.fillStyle = '#6d7d8e';
        ctx.fillText(label, x, PAD.t + plotH + 8);
      });

      // bars
      const xs = new Array(n);
      for (let i = 0; i < n; i++) xs[i] = fX(centers[i], plotW);
      const barW = new Array(n);
      for (let i = 0; i < n; i++) {
        const left = i > 0 ? xs[i] - xs[i - 1] : xs[1] - xs[0];
        const right = i < n - 1 ? xs[i + 1] - xs[i] : xs[n - 1] - xs[n - 2];
        barW[i] = Math.min(left, right);
      }
      const baseY = PAD.t + plotH;

      const drawBars = (arr, r, g, b, aTop, aBot) => {
        ctx.globalCompositeOperation = 'lighter';
        for (let i = 0; i < n; i++) {
          const w = Math.max(1.5, barW[i] * 0.82);
          const y = dbY(arr[i], plotH);
          const grad = ctx.createLinearGradient(0, y, 0, baseY);
          grad.addColorStop(0, `rgba(${r},${g},${b},${aTop})`);
          grad.addColorStop(1, `rgba(${r},${g},${b},${aBot})`);
          ctx.fillStyle = grad;
          ctx.fillRect(xs[i] - w / 2, y, w, baseY - y);
        }
        ctx.globalCompositeOperation = 'source-over';
        ctx.fillStyle = `rgb(${r},${g},${b})`;
        for (let i = 0; i < n; i++) {
          const w = Math.max(1.5, barW[i] * 0.82);
          const y = dbY(arr[i], plotH);
          ctx.fillRect(xs[i] - w / 2, y - 1, w, 2.5);
        }
      };

      const drawHoldTicks = (arr, color) => {
        ctx.fillStyle = color;
        for (let i = 0; i < n; i++) {
          const w = Math.max(1.5, barW[i] * 0.82);
          ctx.fillRect(xs[i] - w / 2, dbY(arr[i], plotH) - 1, w, 2);
        }
      };

      const aT = showTransfer ? 0.3 : 0.5;
      const aB = showTransfer ? 0.05 : 0.07;
      ctx.save();
      ctx.beginPath();
      ctx.rect(PAD.l, PAD.t, plotW, plotH); ctx.clip();
      if (soloOn) drawBars(st.dispSolo, 245, 165, 36, aT, aB);
      if (micOn) drawBars(st.dispMic, 34, 211, 238, aT, aB);
      if (peakHold) {
        if (soloOn) drawHoldTicks(st.holdSolo, 'rgba(245,165,36,0.85)');
        if (micOn) drawHoldTicks(st.holdMic, 'rgba(34,211,238,0.9)');
      }

      // reference ghost
      if (reference && showReference) {
        const R = reference;
        const ghost = (vals, color) => {
          ctx.beginPath();
          for (let i = 0; i < R.centers.length; i++) {
            const x = fX(R.centers[i], plotW), y = dbY(vals[i], plotH);
            i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
          }
          ctx.lineWidth = 1.5; ctx.strokeStyle = color; ctx.setLineDash([5, 4]);
          ctx.lineJoin = 'round'; ctx.stroke(); ctx.setLineDash([]);
        };
        if (micOn) ghost(R.mic, 'rgba(120,222,242,0.6)');
        if (soloOn) ghost(R.solo, 'rgba(247,200,120,0.6)');
      }

      // transfer function
      const tfShown = showTransfer && micOn && soloOn;
      if (tfShown) {
        const TF = 24, midY = PAD.t + plotH / 2, half = (plotH / 2) * 0.8;
        const tfY = (d) => midY - Math.max(-TF, Math.min(TF, d)) / TF * half;
        ctx.strokeStyle = 'rgba(183,148,246,0.28)'; ctx.setLineDash([6, 6]);
        ctx.beginPath(); ctx.moveTo(PAD.l, midY); ctx.lineTo(W - PAD.r, midY); ctx.stroke(); ctx.setLineDash([]);
        ctx.beginPath();
        for (let i = 0; i < n; i++) {
          const x = fX(centers[i], plotW), y = tfY(st.dispMic[i] - st.dispSolo[i]);
          i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
        }
        ctx.lineWidth = 2.5; ctx.strokeStyle = '#b794f6'; ctx.lineJoin = 'round';
        ctx.shadowColor = '#b794f6'; ctx.shadowBlur = 7; ctx.stroke(); ctx.shadowBlur = 0;
      }
      ctx.restore();

      if (tfShown) {
        const TF = 24, midY = PAD.t + plotH / 2, half = (plotH / 2) * 0.8;
        const tfY = (d) => midY - d / TF * half;
        ctx.font = "10px 'IBM Plex Mono', monospace"; ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
        [[24, '+24'], [12, '+12'], [0, '0'], [-12, '−12'], [-24, '−24']].forEach(([d, l]) => {
          ctx.fillStyle = 'rgba(183,148,246,0.7)'; ctx.fillText(l, W - PAD.r - 6, tfY(d));
        });
        ctx.textAlign = 'left'; ctx.fillStyle = 'rgba(183,148,246,0.9)'; ctx.font = "600 9px 'IBM Plex Mono', monospace";
        ctx.fillText('TF · MIC − SOLO  (dB)', PAD.l + 8, PAD.t + 11);
      }

      // peak markers
      if (markers) {
        const src = markerSource === 'solo' ? st.dispSolo : st.dispMic;
        const on = markerSource === 'solo' ? soloOn : micOn;
        const col = markerSource === 'solo' ? '#f5a524' : '#22d3ee';
        if (on) {
          const peaks = [];
          for (let i = 2; i < n - 2; i++) {
            if (src[i] > src[i - 1] && src[i] >= src[i + 1] &&
                src[i] > src[i - 2] && src[i] > src[i + 2]) {
              const local = (src[i - 2] + src[i + 2]) / 2;
              const prom = src[i] - local;
              if (prom > 3.5) peaks.push({ i, v: src[i], prom, f: centers[i] });
            }
          }
          peaks.sort((a, b) => b.v - a.v);
          peaks.slice(0, 3).forEach((pk) => {
            const x = fX(pk.f, plotW), y = dbY(pk.v, plotH);
            ctx.beginPath(); ctx.arc(x, y, 3.5, 0, Math.PI * 2);
            ctx.fillStyle = '#e9fb9b'; ctx.fill();
            ctx.strokeStyle = col; ctx.lineWidth = 1.5; ctx.stroke();
            const fl = pk.f >= 1000 ? (pk.f / 1000).toFixed(pk.f >= 10000 ? 0 : 1) + 'k' : Math.round(pk.f);
            const label = fl + ' · ' + noteName(pk.f);
            ctx.font = "600 11px 'IBM Plex Mono', monospace";
            const tw = ctx.measureText(label).width;
            let bx = x - tw / 2 - 6;
            bx = Math.max(PAD.l + 2, Math.min(bx, W - PAD.r - tw - 12));
            const by = Math.max(PAD.t + 2, y - 26);
            ctx.fillStyle = 'rgba(8,11,17,0.9)';
            ctx.strokeStyle = 'rgba(233,251,155,0.5)'; ctx.lineWidth = 1;
            roundRect(ctx, bx, by, tw + 12, 17, 4); ctx.fill(); ctx.stroke();
            ctx.fillStyle = '#e9fb9b'; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
            ctx.fillText(label, bx + 6, by + 9);
          });
        }
      }

      // locate line
      if (locateFreq) {
        const x = fX(locateFreq, plotW);
        if (x >= PAD.l && x <= W - PAD.r) {
          ctx.strokeStyle = 'rgba(255,91,96,0.85)'; ctx.lineWidth = 1.5; ctx.setLineDash([4, 3]);
          ctx.beginPath(); ctx.moveTo(x, PAD.t); ctx.lineTo(x, PAD.t + plotH); ctx.stroke(); ctx.setLineDash([]);
          const lab = locateFreq >= 1000
            ? (locateFreq / 1000).toFixed(locateFreq >= 10000 ? 1 : 2) + 'k'
            : Math.round(locateFreq) + '';
          ctx.font = "600 11px 'IBM Plex Mono', monospace";
          const tw = ctx.measureText(lab).width;
          let bx = x - tw / 2 - 6;
          bx = Math.max(PAD.l, Math.min(bx, W - PAD.r - tw - 12));
          ctx.fillStyle = 'rgba(255,91,96,0.94)';
          roundRect(ctx, bx, PAD.t + 2, tw + 12, 17, 4); ctx.fill();
          ctx.fillStyle = '#180405'; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
          ctx.fillText(lab, bx + 6, PAD.t + 11);
        }
      }

      // hover cursor
      const m = st.mouse;
      if (m.inside && m.x > PAD.l && m.x < W - PAD.r && m.y > PAD.t && m.y < PAD.t + plotH) {
        const nrm = (m.x - PAD.l) / plotW;
        const f = normFreq(Math.max(0, Math.min(1, nrm)));
        let idx = 0, best = 1e9;
        for (let i = 0; i < n; i++) { const d = Math.abs(centers[i] - f); if (d < best) { best = d; idx = i; } }
        const cx = fX(centers[idx], plotW);
        ctx.strokeStyle = 'rgba(255,255,255,0.22)'; ctx.lineWidth = 1;
        ctx.beginPath(); ctx.moveTo(cx, PAD.t); ctx.lineTo(cx, PAD.t + plotH); ctx.stroke();

        const micV = st.dispMic[idx], soloV = st.dispSolo[idx];
        if (micOn) dot(ctx, cx, dbY(micV, plotH), '#22d3ee');
        if (soloOn) dot(ctx, cx, dbY(soloV, plotH), '#f5a524');

        const fl = centers[idx] >= 1000
          ? (centers[idx] / 1000).toFixed(centers[idx] >= 10000 ? 1 : 2) + ' kHz'
          : Math.round(centers[idx]) + ' Hz';
        const lines = [{ t: fl + '  ' + noteName(centers[idx]), c: '#e7eef6' }];
        if (micOn) lines.push({ t: 'MIC  ' + micV.toFixed(1) + ' dB', c: '#22d3ee' });
        if (soloOn) lines.push({ t: 'SOLO ' + soloV.toFixed(1) + ' dB', c: '#f5a524' });
        if (micOn && soloOn) lines.push({ t: 'Δ    ' + (micV - soloV >= 0 ? '+' : '') + (micV - soloV).toFixed(1) + ' dB', c: '#a3e635' });

        ctx.font = "11px 'IBM Plex Mono', monospace";
        let bw = 0; lines.forEach((l) => { bw = Math.max(bw, ctx.measureText(l.t).width); });
        bw += 18; const bh = lines.length * 16 + 10;
        let bx = cx + 12; if (bx + bw > W - PAD.r) bx = cx - bw - 12;
        let by = m.y - bh - 10; if (by < PAD.t) by = m.y + 14;
        ctx.fillStyle = 'rgba(10,14,21,0.94)';
        ctx.strokeStyle = 'rgba(255,255,255,0.14)'; ctx.lineWidth = 1;
        roundRect(ctx, bx, by, bw, bh, 6); ctx.fill(); ctx.stroke();
        ctx.textAlign = 'left'; ctx.textBaseline = 'top';
        lines.forEach((l, i) => { ctx.fillStyle = l.c; ctx.fillText(l.t, bx + 9, by + 7 + i * 16); });
      }

      // zone strip
      const zy = PAD.t + plotH + 24, zh = 14;
      zones.forEach((z) => {
        const x0 = fX(Math.max(FMIN, z.f0), plotW);
        const x1 = fX(Math.min(FMAX, z.f1), plotW);
        ctx.fillStyle = z.color; ctx.globalAlpha = 0.85;
        ctx.fillRect(x0, zy, x1 - x0 - 1.5, zh);
        ctx.globalAlpha = 1;
        ctx.fillStyle = 'rgba(0,0,0,0.55)';
        ctx.font = "600 8.5px 'IBM Plex Mono', monospace";
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        if (x1 - x0 > 36) ctx.fillText(z.name, (x0 + x1) / 2, zy + zh / 2 + 0.5);
      });
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
