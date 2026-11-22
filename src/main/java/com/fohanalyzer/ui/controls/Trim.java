package com.fohanalyzer.ui.controls;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.util.function.DoubleFunction;

/**
 * A dB value that can be typed, or nudged with − and + in {@link #STEP} steps
 * and the scroll wheel, for a level that drifts during a show. A double-click
 * on the value puts it back to its reset value, where it has one.
 */
public final class Trim extends HBox
{
	public static final double STEP = 0.5;

	/** {@code 98.0 dB}, for a level rather than an offset. */
	public static final DoubleFunction<String> LEVEL = db -> String.format(java.util.Locale.US, "%.1f dB", db);

	private static final double VALUE_WIDTH = 72;

	/**
	 * @param showValue
	 *            show the value between the buttons, with double-click to
	 *            reset. Off for a trim folded into another number, where the
	 *            value alone means nothing and zero is not a sensible reset.
	 */
	public Trim(DoubleProperty value, double min, double max, boolean showValue)
	{
		this(value, min, max, STEP, 0, showValue);
	}

	/**
	 * A trim stepping {@code step} dB at a time whose double-click goes back to
	 * {@code resetTo}, for a level that is not an offset from zero.
	 */
	public Trim(DoubleProperty value, double min, double max, double step, double resetTo, boolean showValue)
	{
		this(value, min, max, step, resetTo, showValue ? Trim::fmtDb : null);
	}

	/**
	 * The general form: the value shown with {@code format}, or not at all when
	 * it is null; no double-click reset when {@code resetTo} is NaN.
	 */
	public Trim(DoubleProperty value, double min, double max, double step, double resetTo,
		DoubleFunction<String> format)
	{
		super(4);
		setAlignment(Pos.CENTER_RIGHT);

		Button down = stepButton("−", () -> nudge(value, -step, min, max));
		Button up = stepButton("+", () -> nudge(value, step, min, max));
		if (format == null)
		{
			getChildren().addAll(down, up);
			scrollNudges(this, value, min, max, step);
			return;
		}

		boolean resettable = !Double.isNaN(resetTo);
		TextField val = new TextField();
		val.getStyleClass().add("trim-val");
		val.setPrefWidth(VALUE_WIDTH);
		val.setMinWidth(VALUE_WIDTH);
		val.setMaxWidth(VALUE_WIDTH);
		val.setAlignment(Pos.CENTER);
		Tooltip.install(val, new Tooltip(resettable
			? "Type a value · scroll to nudge · double-click to reset"
			: "Type a value · scroll to nudge"));

		Runnable refresh = () -> {
			val.setText(format.apply(value.get()));
			val.getStyleClass().remove("trim-on");
			if (resettable && value.get() != resetTo) val.getStyleClass().add("trim-on");
		};
		// Typed text is taken on Enter or when the field is left; anything
		// that does not read as a number puts the value back as it was.
		Runnable commit = () -> {
			Double typed = parse(val.getText());
			if (typed != null) value.set(Math.clamp(Math.round(typed * 10) / 10.0, min, max));
			refresh.run();
		};
		val.setOnAction(e -> {
			commit.run();
			val.selectAll();
		});
		val.focusedProperty().addListener((o, was, is) -> {
			if (!is) commit.run();
		});
		val.setOnKeyPressed(e -> {
			if (e.getCode() != KeyCode.ESCAPE) return;
			refresh.run();
			val.selectAll();
			e.consume();
		});
		if (resettable)
		{
			val.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
				if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) return;
				value.set(resetTo);
				refresh.run();
				val.selectAll();
				e.consume();
			});
		}
		value.addListener((o, a, b) -> {
			// Not while someone is typing into it.
			if (!val.isFocused()) refresh.run();
		});
		refresh.run();

		getChildren().addAll(down, val, up);
		scrollNudges(this, value, min, max, step);
	}

	/** Let the scroll wheel over {@code node} step {@code value}. */
	public static void scrollNudges(Node node, DoubleProperty value, double min, double max)
	{
		scrollNudges(node, value, min, max, STEP);
	}

	private static void scrollNudges(Node node, DoubleProperty value, double min, double max, double step)
	{
		node.setOnScroll(e -> {
			if (e.getDeltaY() == 0 || node.isDisabled()) return;
			nudge(value, e.getDeltaY() > 0 ? step : -step, min, max);
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

	/**
	 * A typed dB value: {@code -3.5}, {@code −3.5 dB}, {@code +2},
	 * {@code 98,5}; null if it is not a number.
	 */
	static Double parse(String text)
	{
		String s = text.trim()
			.replace('−', '-')
			.replace(',', '.')
			.replaceFirst("(?i)\\s*db$", "")
			.trim();
		try
		{
			double v = Double.parseDouble(s);
			return Double.isFinite(v) ? v : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
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
