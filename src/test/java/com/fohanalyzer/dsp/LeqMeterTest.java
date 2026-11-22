package com.fohanalyzer.dsp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Leq, Fast, max and peak over synthetic level sequences whose answer is known.
 */
class LeqMeterTest
{
	static final double RATE = 48000;
	static final double TOLERANCE = 0.01;
	/** 10·log10(2): half the energy. */
	static final double HALF = 3.0103;

	LeqMeter meter;

	@BeforeEach
	void setUp()
	{
		meter = new LeqMeter();
	}

	/**
	 * Feed {@code seconds} of a constant sample value whose square is
	 * {@code db} dBFS, in capture-sized blocks.
	 */
	void feed(double db, double seconds)
	{
		float v = db == Double.NEGATIVE_INFINITY ? 0f : (float)Math.pow(10, db / 20);
		feed(v, null, seconds);
	}

	void feed(float left, Float right, double seconds)
	{
		int block = 1024;
		float[] l = new float[block];
		float[] r = new float[block];
		Arrays.fill(l, left);
		if (right != null) Arrays.fill(r, right);

		long remaining = Math.round(seconds * RATE);
		while (remaining > 0)
		{
			int n = (int)Math.min(block, remaining);
			meter.accept(l, right != null ? r : null, n, RATE);
			remaining -= n;
		}
	}

	@Nested
	class Leq
	{
		@Test
		void constantLevelIntegratesToItself()
		{
			feed(-20, 12);

			LeqMeter.Reading r = meter.reading(10);

			assertEquals(-20, r.leqDb(), TOLERANCE);
			assertTrue(r.isFilled());
		}

		@Test
		void levelForHalfTheWindowAndSilenceForTheOtherHalfIsThreeDbDown()
		{
			feed(-20, 5);
			feed(Double.NEGATIVE_INFINITY, 5);

			assertEquals(-20 - HALF, meter.reading(10).leqDb(), TOLERANCE);
		}

		@Test
		void orderWithinTheWindowDoesNotMatter()
		{
			feed(Double.NEGATIVE_INFINITY, 5);
			feed(-20, 5);

			assertEquals(-20 - HALF, meter.reading(10).leqDb(), TOLERANCE);
		}

		@Test
		void levelOlderThanTheWindowFallsOut()
		{
			feed(-6, 30);
			feed(-40, 10);

			assertEquals(-40, meter.reading(10).leqDb(), TOLERANCE);
		}

		@Test
		void aLongerWindowIsRecomputedFromWhatIsKept()
		{
			feed(-20, 10);
			feed(Double.NEGATIVE_INFINITY, 10);

			assertEquals(LeqMeter.FLOOR_DB, meter.reading(10).leqDb(), TOLERANCE);
			assertEquals(-20 - HALF, meter.reading(20).leqDb(), TOLERANCE);
		}

		@Test
		void aWindowNotYetFilledIsTheLeqSoFar()
		{
			feed(-20, 3);

			LeqMeter.Reading r = meter.reading(10);

			assertEquals(-20, r.leqDb(), TOLERANCE);
			assertEquals(3, r.integratedSeconds(), 0.001);
			assertFalse(r.isFilled());
		}

		@Test
		void nothingIsKeptBeyondTheLongestWindow()
		{
			feed(-6, 60);
			feed(-30, LeqMeter.MAX_WINDOW_SECONDS);

			LeqMeter.Reading r = meter.reading(2 * LeqMeter.MAX_WINDOW_SECONDS);

			assertEquals(-30, r.leqDb(), TOLERANCE);
			assertEquals(LeqMeter.MAX_WINDOW_SECONDS, r.integratedSeconds(), 0.001);
		}

		@Test
		void aPairIsAveragedInThePowerDomain()
		{
			// 0.5 on one side, silence on the other: half of -6.02 dB.
			feed(0.5f, 0f, 2);

			assertEquals(20 * Math.log10(0.5) - HALF, meter.reading(1).leqDb(), TOLERANCE);
		}

		@Test
		void nothingFedIsNotANumber()
		{
			LeqMeter.Reading r = meter.reading(10);

			assertTrue(Double.isNaN(r.leqDb()));
			assertTrue(Double.isNaN(r.maxDb()));
			assertEquals(0, r.integratedSeconds());
		}
	}

	@Nested
	class MaxAndPeak
	{
		@Test
		void fastLevelSettlesOnASteadySignal()
		{
			feed(-20, 2);

			assertEquals(-20, meter.reading(10).fastDb(), TOLERANCE);
		}

		@Test
		void fastLevelDecaysAtTheFastTimeConstant()
		{
			feed(-20, 2);
			feed(Double.NEGATIVE_INFINITY, LeqMeter.FAST_TAU_SECONDS);

			// One time constant takes the mean square to 1/e: −4.34 dB.
			assertEquals(-20 - 10 * Math.log10(Math.E), meter.reading(10).fastDb(), 0.05);
		}

		@Test
		void maxAndPeakAreHeldAfterTheLevelDrops()
		{
			feed(-10, 2);
			feed(-40, 20);

			LeqMeter.Reading r = meter.reading(10);

			assertEquals(-10, r.maxDb(), TOLERANCE);
			assertEquals(-10, r.peakDb(), TOLERANCE);
			assertEquals(-40, r.leqDb(), TOLERANCE);
		}

		@Test
		void peakOfAPairIsTheLouderSide()
		{
			feed(0.5f, 0.25f, 1);

			assertEquals(20 * Math.log10(0.5), meter.reading(10).peakDb(), TOLERANCE);
		}

		@Test
		void aShortBurstMovesThePeakMoreThanTheFastMax()
		{
			feed(-40, 1);
			// 1 ms: far shorter than the 125 ms the Fast level needs to rise.
			feed(-10, 0.001);
			feed(-40, 1);

			LeqMeter.Reading r = meter.reading(10);

			assertEquals(-10, r.peakDb(), TOLERANCE);
			assertTrue(r.maxDb() < -25, "Fast max " + r.maxDb());
		}

		@Test
		void resetStartsOver()
		{
			feed(-10, 5);
			// Long enough for the Fast level to settle, which a reset leaves
			// running.
			feed(-30, 5);
			meter.reset();
			feed(-30, 2);

			LeqMeter.Reading r = meter.reading(10);

			assertEquals(-30, r.leqDb(), TOLERANCE);
			assertEquals(-30, r.maxDb(), 0.1);
			assertEquals(-30, r.peakDb(), TOLERANCE);
			assertEquals(2, r.integratedSeconds(), 0.001);
		}
	}

	@Nested
	class Intervals
	{
		@Test
		void eachIntervalCoversOnlyWhatArrivedSinceTheLast()
		{
			feed(-10, 1);
			LeqMeter.Interval first = meter.drainInterval();
			feed(-30, 1);
			LeqMeter.Interval second = meter.drainInterval();

			assertEquals(-10, first.leqDb(), TOLERANCE);
			assertEquals(-30, second.leqDb(), TOLERANCE);
			assertEquals(1, second.seconds(), 0.001);
			assertEquals(-30, second.peakDb(), TOLERANCE);
		}

		@Test
		void noSamplesNoInterval()
		{
			assertNull(meter.drainInterval());
			feed(-10, 1);
			meter.drainInterval();

			assertNull(meter.drainInterval());
		}
	}
}
