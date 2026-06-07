import { render } from '@testing-library/svelte';
import { describe, it, expect } from 'vitest';
import Meter from './Meter.svelte';

// Scale: 0% at -90 dBFS, 100% at -6 dBFS (range = 84 dB)

describe('Meter', () => {
  it('shows 0% fill at the floor (-90 dBFS)', () => {
    const { container } = render(Meter, { value: -90 });
    expect(container.querySelector('.meter-fill').style.width).toBe('0%');
  });

  it('shows 100% fill at the ceiling (-6 dBFS)', () => {
    const { container } = render(Meter, { value: -6 });
    expect(container.querySelector('.meter-fill').style.width).toBe('100%');
  });

  it('shows 50% fill at the midpoint (-48 dBFS)', () => {
    const { container } = render(Meter, { value: -48 });
    expect(container.querySelector('.meter-fill').style.width).toBe('50%');
  });

  it('clamps below floor to 0%', () => {
    const { container } = render(Meter, { value: -120 });
    expect(container.querySelector('.meter-fill').style.width).toBe('0%');
  });

  it('clamps above ceiling to 100%', () => {
    const { container } = render(Meter, { value: 0 });
    expect(container.querySelector('.meter-fill').style.width).toBe('100%');
  });

  it('applies the provided color to the fill', () => {
    const { container } = render(Meter, { value: -50, color: '#f5a524' });
    // jsdom normalizes hex to rgb
    expect(container.querySelector('.meter-fill').style.background).toBe('rgb(245, 165, 36)');
  });

  it('defaults to cyan (#22d3ee) when no color prop given', () => {
    const { container } = render(Meter, { value: -50 });
    expect(container.querySelector('.meter-fill').style.background).toBe('rgb(34, 211, 238)');
  });
});
