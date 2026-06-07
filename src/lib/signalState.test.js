import { describe, it, expect, beforeEach } from 'vitest';
import { SignalState } from './signalState.js';

const NO_VOICE = { g: 0, tilt: 0 };
const centers = [100, 500, 1000, 4000, 10000];
const n = centers.length;

let sig;
beforeEach(() => {
  sig = new SignalState();
  sig.ensureBands(12, n);
});

describe('ensureBands', () => {
  it('initialises all arrays to the correct length', () => {
    expect(sig.dispMic.length).toBe(n);
    expect(sig.dispSolo.length).toBe(n);
    expect(sig.holdMic.length).toBe(n);
    expect(sig.holdSolo.length).toBe(n);
  });

  it('fills disp buffers at -65 and hold buffers at -95', () => {
    expect(Array.from(sig.dispMic).every(v => v === -65)).toBe(true);
    expect(Array.from(sig.dispSolo).every(v => v === -65)).toBe(true);
    expect(Array.from(sig.holdMic).every(v => v === -95)).toBe(true);
    expect(Array.from(sig.holdSolo).every(v => v === -95)).toBe(true);
  });

  it('is a no-op when frac and n are unchanged', () => {
    sig.dispMic[0] = -30;
    sig.ensureBands(12, n);
    expect(sig.dispMic[0]).toBe(-30); // unchanged
  });

  it('reinitialises when frac changes', () => {
    sig.dispMic[0] = -30;
    sig.ensureBands(6, n);
    expect(sig.dispMic[0]).toBe(-65);
  });

  it('reinitialises when band count changes', () => {
    sig.dispMic[0] = -30;
    sig.ensureBands(12, n + 1);
    expect(sig.dispMic.length).toBe(n + 1);
    expect(sig.dispMic[0]).toBe(-65);
  });
});

describe('resetHold', () => {
  it('resets holdMic and holdSolo to -95', () => {
    const mic  = new Float32Array(n).fill(-30);
    const solo = new Float32Array(n).fill(-30);
    sig.update(mic, solo, NO_VOICE, NO_VOICE, centers, 0, 1);
    sig.resetHold();
    expect(Array.from(sig.holdMic).every(v => v === -95)).toBe(true);
    expect(Array.from(sig.holdSolo).every(v => v === -95)).toBe(true);
  });

  it('does not affect dispMic or dispSolo', () => {
    const mic  = new Float32Array(n).fill(-30);
    const solo = new Float32Array(n).fill(-30);
    sig.update(mic, solo, NO_VOICE, NO_VOICE, centers, 0, 1);
    const dispMicBefore = Array.from(sig.dispMic);
    sig.resetHold();
    expect(Array.from(sig.dispMic)).toEqual(dispMicBefore);
  });
});

describe('update — averaging (avgN)', () => {
  it('with avgN=1, disp converges to input in a single update (no smoothing)', () => {
    const mic  = new Float32Array(n).fill(-40);
    const solo = new Float32Array(n).fill(-50);
    sig.update(mic, solo, NO_VOICE, NO_VOICE, centers, 0, 1);
    expect(sig.dispMic[2]).toBeCloseTo(-40, 5);
    expect(sig.dispSolo[2]).toBeCloseTo(-50, 5);
  });

  it('with avgN=4, disp approaches input gradually', () => {
    const target = new Float32Array(n).fill(-30);
    // after one update from -65, should be between -65 and -30
    sig.update(target, target, NO_VOICE, NO_VOICE, centers, 0, 4);
    expect(sig.dispMic[0]).toBeGreaterThan(-65);
    expect(sig.dispMic[0]).toBeLessThan(-30);
  });

  it('with avgN=4, repeated identical input converges toward target', () => {
    const target = new Float32Array(n).fill(-30);
    for (let i = 0; i < 30; i++) sig.update(target, target, NO_VOICE, NO_VOICE, centers, 0, 4);
    expect(sig.dispMic[0]).toBeCloseTo(-30, 0);
  });
});

describe('update — smoothing', () => {
  it('with smoothing=0, rising and falling signals track instantly', () => {
    const high = new Float32Array(n).fill(-20);
    const low  = new Float32Array(n).fill(-60);
    sig.update(high, high, NO_VOICE, NO_VOICE, centers, 0, 1);
    expect(sig.dispMic[0]).toBeCloseTo(-20, 5);
    sig.update(low, low, NO_VOICE, NO_VOICE, centers, 0, 1);
    expect(sig.dispMic[0]).toBeCloseTo(-60, 5);
  });

  it('fast attack: rising signal jumps immediately regardless of smoothing', () => {
    // Start below target; high smoothing should not slow a rise
    const high = new Float32Array(n).fill(-20);
    sig.update(high, high, NO_VOICE, NO_VOICE, centers, 0.9, 1);
    expect(sig.dispMic[0]).toBeCloseTo(-20, 5);
  });

  it('slow release: falling signal is smoothed when smoothing > 0', () => {
    // First push disp up
    const high = new Float32Array(n).fill(-20);
    sig.update(high, high, NO_VOICE, NO_VOICE, centers, 0, 1);
    // Now drop the input; with smoothing=0.8 the display should lag
    const low = new Float32Array(n).fill(-80);
    sig.update(low, low, NO_VOICE, NO_VOICE, centers, 0.8, 1);
    expect(sig.dispMic[0]).toBeGreaterThan(-80);
    expect(sig.dispMic[0]).toBeLessThan(-20);
  });
});

describe('update — voicing', () => {
  it('applies gain offset to mic', () => {
    const mic = new Float32Array(n).fill(-50);
    const micVoice = { g: 6, tilt: 0 };
    sig.update(mic, mic, micVoice, NO_VOICE, centers, 0, 1);
    expect(sig.dispMic[2]).toBeCloseTo(-44, 4);  // 1kHz: lf=0, so g adds directly
  });

  it('applies gain offset to solo independently', () => {
    const solo = new Float32Array(n).fill(-50);
    const soloVoice = { g: -3, tilt: 0 };
    sig.update(solo, solo, NO_VOICE, soloVoice, centers, 0, 1);
    expect(sig.dispSolo[2]).toBeCloseTo(-53, 4);
  });

  it('applies positive tilt (more gain at high frequencies)', () => {
    const input = new Float32Array(n).fill(-50);
    const voice = { g: 0, tilt: 3 };
    sig.update(input, input, voice, NO_VOICE, centers, 0, 1);
    // lf = log2(f/1000): negative below 1k, positive above 1k
    expect(sig.dispMic[4]).toBeGreaterThan(sig.dispMic[2]); // 10k > 1k
    expect(sig.dispMic[0]).toBeLessThan(sig.dispMic[2]);    // 100Hz < 1k
  });

  it('is neutral at 1kHz regardless of tilt (log2(1000/1000) = 0)', () => {
    const input = new Float32Array(n).fill(-50);
    const voice = { g: 0, tilt: 6 };
    sig.update(input, input, voice, NO_VOICE, [1000], 0, 1);
    expect(sig.dispMic[0]).toBeCloseTo(-50, 4);
  });
});

describe('update — peak hold', () => {
  it('hold tracks rising signal', () => {
    const high = new Float32Array(n).fill(-20);
    sig.update(high, high, NO_VOICE, NO_VOICE, centers, 0, 1);
    expect(sig.holdMic[0]).toBeCloseTo(-20, 5);
  });

  it('hold decays at 0.42 dB per update when signal falls away', () => {
    const high = new Float32Array(n).fill(-20);
    sig.update(high, high, NO_VOICE, NO_VOICE, centers, 0, 1);
    const held = sig.holdMic[0];
    const low = new Float32Array(n).fill(-80);
    sig.update(low, low, NO_VOICE, NO_VOICE, centers, 0, 1);
    expect(sig.holdMic[0]).toBeCloseTo(held - 0.42, 4);
  });

  it('hold never falls below current disp value', () => {
    const level = new Float32Array(n).fill(-30);
    sig.update(level, level, NO_VOICE, NO_VOICE, centers, 0, 1);
    // hold should equal disp, not decay below it
    expect(sig.holdMic[0]).toBeCloseTo(sig.dispMic[0], 4);
  });
});

describe('getStats', () => {
  it('returns peakFreq matching the band with highest mic level', () => {
    sig.update(new Float32Array(n).fill(-60), new Float32Array(n).fill(-60), NO_VOICE, NO_VOICE, centers, 0, 1);
    sig.dispMic[3] = -20; // 4kHz band is the loudest
    const stats = sig.getStats(centers);
    expect(stats.peakFreq).toBe(centers[3]);
    expect(stats.micPeak).toBeCloseTo(-20, 4);
  });

  it('returns correct micAvg and soloAvg', () => {
    sig.dispMic.fill(-40);
    sig.dispSolo.fill(-50);
    const stats = sig.getStats(centers);
    expect(stats.micAvg).toBeCloseTo(-40, 4);
    expect(stats.soloAvg).toBeCloseTo(-50, 4);
  });

  it('returns soloPeak as the maximum of dispSolo', () => {
    sig.dispSolo.fill(-60);
    sig.dispSolo[1] = -25;
    const stats = sig.getStats(centers);
    expect(stats.soloPeak).toBeCloseTo(-25, 4);
  });
});
