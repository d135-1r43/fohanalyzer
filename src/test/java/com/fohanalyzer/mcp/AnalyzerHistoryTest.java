package com.fohanalyzer.mcp;

import com.fohanalyzer.dsp.Stats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerHistoryTest
{
	private static final Stats STATS = new Stats(1000, -20, -40, -45, -25, 0, null);

	private final MutableClock clock = new MutableClock();
	private final AnalyzerHistory history = new AnalyzerHistory(clock);

	@Test
	void keepsAtMostOneStatsSamplePerInterval()
	{
		for (int i = 0; i < 25; i++)
		{
			history.recordStats(STATS, null, true, false);
			clock.advance(Duration.ofMillis(120));
		}

		// 25 reports over 3 s: one kept at 0 s, 1.08 s, 2.16 s
		assertEquals(3, history.stats(Duration.ofMinutes(1)).size());
	}

	@Test
	void dropsStatsOlderThanTheRetention()
	{
		history.recordStats(STATS, null, true, false);
		clock.advance(AnalyzerHistory.STATS_RETENTION.plusSeconds(1));
		history.recordStats(STATS, null, true, false);

		assertEquals(1, history.stats(Duration.ofHours(1)).size());
	}

	@Test
	void statsWindowOnlyReturnsRecentSamples()
	{
		history.recordStats(STATS, null, true, false);
		clock.advance(Duration.ofMinutes(10));
		history.recordStats(STATS, 96.5, true, true);

		List<AnalyzerHistory.StatsSample> recent = history.stats(Duration.ofMinutes(5));
		assertEquals(1, recent.size());
		assertEquals(96.5, recent.getFirst().splDb());
		assertTrue(recent.getFirst().soloLive());
	}

	@Test
	void ringEventsAreCappedOldestFirst()
	{
		for (int i = 0; i < AnalyzerHistory.MAX_RING_EVENTS + 10; i++)
			history.recordRing(100 + i, "A4", 100, 6);

		List<AnalyzerHistory.RingEvent> all = history.ringEvents(null);
		assertEquals(AnalyzerHistory.MAX_RING_EVENTS, all.size());
		assertEquals(110, all.getFirst().freq());
	}

	@Test
	void ringWindowFiltersByAge()
	{
		history.recordRing(2500, "D#7", 2500, 6);
		clock.advance(Duration.ofMinutes(20));
		history.recordRing(3150, "G7", 3150, 9);

		List<AnalyzerHistory.RingEvent> recent = history.ringEvents(Duration.ofMinutes(10));
		assertEquals(1, recent.size());
		assertEquals(3150, recent.getFirst().freq());
		assertEquals(2, history.ringEvents(null).size());
	}

	@Test
	void bandSnapshotIsACopy()
	{
		double[] centers = { 100, 1000 };
		float[] mic = { -30, -20 };
		float[] solo = { -35, -25 };
		history.recordBands(3, centers, mic, solo, true, false);
		mic[0] = 0;

		assertEquals(-30, history.bands().mic()[0]);
	}

	@Test
	void concurrentWritersAndReadersDoNotCorrupt() throws Exception
	{
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try
		{
			Future<?> writer = pool.submit(() -> {
				for (int i = 0; i < 5000; i++)
					history.recordRing(1000, "B5", 1000, 6);
			});
			Future<?> reader = pool.submit(() -> {
				for (int i = 0; i < 5000; i++)
					history.ringEvents(Duration.ofMinutes(1));
			});
			writer.get();
			reader.get();
		}
		finally
		{
			pool.shutdown();
		}
		assertEquals(AnalyzerHistory.MAX_RING_EVENTS, history.ringEvents(null).size());
	}
}
