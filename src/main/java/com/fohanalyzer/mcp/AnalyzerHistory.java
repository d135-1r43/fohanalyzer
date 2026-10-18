package com.fohanalyzer.mcp;

import com.fohanalyzer.dsp.Stats;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What the analyzer has seen recently, kept for the MCP tools.
 *
 * <p>
 * Written from the FX thread (the frame loop and the ring-out log) and read
 * from Jetty's request threads, so nothing here is a JavaFX type and every
 * value handed out is a copy. The ring events are independent of the rail's
 * feedback log: that one is capped at six and can be cleared by hand, while a
 * ring that keeps coming back over a whole show is exactly what a tip is made
 * from.
 */
public final class AnalyzerHistory
{
	/** Enough for a long show; older rings are dropped first. */
	static final int MAX_RING_EVENTS = 500;

	/** How long stats samples are kept. */
	static final Duration STATS_RETENTION = Duration.ofMinutes(30);

	/**
	 * One stats sample per this interval, however often the frame loop reports.
	 */
	static final Duration STATS_INTERVAL = Duration.ofSeconds(1);

	/**
	 * A ring logged by the engineer. {@code band} is the nearest ISO 1/3-octave
	 * GEQ band.
	 */
	public record RingEvent(Instant at, double freq, String note, double band, int cut)
	{
	}

	/**
	 * One readout sample. Levels are the plotted band levels in dB;
	 * {@code splDb} is only set when the mic is live and calibrated.
	 */
	public record StatsSample(Instant at, double peakFreq, double micPeak, double micAvg, double soloAvg,
		double soloPeak, Double micRmsDbfs, Double splDb, boolean micLive, boolean soloLive)
	{
	}

	/**
	 * The plotted per-band levels at one moment. The arrays are private copies.
	 */
	public record BandSnapshot(Instant at, int frac, double[] centers, float[] mic, float[] solo, boolean micLive,
		boolean soloLive)
	{
	}

	private final Clock clock;
	private final Deque<RingEvent> rings = new ArrayDeque<>();
	private final Deque<StatsSample> stats = new ArrayDeque<>();
	private volatile BandSnapshot bands;

	public AnalyzerHistory()
	{
		this(Clock.systemUTC());
	}

	/** Test seam: drive time by hand. */
	AnalyzerHistory(Clock clock)
	{
		this.clock = clock;
	}

	public synchronized void recordRing(double freq, String note, double band, int cut)
	{
		rings.addLast(new RingEvent(clock.instant(), freq, note, band, cut));
		while (rings.size() > MAX_RING_EVENTS)
			rings.removeFirst();
	}

	/**
	 * Keep {@code s} if a sample interval has passed since the last one. The
	 * frame loop reports several times a second; a second is plenty for a model
	 * looking at minutes, and keeps half an hour to a few thousand entries.
	 */
	public synchronized void recordStats(Stats s, Double splDb, boolean micLive, boolean soloLive)
	{
		Instant now = clock.instant();
		StatsSample last = stats.peekLast();
		if (last != null && Duration.between(last.at(), now).compareTo(STATS_INTERVAL) < 0) return;

		stats.addLast(new StatsSample(now, s.peakFreq(), s.micPeak(), s.micAvg(), s.soloAvg(), s.soloPeak(),
			s.micRmsDbfs(), splDb, micLive, soloLive));
		Instant cutoff = now.minus(STATS_RETENTION);
		while (!stats.isEmpty() && stats.peekFirst().at().isBefore(cutoff))
			stats.removeFirst();
	}

	public void recordBands(int frac, double[] centers, float[] mic, float[] solo, boolean micLive,
		boolean soloLive)
	{
		bands = new BandSnapshot(clock.instant(), frac, centers.clone(), mic.clone(), solo.clone(), micLive,
			soloLive);
	}

	/**
	 * Ring events newer than {@code window} ago, oldest first; all of them when
	 * {@code window} is null.
	 */
	public synchronized List<RingEvent> ringEvents(Duration window)
	{
		if (window == null) return List.copyOf(rings);

		Instant cutoff = clock.instant().minus(window);
		List<RingEvent> out = new ArrayList<>();
		for (RingEvent e : rings)
		{
			if (!e.at().isBefore(cutoff)) out.add(e);
		}
		return out;
	}

	/** Stats samples newer than {@code window} ago, oldest first. */
	public synchronized List<StatsSample> stats(Duration window)
	{
		Instant cutoff = clock.instant().minus(window);
		List<StatsSample> out = new ArrayList<>();
		for (StatsSample s : stats)
		{
			if (!s.at().isBefore(cutoff)) out.add(s);
		}
		return out;
	}

	/** The latest band snapshot, or {@code null} before the first frame. */
	public BandSnapshot bands()
	{
		return bands;
	}

	/** When this history is being read, per its own clock. */
	Instant now()
	{
		return clock.instant();
	}
}
