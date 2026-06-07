import { render, fireEvent } from '@testing-library/svelte';
import { describe, it, expect, vi } from 'vitest';
import Toggle from './Toggle.svelte';

describe('Toggle', () => {
  it('renders with aria-pressed=false when off', () => {
    const { getByRole } = render(Toggle, { on: false, onChange: vi.fn() });
    expect(getByRole('button').getAttribute('aria-pressed')).toBe('false');
  });

  it('renders with aria-pressed=true when on', () => {
    const { getByRole } = render(Toggle, { on: true, onChange: vi.fn() });
    expect(getByRole('button').getAttribute('aria-pressed')).toBe('true');
  });

  it('calls onChange(true) when clicked while off', async () => {
    const onChange = vi.fn();
    const { getByRole } = render(Toggle, { on: false, onChange });
    await fireEvent.click(getByRole('button'));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('calls onChange(false) when clicked while on', async () => {
    const onChange = vi.fn();
    const { getByRole } = render(Toggle, { on: true, onChange });
    await fireEvent.click(getByRole('button'));
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('applies custom color style when on', () => {
    const { getByRole } = render(Toggle, { on: true, onChange: vi.fn(), color: '#ff0000' });
    expect(getByRole('button').getAttribute('style')).toContain('background');
  });

  it('does not apply color style when off', () => {
    const { getByRole } = render(Toggle, { on: false, onChange: vi.fn(), color: '#ff0000' });
    expect(getByRole('button').getAttribute('style') ?? '').toBe('');
  });
});
