package com.fohanalyzer.ui.controls;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * The Leq over the last half hour, with the venue limit as a dashed line and
 * whatever went over it shaded red.
 */
public final class LeqTrend extends Region
{
	private static final double HEIGHT = 44;
	/** dB shown under the limit, and at least over it. */
	private static final double BELOW_DB = 12;
	private static final double ABOVE_DB = 4;

	private static final Color LINE = Color.web("#a3e635");
	private static final Color LIMIT = Color.web("#ff5b60");
	private static final Color OVER = Color.web("#ff5b60", 0.35);
	private static final Color GRID = Color.web("#1c2733");

	private final Canvas canvas = new Canvas();
	private double[] seconds = new double[0];
	private double[] leqDb = new double[0];
	private double limitDb = Double.NaN;
	private double spanSeconds = 1;

	public LeqTrend()
	{
		getStyleClass().add("leq-trend");
		getChildren().add(canvas);
		setMinHeight(HEIGHT);
		setPrefHeight(HEIGHT);
		setMaxHeight(HEIGHT);
	}

	/**
	 * Samples at {@code seconds} before now (so 0 is now and the oldest is
	 * {@code spanSeconds} back), held against {@code limitDb}.
	 */
	public void set(double[] seconds, double[] leqDb, double limitDb, double spanSeconds)
	{
		this.seconds = seconds;
		this.leqDb = leqDb;
		this.limitDb = limitDb;
		this.spanSeconds = spanSeconds;
		draw();
	}

	@Override
	protected void layoutChildren()
	{
		canvas.setWidth(getWidth());
		canvas.setHeight(getHeight());
		draw();
	}

	private void draw()
	{
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		GraphicsContext g = canvas.getGraphicsContext2D();
		g.clearRect(0, 0, w, h);
		if (w <= 0 || h <= 0 || !Double.isFinite(limitDb)) return;

		double top = limitDb + ABOVE_DB;
		for (double v : leqDb)
		{
			if (Double.isFinite(v)) top = Math.max(top, v + 1);
		}
		double bottom = limitDb - BELOW_DB;
		double range = top - bottom;

		g.setStroke(GRID);
		g.setLineWidth(1);
		g.strokeLine(0, h - 0.5, w, h - 0.5);

		double limitY = Math.round(y(limitDb, top, range, h)) + 0.5;
		// Over the limit: from the line down to the dashed limit, per sample.
		g.setFill(OVER);
		for (int i = 0; i < leqDb.length; i++)
		{
			if (!(leqDb[i] > limitDb)) continue;
			double x0 = x(seconds[i], w);
			double x1 = i + 1 < seconds.length ? x(seconds[i + 1], w) : w;
			double ly = y(leqDb[i], top, range, h);
			g.fillRect(x0, ly, Math.max(1, x1 - x0), limitY - ly);
		}

		g.setStroke(LINE);
		g.setLineWidth(1.5);
		g.beginPath();
		boolean pen = false;
		for (int i = 0; i < leqDb.length; i++)
		{
			if (!Double.isFinite(leqDb[i]))
			{
				pen = false;
				continue;
			}
			double x = x(seconds[i], w);
			double y = Math.clamp(y(leqDb[i], top, range, h), 0, h);
			if (pen) g.lineTo(x, y);
			else
				g.moveTo(x, y);
			pen = true;
		}
		g.stroke();

		g.setStroke(LIMIT);
		g.setLineWidth(1);
		g.setLineDashes(4, 3);
		g.strokeLine(0, limitY, w, limitY);
		g.setLineDashes((double[])null);
	}

	private double x(double secondsAgo, double w)
	{
		return w * (1 - secondsAgo / spanSeconds);
	}

	private static double y(double db, double top, double range, double h)
	{
		return h * (top - db) / range;
	}
}
