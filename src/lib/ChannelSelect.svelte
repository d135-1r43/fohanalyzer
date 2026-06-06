<script>
  let { value, options = [], onChange, color = '#22d3ee' } = $props();
  let open = $state(false);
  const cur = $derived(options.find((o) => o.id === value) || options[0]);
</script>

<div class="chan-sel">
  {#if open}
    <div class="chan-backdrop" onclick={() => (open = false)}></div>
  {/if}
  <button class="chan-btn" class:open onclick={() => (open = !open)}>
    <span class="chan-tag" style="color:{color};border-color:{color}">IN</span>
    <span class="chan-val">{cur?.label}</span>
    <span class="chan-caret">{open ? '▴' : '▾'}</span>
  </button>
  {#if open}
    <div class="chan-menu">
      {#each options as opt}
        <button
          class="chan-opt"
          class:on={opt.id === value}
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
  .chan-opt {
    width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px;
    background: transparent; border: 0; border-radius: 6px; cursor: pointer; white-space: nowrap;
    padding: 8px 9px; font-family: 'IBM Plex Mono', monospace; font-size: 11.5px; color: var(--txt-dim);
  }
  .chan-opt:hover { background: #161f2a; color: var(--txt); }
  .chan-opt.on { color: var(--txt); }
  .chan-check { font-size: 9px; }
</style>
