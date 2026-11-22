package com.fohanalyzer.dev;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.mcp.AnalyzerHistory;
import com.fohanalyzer.ui.AppState;
import com.fohanalyzer.ui.FeedbackEntry;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.time.Instant;
import java.util.Random;

/**
 * Dev aid: with {@code FOH_DEMO=true} the app starts with a representative show
 * in progress instead of the stored settings and real inputs, for the
 * screenshots in the README and on the website. Run with {@code PROBE} to
 * snapshot it:
 * {@code FOH_DEMO=true PROBE=13 PROBE_PREFS=1 mvn -q -o javafx:run}.
 *
 * <p>
 * Both sources are named as live channels of an interface, but no line is
 * opened: the plot draws the built-in simulation, and the SPL meter is fed a
 * synthetic programme level. The stored settings are not read or bound (only
 * the preferences window's position is remembered), and no SPL log is written.
 */
public final class Demo
{
	private static final String DEVICE_ID = "demo-18i20";

	/** dBFS of the synthetic programme; with the offset it reads ~96 dB. */
	private static final double PROGRAMME_DBFS = -34;
	private static final double CALIBRATION = 130.2;
	/**
	 * How much of the Leq window has been integrated when the shot is taken.
	 */
	private static final double INTEGRATED_SECONDS = 192;
	/**
	 * The venue limit; the programme sits just under it, so the Leq reads
	 * amber.
	 */
	private static final double LIMIT = 98;
	/** How much Leq history the limit trend is staged with. */
	private static final int TREND_SECONDS = 30 * 60;

	private Demo()
	{
	}

	public static boolean enabled()
	{
		return "true".equalsIgnoreCase(System.getenv("FOH_DEMO"));
	}

	public static void stage(AppState state)
	{
		state.audioDevices.add(new AudioDevice(DEVICE_ID, "Scarlett 18i20 USB", null));
		state.micChan.set("live:" + DEVICE_ID);
		state.micChanCount.set(18);
		state.micChanIdx.set(2);
		state.soloChan.set("live:" + DEVICE_ID);
		state.soloChanCount.set(18);
		state.soloChanIdx.set(4);
		state.soloStereo.set(true);
		state.soloTrim.set(-3.5);

		state.calibrate(CALIBRATION, 94, java.time.LocalDate.now().minusDays(12));
		// A second rig, last used a while ago, for the saved calibrations.
		state.calibrations.add(new com.fohanalyzer.ui.Calibration("demo-wing", 31, "Behringer WING", 121.7,
			java.time.LocalDate.now().minusDays(47), 94));
		state.showSpl.set(true);
		state.leqWindow.set(300);
		feedProgramme(state);
		state.splLimit.set(LIMIT);
		state.splLimitOn.set(true);
		backfillTrend(state);

		// Split, so the shot shows the spectrograph under the RTA.
		state.view.set(AppState.VIEW_SPLIT);
		state.spectroHistory.set(10);

		state.markers.set(true);
		state.markerSource.set("mic");
		// Once the averaging has settled, or the ghost sits far below the
		// traces it is meant to shadow.
		PauseTransition settle = new PauseTransition(Duration.seconds(3));
		settle.setOnFinished(e -> state.captureNonce.set(state.captureNonce.get() + 1));
		settle.play();

		ring(state, 2512, 6, "21:41");
		ring(state, 316, 4, "21:37");
		state.locateFreq.set(2512.0);
		state.detectStatus.set("YIN · 2.51 kHz · 94% confidence");
	}

	private static void ring(AppState state, double hz, int cut, String time)
	{
		double[] iso = Engine.bandCenters(3);
		double band = iso[0];
		for (double b : iso)
		{
			if (Math.abs(Math.log(b / hz)) < Math.abs(Math.log(band / hz))) band = b;
		}
		state.feedbackLog.add(new FeedbackEntry(System.nanoTime(), hz, Engine.noteName(hz), band, cut, time));
	}

	/**
	 * The last half hour of Leq for the limit trend: a set that builds, goes
	 * over the limit for a few minutes in the middle, and comes back under it.
	 * The time over since the reset is the same few minutes.
	 */
	private static void backfillTrend(AppState state)
	{
		Instant now = Instant.now();
		double over = 0;
		for (int ago = TREND_SECONDS; ago > 0; ago--)
		{
			double t = (TREND_SECONDS - ago) / (double)TREND_SECONDS;
			double db = 93.5 + 2.5 * t + 5 * Math.exp(-Math.pow((t - 0.55) / 0.12, 2)) + 0.6 * Math.sin(ago / 47.0);
			if (db > LIMIT) over++;
			AnalyzerHistory.Leq leq = new AnalyzerHistory.Leq(db, state.leqWindow.get(), state.leqWindow.get(),
				LIMIT);
			state.history.backfill(new AnalyzerHistory.StatsSample(now.minusSeconds(ago), 0, 0, 0, 0, 0, null, db,
				leq, true, true));
		}
		state.timeOverLimit.tick(over, LIMIT + 1, LIMIT);
	}

	/**
	 * Noise at a level that swells and dips like a set does, so the Leq, the
	 * Fast level, its maximum and the peak all read differently.
	 */
	private static void feedProgramme(AppState state)
	{
		double rate = 48000;
		int block = 1024;
		float[] buf = new float[block];
		Random random = new Random(22);
		long total = Math.round(INTEGRATED_SECONDS * rate);
		for (long done = 0; done < total; done += block)
		{
			double t = done / rate;
			double db = PROGRAMME_DBFS + 3 * Math.sin(t / 9) + 1.5 * Math.sin(t * 1.7);
			double amp = Math.pow(10, db / 20);
			for (int i = 0; i < block; i++)
				buf[i] = (float)(random.nextGaussian() * amp);
			state.leq.accept(buf, null, block, rate);
		}
	}
}
