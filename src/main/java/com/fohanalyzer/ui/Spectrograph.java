package com.fohanalyzer.ui;

import com.fohanalyzer.dsp.SpectrogramBuffer;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The spectrograph: per-band display levels over time, newest at the top,
 * brightness is level.
 *
 * <p>
 * Rows come from the levels the RTA already computed — no second FFT — and are
 * recorded whatever the view, so switching to the spectrograph shows the past
 * straight away. Drawing is one image, one pixel per band and per row, scaled
 * onto the plot. The image mirrors the buffer's ring: a tick writes only the
 * new rows, and the scroll is the two blits either side of the head, rather
 * than thousands of rectangles filled every frame. It is only repainted whole
 * when the source or the colour range changes, or the history was cleared.
 */
final class Spectrograph
{
	/**
	 * Rows across the whole history. At 10 s this is about one per frame; at 60
	 * s one every 200 ms, still finer than the plot is tall.
	 */
	static final int ROWS = 300;

	/** dB either side of zero the Mic−Solo difference is mapped across. */
	static final double DIFF_RANGE = 24;

	private static final int LUT_SIZE = 256;
	private static final int[] LUT = buildLut();

	private final SpectrogramBuffer buffer = new SpectrogramBuffer(ROWS);

	private double pending = 0;

	private WritableImage image;
	private int[] rowPixels;
	private long drawnWritten = -1;
	private int drawnGeneration = -1;
	private int drawnSource = -1;
	private double drawnFloor = Double.NaN;
	private double drawnCeiling = Double.NaN;

	/**
	 * Darkest to brightest: the plot background, through the blue and teal of
	 * the palette, to lime, yellow and white.
	 */
	private static int[] buildLut()
	{
		double[][] stops = {
			{ 0, 8, 11, 17 },
			{ 0.3, 16, 52, 92 },
			{ 0.55, 18, 150, 160 },
			{ 0.78, 163, 230, 53 },
			{ 0.92, 250, 220, 90 },
			{ 1, 255, 255, 240 } };
		int[] lut = new int[LUT_SIZE];
		for (int i = 0; i < LUT_SIZE; i++)
		{
			double t = i / (double)(LUT_SIZE - 1);
			int k = 1;
			while (k < stops.length - 1 && t > stops[k][0])
				k++;
			double[] a = stops[k - 1];
			double[] b = stops[k];
			double u = (t - a[0]) / (b[0] - a[0]);
			int r = (int)Math.round(a[1] + (b[1] - a[1]) * u);
			int g = (int)Math.round(a[2] + (b[2] - a[2]) * u);
			int bl = (int)Math.round(a[3] + (b[3] - a[3]) * u);
			lut[i] = 0xff000000 | r << 16 | g << 8 | bl;
		}
		return lut;
	}

	/** The colour at {@code t} in 0…1 along the map, for the rail's key. */
	static Color color(double t)
	{
		int argb = LUT[(int)Math.round(Math.clamp(t, 0, 1) * (LUT_SIZE - 1))];
		return Color.rgb(argb >> 16 & 0xff, argb >> 8 & 0xff, argb & 0xff);
	}

	/**
	 * Record the current levels, as many rows as {@code dt} seconds are worth
	 * at the history's row interval. A stall longer than the whole history
	 * writes at most one history's worth.
	 */
	void record(int frac, float[] mic, float[] solo, int historySec, double dt)
	{
		if (buffer.configure(frac, mic.length, historySec)) pending = 0;
		double rowSec = historySec / (double)ROWS;
		pending += Math.max(0, dt);
		int rows = 0;
		while (pending >= rowSec && rows < ROWS)
		{
			pending -= rowSec;
			buffer.push(mic, solo);
			rows++;
		}
		if (rows == ROWS) pending = 0;
	}

	/**
	 * Draw the history from {@code y} down {@code h} pixels, each band placed
	 * on the plot's frequency axis. The caller clips to the plot.
	 */
	void draw(GraphicsContext g, double y, double h, double plotW, double[] centers, int frac,
		int source, double floor, double ceiling)
	{
		int bands = buffer.bands();
		if (bands != centers.length || bands < 1) return;
		update(source, floor, ceiling);

		// Each band spans its own fractional octave, centre ± half a step.
		double half = Math.pow(2, 1.0 / (2 * frac));
		double x0 = Draw.fX(centers[0] / half, plotW);
		double x1 = Draw.fX(centers[bands - 1] * half, plotW);
		double rowH = h / ROWS;
		int head = buffer.head();
		int upper = ROWS - head;

		g.setImageSmoothing(false);
		g.drawImage(image, 0, head, bands, upper, x0, y, x1 - x0, upper * rowH);
		if (head > 0) g.drawImage(image, 0, 0, bands, head, x0, y + upper * rowH, x1 - x0, head * rowH);
		g.setImageSmoothing(true);
	}

	/** Bring the image up to date: the new rows, or all of it when stale. */
	private void update(int source, double floor, double ceiling)
	{
		int bands = buffer.bands();
		boolean whole = image == null || (int)image.getWidth() != bands
			|| drawnGeneration != buffer.generation()
			|| source != drawnSource || floor != drawnFloor || ceiling != drawnCeiling
			|| buffer.written() - drawnWritten >= ROWS;

		if (image == null || (int)image.getWidth() != bands)
		{
			image = new WritableImage(bands, ROWS);
			rowPixels = new int[bands];
		}

		PixelWriter pw = image.getPixelWriter();
		int fresh = whole ? ROWS : (int)(buffer.written() - drawnWritten);
		for (int age = 0; age < fresh; age++)
		{
			if (age < buffer.size())
			{
				fillRow(buffer.mic(age), buffer.solo(age), source, floor, ceiling);
				pw.setPixels(0, buffer.slot(age), bands, 1, PixelFormat.getIntArgbInstance(), rowPixels, 0, bands);
			}
			else
			{
				// Older than anything recorded: the slot of a row yet to come.
				java.util.Arrays.fill(rowPixels, LUT[0]);
				int slot = Math.floorMod(buffer.head() + age, ROWS);
				pw.setPixels(0, slot, bands, 1, PixelFormat.getIntArgbInstance(), rowPixels, 0, bands);
			}
		}

		drawnWritten = buffer.written();
		drawnGeneration = buffer.generation();
		drawnSource = source;
		drawnFloor = floor;
		drawnCeiling = ceiling;
	}

	private void fillRow(float[] mic, float[] solo, int source, double floor, double ceiling)
	{
		// A range drawn inverted or empty has no meaning; keep at least a dB.
		double span = Math.max(1, ceiling - floor);
		for (int i = 0; i < rowPixels.length; i++)
		{
			double t = switch (source)
			{
				case AppState.SPECTRO_SOLO -> (solo[i] - floor) / span;
				case AppState.SPECTRO_DIFF -> (mic[i] - solo[i] + DIFF_RANGE) / (2 * DIFF_RANGE);
				default -> (mic[i] - floor) / span;
			};
			rowPixels[i] = LUT[(int)(Math.clamp(t, 0, 1) * (LUT_SIZE - 1))];
		}
	}
}
