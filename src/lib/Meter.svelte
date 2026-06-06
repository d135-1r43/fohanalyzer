<script>
  let { value = -90, color = '#22d3ee' } = $props();
  const pct = $derived(Math.max(0, Math.min(1, (value + 90) / 84)) * 100);
</script>

<div class="meter">
  <div class="meter-fill" style="width:{pct}%;background:{color}"></div>
</div>

<style>
  .meter {
    flex: 1; height: 6px; background: #0a0e14;
    border-radius: 3px; overflow: hidden; border: 1px solid var(--line-soft);
  }
  .meter-fill { height: 100%; border-radius: 3px; transition: width .1s linear; opacity: .85; }
</style>
