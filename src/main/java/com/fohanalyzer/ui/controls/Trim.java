package com.fohanalyzer.ui.controls;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

/**
 * A dB nudge for a level that drifts during a show: − and + in {@link #STEP}
 * steps, the scroll wheel doing the same, and — when the value is shown — a
 * double-click on it to reset to zero.
 */
public final class Trim extends HBox
{
	public static final double STEP = 0.5;

	/**
	 * @param showValue
	 *            show the value between the buttons, with double-click to
	 *            reset. Off for a trim folded into another number, where the
	 *            value alone means nothing and zero is not a sensible reset.
	 */
	public Trim(DoubleProperty value, double min, double max, boolean showValue)
	{
		super(4);
		setAlignment(Pos.CENTER_RIGHT);

		Button down = stepButton("−", () -> nudge(value, -STEP, min, max));
		Button up = stepButton("+", () -> nudge(value, STEP, min, max));
		if (!showValue)
		{
			getChildren().addAll(down, up);
			scrollNudges(this, value, min, max);
			return;
		}

		Label val = new Label();
		val.getStyleClass().add("trim-val");
		val.setMinWidth(62);
		val.setAlignment(Pos.CENTER);
		Tooltip.install(val, new Tooltip("Scroll to nudge · double-click to reset"));
		val.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) value.set(0);
		});
		Runnable refresh = () -> {
			val.setText(fmtDb(value.get()));
			val.getStyleClass().remove("trim-on");
			if (value.get() != 0) val.getStyleClass().add("trim-on");
		};
		value.addListener((o, a, b) -> refresh.run());
		refresh.run();

		getChildren().addAll(down, val, up);
		scrollNudges(this, value, min, max);
	}

	/** Let the scroll wheel over {@code node} step {@code value}. */
	public static void scrollNudges(Node node, DoubleProperty value, double min, double max)
	{
		node.setOnScroll(e -> {
			if (e.getDeltaY() == 0 || node.isDisabled()) return;
			nudge(value, e.getDeltaY() > 0 ? STEP : -STEP, min, max);
			e.consume();
		});
	}

	/**
	 * A signed dB figure, e.g. {@code +12.0 dB}, with a typographic minus like
	 * the transfer-curve labels.
	 */
	public static String fmtDb(double db)
	{
		if (db == 0) return "0.0 dB";
		return String.format(java.util.Locale.US, "%+.1f dB", db).replace('-', '−');
	}

	private static void nudge(DoubleProperty value, double delta, double min, double max)
	{
		// Round off float drift so repeated steps keep landing on the grid.
		double next = Math.round((value.get() + delta) * 10) / 10.0;
		value.set(Math.clamp(next, min, max));
	}

	private static Button stepButton(String text, Runnable action)
	{
		Button b = new Button(text);
		b.getStyleClass().addAll("mini-btn", "trim-btn");
		b.setOnAction(e -> action.run());
		return b;
	}
}
