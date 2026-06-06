<script>
  let { options = [], value, onChange } = $props();
</script>

<div class="seg">
  {#each options as opt}
    <button
      class="seg-btn"
      class:on={value === opt.v}
      onclick={() => onChange(opt.v)}
    >{opt.label}</button>
  {/each}
</div>

<style>
  .seg {
    display: flex; gap: 4px;
    background: #0a0e14; border: 1px solid var(--line-soft);
    border-radius: 9px; padding: 4px;
  }
  .seg-btn {
    flex: 1; font-family: 'IBM Plex Mono', monospace; font-size: 12px; font-weight: 500;
    color: var(--txt-dim); background: transparent; border: 0; border-radius: 6px;
    padding: 8px 0; cursor: pointer; transition: all .14s;
  }
  .seg-btn:hover { color: var(--txt); }
  .seg-btn.on { background: #1b2632; color: var(--txt); box-shadow: 0 1px 0 rgba(255,255,255,.04); }
</style>
