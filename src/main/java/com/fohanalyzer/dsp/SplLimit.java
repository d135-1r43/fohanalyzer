package com.fohanalyzer.dsp;

/**
 * A venue's SPL limit against the Leq: whether the level is under it, close to
 * it or over, and how long it has been over.
 *
 * <p>
 * The limit is weighted and windowed like the meter it is compared with; this
 * class only compares numbers, both in the same dB.
 */
public final class SplLimit
{
	/** How far under the limit the level starts to count as close to it. */
	public static final double NEAR_DB = 3;

	/**
	 * The longest gap between two samples that still counts as continuous; past
	 * it the meter was off, and the time between says nothing.
	 */
	static final double MAX_GAP_SECONDS = 2;

	public enum Zone
	{
		UNDER, NEAR, OVER
	}

	private SplLimit()
	{
	}

	/**
	 * Where {@code leqDb} stands against {@code limitDb}: over above it, near
	 * within {@link #NEAR_DB} below it, under otherwise or with no level.
	 */
	public static Zone zone(double leqDb, double limitDb)
	{
		if (!Double.isFinite(leqDb) || !Double.isFinite(limitDb)) return Zone.UNDER;
		if (leqDb > limitDb) return Zone.OVER;
		if (leqDb >= limitDb - NEAR_DB) return Zone.NEAR;

		return Zone.UNDER;
	}

	/**
	 * Seconds spent over {@code limitDb} in a series of samples taken at
	 * {@code seconds} (ascending). Each sample over the limit counts until the
	 * next one, and the last one for {@code lastSeconds}; a gap longer than
	 * {@link #MAX_GAP_SECONDS} counts as that long, so a stretch with the meter
	 * off is not charged to the level before it.
	 */
	public static double secondsOver(double[] seconds, double[] leqDb, double limitDb, double lastSeconds)
	{
		double over = 0;
		for (int i = 0; i < seconds.length; i++)
		{
			if (zone(leqDb[i], limitDb) != Zone.OVER) continue;
			double gap = i + 1 < seconds.length ? seconds[i + 1] - seconds[i] : lastSeconds;
			over += Math.clamp(gap, 0, MAX_GAP_SECONDS);
		}
		return over;
	}

	/**
	 * Adds up the time the Leq spends over the limit, a tick at a time, until
	 * reset. Ticked from the FX thread and read from the MCP server's, so
	 * synchronized.
	 */
	public static final class TimeOver
	{
		private double seconds;

		/** {@code dt} seconds at {@code leqDb} against {@code limitDb}. */
		public synchronized void tick(double dt, double leqDb, double limitDb)
		{
			if (dt > 0 && zone(leqDb, limitDb) == Zone.OVER) seconds += dt;
		}

		public synchronized double seconds()
		{
			return seconds;
		}

		public synchronized void reset()
		{
			seconds = 0;
		}
	}
}
