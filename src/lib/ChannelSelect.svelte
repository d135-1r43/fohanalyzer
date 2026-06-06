<script>
  let { value, options = [], audioDevices = [], onChange, color = '#22d3ee' } = $props();
  let open = $state(false);

  // value is either a simulated id or 'live:<deviceId>'
  const isLive = $derived(value.startsWith('live:'));
  const liveId = $derived(isLive ? value.slice(5) : null);

  const curSim  = $derived(options.find((o) => o.id === value));
  const curLive = $derived(audioDevices.find((d) => d.deviceId === liveId));
  const curLabel = $derived(
    isLive
      ? (curLive?.label || 'Unknown device')
      : (curSim?.label || options[0]?.label)
  );
  const tagLabel = $derived(isLive ? 'LIVE' : 'SIM');
  const tagColor = $derived(isLive ? '#a3e635' : color);
</script>

<div class="chan-sel">
  {#if open}
    <div class="chan-backdrop" onclick={() => (open = false)} role="presentation"></div>
  {/if}
  <button class="chan-btn" class:open onclick={() => (open = !open)}>
    <span class="chan-tag" style="color:{tagColor};border-color:{tagColor}">{tagLabel}</span>
    <span class="chan-val">{curLabel}</span>
    <span class="chan-caret">{open ? '▴' : '▾'}</span>
  </button>
  {#if open}
    <div class="chan-menu">
      {#if audioDevices.length > 0}
        <div class="chan-group-label">Live inputs</div>
        {#each audioDevices as dev}
          {@const devVal = 'live:' + dev.deviceId}
          <button
            class="chan-opt"
            class:on={value === devVal}
            onclick={() => { onChange(devVal); open = false; }}
          >
            <span class="opt-label">
              <span class="opt-live-dot"></span>
              {dev.label || 'Microphone ' + dev.deviceId.slice(0, 6)}
            </span>
            {#if value === devVal}
              <span class="chan-check" style="color:#a3e635">●</span>
            {/if}
          </button>
        {/each}
        <div class="chan-divider"></div>
        <div class="chan-group-label">Simulated</div>
      {/if}
      {#each options as opt}
        <button
          class="chan-opt"
          class:on={value === opt.id}
          onclick={() => { onChange(opt.id); open = false; }}
        >
          <span>{opt.label}</span>
          {#if opt.id === value}
            <span class="chan-check" style="color:{color}">●</span>
          {/if}
        </button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .chan-sel { position: relative; margin-top: 10px; }
  .chan-backdrop { position: fixed; inset: 0; z-index: 40; }
  .chan-btn {
    width: 100%; display: flex; align-items: center; gap: 8px;
    background: #0a0e14; border: 1px solid var(--line); border-radius: 8px;
    padding: 8px 10px; cursor: pointer; text-align: left; transition: border-color .15s;
  }
  .chan-btn:hover { border-color: var(--txt-mut); }
  .chan-btn.open { border-color: var(--txt-dim); }
  .chan-tag {
    font-family: 'IBM Plex Mono', monospace; font-size: 8.5px; font-weight: 600; letter-spacing: 1px;
    border: 1px solid; border-radius: 4px; padding: 2px 4px; opacity: .85; flex: 0 0 auto;
  }
  .chan-val {
    flex: 1; font-family: 'IBM Plex Mono', monospace; font-size: 12px; color: var(--txt);
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  .chan-caret { color: var(--txt-mut); font-size: 10px; flex: 0 0 auto; }
  .chan-menu {
    position: absolute; top: calc(100% + 5px); left: 0; right: 0; z-index: 60;
    background: #0d131c; border: 1px solid var(--line); border-radius: 9px; padding: 4px;
    box-shadow: 0 14px 36px rgba(0,0,0,.55);
  }
  .chan-group-label {
    font-family: 'IBM Plex Mono', monospace; font-size: 9px; font-weight: 600;
    letter-spacing: 1.5px; text-transform: uppercase; color: var(--txt-mut);
    padding: 6px 9px 4px;
  }
  .chan-divider { height: 1px; background: var(--line-soft); margin: 4px 6px; }
  .chan-opt {
    width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px;
    background: transparent; border: 0; border-radius: 6px; cursor: pointer;
    padding: 8px 9px; font-family: 'IBM Plex Mono', monospace; font-size: 11.5px; color: var(--txt-dim);
  }
  .chan-opt:hover { background: #161f2a; color: var(--txt); }
  .chan-opt.on { color: var(--txt); }
  .opt-label { display: flex; align-items: center; gap: 6px; }
  .opt-live-dot {
    width: 6px; height: 6px; border-radius: 50%;
    background: #a3e635; box-shadow: 0 0 5px #a3e635; flex: 0 0 auto;
  }
  .chan-check { font-size: 9px; }
</style>
