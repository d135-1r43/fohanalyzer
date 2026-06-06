<script>
  import Toggle from './Toggle.svelte';
  import ChannelSelect from './ChannelSelect.svelte';
  import Meter from './Meter.svelte';

  let { name, sub, color, on, setOn, level, isMarker, setMarker, options, chan, setChan } = $props();
</script>

<div class="src-card" class:off={!on}>
  <div class="src-top">
    <span class="src-dot" style="background:{color};box-shadow:0 0 8px {color}"></span>
    <div class="src-names">
      <div class="src-name">{name}</div>
      <div class="src-sub">{sub}</div>
    </div>
    <Toggle {on} onChange={setOn} {color} />
  </div>
  <ChannelSelect value={chan} {options} onChange={setChan} {color} />
  <div class="src-bottom">
    <Meter value={level} {color} />
    <button
      class="marker-pill"
      class:on={isMarker}
      style={isMarker ? `border-color:${color};color:${color}` : ''}
      onclick={setMarker}
      title="Show top-offender markers on this source"
    >{isMarker ? '◎ markers' : '○ markers'}</button>
  </div>
</div>

<style>
  .src-card {
    background: var(--panel-2); border: 1px solid var(--line-soft);
    border-radius: 10px; padding: 11px 12px; margin-bottom: 9px; transition: opacity .2s;
  }
  .src-card.off { opacity: .5; }
  .src-top { display: flex; align-items: center; gap: 10px; }
  .src-dot { width: 9px; height: 9px; border-radius: 50%; flex: 0 0 auto; }
  .src-names { flex: 1; min-width: 0; }
  .src-name { font-size: 14px; font-weight: 600; }
  .src-sub { font-family: 'IBM Plex Mono', monospace; font-size: 9.5px; letter-spacing: .5px; color: var(--txt-mut); margin-top: 2px; }
  .src-bottom { display: flex; align-items: center; gap: 10px; margin-top: 11px; }
  .marker-pill {
    font-family: 'IBM Plex Mono', monospace; font-size: 10px; letter-spacing: .5px;
    background: transparent; border: 1px solid var(--line); color: var(--txt-mut);
    border-radius: 20px; padding: 4px 9px; cursor: pointer; white-space: nowrap; transition: all .15s;
  }
  .marker-pill:hover { border-color: var(--txt-dim); color: var(--txt-dim); }
  .marker-pill.on { font-weight: 600; }
</style>
