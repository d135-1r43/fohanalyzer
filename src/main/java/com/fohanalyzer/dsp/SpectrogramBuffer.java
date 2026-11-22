package com.fohanalyzer.dsp;

/**
 * The spectrograph's history: a fixed number of rows of per-band display
 * levels, mic and solo side by side, newest first.
 *
 * <p>
 * Rows live in a ring of preallocated arrays, so pushing a row copies into an
 * existing slot instead of allocating one every frame. Slots are written in
 * descending order — the newest row sits at {@link #slot(int) slot(0)} and each
 * older one at the next slot up, wrapping — so a renderer that mirrors the
 * slots in an image can draw newest-at-top with two straight blits.
 *
 * <p>
 * Changing the resolution or the history length clears it: a row of a different
 * band count cannot be drawn on the new axis, and a row recorded at a different
 * time scale would be drawn at the wrong age.
 */
public final class SpectrogramBuffer
{
	private final int capacity;

	private int bands = -1;
	private int frac = -1;
	private int historySec = -1;

	private float[][] mic = new float[0][];
	private float[][] solo = new float[0][];

	private int head = 0;
	private int size = 0;
	private long written = 0;
	private int generation = 0;

	public SpectrogramBuffer(int capacity)
	{
		if (capacity < 1) throw new IllegalArgumentException("capacity must be positive: " + capacity);
		this.capacity = capacity;
	}

	/**
	 * Set the shape of the rows to come; clears the history when anything
	 * changed.
	 *
	 * @return whether the history was cleared
	 */
	public boolean configure(int frac, int bands, int historySec)
	{
		if (this.frac == frac && this.bands == bands && this.historySec == historySec) return false;
		this.frac = frac;
		this.bands = bands;
		this.historySec = historySec;
		mic = new float[capacity][bands];
		solo = new float[capacity][bands];
		clear();
		return true;
	}

	/** Forget every row. */
	public void clear()
	{
		head = 0;
		size = 0;
		generation++;
	}

	/**
	 * Append a row. Both arrays must have {@link #bands()} entries; they are
	 * copied, so the caller may keep reusing them.
	 */
	public void push(float[] micRow, float[] soloRow)
	{
		if (micRow.length != bands || soloRow.length != bands)
		{
			throw new IllegalArgumentException("expected " + bands + " bands, got "
				+ micRow.length + " / " + soloRow.length);
		}
		head = Math.floorMod(head - 1, capacity);
		System.arraycopy(micRow, 0, mic[head], 0, bands);
		System.arraycopy(soloRow, 0, solo[head], 0, bands);
		size = Math.min(size + 1, capacity);
		written++;
	}

	/** Physical slot of the row {@code age} rows old; 0 is the newest. */
	public int slot(int age)
	{
		if (age < 0 || age >= size) throw new IndexOutOfBoundsException("age " + age + " of " + size);
		return (head + age) % capacity;
	}

	/**
	 * The mic row {@code age} rows old. The array is the buffer's own: do not
	 * modify it.
	 */
	public float[] mic(int age)
	{
		return mic[slot(age)];
	}

	/**
	 * The solo row {@code age} rows old. The array is the buffer's own: do not
	 * modify it.
	 */
	public float[] solo(int age)
	{
		return solo[slot(age)];
	}

	/**
	 * Slot the newest row was written to, where a newest-first image starts.
	 */
	public int head()
	{
		return head;
	}

	public int size()
	{
		return size;
	}

	public int capacity()
	{
		return capacity;
	}

	public int bands()
	{
		return bands;
	}

	public int historySec()
	{
		return historySec;
	}

	/**
	 * Rows pushed since construction, never reset; tells a renderer how many
	 * are new.
	 */
	public long written()
	{
		return written;
	}

	/** Bumped on every clear, so a renderer knows its copy is stale. */
	public int generation()
	{
		return generation;
	}
}
