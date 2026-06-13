<script>
  import { onMount } from 'svelte';
  import Logo from './lib/Logo.svelte';
  import Toggle from './lib/Toggle.svelte';
  import Segmented from './lib/Segmented.svelte';
  import SourceCard from './lib/SourceCard.svelte';
  import AnalyzerCanvas from './lib/AnalyzerCanvas.svelte';
  import { bandCenters, noteName, fmtFreq, fmtShort } from './lib/engine.js';
  import { AudioSource, enumerateAudioInputs } from './lib/audioInput.js';

  /* global __APP_VERSION__ */
  const appVersion = __APP_VERSION__;

  const MIC_INPUTS = [
    { id: 'ch32', label: 'Ch 32 · Meas Mic', g: 0, tilt: 0 },
    { id: 'ch31', label: 'Ch 31 · Meas Mic', g: -1.5, tilt: 0.3 },
    { id: 'rta',  label: 'RTA Input',        g: 1,    tilt: -0.4 },
    { id: 'usb',  label: 'USB Rtn 1',        g: -0.5, tilt: 0.6 },
    { id: 'aes',  label: 'AES50-A 24',       g: 0.5,  tilt: -0.2 },
  ];
  const SOLO_INPUTS = [
    { id: 'pfl',  label: 'Solo Bus · PFL',   g: 0,    tilt: 0 },
    { id: 'main', label: 'Main L/R',          g: 1.5,  tilt: -0.4 },
    { id: 'mono', label: 'Mono / Sub',        g: 1,    tilt: -1.5 },
    { id: 'mtx1', label: 'Matrix 1-2',        g: -1,   tilt: -0.2 },
    { id: 'aux4', label: 'Aux 4 · Mon Wedge', g: -2,   tilt: 0.5 },
  ];

  let micOn       = $state(true);
  let soloOn      = $state(true);
  let frac        = $state(12);
  let smoothing   = $state(0.62);
  let avgN        = $state(4);
  let peakHold    = $state(false);
  let holdReset   = $state(0);
  let markers     = $state(true);
  let markerSource = $state('mic');
  let micChan     = $state('ch32');
  let soloChan    = $state('pfl');
  let ring        = $state({ active: false, fc: 2500, t0: 0 });
  let showTransfer    = $state(false);
  let captureNonce    = $state(0);
  let reference       = $state(null);
  let showReference   = $state(true);
  let feedbackLog     = $state([]);
  let locateFreq      = $state(null);
  let stats = $state({ peakFreq: 0, micPeak: -90, micAvg: -90, soloAvg: -90, soloPeak: -90, micRmsDbfs: null });
  let splOffset = $state(0);
  let calRefSpl = $state(94);
  let clock = $state('');
  let audioDevices = $state([]);

  const isMicLive  = $derived(micChan.startsWith('live:'));
  const isSoloLive = $derived(soloChan.startsWith('live:'));
  const signalStatus = $derived(
    isMicLive && isSoloLive ? 'live signal'
    : isMicLive             ? 'mic live · solo simulated'
    : isSoloLive            ? 'mic simulated · solo live'
    :                         'simulated signal'
  );
  const micSpl = $derived(stats.micRmsDbfs != null ? stats.micRmsDbfs + splOffset : null);

  const micVoice  = $derived(MIC_INPUTS.find((o) => o.id === micChan));
  const soloVoice = $derived(SOLO_INPUTS.find((o) => o.id === soloChan));

  // Live AudioSource instances — plain (non-reactive) objects
  let micSource = null;
  let soloSource = null;

  $effect(() => {
    if (micChan.startsWith('live:')) {
      const deviceId = micChan.slice(5);
      if (!micSource) micSource = new AudioSource();
      micSource.connect(deviceId);
    } else {
      micSource?.disconnect();
      micSource = null;
    }
  });

  $effect(() => {
    if (soloChan.startsWith('live:')) {
      const deviceId = soloChan.slice(5);
      if (!soloSource) soloSource = new AudioSource();
      soloSource.connect(deviceId);
    } else {
      soloSource?.disconnect();
      soloSource = null;
    }
  });

  onMount(async () => {
    const id = setInterval(() => {
      const d = new Date();
      clock = d.toLocaleTimeString('en-GB');
    }, 1000);

    audioDevices = await enumerateAudioInputs();

    // Re-enumerate after permission is granted (labels fill in)
    navigator.mediaDevices.addEventListener('devicechange', async () => {
      audioDevices = await enumerateAudioInputs();
    });

    return () => {
      clearInterval(id);
      micSource?.disconnect();
      soloSource?.disconnect();
    };
  });

  function onStats(s) { stats = s; }
  function calibrateSpl() { if (stats.micRmsDbfs != null) splOffset = calRefSpl - stats.micRmsDbfs; }

  function onCapture(d) {
    reference = { ...d, time: new Date().toLocaleTimeString('en-GB').slice(0, 5) };
    showReference = true;
  }

  function doCapture() { captureNonce += 1; }

  function injectRing() {
    if (ring.active) { ring = { ...ring, active: false }; return; }
    const fc = 1600 + Math.random() * 2600;
    ring = { active: true, fc, t0: performance.now() / 1000 };
    const iso = bandCenters(3);
    let band = iso[0];
    for (const b of iso) if (Math.abs(Math.log2(b / fc)) < Math.abs(Math.log2(band / fc))) band = b;
    const cut = 4 + Math.round(Math.random() * 3);
    const entry = {
      id: Date.now(), freq: fc, note: noteName(fc), band, cut,
      time: new Date().toLocaleTimeString('en-GB').slice(0, 5),
    };
    feedbackLog = [entry, ...feedbackLog].slice(0, 6);
    locateFreq = fc;
  }

  function removeFbEntry(id) {
    const en = feedbackLog.find((x) => x.id === id);
    if (en && locateFreq === en.freq) locateFreq = null;
    feedbackLog = feedbackLog.filter((x) => x.id !== id);
  }

  function clearFeedbackLog() { feedbackLog = []; locateFreq = null; }

  function toggleLocate(freq) { locateFreq = locateFreq === freq ? null : freq; }
</script>

<div class="app">
  <!-- header -->
  <header class="header">
    <div class="brand">
      <Logo />
      <div class="wordmark">
        <div class="wm-top"><b>FOH</b>analyzer</div>
        <div class="wm-sub">DUAL SPECTRUM · RTA</div>
      </div>
      <span class="ver-chip">{appVersion}</span>
    </div>

    <div class="hdr-readouts">
      <div class="ro">
        <span class="ro-lbl">PEAK</span>
        <span class="ro-val" style="color:#e9fb9b">{fmtFreq(stats.peakFreq)}</span>
      </div>
      <div class="ro">
        <span class="ro-lbl">MIC</span>
        <span class="ro-val" style="color:#22d3ee">{stats.micPeak.toFixed(1)}<i>dB</i></span>
      </div>
      <div class="ro">
        <span class="ro-lbl">SOLO</span>
        <span class="ro-val" style="color:#f5a524">{stats.soloPeak.toFixed(1)}<i>dB</i></span>
      </div>
      {#if isMicLive}
        <div class="ro ro-spl" class:ro-spl-cal={splOffset !== 0}>
          <span class="ro-lbl">SPL{splOffset === 0 ? ' · UNCAL' : ''}</span>
          <span class="ro-val" style="color:#a3e635">
            {micSpl != null ? micSpl.toFixed(1) : '—'}<i>dB</i>
          </span>
        </div>
      {/if}
    </div>

    <div class="status">
      <span class="led"></span>
      <span class="st-txt">LIVE</span>
      <span class="st-div"></span>
      <span class="st-mono">48 kHz / 24-bit</span>
      <span class="st-div"></span>
      <span class="st-mono">{clock}</span>
    </div>
  </header>

  <!-- main -->
  <div class="main">
    <div class="stage">
      <AnalyzerCanvas
        {micOn} {soloOn} {frac} {smoothing} {avgN} {peakHold}
        {holdReset} {markers} {markerSource} {ring} {onStats}
        {micVoice} {soloVoice} {micSource} {soloSource}
        {showTransfer} {captureNonce} {onCapture} {reference} {showReference} {locateFreq}
      />
    </div>

    <!-- control rail -->
    <aside class="rail">
      <!-- Sources -->
      <section class="section">
        <div class="sec-title">Sources</div>
        <SourceCard
          name="Measurement Mic" sub="Room · capture" color="#22d3ee"
          on={micOn} setOn={(v) => (micOn = v)} level={stats.micAvg}
          isMarker={markerSource === 'mic'} setMarker={() => (markerSource = 'mic')}
          options={MIC_INPUTS} chan={micChan} setChan={(v) => (micChan = v)}
          {audioDevices}
        />
        <SourceCard
          name="Solo Bus" sub="Console · PFL/AFL" color="#f5a524"
          on={soloOn} setOn={(v) => (soloOn = v)} level={stats.soloAvg}
          isMarker={markerSource === 'solo'} setMarker={() => (markerSource = 'solo')}
          options={SOLO_INPUTS} chan={soloChan} setChan={(v) => (soloChan = v)}
          {audioDevices}
        />
      </section>

      <!-- SPL Meter -->
      {#if isMicLive}
        <section class="section">
          <div class="sec-title">SPL Meter</div>
          <div class="spl-display" class:spl-cal={splOffset !== 0}>
            <div class="spl-readout">
              <span class="spl-val">{micSpl != null ? micSpl.toFixed(1) : '—'}</span>
              <span class="spl-unit">dB SPL</span>
            </div>
            <span class="spl-badge" class:spl-badge-cal={splOffset !== 0}>
              {splOffset !== 0 ? 'CAL' : 'UNCAL'}
            </span>
          </div>
          <div class="spl-cal-row">
            <input
              type="number" class="spl-input" bind:value={calRefSpl}
              min="60" max="140" step="0.1"
            />
            <span class="spl-ref-lbl">dB SPL ref</span>
            <button class="mini-btn" onclick={calibrateSpl}>Calibrate</button>
          </div>
          {#if splOffset !== 0}
            <div class="hint">
              Offset {splOffset > 0 ? '+' : ''}{splOffset.toFixed(1)} dB ·
              <button class="lnk-btn" onclick={() => (splOffset = 0)}>Reset</button>
            </div>
          {:else}
            <div class="hint">Play a known reference level, enter it, then tap Calibrate.</div>
          {/if}
        </section>
      {/if}

      <!-- Resolution -->
      <section class="section">
        <div class="sec-title">Resolution</div>
        <Segmented
          value={frac} onChange={(v) => (frac = v)}
          options={[
            { v: 1, label: '1/1' }, { v: 3, label: '1/3' }, { v: 6, label: '1/6' },
            { v: 12, label: '1/12' }, { v: 24, label: '1/24' },
          ]}
        />
        <div class="hint">Octave-band averaging width</div>
      </section>

      <!-- Response -->
      <section class="section">
        <div class="sec-title">Response</div>
        <div class="row">
          <span class="row-lbl">Smoothing</span>
          <span class="row-val">{Math.round(smoothing * 100)}%</span>
        </div>
        <input
          type="range" class="slider" min="0" max="0.95" step="0.01"
          bind:value={smoothing}
        />
        <div class="row" style="margin-top:14px">
          <span class="row-lbl">Averaging</span>
        </div>
        <Segmented
          value={avgN} onChange={(v) => (avgN = v)}
          options={[
            { v: 1, label: 'Off' }, { v: 2, label: '2' }, { v: 4, label: '4' },
            { v: 8, label: '8' }, { v: 16, label: '16' },
          ]}
        />
      </section>

      <!-- Overlays -->
      <section class="section">
        <div class="sec-title">Overlays</div>
        <div class="ctl-row">
          <span class="ctl-lbl">Peak hold</span>
          <div class="ctl-right">
            {#if peakHold}
              <button class="mini-btn" onclick={() => (holdReset += 1)}>Reset</button>
            {/if}
            <Toggle on={peakHold} onChange={(v) => (peakHold = v)} color="#a3e635" />
          </div>
        </div>
        <div class="ctl-row">
          <span class="ctl-lbl">Peak markers</span>
          <Toggle on={markers} onChange={(v) => (markers = v)} color="#a3e635" />
        </div>
        <div class="ctl-row">
          <span class="ctl-lbl">Transfer fn <i class="ctl-sub">Mic−Solo</i></span>
          <Toggle on={showTransfer} onChange={(v) => (showTransfer = v)} color="#b794f6" />
        </div>
      </section>

      <!-- Reference capture -->
      <section class="section">
        <div class="sec-title">Reference capture</div>
        {#if !reference}
          <button class="btn btn-capture" onclick={doCapture}>⊕  Capture reference</button>
        {:else}
          <div class="ref-active">
            <div class="ref-info">
              <span class="ref-dot"></span>
              Snapshot <b>{reference.time}</b>
            </div>
            <div class="ref-actions">
              <button class="mini-btn" onclick={() => (showReference = !showReference)}>
                {showReference ? 'Hide' : 'Show'}
              </button>
              <button class="mini-btn" onclick={doCapture}>Re-cap</button>
              <button class="mini-btn" onclick={() => (reference = null)}>Clear</button>
            </div>
          </div>
        {/if}
        <div class="hint">Dashed ghost of both traces — A/B before vs after EQ.</div>
      </section>

      <!-- Ring-out assist -->
      <section class="section ringout">
        <div class="sec-title">Ring-out assist</div>
        <p class="ro-copy">Simulate a monitor feedback ring to practise hunting the offending band.</p>
        {#if ring.active}
          <div class="ring-active">
            <div class="ring-freq">
              <span class="rf-dot"></span>
              Ringing @ <b>{fmtFreq(ring.fc)}</b> · {noteName(ring.fc)}
            </div>
            <button class="btn btn-clear" onclick={injectRing}>Clear feedback</button>
          </div>
        {:else}
          <button class="btn btn-ring" onclick={injectRing}>⚠  Inject feedback</button>
        {/if}
        {#if feedbackLog.length > 0}
          <div class="fb-log">
            <div class="fb-head">
              <span>Feedback log</span>
              <button class="fb-clear" onclick={clearFeedbackLog}>Clear all</button>
            </div>
            {#each feedbackLog as en (en.id)}
              <div
                class="fb-row"
                class:on={locateFreq === en.freq}
                onclick={() => toggleLocate(en.freq)}
                title="Tap to locate on the analyzer"
              >
                <div class="fb-left">
                  <span class="fb-freq">{fmtFreq(en.freq)}</span>
                  <span class="fb-note">{en.note}</span>
                </div>
                <div class="fb-eq">GEQ {fmtShort(en.band)} · −{en.cut} dB</div>
                <button
                  class="fb-x"
                  onclick={(ev) => { ev.stopPropagation(); removeFbEntry(en.id); }}
                >×</button>
              </div>
            {/each}
          </div>
        {/if}
      </section>

      <div class="rail-foot">FOHanalyzer {appVersion} · {signalStatus}</div>
    </aside>
  </div>
</div>

<style>
  :global(:root) {
    --bg: #070a0f;
    --panel: #0d121b;
    --panel-2: #10161f;
    --line: #1c2733;
    --line-soft: #161e28;
    --txt: #e7eef6;
    --txt-dim: #8595a6;
    --txt-mut: #5b6a7a;
    --cyan: #22d3ee;
    --amber: #f5a524;
    --lime: #a3e635;
  }
  :global(*, *::before, *::after) { box-sizing: border-box; margin: 0; padding: 0; }
  :global(html), :global(body) { height: 100%; }
  :global(body) {
    background: var(--bg);
    color: var(--txt);
    font-family: 'Saira', system-ui, sans-serif;
    overflow: hidden;
    -webkit-font-smoothing: antialiased;
    user-select: none;
  }
  :global(#app) { height: 100vh; }

  .app { display: flex; flex-direction: column; height: 100vh; }

  /* header */
  .header {
    height: 66px; flex: 0 0 66px;
    display: flex; align-items: center; gap: 24px;
    padding: 0 18px 0 16px;
    background: linear-gradient(180deg, #0e141d, #0b1018);
    border-bottom: 1px solid var(--line);
  }
  .brand { display: flex; align-items: center; gap: 12px; }
  .wordmark { line-height: 1; }
  .wm-top { font-size: 20px; letter-spacing: .2px; color: var(--txt); font-weight: 500; }
  .wm-top :global(b) { font-weight: 700; }
  .wm-sub { font-family: 'IBM Plex Mono', monospace; font-size: 9px; letter-spacing: 2.5px; color: var(--txt-mut); margin-top: 4px; }
  .ver-chip {
    font-family: 'IBM Plex Mono', monospace; font-size: 11px; font-weight: 600;
    color: var(--lime); border: 1px solid rgba(163,230,53,.35);
    background: rgba(163,230,53,.08); border-radius: 5px; padding: 3px 7px; align-self: flex-start;
  }
  .hdr-readouts { display: flex; gap: 10px; margin-left: auto; }
  .ro {
    background: var(--panel); border: 1px solid var(--line-soft); border-radius: 8px;
    padding: 7px 13px; min-width: 96px; display: flex; flex-direction: column; gap: 3px;
  }
  .ro-lbl { font-family: 'IBM Plex Mono', monospace; font-size: 9px; letter-spacing: 2px; color: var(--txt-mut); }
  .ro-val {
    font-family: 'IBM Plex Mono', monospace; font-size: 17px; font-weight: 600;
    display: flex; align-items: baseline; gap: 3px;
  }
  .ro-val i { font-style: normal; font-size: 10px; color: var(--txt-mut); }
  .status { display: flex; align-items: center; gap: 10px; padding-left: 4px; }
  .led {
    width: 9px; height: 9px; border-radius: 50%;
    background: #ff4d4d; box-shadow: 0 0 9px #ff4d4d;
    animation: pulse 1.4s ease-in-out infinite;
  }
  @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: .35; } }
  .st-txt { font-family: 'IBM Plex Mono', monospace; font-size: 11px; font-weight: 600; letter-spacing: 2px; color: #ff8080; }
  .st-div { width: 1px; height: 16px; background: var(--line); }
  .st-mono { font-family: 'IBM Plex Mono', monospace; font-size: 11px; color: var(--txt-dim); }

  /* main */
  .main { flex: 1; display: flex; min-height: 0; }
  .stage { flex: 1; min-width: 0; padding: 12px; }

  /* rail */
  .rail {
    width: 326px; flex: 0 0 326px;
    background: var(--panel); border-left: 1px solid var(--line);
    padding: 14px 14px 0; overflow-y: auto; display: flex; flex-direction: column;
  }
  .section { padding: 14px 0; border-bottom: 1px solid var(--line-soft); }
  .section:first-child { padding-top: 2px; }
  .sec-title {
    font-family: 'IBM Plex Mono', monospace; font-size: 10px; font-weight: 600;
    letter-spacing: 2.5px; text-transform: uppercase; color: var(--txt-mut); margin-bottom: 11px;
  }
  .hint { font-size: 11px; color: var(--txt-mut); margin-top: 8px; }

  /* slider */
  .row { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 9px; }
  .row-lbl { font-size: 13px; color: var(--txt-dim); }
  .row-val { font-family: 'IBM Plex Mono', monospace; font-size: 13px; color: var(--txt); }
  .slider {
    -webkit-appearance: none; appearance: none; width: 100%; height: 5px; border-radius: 3px;
    background: linear-gradient(90deg, #1d2a36, #2b3d4d); outline: none; cursor: pointer;
  }
  .slider::-webkit-slider-thumb {
    -webkit-appearance: none; width: 17px; height: 17px; border-radius: 50%;
    background: var(--cyan); border: 3px solid #0a121a;
    box-shadow: 0 0 0 1px var(--cyan), 0 0 8px rgba(34,211,238,.5); cursor: pointer;
  }
  .slider::-moz-range-thumb {
    width: 17px; height: 17px; border-radius: 50%;
    background: var(--cyan); border: 3px solid #0a121a; cursor: pointer;
  }

  /* ctl rows */
  .ctl-row { display: flex; align-items: center; justify-content: space-between; padding: 7px 0; }
  .ctl-lbl { font-size: 13px; color: var(--txt-dim); }
  .ctl-right { display: flex; align-items: center; gap: 9px; }
  .ctl-sub { font-style: normal; font-family: 'IBM Plex Mono', monospace; font-size: 10px; color: #8b7bb5; margin-left: 2px; }
  .mini-btn {
    font-family: 'IBM Plex Mono', monospace; font-size: 10px; color: var(--txt-dim);
    background: #131b25; border: 1px solid var(--line); border-radius: 5px; padding: 4px 8px; cursor: pointer;
  }
  .mini-btn:hover { color: var(--txt); border-color: var(--txt-mut); }

  /* reference capture */
  .btn {
    width: 100%; font-family: 'Saira', sans-serif; font-size: 13px; font-weight: 600;
    border-radius: 9px; padding: 11px; cursor: pointer; transition: all .15s;
  }
  .btn-capture { background: rgba(34,211,238,.08); color: var(--cyan); border: 1px solid rgba(34,211,238,.35); }
  .btn-capture:hover { background: rgba(34,211,238,.15); }
  .ref-active { display: flex; flex-direction: column; gap: 9px; }
  .ref-info {
    display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--txt-dim);
    background: #10161f; border: 1px solid var(--line-soft); border-radius: 8px; padding: 9px 11px;
  }
  .ref-info :global(b) { font-family: 'IBM Plex Mono', monospace; color: var(--txt); }
  .ref-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--cyan); box-shadow: 0 0 7px var(--cyan); }
  .ref-actions { display: flex; gap: 7px; }
  .ref-actions .mini-btn { flex: 1; text-align: center; }

  /* ring-out */
  .ro-copy { font-size: 12px; line-height: 1.5; color: var(--txt-mut); margin-bottom: 12px; }
  .btn-ring { background: rgba(245,87,93,.1); color: #ff8a8e; border: 1px solid rgba(245,87,93,.4); }
  .btn-ring:hover { background: rgba(245,87,93,.18); }
  .ring-active { display: flex; flex-direction: column; gap: 10px; }
  .ring-freq {
    display: flex; align-items: center; gap: 8px; font-size: 13px; color: #ffb3b6;
    background: rgba(245,87,93,.08); border: 1px solid rgba(245,87,93,.3); border-radius: 8px; padding: 9px 11px;
  }
  .ring-freq :global(b) { font-family: 'IBM Plex Mono', monospace; color: #ff8a8e; }
  .rf-dot { width: 8px; height: 8px; border-radius: 50%; background: #ff5b60; box-shadow: 0 0 8px #ff5b60; animation: pulse .7s infinite; }
  .btn-clear { background: #131b25; color: var(--txt-dim); border: 1px solid var(--line); }
  .btn-clear:hover { color: var(--txt); }

  /* feedback log */
  .fb-log { margin-top: 14px; }
  .fb-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
  .fb-head span { font-family: 'IBM Plex Mono', monospace; font-size: 10px; letter-spacing: 1.5px; text-transform: uppercase; color: var(--txt-mut); }
  .fb-clear { font-family: 'IBM Plex Mono', monospace; font-size: 10px; color: var(--txt-mut); background: none; border: 0; cursor: pointer; }
  .fb-clear:hover { color: #ff8a8e; }
  .fb-row {
    display: flex; align-items: center; gap: 8px; cursor: pointer;
    background: #10161f; border: 1px solid var(--line-soft); border-left: 2px solid #ff5b60;
    border-radius: 7px; padding: 8px 9px; margin-bottom: 6px; transition: background .14s, border-color .14s;
  }
  .fb-row:hover { background: #161f2a; }
  .fb-row.on { background: rgba(255,91,96,.1); border-color: rgba(255,91,96,.5); }
  .fb-left { display: flex; flex-direction: column; gap: 2px; min-width: 64px; }
  .fb-freq { font-family: 'IBM Plex Mono', monospace; font-size: 13px; font-weight: 600; color: #ffb3b6; }
  .fb-note { font-family: 'IBM Plex Mono', monospace; font-size: 9.5px; color: var(--txt-mut); }
  .fb-eq { flex: 1; font-family: 'IBM Plex Mono', monospace; font-size: 11px; color: var(--txt-dim); text-align: right; }
  .fb-x { font-size: 16px; line-height: 1; color: var(--txt-mut); background: none; border: 0; cursor: pointer; padding: 0 2px; }
  .fb-x:hover { color: #ff8a8e; }

  .rail-foot {
    margin-top: auto; padding: 16px 0 14px;
    font-family: 'IBM Plex Mono', monospace; font-size: 9.5px; letter-spacing: 1px;
    color: #3a4654; text-align: center;
  }
  .rail::-webkit-scrollbar { width: 8px; }
  .rail::-webkit-scrollbar-thumb { background: #1c2733; border-radius: 4px; }

  /* spl header chip */
  .ro-spl { border-color: rgba(163,230,53,.2); }
  .ro-spl-cal { border-color: rgba(163,230,53,.4); }

  /* spl section */
  .spl-display {
    display: flex; align-items: center; justify-content: space-between;
    background: #10161f; border: 1px solid rgba(163,230,53,.15);
    border-radius: 10px; padding: 11px 14px; margin-bottom: 10px;
  }
  .spl-cal { border-color: rgba(163,230,53,.4); }
  .spl-readout { display: flex; align-items: baseline; gap: 6px; }
  .spl-val {
    font-family: 'IBM Plex Mono', monospace; font-size: 30px; font-weight: 600;
    color: var(--lime);
  }
  .spl-unit { font-family: 'IBM Plex Mono', monospace; font-size: 11px; color: var(--txt-mut); }
  .spl-badge {
    font-family: 'IBM Plex Mono', monospace; font-size: 9px; font-weight: 700;
    letter-spacing: 1.5px; padding: 3px 7px; border-radius: 4px;
    background: rgba(255,255,255,.05); color: var(--txt-mut); border: 1px solid var(--line);
  }
  .spl-badge-cal {
    background: rgba(163,230,53,.12); color: var(--lime); border-color: rgba(163,230,53,.35);
  }
  .spl-cal-row {
    display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
  }
  .spl-input {
    width: 68px; padding: 7px 8px; font-family: 'IBM Plex Mono', monospace; font-size: 13px;
    background: #131b25; border: 1px solid var(--line); border-radius: 7px;
    color: var(--txt); outline: none; text-align: right; -moz-appearance: textfield;
  }
  .spl-input::-webkit-inner-spin-button,
  .spl-input::-webkit-outer-spin-button { -webkit-appearance: none; }
  .spl-input:focus { border-color: var(--lime); }
  .spl-ref-lbl { font-size: 12px; color: var(--txt-mut); flex: 1; }
  .lnk-btn {
    background: none; border: 0; padding: 0; cursor: pointer;
    font-family: 'IBM Plex Mono', monospace; font-size: 11px;
    color: var(--txt-mut); text-decoration: underline;
  }
  .lnk-btn:hover { color: var(--txt); }
</style>
