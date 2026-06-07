import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AudioSource, enumerateAudioInputs } from './audioInput.js';

const mockStop = vi.fn();
const mockStream = { getTracks: vi.fn(() => [{ stop: mockStop }]) };
const mockGetFloatFrequencyData = vi.fn((arr) => arr.fill(-60));
const mockGetFloatTimeDomainData = vi.fn((arr) => arr.fill(0));
const mockAnalyser = {
  fftSize: 16384,
  smoothingTimeConstant: 0,
  frequencyBinCount: 8192,
  getFloatFrequencyData: mockGetFloatFrequencyData,
  getFloatTimeDomainData: mockGetFloatTimeDomainData,
};
const mockClose = vi.fn();
const mockCtx = {
  createAnalyser: vi.fn(() => mockAnalyser),
  createMediaStreamSource: vi.fn(() => ({ connect: vi.fn() })),
  sampleRate: 48000,
  close: mockClose,
};

beforeEach(() => {
  vi.clearAllMocks();
  // Must use function (not arrow) — AudioSource calls `new AudioContext()`
  vi.stubGlobal('AudioContext', vi.fn(function () { return mockCtx; }));
  vi.stubGlobal('navigator', {
    mediaDevices: {
      getUserMedia: vi.fn().mockResolvedValue(mockStream),
      enumerateDevices: vi.fn().mockResolvedValue([
        { kind: 'audioinput', deviceId: 'a', label: 'Mic 1' },
        { kind: 'audioinput', deviceId: 'b', label: 'Mic 2' },
        { kind: 'audiooutput', deviceId: 'c', label: 'Speaker' },
      ]),
    },
  });
});

describe('AudioSource', () => {
  it('starts disconnected with no error', () => {
    const src = new AudioSource();
    expect(src.connected).toBe(false);
    expect(src.error).toBeNull();
  });

  describe('connect()', () => {
    it('is connected after successful connect', async () => {
      const src = new AudioSource();
      await src.connect('default');
      expect(src.connected).toBe(true);
      expect(src.error).toBeNull();
    });

    it('passes audio:true for "default" deviceId', async () => {
      const src = new AudioSource();
      await src.connect('default');
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith(
        expect.objectContaining({ audio: true })
      );
    });

    it('passes exact deviceId constraint for specific devices', async () => {
      const src = new AudioSource();
      await src.connect('my-device-id');
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith(
        expect.objectContaining({
          audio: expect.objectContaining({ deviceId: { exact: 'my-device-id' } }),
        })
      );
    });

    it('sets error and stays disconnected when getUserMedia rejects', async () => {
      navigator.mediaDevices.getUserMedia.mockRejectedValueOnce(new Error('Permission denied'));
      const src = new AudioSource();
      await src.connect('default');
      expect(src.connected).toBe(false);
      expect(src.error).toBe('Permission denied');
    });

    it('disconnects any prior connection before reconnecting', async () => {
      const src = new AudioSource();
      await src.connect('default');
      await src.connect('b');
      expect(mockStop).toHaveBeenCalledTimes(1);
      expect(mockClose).toHaveBeenCalledTimes(1);
    });
  });

  describe('disconnect()', () => {
    it('stops stream tracks and closes context', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.disconnect();
      expect(mockStop).toHaveBeenCalled();
      expect(mockClose).toHaveBeenCalled();
      expect(src.connected).toBe(false);
    });

    it('is safe to call when already disconnected', () => {
      const src = new AudioSource();
      expect(() => src.disconnect()).not.toThrow();
    });
  });

  describe('readBands()', () => {
    it('returns null when not connected', () => {
      const src = new AudioSource();
      expect(src.readBands([1000], 12)).toBeNull();
    });

    it('returns Float32Array of correct length', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.freqData = new Float32Array(8192).fill(-60);
      const result = src.readBands([100, 1000, 10000], 12);
      expect(result).toBeInstanceOf(Float32Array);
      expect(result.length).toBe(3);
    });

    it('clamps very quiet FFT data to -95', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.freqData = new Float32Array(8192).fill(-200);
      mockAnalyser.getFloatFrequencyData.mockImplementation((arr) => arr.fill(-200));
      const result = src.readBands([1000], 12);
      expect(result[0]).toBe(-95);
    });

    it('clamps values above -2 to -2', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.freqData = new Float32Array(8192).fill(0);
      mockAnalyser.getFloatFrequencyData.mockImplementation((arr) => arr.fill(0));
      const result = src.readBands([1000], 12);
      expect(result[0]).toBe(-2);
    });
  });

  describe('readRMS()', () => {
    it('returns null when not connected', () => {
      const src = new AudioSource();
      expect(src.readRMS()).toBeNull();
    });

    it('returns a number when connected', async () => {
      const src = new AudioSource();
      await src.connect('default');
      // default mock fills with 0 → returns -144 (silence floor)
      expect(typeof src.readRMS()).toBe('number');
    });

    it('returns -144 for silence (all-zero buffer)', async () => {
      const src = new AudioSource();
      await src.connect('default');
      mockGetFloatTimeDomainData.mockImplementation((arr) => arr.fill(0));
      expect(src.readRMS()).toBe(-144);
    });

    it('returns -144 for signals below the 1e-9 threshold', async () => {
      const src = new AudioSource();
      await src.connect('default');
      mockGetFloatTimeDomainData.mockImplementation((arr) => arr.fill(1e-12));
      expect(src.readRMS()).toBe(-144);
    });

    it('returns 0 dBFS for a full-scale constant signal (amplitude 1.0)', async () => {
      const src = new AudioSource();
      await src.connect('default');
      mockGetFloatTimeDomainData.mockImplementation((arr) => arr.fill(1.0));
      expect(src.readRMS()).toBeCloseTo(0, 5);
    });

    it('returns ~-6.02 dBFS for amplitude 0.5', async () => {
      const src = new AudioSource();
      await src.connect('default');
      mockGetFloatTimeDomainData.mockImplementation((arr) => arr.fill(0.5));
      // RMS of constant 0.5 = 0.5 → 20*log10(0.5) ≈ -6.0206
      expect(src.readRMS()).toBeCloseTo(20 * Math.log10(0.5), 4);
    });

    it('calls getFloatTimeDomainData on the analyser', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.readRMS();
      expect(mockGetFloatTimeDomainData).toHaveBeenCalledTimes(1);
    });

    it('passes the timeData buffer to getFloatTimeDomainData', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.readRMS();
      expect(mockGetFloatTimeDomainData).toHaveBeenCalledWith(src.timeData);
    });

    it('returns null after disconnect', async () => {
      const src = new AudioSource();
      await src.connect('default');
      src.disconnect();
      expect(src.readRMS()).toBeNull();
    });
  });
});

describe('enumerateAudioInputs', () => {
  it('returns only audioinput devices', async () => {
    const devices = await enumerateAudioInputs();
    expect(devices.every(d => d.kind === 'audioinput')).toBe(true);
    expect(devices.length).toBe(2);
  });

  it('still enumerates when getUserMedia rejects (permission denied)', async () => {
    navigator.mediaDevices.getUserMedia.mockRejectedValueOnce(new Error('NotAllowedError'));
    const devices = await enumerateAudioInputs();
    expect(Array.isArray(devices)).toBe(true);
  });
});
