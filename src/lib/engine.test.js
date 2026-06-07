import { describe, it, expect } from 'vitest';
import { bandCenters, freqNorm, normFreq, noteName, fmtFreq, fmtShort, sample, FMIN, FMAX } from './engine.js';

describe('freqNorm / normFreq', () => {
  it('maps FMIN to 0 and FMAX to 1', () => {
    expect(freqNorm(FMIN)).toBeCloseTo(0);
    expect(freqNorm(FMAX)).toBeCloseTo(1);
  });

  it('roundtrips correctly', () => {
    for (const f of [50, 440, 1000, 4000, 10000]) {
      expect(normFreq(freqNorm(f))).toBeCloseTo(f, 5);
    }
  });
});

describe('bandCenters', () => {
  it('all bands fall within FMIN–FMAX', () => {
    for (const frac of [1, 3, 6, 12, 24]) {
      const bands = bandCenters(frac);
      expect(bands.every(f => f >= FMIN && f <= FMAX)).toBe(true);
    }
  });

  it('higher resolution yields more bands', () => {
    expect(bandCenters(3).length).toBeGreaterThan(bandCenters(1).length);
    expect(bandCenters(12).length).toBeGreaterThan(bandCenters(3).length);
    expect(bandCenters(24).length).toBeGreaterThan(bandCenters(12).length);
  });

  it('1/1 octave produces ~10 bands across 20–20kHz', () => {
    const bands = bandCenters(1);
    expect(bands.length).toBeGreaterThanOrEqual(9);
    expect(bands.length).toBeLessThanOrEqual(11);
  });

  it('consecutive bands have equal log ratio (log-spacing)', () => {
    const bands = bandCenters(3);
    const ratios = bands.slice(1).map((f, i) => f / bands[i]);
    const expected = ratios[0];
    for (const r of ratios) expect(r).toBeCloseTo(expected, 6);
  });
});

describe('noteName', () => {
  it('returns empty string for f <= 0', () => {
    expect(noteName(0)).toBe('');
    expect(noteName(-1)).toBe('');
  });

  it('identifies A4 at 440 Hz', () => {
    expect(noteName(440)).toBe('A4');
  });

  it('identifies A3 at 220 Hz (one octave below A4)', () => {
    expect(noteName(220)).toBe('A3');
  });

  it('identifies C4 (middle C) at ~261.63 Hz', () => {
    expect(noteName(261.63)).toBe('C4');
  });

  it('identifies A5 at 880 Hz', () => {
    expect(noteName(880)).toBe('A5');
  });
});

describe('fmtFreq', () => {
  it('returns "—" for falsy input', () => {
    expect(fmtFreq(0)).toBe('—');
    expect(fmtFreq(null)).toBe('—');
  });

  it('formats sub-1kHz as integer Hz', () => {
    expect(fmtFreq(100)).toBe('100 Hz');
    expect(fmtFreq(440)).toBe('440 Hz');
  });

  it('formats 1–9.99 kHz with two decimals', () => {
    expect(fmtFreq(1000)).toBe('1.00 kHz');
    expect(fmtFreq(4500)).toBe('4.50 kHz');
  });

  it('formats 10 kHz+ with one decimal', () => {
    expect(fmtFreq(10000)).toBe('10.0 kHz');
    expect(fmtFreq(16000)).toBe('16.0 kHz');
  });
});

describe('fmtShort', () => {
  it('formats sub-1kHz as integer without unit', () => {
    expect(fmtShort(100)).toBe('100');
    expect(fmtShort(440)).toBe('440');
  });

  it('formats 1–9.9 kHz with one decimal and "k"', () => {
    expect(fmtShort(1000)).toBe('1.0k');
    expect(fmtShort(4500)).toBe('4.5k');
  });

  it('formats 10 kHz+ as integer with "k"', () => {
    expect(fmtShort(10000)).toBe('10k');
    expect(fmtShort(16000)).toBe('16k');
  });
});

describe('sample', () => {
  const centers = bandCenters(12);

  it('returns Float32Arrays matching centers length', () => {
    const { mic, solo } = sample(centers, 0, null);
    expect(mic).toBeInstanceOf(Float32Array);
    expect(solo).toBeInstanceOf(Float32Array);
    expect(mic.length).toBe(centers.length);
    expect(solo.length).toBe(centers.length);
  });

  it('clamps all values to [-95, -2] dBFS', () => {
    for (const t of [0, 0.5, 1.0, 2.0]) {
      const { mic, solo } = sample(centers, t, null);
      for (let i = 0; i < centers.length; i++) {
        expect(mic[i]).toBeGreaterThanOrEqual(-95);
        expect(mic[i]).toBeLessThanOrEqual(-2);
        expect(solo[i]).toBeGreaterThanOrEqual(-95);
        expect(solo[i]).toBeLessThanOrEqual(-2);
      }
    }
  });

  it('active ring-out raises levels near the ring frequency', () => {
    const fc = 1000;
    const ring = { active: true, t0: 0, fc };
    const { mic: micRing } = sample(centers, 2, ring);
    const { mic: micQuiet } = sample(centers, 2, null);
    const nearIdx = centers.reduce((best, f, i) =>
      Math.abs(f - fc) < Math.abs(centers[best] - fc) ? i : best, 0);
    expect(micRing[nearIdx]).toBeGreaterThan(micQuiet[nearIdx]);
  });

  it('inactive ring (ring.active=false) produces same output as null ring', () => {
    const ring = { active: false, t0: 0, fc: 1000 };
    const { mic: micRing, solo: soloRing } = sample(centers, 1, ring);
    const { mic: micNull, solo: soloNull } = sample(centers, 1, null);
    for (let i = 0; i < centers.length; i++) {
      expect(micRing[i]).toBe(micNull[i]);
      expect(soloRing[i]).toBe(soloNull[i]);
    }
  });

  it('output varies over time (simulation is not static)', () => {
    const { mic: mic0 } = sample(centers, 0, null);
    const { mic: mic1 } = sample(centers, 1, null);
    const differs = Array.from(mic0).some((v, i) => v !== mic1[i]);
    expect(differs).toBe(true);
  });
});
