import { render, fireEvent } from '@testing-library/svelte';
import { describe, it, expect, vi } from 'vitest';
import Segmented from './Segmented.svelte';

const options = [
  { v: 1, label: 'A' },
  { v: 3, label: 'B' },
  { v: 6, label: 'C' },
];

describe('Segmented', () => {
  it('renders a button for each option', () => {
    const { getAllByRole } = render(Segmented, { options, value: 1, onChange: vi.fn() });
    expect(getAllByRole('button').length).toBe(3);
  });

  it('shows option labels as button text', () => {
    const { getByText } = render(Segmented, { options, value: 1, onChange: vi.fn() });
    expect(getByText('A')).toBeTruthy();
    expect(getByText('B')).toBeTruthy();
    expect(getByText('C')).toBeTruthy();
  });

  it('marks only the active option with class "on"', () => {
    const { getAllByRole } = render(Segmented, { options, value: 3, onChange: vi.fn() });
    const [a, b, c] = getAllByRole('button');
    expect(a.classList.contains('on')).toBe(false);
    expect(b.classList.contains('on')).toBe(true);
    expect(c.classList.contains('on')).toBe(false);
  });

  it('calls onChange with the clicked option value', async () => {
    const onChange = vi.fn();
    const { getAllByRole } = render(Segmented, { options, value: 1, onChange });
    await fireEvent.click(getAllByRole('button')[2]);
    expect(onChange).toHaveBeenCalledWith(6);
  });

  it('calls onChange when the already-active option is clicked', async () => {
    const onChange = vi.fn();
    const { getAllByRole } = render(Segmented, { options, value: 1, onChange });
    await fireEvent.click(getAllByRole('button')[0]);
    expect(onChange).toHaveBeenCalledWith(1);
  });
});
