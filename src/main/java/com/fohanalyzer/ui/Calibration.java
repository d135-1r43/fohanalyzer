package com.fohanalyzer.ui;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An SPL calibration of one input: the offset that turns the mic's dBFS into dB
 * SPL on that interface and channel, and when and against what it was made.
 *
 * @param deviceId
 *            the interface, as {@link com.fohanalyzer.audio.AudioDevice#id}
 * @param channel
 *            0-based channel index on it
 * @param deviceName
 *            what to call the interface while it is not plugged in
 * @param date
 *            when it was calibrated; null for one carried over from before
 *            calibrations were per input
 * @param refDb
 *            the reference level calibrated against, NaN when not known
 */
public record Calibration(String deviceId, int channel, String deviceName, double offsetDb, LocalDate date,
	double refDb)
{
	/** A calibration this old is flagged, though still used. */
	public static final long STALE_AFTER_DAYS = 30;

	public boolean isFor(String deviceId, int channel)
	{
		return this.deviceId.equals(deviceId) && this.channel == channel;
	}

	public Calibration withOffset(double offset)
	{
		return new Calibration(deviceId, channel, deviceName, offset, date, refDb);
	}

	/** Whether it is old enough to be worth redoing. */
	public boolean isStale(LocalDate today)
	{
		return date != null && ChronoUnit.DAYS.between(date, today) > STALE_AFTER_DAYS;
	}

	// ---- storage ----------------------------------------------------------

	private static final String FIELD = "\t";
	private static final String NONE = "-";

	/**
	 * One line per calibration, tab-separated. Names are the system's; a tab or
	 * line break in one is flattened rather than escaped.
	 */
	static String encode(List<Calibration> calibrations)
	{
		StringBuilder out = new StringBuilder();
		for (Calibration c : calibrations)
		{
			out.append(clean(c.deviceId)).append(FIELD)
				.append(c.channel).append(FIELD)
				.append(clean(c.deviceName)).append(FIELD)
				.append(c.offsetDb).append(FIELD)
				.append(c.date != null ? c.date.toString() : NONE).append(FIELD)
				.append(Double.isFinite(c.refDb) ? Double.toString(c.refDb) : NONE)
				.append('\n');
		}
		return out.toString();
	}

	/**
	 * The calibrations in {@code stored}. A line that does not read back — a
	 * hand edit, a format from a later build — is skipped, not fatal; so is a
	 * second calibration of the same input.
	 */
	static List<Calibration> decode(String stored, double minOffset, double maxOffset)
	{
		List<Calibration> out = new ArrayList<>();
		if (stored == null) return out;

		for (String line : stored.split("\n"))
		{
			String[] f = line.split(FIELD, -1);
			if (f.length != 6) continue;
			try
			{
				int channel = Integer.parseInt(f[1]);
				double offset = Double.parseDouble(f[3]);
				LocalDate date = NONE.equals(f[4]) ? null : LocalDate.parse(f[4]);
				double ref = NONE.equals(f[5]) ? Double.NaN : Double.parseDouble(f[5]);
				if (f[0].isBlank() || channel < 0) continue;
				if (!Double.isFinite(offset) || offset == 0 || offset < minOffset || offset > maxOffset) continue;
				if (out.stream().anyMatch(c -> c.isFor(f[0], channel))) continue;
				out.add(new Calibration(f[0], channel, f[2].isBlank() ? f[0] : f[2], offset, date, ref));
			}
			catch (RuntimeException e)
			{
				// skipped: see above
			}
		}
		return out;
	}

	private static String clean(String s)
	{
		return s.replaceAll("[\\t\\r\\n]", " ");
	}

	// ---- display ----------------------------------------------------------

	/** {@code 3 Nov 2026}, or {@code date unknown}. */
	public String dateText()
	{
		if (date == null) return "date unknown";
		return date.getDayOfMonth() + " "
			+ date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " "
			+ date.getYear();
	}

	/** {@code ref 94.0 dB}, or empty when the reference is not known. */
	public String refText()
	{
		return Double.isFinite(refDb) ? String.format(Locale.US, "ref %.1f dB", refDb) : "";
	}
}
