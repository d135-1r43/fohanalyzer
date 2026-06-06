<script>
  let { on = false, onChange, color = null } = $props();
</script>

<button
  class="tgl"
  class:on
  style={on && color ? `background:${color};border-color:${color}` : ''}
  onclick={() => onChange(!on)}
  aria-pressed={on}
>
  <span class="knob"></span>
</button>

<style>
  .tgl {
    width: 38px; height: 22px; border-radius: 12px;
    background: #1a232e; border: 1px solid var(--line);
    position: relative; cursor: pointer; flex: 0 0 auto;
    transition: background .18s, border-color .18s; padding: 0;
  }
  .knob {
    position: absolute; top: 2px; left: 2px;
    width: 16px; height: 16px; border-radius: 50%;
    background: #6b7686; transition: transform .18s, background .18s;
    display: block;
  }
  .on .knob { transform: translateX(16px); background: #07120a; }
</style>
