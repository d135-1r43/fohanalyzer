package com.fohanalyzer.dsp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A sound level meter over the broadband signal: the equivalent continuous
 * level (Leq) over a rolling window, the Fast time-weighted level, and the
 * maximum and peak held since a reset.
 *
 * <p>
 * Fed every captured sample, not a reading now and then: an Leq is the energy
 * of everything that happened in the window, and a spectrum frame sampled once
 * a second would miss whatever fell between. The energy is kept in short
 * buckets for up to {@link #MAX_WINDOW_SECONDS}, so switching the window
 * recomputes from what is already there instead of starting over.
 *
 * <p>
 * All levels are in dBFS, as squared full-scale samples — a full-scale sine
 * reads −3 dB, matching {@link com.fohanalyzer.audio.AudioDsp#rmsDb}. The
 * calibration offset is the caller's to add. Nothing is weighted yet, so
 * everything here is Z ({@link #WEIGHTING}); whatever shows or exports these
 * figures should say so.
 *
 * <p>
 * Written from the capture thread and read from the FX thread and the MCP
 * server's, so every public method is synchronized. Each one is cheap.
 */
public final class LeqMeter
{
	/** Frequency weighting of every level here: none. */
	public static final String WEIGHTING = "Z";

	/** The longest window an Leq can be asked for, and how much is kept. */
	public static final double MAX_WINDOW_SECONDS = 3600;

	/** Granularity of the rolling window. */
	static final double BUCKET_SECONDS = 0.1;

	/** IEC 61672 "Fast" time constant. */
	static final double FAST_TAU_SECONDS = 0.125;

	private static final double WINDOW_EPSILON = 1e-9;

	/** Reported for no signal at all, rather than −∞. */
	static final double FLOOR_DB = -200;

	/**
	 * Levels over a rolling window, plus the maximum and peak held since the
	 * last reset. {@code integratedSeconds} is how much of the window has been
	 * filled; until it reaches {@code windowSeconds} the Leq is over what has
	 * been integrated so far. Levels are {@code NaN} before the first sample.
	 */
	public record Reading(double windowSeconds, double integratedSeconds, double leqDb, double fastDb,
		double maxDb, double peakDb)
	{
		public boolean isFilled()
		{
			return integratedSeconds >= windowSeconds;
		}
	}

	/**
	 * What happened between two {@link #drainInterval} calls: the Leq over it,
	 * and the highest Fast level and peak within it.
	 */
	public record Interval(double seconds, double leqDb, double maxDb, double peakDb)
	{
	}

	/** Energy of a run of samples, in squared full scale times samples. */
	private static final class Bucket
	{
		final double sampleRate;
		double sumSquares;
		long samples;
		double maxFast;
		double peakSquare;

		Bucket(double sampleRate)
		{
			this.sampleRate = sampleRate;
		}

		double seconds()
		{
			return samples / sampleRate;
		}

		double energy()
		{
			return sumSquares / sampleRate;
		}

		void add(double square, double fast, double peak)
		{
			sumSquares += square;
			samples++;
			maxFast = Math.max(maxFast, fast);
			peakSquare = Math.max(peakSquare, peak);
		}
	}

	private final Deque<Bucket> buckets = new ArrayDeque<>();
	private double keptSeconds;
	private Bucket current;
	private Bucket interval;

	private double sampleRate = -1;
	private long bucketSamples;
	private double fastAlpha;
	private double fast = Double.NaN;

	private double maxFast = Double.NaN;
	private double peakSquare = Double.NaN;

	/**
	 * Integrate a block of samples. With {@code right} set the two channels are
	 * a pair, combined in the power domain like the rest of the analyzer reads
	 * a pair, and the peak is the higher of the two.
	 */
	public synchronized void accept(float[] left, float[] right, int count, double rate)
	{
		if (rate != sampleRate) configure(rate);

		for (int i = 0; i < count; i++)
		{
			double l = left[i];
			double square;
			double peak;
			if (right == null)
			{
				square = l * l;
				peak = square;
			}
			else
			{
				double r = right[i];
				square = (l * l + r * r) / 2;
				peak = Math.max(l * l, r * r);
			}

			fast = Double.isNaN(fast) ? square : fast + fastAlpha * (square - fast);
			maxFast = Double.isNaN(maxFast) ? fast : Math.max(maxFast, fast);
			peakSquare = Double.isNaN(peakSquare) ? peak : Math.max(peakSquare, peak);

			if (current == null) current = new Bucket(sampleRate);
			current.add(square, fast, peak);
			if (interval == null) interval = new Bucket(sampleRate);
			interval.add(square, fast, peak);
			if (current.samples >= bucketSamples) closeBucket();
		}
	}

	/**
	 * The Leq over the last {@code windowSeconds} (up to
	 * {@link #MAX_WINDOW_SECONDS}), with the held maximum and peak.
	 */
	public synchronized Reading reading(double windowSeconds)
	{
		double window = Math.clamp(windowSeconds, BUCKET_SECONDS, MAX_WINDOW_SECONDS);
		double energy = 0;
		double seconds = 0;

		Iterator<Bucket> older = buckets.descendingIterator();
		Bucket b = current != null && current.samples > 0 ? current : older.hasNext() ? older.next() : null;
		// Bucket lengths are summed in floating point, so "the window is
		// full" is within a hair rather than exactly equal.
		while (b != null && window - seconds > WINDOW_EPSILON)
		{
			double s = b.seconds();
			double take = Math.min(s, window - seconds);
			energy += b.energy() * take / s;
			seconds += take;
			b = older.hasNext() ? older.next() : null;
		}

		if (window - seconds <= WINDOW_EPSILON) seconds = window;

		double leq = seconds > 0 ? toDb(energy / seconds) : Double.NaN;
		return new Reading(window, seconds, leq, toDb(fast), toDb(maxFast), toDb(peakSquare));
	}

	/**
	 * The levels since the previous call, and start the next interval; null if
	 * no samples arrived in between. One consumer only — the session log.
	 */
	public synchronized Interval drainInterval()
	{
		Bucket iv = interval;
		interval = null;
		if (iv == null || iv.samples == 0) return null;

		return new Interval(iv.seconds(), toDb(iv.sumSquares / iv.samples), toDb(iv.maxFast),
			toDb(iv.peakSquare));
	}

	/**
	 * Start integrating afresh and drop the held maximum and peak. The Fast
	 * level keeps running, as it would on a hand-held meter.
	 */
	public synchronized void reset()
	{
		buckets.clear();
		keptSeconds = 0;
		current = null;
		maxFast = Double.NaN;
		peakSquare = Double.NaN;
	}

	private void configure(double rate)
	{
		// A different rate is a different device; its buckets keep their own
		// rate, so the window stays right across the change.
		if (current != null && current.samples > 0) closeBucket();
		current = null;
		interval = null;
		sampleRate = rate;
		bucketSamples = Math.max(1, Math.round(BUCKET_SECONDS * rate));
		fastAlpha = 1 - Math.exp(-1 / (FAST_TAU_SECONDS * rate));
	}

	private void closeBucket()
	{
		buckets.addLast(current);
		keptSeconds += current.seconds();
		current = null;

		while (!buckets.isEmpty() && keptSeconds - buckets.peekFirst().seconds() >= MAX_WINDOW_SECONDS)
			keptSeconds -= buckets.removeFirst().seconds();
	}

	private static double toDb(double square)
	{
		if (Double.isNaN(square)) return Double.NaN;

		return square > 1e-20 ? 10 * Math.log10(square) : FLOOR_DB;
	}
}
