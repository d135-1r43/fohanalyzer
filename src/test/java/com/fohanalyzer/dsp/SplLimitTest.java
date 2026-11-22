package com.fohanalyzer.dsp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplLimitTest
{
	@Test
	void zoneIsUnderNearAndOverTheLimit()
	{
		assertEquals(SplLimit.Zone.UNDER, SplLimit.zone(94.9, 98));
		assertEquals(SplLimit.Zone.NEAR, SplLimit.zone(95, 98));
		assertEquals(SplLimit.Zone.NEAR, SplLimit.zone(98, 98));
		assertEquals(SplLimit.Zone.OVER, SplLimit.zone(98.1, 98));
	}

	@Test
	void noLevelIsUnder()
	{
		assertEquals(SplLimit.Zone.UNDER, SplLimit.zone(Double.NaN, 98));
	}

	@Test
	void timeOverAddsUpOnlyTheSecondsAbove()
	{
		SplLimit.TimeOver over = new SplLimit.TimeOver();
		// 10 s under, 30 s crossing 1.5 dB over, 20 s back under.
		for (int s = 0; s < 60; s++)
		{
			double leq = s >= 10 && s < 40 ? 99.5 : 96;
			over.tick(1, leq, 98);
		}

		assertEquals(30, over.seconds(), 1e-9);

		over.reset();

		assertEquals(0, over.seconds());
	}

	@Test
	void secondsOverCountsEachSampleUntilTheNext()
	{
		double[] t = { 0, 1, 2, 3, 4 };
		double[] leq = { 97, 99, 99, 97, 99 };

		assertEquals(3, SplLimit.secondsOver(t, leq, 98, 1), 1e-9);
	}

	@Test
	void secondsOverDoesNotChargeAGapToTheLevelBeforeIt()
	{
		double[] t = { 0, 1, 600 };
		double[] leq = { 99, 99, 99 };

		assertEquals(1 + SplLimit.MAX_GAP_SECONDS + 1, SplLimit.secondsOver(t, leq, 98, 1), 1e-9);
	}
}
