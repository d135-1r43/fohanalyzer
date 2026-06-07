export class SignalState {
  frac = 0;
  avgMic = null; avgSolo = null;
  dispMic = null; dispSolo = null;
  holdMic = null; holdSolo = null;

  ensureBands(frac, n) {
    if (this.frac === frac && this.dispMic?.length === n) return;
    this.frac = frac;
    this.avgMic  = new Float32Array(n).fill(-65);
    this.avgSolo = new Float32Array(n).fill(-65);
    this.dispMic  = new Float32Array(n).fill(-65);
    this.dispSolo = new Float32Array(n).fill(-65);
    this.holdMic  = new Float32Array(n).fill(-95);
    this.holdSolo = new Float32Array(n).fill(-95);
  }

  resetHold() {
    this.holdMic.fill(-95);
    this.holdSolo.fill(-95);
  }

  update(mic, solo, micVoice, soloVoice, centers, smoothing, avgN) {
    const n = centers.length;
    const ca = avgN > 1 ? 1 - 1 / avgN : 0;
    const cs = smoothing;
    for (let i = 0; i < n; i++) {
      const lf = Math.log2(centers[i] / 1000);
      const mt = mic[i]  + micVoice.g  + micVoice.tilt  * lf;
      const sv = solo[i] + soloVoice.g + soloVoice.tilt * lf;
      this.avgMic[i]  = ca > 0 ? this.avgMic[i]  * ca + mt * (1 - ca) : mt;
      this.avgSolo[i] = ca > 0 ? this.avgSolo[i] * ca + sv * (1 - ca) : sv;
      const csm = this.avgMic[i]  > this.dispMic[i]  ? 0 : cs;
      const css = this.avgSolo[i] > this.dispSolo[i] ? 0 : cs;
      this.dispMic[i]  = this.dispMic[i]  * csm + this.avgMic[i]  * (1 - csm);
      this.dispSolo[i] = this.dispSolo[i] * css + this.avgSolo[i] * (1 - css);
      this.holdMic[i]  = Math.max(this.holdMic[i]  - 0.42, this.dispMic[i]);
      this.holdSolo[i] = Math.max(this.holdSolo[i] - 0.42, this.dispSolo[i]);
    }
  }

  getStats(centers) {
    const n = centers.length;
    let pMic = -200, pIdx = 0, sMic = 0, sSolo = 0;
    for (let i = 0; i < n; i++) {
      if (this.dispMic[i] > pMic) { pMic = this.dispMic[i]; pIdx = i; }
      sMic += this.dispMic[i]; sSolo += this.dispSolo[i];
    }
    return {
      peakFreq: centers[pIdx], micPeak: pMic,
      micAvg: sMic / n, soloAvg: sSolo / n,
      soloPeak: Array.from(this.dispSolo).reduce((a, b) => Math.max(a, b), -200),
    };
  }
}
