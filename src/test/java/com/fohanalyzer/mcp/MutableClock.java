package com.fohanalyzer.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand. */
final class MutableClock extends Clock
{
	private Instant now = Instant.parse("2026-09-27T19:30:00Z");

	void advance(Duration d)
	{
		now = now.plus(d);
	}

	@Override
	public Instant instant()
	{
		return now;
	}

	@Override
	public ZoneId getZone()
	{
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone)
	{
		return this;
	}
}
