export const FMIN = 20, FMAX = 20000;
const LOGMIN = Math.log2(FMIN), LOGMAX = Math.log2(FMAX), LOGSPAN = LOGMAX - LOGMIN;

export function freqNorm(f) { return (Math.log2(f) - LOGMIN) / LOGSPAN; }
export function normFreq(n) { return Math.pow(2, LOGMIN + n * LOGSPAN); }

export function bandCenters(frac) {
  const r = Math.pow(2, 1 / frac);
  const lnr = Math.log(r);
  const kmin = Math.ceil(Math.log(FMIN / 1000) / lnr);
  const kmax = Math.floor(Math.log(FMAX / 1000) / lnr);
  const out = [];
  for (let k = kmin; k <= kmax; k++) out.push(1000 * Math.pow(r, k));
  return out;
}

function bump(f, fc, widthOct, gainDb) {
  const x = Math.log2(f / fc) / widthOct;
  return gainDb * Math.exp(-x * x);
}

const NOTES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
export function noteName(f) {
  if (f <= 0) return '';
  const n = Math.round(12 * Math.log2(f / 440)) + 69;
  const name = NOTES[((n % 12) + 12) % 12];
  const oct = Math.floor(n / 12) - 1;
  return name + oct;
}

export const zones = [
  { f0: 20,   f1: 60,    name: 'SUB',       color: '#3b5bff' },
  { f0: 60,   f1: 200,   name: 'BASS',      color: '#2f9bff' },
  { f0: 200,  f1: 500,   name: 'LOW-MID',   color: '#19c6c6' },
  { f0: 500,  f1: 2000,  name: 'MID',       color: '#2fd07a' },
  { f0: 2000, f1: 4000,  name: 'UPPER-MID', color: '#a3e635' },
  { f0: 4000, f1: 8000,  name: 'PRESENCE',  color: '#f5a524' },
  { f0: 8000, f1: 20000, name: 'AIR',       color: '#f5575d' },
];

export function sample(centers, t, ring) {
  const n = centers.length;
  const mic = new Float32Array(n);
  const solo = new Float32Array(n);

  const bps = 2.0;
  const beat = t * bps;
  const beatFrac = beat - Math.floor(beat);
  const beatIdx = Math.floor(beat) % 4;
  const kickEnv = Math.exp(-beatFrac * 16);
  const snareEnv = (beatIdx === 1 || beatIdx === 3) ? Math.exp(-beatFrac * 20) : 0;
  const hatFrac = (t * bps * 2) % 1;
  const hatEnv = Math.exp(-hatFrac * 26) * 0.8;
  const bassSust = 0.45 + 0.55 * (0.5 + 0.5 * Math.sin(t * Math.PI));
  let lead = 0.5 + 0.5 * Math.sin(t * 1.27) + 0.3 * Math.sin(t * 2.73 + 1.1);
  lead = Math.max(0, Math.min(1, lead));

  let ringLvl = 0, ringOsc = 1, ringFc = 1000;
  if (ring && ring.active) {
    const age = t - ring.t0;
    ringLvl = Math.min(42, age * 50);
    ringOsc = 1 + 0.12 * Math.sin(t * 38);
    ringFc = ring.fc;
  }

  for (let i = 0; i < n; i++) {
    const f = centers[i];
    let g = -3.0 * Math.log2(f / 1000) - 37;
    g += kickEnv * bump(f, 58, 0.5, 15);
    g += bassSust * bump(f, 95, 0.7, 9);
    g += bump(f, 150, 0.6, 4);
    g += bump(f, 400, 0.6, -2.5);
    g += snareEnv * bump(f, 210, 0.5, 12);
    g += snareEnv * bump(f, 3200, 0.85, 8);
    g += (0.4 + 0.6 * lead) * bump(f, 750, 1.0, 6);
    g += (0.3 + 0.7 * lead) * bump(f, 2500, 0.8, 7);
    g += hatEnv * bump(f, 11000, 0.9, 10);
    g += (0.5 + 0.5 * hatEnv) * bump(f, 16000, 1.2, 2.5);

    const h = f * 0.0017;
    const jScale = 0.55 + 0.07 * Math.log2(f / 100);
    const jBase =
      2.0 * Math.sin(t * 8.0 + h * 97) +
      1.3 * Math.sin(t * 15.3 + h * 53) +
      0.8 * Math.sin(t * 23.1 + h * 131);

    let md = 3.0 * Math.cos((2 * Math.PI * f) / 120) * Math.exp(-f / 2600);
    md += bump(f, 48, 0.05, 5) + bump(f, 92, 0.06, 4) + bump(f, 146, 0.06, 3);
    md += bump(f, 62, 0.85, 3);
    md += -3.6 * Math.max(0, Math.log2(f / 7000));
    md += -1.5;
    const micJit = jBase * jScale;
    const soloJit = (jBase * 0.85 + 1.1 * Math.sin(t * 19.0 + h * 71)) * jScale;

    let mv = g + md + micJit;
    let sv = g + 1.2 + soloJit;

    if (ringLvl > 0) {
      mv += bump(f, ringFc, 0.16, ringLvl) * ringOsc;
      sv += bump(f, ringFc, 0.17, ringLvl * 0.7) * ringOsc;
    }

    mic[i] = Math.max(-95, Math.min(-2, mv));
    solo[i] = Math.max(-95, Math.min(-2, sv));
  }
  return { mic, solo };
}

export function fmtFreq(f) {
  if (!f) return '—';
  return f >= 1000 ? (f / 1000).toFixed(f >= 10000 ? 1 : 2) + ' kHz' : Math.round(f) + ' Hz';
}

export function fmtShort(f) {
  return f >= 1000 ? (f / 1000).toFixed(f >= 10000 ? 0 : 1) + 'k' : Math.round(f) + '';
}
