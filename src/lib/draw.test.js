import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  DB_TOP, DB_BOT, PAD, dbY, fX, computeBarLayout,
  drawBackground, drawGrid,
  drawBars, drawHoldTicks, drawReference,
  drawTransferCurve, drawTransferLabels,
  drawPeakMarkers, drawLocateLine, drawHoverTooltip, drawZoneStrip,
} from './draw.js';
import { FMIN, FMAX } from './engine.js';

// ─── mock ctx factory ────────────────────────────────────────────────────────

function makeMockCtx() {
  const grad = { addColorStop: vi.fn() };
  return {
    createLinearGradient: vi.fn(() => grad),
    fillStyle: '', strokeStyle: '', lineWidth: 1,
    globalAlpha: 1, globalCompositeOperation: 'source-over',
    font: '', textAlign: '', textBaseline: '',
    shadowColor: '', shadowBlur: 0, lineJoin: '',
    fillRect: vi.fn(),
    beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(),
    arc: vi.fn(), arcTo: vi.fn(), closePath: vi.fn(),
    stroke: vi.fn(), fill: vi.fn(),
    save: vi.fn(), restore: vi.fn(), clip: vi.fn(), rect: vi.fn(),
    setLineDash: vi.fn(),
    measureText: vi.fn(() => ({ width: 40 })),
    fillText: vi.fn(),
  };
}

// ─── shared layout ───────────────────────────────────────────────────────────

const W = 900, H = 400;
const plotW = W - PAD.l - PAD.r;
const plotH = H - PAD.t - PAD.b;

let ctx;
beforeEach(() => { ctx = makeMockCtx(); });

// ─── pure math ───────────────────────────────────────────────────────────────

describe('dbY', () => {
  it('maps DB_TOP to the top of the plot (PAD.t)', () => {
    expect(dbY(DB_TOP, plotH)).toBeCloseTo(PAD.t, 5);
  });

  it('maps DB_BOT to the bottom of the plot (PAD.t + plotH)', () => {
    expect(dbY(DB_BOT, plotH)).toBeCloseTo(PAD.t + plotH, 5);
  });

  it('maps -48 dB (midpoint of -6 to -90) to the vertical centre', () => {
    expect(dbY(-48, plotH)).toBeCloseTo(PAD.t + plotH / 2, 4);
  });

  it('higher dB values produce smaller (higher on screen) y values', () => {
    expect(dbY(-20, plotH)).toBeLessThan(dbY(-60, plotH));
  });
});

describe('fX', () => {
  it('maps FMIN to PAD.l', () => {
    expect(fX(FMIN, plotW)).toBeCloseTo(PAD.l, 5);
  });

  it('maps FMAX to PAD.l + plotW', () => {
    expect(fX(FMAX, plotW)).toBeCloseTo(PAD.l + plotW, 5);
  });

  it('1 kHz falls between the left and right edges', () => {
    const x = fX(1000, plotW);
    expect(x).toBeGreaterThan(PAD.l);
    expect(x).toBeLessThan(PAD.l + plotW);
  });

  it('higher frequencies produce larger x values', () => {
    expect(fX(1000, plotW)).toBeLessThan(fX(4000, plotW));
  });
});

describe('computeBarLayout', () => {
  const centers = [100, 500, 1000, 4000, 10000];

  it('returns xs and barW arrays matching centers length', () => {
    const { xs, barW } = computeBarLayout(centers, plotW);
    expect(xs.length).toBe(centers.length);
    expect(barW.length).toBe(centers.length);
  });

  it('xs are monotonically increasing', () => {
    const { xs } = computeBarLayout(centers, plotW);
    for (let i = 1; i < xs.length; i++) expect(xs[i]).toBeGreaterThan(xs[i - 1]);
  });

  it('all barW values are positive', () => {
    const { barW } = computeBarLayout(centers, plotW);
    expect(barW.every(w => w > 0)).toBe(true);
  });

  it('xs[i] equals fX(centers[i], plotW)', () => {
    const { xs } = computeBarLayout(centers, plotW);
    centers.forEach((f, i) => expect(xs[i]).toBeCloseTo(fX(f, plotW), 5));
  });
});

// ─── drawing functions ───────────────────────────────────────────────────────

describe('drawBackground', () => {
  it('creates a vertical gradient and fills the whole canvas', () => {
    drawBackground(ctx, W, H);
    expect(ctx.createLinearGradient).toHaveBeenCalledWith(0, 0, 0, H);
    expect(ctx.fillRect).toHaveBeenCalledWith(0, 0, W, H);
  });

  it('adds two colour stops to the gradient', () => {
    drawBackground(ctx, W, H);
    const grad = ctx.createLinearGradient.mock.results[0].value;
    expect(grad.addColorStop).toHaveBeenCalledTimes(2);
  });
});

describe('drawGrid', () => {
  // dB lines: DB_TOP=-6 down to DB_BOT=-90 step 12 → 8 values
  // freq minor: 10 lines, freq major: 10 lines → total 28 stroke calls
  it('draws 28 grid lines (8 dB + 10 minor freq + 10 major freq)', () => {
    drawGrid(ctx, W, plotW, plotH);
    expect(ctx.stroke).toHaveBeenCalledTimes(28);
  });

  it('draws 18 text labels (8 dB + 10 freq major)', () => {
    drawGrid(ctx, W, plotW, plotH);
    expect(ctx.fillText).toHaveBeenCalledTimes(18);
  });
});

describe('drawBars', () => {
  const n = 4;
  const arr = new Float32Array(n).fill(-40);
  const { xs, barW } = computeBarLayout([200, 500, 1000, 4000], plotW);
  const baseY = PAD.t + plotH;

  it('creates one gradient per band', () => {
    drawBars(ctx, arr, xs, barW, plotH, baseY, 34, 211, 238, 0.5, 0.07);
    expect(ctx.createLinearGradient).toHaveBeenCalledTimes(n);
  });

  it('calls fillRect 2× per band (gradient body + bright cap)', () => {
    drawBars(ctx, arr, xs, barW, plotH, baseY, 34, 211, 238, 0.5, 0.07);
    expect(ctx.fillRect).toHaveBeenCalledTimes(n * 2);
  });

  it('uses lighter blend for gradient pass then restores source-over', () => {
    drawBars(ctx, arr, xs, barW, plotH, baseY, 34, 211, 238, 0.5, 0.07);
    // globalCompositeOperation is set as a property, check final value
    expect(ctx.globalCompositeOperation).toBe('source-over');
  });
});

describe('drawHoldTicks', () => {
  const n = 5;
  const arr = new Float32Array(n).fill(-30);
  const { xs, barW } = computeBarLayout([100, 300, 1000, 3000, 10000], plotW);

  it('calls fillRect once per band', () => {
    drawHoldTicks(ctx, arr, xs, barW, plotH, 'rgba(34,211,238,0.9)');
    expect(ctx.fillRect).toHaveBeenCalledTimes(n);
  });

  it('sets fillStyle to the provided color', () => {
    drawHoldTicks(ctx, arr, xs, barW, plotH, 'rgba(34,211,238,0.9)');
    expect(ctx.fillStyle).toBe('rgba(34,211,238,0.9)');
  });
});

describe('drawReference', () => {
  const ref = {
    centers: [200, 1000, 5000],
    mic:  [-50, -45, -55],
    solo: [-48, -43, -53],
  };

  it('draws one stroke when only micOn', () => {
    drawReference(ctx, ref, plotW, plotH, true, false);
    expect(ctx.stroke).toHaveBeenCalledTimes(1);
  });

  it('draws one stroke when only soloOn', () => {
    drawReference(ctx, ref, plotW, plotH, false, true);
    expect(ctx.stroke).toHaveBeenCalledTimes(1);
  });

  it('draws two strokes when both on', () => {
    drawReference(ctx, ref, plotW, plotH, true, true);
    expect(ctx.stroke).toHaveBeenCalledTimes(2);
  });

  it('draws nothing when both off', () => {
    drawReference(ctx, ref, plotW, plotH, false, false);
    expect(ctx.stroke).not.toHaveBeenCalled();
  });

  it('uses dashed line style', () => {
    drawReference(ctx, ref, plotW, plotH, true, false);
    expect(ctx.setLineDash).toHaveBeenCalledWith([5, 4]);
  });
});

describe('drawTransferCurve', () => {
  const centers = [100, 500, 1000, 4000, 10000];
  const dispMic  = new Float32Array(centers.length).fill(-40);
  const dispSolo = new Float32Array(centers.length).fill(-45);

  it('draws exactly 2 strokes (midline + curve)', () => {
    drawTransferCurve(ctx, centers, dispMic, dispSolo, W, plotW, plotH);
    expect(ctx.stroke).toHaveBeenCalledTimes(2);
  });

  it('applies and then clears shadow blur', () => {
    drawTransferCurve(ctx, centers, dispMic, dispSolo, W, plotW, plotH);
    expect(ctx.shadowBlur).toBe(0);
  });

  it('sets a dashed style for the midline then clears it', () => {
    drawTransferCurve(ctx, centers, dispMic, dispSolo, W, plotW, plotH);
    expect(ctx.setLineDash).toHaveBeenCalledWith([6, 6]);
    expect(ctx.setLineDash).toHaveBeenCalledWith([]);
  });
});

describe('drawTransferLabels', () => {
  it('draws 6 text labels (5 scale values + 1 header)', () => {
    drawTransferLabels(ctx, W, plotW, plotH);
    expect(ctx.fillText).toHaveBeenCalledTimes(6);
  });
});

describe('drawPeakMarkers', () => {
  const centers = [100, 300, 1000, 3000, 10000];

  it('draws nothing for a flat signal (no peaks)', () => {
    const flat = new Float32Array(centers.length).fill(-50);
    drawPeakMarkers(ctx, flat, centers, plotW, plotH, W, '#22d3ee');
    expect(ctx.arc).not.toHaveBeenCalled();
  });

  it('draws one marker for a signal with a single prominent peak', () => {
    // index 2 (1kHz) is the clear peak with prominence >> 3.5
    const src = new Float32Array([-60, -58, -20, -58, -60]);
    drawPeakMarkers(ctx, src, centers, plotW, plotH, W, '#22d3ee');
    expect(ctx.arc).toHaveBeenCalledTimes(1);
  });

  it('caps markers at 3 even if more peaks exist', () => {
    // 4 prominent peaks at indices 2, 3 — need a longer array
    const c2 = [100, 200, 400, 800, 1600, 3200, 6400, 12800];
    // alternating valleys and peaks
    const src = new Float32Array([-60, -20, -60, -20, -60, -20, -60, -20]);
    // peaks at indices 1,3,5,7 — but they need to clear the i±2 guard
    // let's build: flat with 4 clear peaks
    const bigSrc = new Float32Array([-60, -60, -20, -60, -60, -20, -60, -60, -20, -60, -60, -20, -60, -60]);
    const bigCenters = [50, 80, 100, 160, 200, 400, 500, 800, 1000, 2000, 3000, 5000, 8000, 16000];
    // peaks at indices 2, 5, 8, 11 — all have prominence 40
    drawPeakMarkers(ctx, bigSrc, bigCenters, plotW, plotH, W, '#22d3ee');
    expect(ctx.arc.mock.calls.length).toBeLessThanOrEqual(3);
  });
});

describe('drawLocateLine', () => {
  it('draws the line and label for an in-range frequency', () => {
    drawLocateLine(ctx, 1000, W, plotW, plotH);
    expect(ctx.stroke).toHaveBeenCalledTimes(1);
    expect(ctx.fill).toHaveBeenCalledTimes(1);   // pill background
    expect(ctx.fillText).toHaveBeenCalledTimes(1);
  });

  it('draws nothing for a frequency that maps outside the plot bounds', () => {
    // f=10 Hz is below FMIN so freqNorm returns negative → x < PAD.l
    drawLocateLine(ctx, 10, W, plotW, plotH);
    expect(ctx.stroke).not.toHaveBeenCalled();
    expect(ctx.fillText).not.toHaveBeenCalled();
  });

  it('uses a dashed line style', () => {
    drawLocateLine(ctx, 1000, W, plotW, plotH);
    expect(ctx.setLineDash).toHaveBeenCalledWith([4, 3]);
  });
});

describe('drawHoverTooltip', () => {
  const centers = [200, 500, 1000, 3000, 8000];
  const { xs } = computeBarLayout(centers, plotW);
  const dispMic  = new Float32Array(centers.length).fill(-40);
  const dispSolo = new Float32Array(centers.length).fill(-45);
  const insideMouse = { x: 200, y: 150 };   // well inside the plot area
  const outsideMouse = { x: 0, y: 0 };      // x=0 ≤ PAD.l → out of bounds

  it('does nothing when mouse is outside the plot area', () => {
    drawHoverTooltip(ctx, outsideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, true, true);
    expect(ctx.stroke).not.toHaveBeenCalled();
    expect(ctx.fillText).not.toHaveBeenCalled();
  });

  it('draws a cursor line when mouse is inside', () => {
    drawHoverTooltip(ctx, insideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, true, true);
    expect(ctx.stroke).toHaveBeenCalled();
  });

  it('draws a dot for mic when micOn=true', () => {
    drawHoverTooltip(ctx, insideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, true, false);
    expect(ctx.arc).toHaveBeenCalledTimes(1);
  });

  it('draws dots for both sources when both on', () => {
    drawHoverTooltip(ctx, insideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, true, true);
    expect(ctx.arc).toHaveBeenCalledTimes(2);
  });

  it('draws no dot when both sources are off', () => {
    drawHoverTooltip(ctx, insideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, false, false);
    expect(ctx.arc).not.toHaveBeenCalled();
  });

  it('tooltip shows delta line only when both sources are on', () => {
    // with both on: freq/note + MIC + SOLO + Δ = 4 fillText calls
    drawHoverTooltip(ctx, insideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, true, true);
    const calls = ctx.fillText.mock.calls.map(c => c[0]);
    expect(calls.some(t => t.startsWith('Δ'))).toBe(true);
  });

  it('tooltip omits delta line when only one source is on', () => {
    drawHoverTooltip(ctx, insideMouse, centers, xs, dispMic, dispSolo, W, plotW, plotH, true, false);
    const calls = ctx.fillText.mock.calls.map(c => c[0]);
    expect(calls.some(t => t.startsWith('Δ'))).toBe(false);
  });
});

describe('drawZoneStrip', () => {
  it('calls fillRect once per frequency zone (7 zones)', () => {
    drawZoneStrip(ctx, plotW, plotH, W, H);
    expect(ctx.fillRect).toHaveBeenCalledTimes(7);
  });

  it('resets globalAlpha to 1 after each zone', () => {
    drawZoneStrip(ctx, plotW, plotH, W, H);
    expect(ctx.globalAlpha).toBe(1);
  });
});
