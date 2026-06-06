export class AudioSource {
  constructor() {
    this.ctx = null;
    this.analyser = null;
    this.stream = null;
    this.freqData = null;
    this.deviceId = null;
    this.error = null;
  }

  async connect(deviceId) {
    this.disconnect();
    this.error = null;
    try {
      this.ctx = new AudioContext();
      const constraints = {
        audio: deviceId === 'default' ? true : { deviceId: { exact: deviceId }, echoCancellation: false, noiseSuppression: false, autoGainControl: false },
      };
      this.stream = await navigator.mediaDevices.getUserMedia(constraints);
      const source = this.ctx.createMediaStreamSource(this.stream);
      this.analyser = this.ctx.createAnalyser();
      this.analyser.fftSize = 16384;
      this.analyser.smoothingTimeConstant = 0;
      source.connect(this.analyser);
      this.freqData = new Float32Array(this.analyser.frequencyBinCount);
      this.deviceId = deviceId;
    } catch (err) {
      this.error = err.message;
      this.disconnect();
    }
  }

  disconnect() {
    if (this.stream) this.stream.getTracks().forEach((t) => t.stop());
    if (this.ctx) this.ctx.close();
    this.ctx = null;
    this.analyser = null;
    this.stream = null;
    this.freqData = null;
    this.deviceId = null;
  }

  // Returns a Float32Array of dBFS values per band center, or null if not connected.
  readBands(centers, frac) {
    if (!this.analyser || !this.freqData) return null;
    this.analyser.getFloatFrequencyData(this.freqData);
    const binHz = this.ctx.sampleRate / this.analyser.fftSize;
    const n = centers.length;
    const out = new Float32Array(n);
    const halfOct = 1 / (2 * frac);
    for (let i = 0; i < n; i++) {
      const fc = centers[i];
      const fLow = fc * Math.pow(2, -halfOct);
      const fHigh = fc * Math.pow(2, halfOct);
      const binLow = Math.max(0, Math.floor(fLow / binHz));
      const binHigh = Math.min(this.freqData.length - 1, Math.ceil(fHigh / binHz));
      let peak = -200;
      for (let b = binLow; b <= binHigh; b++) {
        if (this.freqData[b] > peak) peak = this.freqData[b];
      }
      out[i] = Math.max(-95, Math.min(-2, peak > -200 ? peak : -95));
    }
    return out;
  }

  get connected() { return this.analyser !== null; }
}

export async function enumerateAudioInputs() {
  try {
    // Probe for permission — labels are empty until granted
    const probe = await navigator.mediaDevices.getUserMedia({ audio: true });
    probe.getTracks().forEach((t) => t.stop());
  } catch { /* permission denied — will still enumerate but labels may be empty */ }
  const devices = await navigator.mediaDevices.enumerateDevices();
  return devices.filter((d) => d.kind === 'audioinput');
}
