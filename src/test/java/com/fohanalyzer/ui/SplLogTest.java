package com.fohanalyzer.ui;

import com.fohanalyzer.dsp.LeqMeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What lands in the session's CSV, and where. */
class SplLogTest
{
	static final ZonedDateTime AT = ZonedDateTime.of(2026, 11, 22, 21, 40, 3, 0, ZoneOffset.ofHours(1));
	static final LeqMeter.Interval SECOND = new LeqMeter.Interval(1.0, -20, -18.5, -6);
	static final LeqMeter.Reading WINDOW = new LeqMeter.Reading(300, 192, -21.25, -20, -12, -6);

	@TempDir
	Path dir;

	@Test
	void theHeaderNamesTheWeightingAndTheWindow()
	{
		assertEquals("time,seconds,LZeq_dB,LZFmax_dB,LZpeak_dB,LZeq_window_dB,window_s,window_integrated_s,"
			+ "calibration_dB", SplLog.HEADER);
	}

	@Test
	void aRowIsInDbSplWithTheCalibrationBesideIt()
	{
		assertEquals("2026-11-22T21:40:03+01:00,1.000,98.0,99.5,112.0,96.8,300,192.0,118.00",
			SplLog.row(AT, SECOND, WINDOW, 118));
	}

	@Test
	void aLevelNotYetMeasuredIsLeftEmpty()
	{
		LeqMeter.Reading none = new LeqMeter.Reading(300, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

		assertTrue(SplLog.row(AT, SECOND, none, 118).contains(",112.0,,300,"));
	}

	@Test
	void oneFilePerSessionNamedForWhenItStarted() throws Exception
	{
		SplLog splLog = new SplLog(dir, Clock.fixed(Instant.from(AT), AT.getZone()));

		Path first = splLog.append(SECOND, WINDOW, 118);
		Path second = splLog.append(SECOND, WINDOW, 118);
		splLog.close();

		assertEquals(first, second);
		assertEquals("spl-2026-11-22_214003.csv", first.getFileName().toString());
		List<String> lines = Files.readAllLines(first);
		assertEquals(3, lines.size());
		assertEquals(SplLog.HEADER, lines.getFirst());
	}

	@Test
	void nothingIsWrittenWithoutARow()
	{
		new SplLog(dir, Clock.systemUTC()).close();

		assertEquals(0, dir.toFile().list().length);
	}
}
