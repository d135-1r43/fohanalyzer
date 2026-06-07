import { freqNorm, normFreq, noteName, zones, FMIN, FMAX } from './engine.js';

export const DB_TOP = -6, DB_BOT = -90;
export const PAD = { l: 50, r: 16, t: 18, b: 50 };

const FREQ_MAJ = [
  [20, '20'], [50, '50'], [100, '100'], [200, '200'], [500, '500'],
  [1000, '1k'], [2000, '2k'], [5000, '5k'], [10000, '10k'], [20000, '20k'],
];
const FREQ_MIN = [30, 40, 70, 300, 400, 700, 3000, 4000, 7000, 15000];

export const dbY = (v, plotH) => PAD.t + (DB_TOP - v) / (DB_TOP - DB_BOT) * plotH;
export const fX = (f, plotW) => PAD.l + freqNorm(f) * plotW;

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

export function drawBackground(ctx, W, H) {
  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, '#0b1019'); bg.addColorStop(1, '#080b11');
  ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
}

export function drawGrid(ctx, W, plotW, plotH) {
  ctx.lineWidth = 1;
  ctx.font = "11px 'IBM Plex Mono', monospace";
  for (let db = DB_TOP; db >= DB_BOT; db -= 12) {
    const y = Math.round(dbY(db, plotH)) + 0.5;
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.beginPath(); ctx.moveTo(PAD.l, y); ctx.lineTo(W - PAD.r, y); ctx.stroke();
    ctx.fillStyle = '#4d5a6a'; ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
    ctx.fillText(db, PAD.l - 8, y);
  }
  ctx.strokeStyle = 'rgba(255,255,255,0.035)';
  FREQ_MIN.forEach((f) => {
    const x = Math.round(fX(f, plotW)) + 0.5;
    ctx.beginPath(); ctx.moveTo(x, PAD.t); ctx.lineTo(x, PAD.t + plotH); ctx.stroke();
  });
  ctx.textAlign = 'center'; ctx.textBaseline = 'top';
  FREQ_MAJ.forEach(([f, label]) => {
    const x = Math.round(fX(f, plotW)) + 0.5;
    ctx.strokeStyle = 'rgba(255,255,255,0.08)';
    ctx.beginPath(); ctx.moveTo(x, PAD.t); ctx.lineTo(x, PAD.t + plotH); ctx.stroke();
    ctx.fillStyle = '#6d7d8e';
    ctx.fillText(label, x, PAD.t + plotH + 8);
  });
}

export function computeBarLayout(centers, plotW) {
  const n = centers.length;
  const xs = centers.map(f => fX(f, plotW));
  const barW = xs.map((x, i) => {
    const left  = i > 0     ? xs[i] - xs[i - 1] : xs[1] - xs[0];
    const right = i < n - 1 ? xs[i + 1] - xs[i] : xs[n - 1] - xs[n - 2];
    return Math.min(left, right);
  });
  return { xs, barW };
}

export function drawBars(ctx, arr, xs, barW, plotH, baseY, r, g, b, aTop, aBot) {
  const n = arr.length;
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
}

export function drawHoldTicks(ctx, arr, xs, barW, plotH, color) {
  ctx.fillStyle = color;
  for (let i = 0; i < arr.length; i++) {
    const w = Math.max(1.5, barW[i] * 0.82);
    ctx.fillRect(xs[i] - w / 2, dbY(arr[i], plotH) - 1, w, 2);
  }
}

export function drawReference(ctx, reference, plotW, plotH, micOn, soloOn) {
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

export function drawTransferCurve(ctx, centers, dispMic, dispSolo, W, plotW, plotH) {
  const TF = 24, midY = PAD.t + plotH / 2, half = (plotH / 2) * 0.8;
  const tfY = (d) => midY - Math.max(-TF, Math.min(TF, d)) / TF * half;
  ctx.strokeStyle = 'rgba(183,148,246,0.28)'; ctx.setLineDash([6, 6]);
  ctx.beginPath(); ctx.moveTo(PAD.l, midY); ctx.lineTo(W - PAD.r, midY); ctx.stroke(); ctx.setLineDash([]);
  ctx.beginPath();
  for (let i = 0; i < centers.length; i++) {
    const x = fX(centers[i], plotW), y = tfY(dispMic[i] - dispSolo[i]);
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
  }
  ctx.lineWidth = 2.5; ctx.strokeStyle = '#b794f6'; ctx.lineJoin = 'round';
  ctx.shadowColor = '#b794f6'; ctx.shadowBlur = 7; ctx.stroke(); ctx.shadowBlur = 0;
}

export function drawTransferLabels(ctx, W, plotW, plotH) {
  const TF = 24, midY = PAD.t + plotH / 2, half = (plotH / 2) * 0.8;
  const tfY = (d) => midY - d / TF * half;
  ctx.font = "10px 'IBM Plex Mono', monospace"; ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
  [[24, '+24'], [12, '+12'], [0, '0'], [-12, '−12'], [-24, '−24']].forEach(([d, l]) => {
    ctx.fillStyle = 'rgba(183,148,246,0.7)'; ctx.fillText(l, W - PAD.r - 6, tfY(d));
  });
  ctx.textAlign = 'left'; ctx.fillStyle = 'rgba(183,148,246,0.9)'; ctx.font = "600 9px 'IBM Plex Mono', monospace";
  ctx.fillText('TF · MIC − SOLO  (dB)', PAD.l + 8, PAD.t + 11);
}

export function drawPeakMarkers(ctx, src, centers, plotW, plotH, W, col) {
  const n = src.length;
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

export function drawLocateLine(ctx, locateFreq, W, plotW, plotH) {
  const x = fX(locateFreq, plotW);
  if (x < PAD.l || x > W - PAD.r) return;
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

export function drawHoverTooltip(ctx, mouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, micOn, soloOn) {
  const { x: mx, y: my } = mouse;
  if (mx <= PAD.l || mx >= W - PAD.r || my <= PAD.t || my >= PAD.t + plotH) return;
  const n = centers.length;
  const f = normFreq(Math.max(0, Math.min(1, (mx - PAD.l) / plotW)));
  let idx = 0, best = 1e9;
  for (let i = 0; i < n; i++) { const d = Math.abs(centers[i] - f); if (d < best) { best = d; idx = i; } }
  const cx = xs[idx];
  ctx.strokeStyle = 'rgba(255,255,255,0.22)'; ctx.lineWidth = 1;
  ctx.beginPath(); ctx.moveTo(cx, PAD.t); ctx.lineTo(cx, PAD.t + plotH); ctx.stroke();
  const micV = dispMic[idx], soloV = dispSolo[idx];
  if (micOn) dot(ctx, cx, dbY(micV, plotH), '#22d3ee');
  if (soloOn) dot(ctx, cx, dbY(soloV, plotH), '#f5a524');
  const fl = centers[idx] >= 1000
    ? (centers[idx] / 1000).toFixed(centers[idx] >= 10000 ? 1 : 2) + ' kHz'
    : Math.round(centers[idx]) + ' Hz';
  const lines = [{ t: fl + '  ' + noteName(centers[idx]), c: '#e7eef6' }];
  if (micOn)  lines.push({ t: 'MIC  ' + micV.toFixed(1) + ' dB', c: '#22d3ee' });
  if (soloOn) lines.push({ t: 'SOLO ' + soloV.toFixed(1) + ' dB', c: '#f5a524' });
  if (micOn && soloOn) lines.push({ t: 'Δ    ' + (micV - soloV >= 0 ? '+' : '') + (micV - soloV).toFixed(1) + ' dB', c: '#a3e635' });
  ctx.font = "11px 'IBM Plex Mono', monospace";
  let bw = 0; lines.forEach((l) => { bw = Math.max(bw, ctx.measureText(l.t).width); });
  bw += 18; const bh = lines.length * 16 + 10;
  let bx = cx + 12; if (bx + bw > W - PAD.r) bx = cx - bw - 12;
  let by = my - bh - 10; if (by < PAD.t) by = my + 14;
  ctx.fillStyle = 'rgba(10,14,21,0.94)';
  ctx.strokeStyle = 'rgba(255,255,255,0.14)'; ctx.lineWidth = 1;
  roundRect(ctx, bx, by, bw, bh, 6); ctx.fill(); ctx.stroke();
  ctx.textAlign = 'left'; ctx.textBaseline = 'top';
  lines.forEach((l, i) => { ctx.fillStyle = l.c; ctx.fillText(l.t, bx + 9, by + 7 + i * 16); });
}

export function drawZoneStrip(ctx, plotW, plotH, W, H) {
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
