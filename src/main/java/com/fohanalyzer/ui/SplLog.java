package com.fohanalyzer.ui;

import com.fohanalyzer.dsp.LeqMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This session's SPL history as a CSV file, one row a second, so it can be
 * taken off the machine and shown to whoever asks what the level was.
 *
 * <p>
 * Each row is the Leq, Fast maximum and peak over that second alone, which is
 * enough to work out any longer window afterwards, and the rolling Leq the
 * meter was showing at the time. Levels are in dB SPL with the calibration
 * offset in effect added, and that offset is on every row too, so the dBFS
 * behind each figure can be recovered. The weighting is in the column names.
 *
 * <p>
 * One file per launch, created with the first row, so a session that never had
 * a calibrated live mic leaves nothing behind. Rows are decided on the FX
 * thread and written on a thread of their own; each is flushed, so a crash
 * loses at most the second it happened in.
 */
public final class SplLog
{
	private static final Logger log = LoggerFactory.getLogger(SplLog.class);

	private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("'spl-'yyyy-MM-dd'_'HHmmss'.csv'", Locale.ROOT);

	private static final String W = LeqMeter.WEIGHTING;

	static final String HEADER = String.join(",",
		"time", "seconds", "L" + W + "eq_dB", "L" + W + "Fmax_dB", "L" + W + "peak_dB",
		"L" + W + "eq_window_dB", "window_s", "window_integrated_s", "calibration_dB");

	private final Path dir;
	private final Clock clock;
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "spl-log");
		t.setDaemon(true);
		return t;
	});

	/** Decided on the first row; only touched on the FX thread. */
	private Path file;
	/** Only touched on {@link #io}. */
	private BufferedWriter out;

	public SplLog()
	{
		this(defaultDir(), Clock.systemDefaultZone());
	}

	SplLog(Path dir, Clock clock)
	{
		this.dir = dir;
		this.clock = clock;
	}

	/** {@code dir} for reading, with the home folder shortened to {@code ~}. */
	public static String displayPath(Path dir)
	{
		Path home = Path.of(System.getProperty("user.home"));
		return dir.startsWith(home) ? "~/" + home.relativize(dir) : dir.toString();
	}

	/** Where the logs go: a folder of their own under Documents. */
	public static Path defaultDir()
	{
		return Path.of(System.getProperty("user.home"), "Documents", "FOHanalyzer", "SPL logs");
	}

	/**
	 * Append a row for {@code interval}, with the meter's rolling
	 * {@code reading} beside it, and return the file it goes to.
	 */
	public Path append(LeqMeter.Interval interval, LeqMeter.Reading reading, double calibration)
	{
		ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		if (file == null) file = dir.resolve(FILE_NAME.format(now));
		Path target = file;
		String line = row(now, interval, reading, calibration);
		io.execute(() -> write(target, line));
		return target;
	}

	/** Write out what is queued and close the file. */
	public void close()
	{
		io.execute(this::closeWriter);
		io.shutdown();
		try
		{
			if (!io.awaitTermination(2, TimeUnit.SECONDS)) log.warn("SPL log did not finish writing");
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	static String row(ZonedDateTime time, LeqMeter.Interval iv, LeqMeter.Reading r, double calibration)
	{
		return String.join(",",
			time.toOffsetDateTime().toString(),
			fmt(iv.seconds(), "%.3f"),
			db(iv.leqDb(), calibration),
			db(iv.maxDb(), calibration),
			db(iv.peakDb(), calibration),
			db(r.leqDb(), calibration),
			fmt(r.windowSeconds(), "%.0f"),
			fmt(r.integratedSeconds(), "%.1f"),
			fmt(calibration, "%.2f"));
	}

	private static String db(double dbfs, double calibration)
	{
		return Double.isFinite(dbfs) ? fmt(dbfs + calibration, "%.1f") : "";
	}

	private static String fmt(double v, String pattern)
	{
		return String.format(Locale.US, pattern, v);
	}

	private void write(Path target, String line)
	{
		try
		{
			if (out == null)
			{
				// Appending, so reopening after a failed write keeps what is
				// already there.
				Files.createDirectories(target.getParent());
				boolean fresh = Files.notExists(target);
				out = Files.newBufferedWriter(target, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				if (fresh)
				{
					out.write(HEADER);
					out.newLine();
				}
			}
			out.write(line);
			out.newLine();
			out.flush();
		}
		catch (IOException e)
		{
			log.warn("could not write the SPL log {}", target, e);
			closeWriter();
		}
	}

	private void closeWriter()
	{
		if (out == null) return;

		try
		{
			out.close();
		}
		catch (IOException e)
		{
			log.warn("could not close the SPL log", e);
		}
		out = null;
	}
}
