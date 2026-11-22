package com.fohanalyzer.ui.controls;

import com.fohanalyzer.dsp.SplLimit;
import javafx.scene.layout.Region;

/**
 * A thin level bar with a tick at the venue limit: how much room is left, at a
 * glance. Spans {@link #BELOW_DB} under the limit to {@link #ABOVE_DB} over it,
 * and fills in the colour of the zone the level is in.
 */
public final class LimitBar extends Region
{
	static final double BELOW_DB = 24;
	static final double ABOVE_DB = 6;

	private static final double HEIGHT = 6;
	private static final double TICK_WIDTH = 2;
	private static final double TICK_OVERHANG = 3;

	private final Region track = new Region();
	private final Region fill = new Region();
	private final Region tick = new Region();
	private double fraction;

	public LimitBar()
	{
		getStyleClass().add("limit-bar");
		track.getStyleClass().add("limit-bar-track");
		fill.getStyleClass().add("limit-bar-fill");
		tick.getStyleClass().add("limit-bar-tick");
		getChildren().addAll(track, fill, tick);
		setMinHeight(HEIGHT + 2 * TICK_OVERHANG);
		setPrefHeight(HEIGHT + 2 * TICK_OVERHANG);
		setMaxHeight(HEIGHT + 2 * TICK_OVERHANG);
	}

	/** Show {@code leqDb} against {@code limitDb}. */
	public void set(double leqDb, double limitDb)
	{
		double lo = limitDb - BELOW_DB;
		fraction = Double.isFinite(leqDb) ? Math.clamp((leqDb - lo) / (BELOW_DB + ABOVE_DB), 0, 1) : 0;
		fill.getStyleClass().removeAll("near", "over");
		switch (SplLimit.zone(leqDb, limitDb))
		{
			case NEAR -> fill.getStyleClass().add("near");
			case OVER -> fill.getStyleClass().add("over");
			default -> {
			}
		}
		requestLayout();
	}

	@Override
	protected void layoutChildren()
	{
		double w = getWidth();
		double y = TICK_OVERHANG;
		track.resizeRelocate(0, y, w, HEIGHT);
		fill.resizeRelocate(0, y, Math.round(w * fraction), HEIGHT);
		double at = w * BELOW_DB / (BELOW_DB + ABOVE_DB);
		tick.resizeRelocate(Math.round(at - TICK_WIDTH / 2), 0, TICK_WIDTH, HEIGHT + 2 * TICK_OVERHANG);
	}
}
