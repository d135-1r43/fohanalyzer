export class AudioSource {
  constructor() {
    this.ctx = null;
    this.analyser = null;
    this.splitter = null;
    this.merger = null;
    this.stream = null;
    this.freqData = null;
    this.timeData = null;
    this.deviceId = null;
    this.channelCount = 1;
    this.channelIndex = 0;
    this.error = null;
  }

  async connect(deviceId, channelIndex = 0) {
    // Same device — just reroute the channel splitter
    if (this.connected && this.deviceId === deviceId) {
      this.setChannel(channelIndex);
      return this.channelCount;
    }

    this.disconnect();
    this.error = null;
    try {
      this.ctx = new AudioContext();
      const base = deviceId === 'default'
        ? {}
        : { deviceId: { exact: deviceId }, echoCancellation: false, noiseSuppression: false, autoGainControl: false };

      // First ask for as many channels as the platform will give us.
      this.stream = await navigator.mediaDevices.getUserMedia({ audio: { ...base, channelCount: { ideal: 32 } } });
      let count = this.stream.getAudioTracks()[0].getSettings().channelCount || 0;

      // Chrome (especially on macOS) often delivers/reports mono for multichannel
      // interfaces, and getSettings().channelCount is unreliable. If we didn't
      // clearly get stereo-or-more, force an exact stereo request so we can reliably
      // split L/R — exact:2 throws if the device truly can't do stereo.
      if (count < 2) {
        try {
          const stereo = await navigator.mediaDevices.getUserMedia({ audio: { ...base, channelCount: { exact: 2 } } });
          this.stream.getTracks().forEach((t) => t.stop());
          this.stream = stereo;
          count = 2;
        } catch {
          count = count || 1; // genuinely mono device
        }
      }
      this.channelCount = count;

      const source = this.ctx.createMediaStreamSource(this.stream);
      this.analyser = this.ctx.createAnalyser();
      this.analyser.fftSize = 16384;
      this.analyser.smoothingTimeConstant = 0;

      if (this.channelCount > 1) {
        this.splitter = this.ctx.createChannelSplitter(this.channelCount);
        this.merger = this.ctx.createChannelMerger(1);
        source.connect(this.splitter);
        const idx = Math.min(Math.max(0, channelIndex), this.channelCount - 1);
        this.splitter.connect(this.merger, idx, 0);
        this.merger.connect(this.analyser);
        this.channelIndex = idx;
      } else {
        source.connect(this.analyser);
        this.channelIndex = 0;
      }

      this.freqData = new Float32Array(this.analyser.frequencyBinCount);
      this.timeData = new Float32Array(this.analyser.fftSize);
      this.deviceId = deviceId;
    } catch (err) {
      this.error = err.message;
      this.disconnect();
    }
    return this.channelCount;
  }

  setChannel(channelIndex) {
    if (!this.splitter || !this.merger || this.channelCount <= 1) return;
    try { this.splitter.disconnect(this.merger, this.channelIndex, 0); } catch { /* already disconnected */ }
    const idx = Math.min(Math.max(0, channelIndex), this.channelCount - 1);
    this.splitter.connect(this.merger, idx, 0);
    this.channelIndex = idx;
  }

  disconnect() {
    if (this.stream) this.stream.getTracks().forEach((t) => t.stop());
    if (this.ctx) this.ctx.close();
    this.ctx = null;
    this.analyser = null;
    this.splitter = null;
    this.merger = null;
    this.stream = null;
    this.freqData = null;
    this.timeData = null;
    this.deviceId = null;
    this.channelCount = 1;
    this.channelIndex = 0;
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

  // Returns the broadband RMS level in dBFS, or null if not connected.
  readRMS() {
    if (!this.analyser || !this.timeData) return null;
    this.analyser.getFloatTimeDomainData(this.timeData);
    let sum = 0;
    for (let i = 0; i < this.timeData.length; i++) sum += this.timeData[i] * this.timeData[i];
    const rms = Math.sqrt(sum / this.timeData.length);
    return rms > 1e-9 ? 20 * Math.log10(rms) : -144;
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
