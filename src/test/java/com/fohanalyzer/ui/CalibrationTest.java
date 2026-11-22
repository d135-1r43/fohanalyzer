package com.fohanalyzer.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalibrationTest
{
	@Test
	void storedCalibrationsReadBackAsWritten()
	{
		List<Calibration> cals = List.of(
			new Calibration("Scarlett 18i20", 2, "Scarlett 18i20 USB", 118.4, LocalDate.of(2026, 11, 3), 94),
			new Calibration("WING", 31, "Behringer\tWING", 121.7, null, Double.NaN));

		List<Calibration> back = Calibration.decode(Calibration.encode(cals), -200, 200);

		assertEquals(cals.getFirst(), back.getFirst());
		assertEquals("Behringer WING", back.get(1).deviceName(), "a tab in a name is flattened");
		assertNull(back.get(1).date());
		assertTrue(Double.isNaN(back.get(1).refDb()));
	}

	@Test
	void linesThatDoNotReadBackAreSkipped()
	{
		String stored = """
			A\t0\tA\t110.0\t2026-11-03\t94.0
			B\tx\tB\t110.0\t-\t-
			C\t0\tC\t999.0\t-\t-
			D\t0\tD\t0.0\t-\t-
			A\t0\tA\t120.0\t-\t-
			garbage
			""";

		List<Calibration> back = Calibration.decode(stored, -200, 200);

		assertEquals(1, back.size());
		assertEquals(110.0, back.getFirst().offsetDb(), 1e-9);
	}

	@Test
	void staleAfterThirtyDays()
	{
		LocalDate today = LocalDate.of(2026, 11, 22);
		Calibration cal = new Calibration("A", 0, "A", 110, today.minusDays(30), 94);

		assertFalse(cal.isStale(today));
		assertTrue(cal.withOffset(110).isStale(today.plusDays(1)));
		assertFalse(new Calibration("A", 0, "A", 110, null, 94).isStale(today), "an unknown date is not flagged");
	}
}
