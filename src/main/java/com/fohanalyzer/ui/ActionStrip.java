package com.fohanalyzer.ui;

import com.fohanalyzer.audio.AudioDsp;
import com.fohanalyzer.audio.AudioSource;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.ui.controls.Segmented;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The strip above the plot: what gets pressed during a show. The view, which
 * traces and overlays are drawn, capturing a reference, detecting a ring, and
 * the resets.
 *
 * <p>
 * Settings live in the preferences window and readouts in the rail; this is
 * only for momentary actions and view switches, kept next to the plot they act
 * on.
 */
public final class ActionStrip extends HBox
{
	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

	/** How many rings the rail keeps listed. */
	private static final int RING_LOG_SIZE = 6;

	private final AppState state;

	public ActionStrip(AppState state)
	{
		this.state = state;
		getStyleClass().add("action-strip");
		setSpacing(8);
		setAlignment(Pos.CENTER_LEFT);

		Map<Integer, String> viewOpts = new LinkedHashMap<>();
		viewOpts.put(AppState.VIEW_RTA, "RTA");
		viewOpts.put(AppState.VIEW_SPLIT, "Split");
		viewOpts.put(AppState.VIEW_SPECTROGRAPH, "Spectro");
		Segmented view = new Segmented(state.view, viewOpts);
		view.getStyleClass().add("strip-seg");

		HBox traces = group(
			chip("Mic", "#22d3ee", state.micOn, "Draw the measurement mic"),
			chip("Solo", "#f5a524", state.soloOn, "Draw the solo bus"),
			chip("Mic−Solo", "#b794f6", state.showTransfer, "Draw the transfer curve, mic minus solo"));

		Button reference = chip("Reference", null, state.showReference, "Show the captured reference");
		reference.disableProperty().bind(state.reference.isNull());
		HBox overlays = group(
			chip("Peak hold", null, state.peakHold, "Hold each band's highest level"),
			chip("Markers", null, state.markers, "Label the loudest peaks"),
			reference);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Button capture = action("⊕  Capture reference", "strip-capture",
			e -> state.captureNonce.set(state.captureNonce.get() + 1));
		capture.setTooltip(new Tooltip("Keep both traces as a dashed ghost, to compare before and after EQ"));

		Button detect = action("⌖  Detect ring", "strip-detect", e -> detectRing());
		detect.setTooltip(new Tooltip("Find what the live mic is ringing at and log the GEQ band to cut"));
		// Detection needs a real signal to listen to.
		Runnable detectVis = () -> {
			boolean live = state.isMicLive();
			detect.setVisible(live);
			detect.setManaged(live);
			state.detectStatus.set("");
		};
		state.micChan.addListener((o, a, b) -> detectVis.run());
		detectVis.run();

		Button reset = action("↺  Reset ▾", null, null);
		ContextMenu resets = resetMenu();
		reset.setOnAction(e -> resets.show(reset, Side.BOTTOM, 0, 4));

		getChildren().addAll(view, sep(), traces, sep(), overlays, spacer, capture, detect, reset);

		// Labels are short and must stay whole: a button squeezed to "S…" is
		// a guess. The spacer gives way instead.
		for (Node n : view.getChildren())
			keepWidth(n);
		for (Node g : new Node[] { traces, overlays })
			((HBox)g).getChildren().forEach(ActionStrip::keepWidth);
		for (Node n : new Node[] { view, traces, overlays, capture, detect, reset })
			keepWidth(n);
	}

	private ContextMenu resetMenu()
	{
		MenuItem hold = new MenuItem("Peak hold");
		hold.setOnAction(e -> state.holdReset.set(state.holdReset.get() + 1));
		hold.disableProperty().bind(state.peakHold.not());

		MenuItem spl = new MenuItem("SPL Leq, max, peak and time over");
		spl.setOnAction(e -> {
			state.leq.reset();
			state.timeOverLimit.reset();
		});
		spl.visibleProperty().bind(state.showSpl);

		MenuItem rings = new MenuItem("Ring log");
		rings.setOnAction(e -> {
			state.feedbackLog.clear();
			state.locateFreq.set(null);
			state.detectStatus.set("");
		});

		MenuItem reference = new MenuItem("Reference");
		reference.setOnAction(e -> state.reference.set(null));
		reference.disableProperty().bind(state.reference.isNull());

		return new ContextMenu(hold, spl, rings, reference);
	}

	/** A toggle for {@code value}, lit while it is on. */
	private static Button chip(String text, String dotColor, BooleanProperty value, String tip)
	{
		Button b = new Button(text);
		b.getStyleClass().add("strip-btn");
		if (dotColor != null) b.setGraphic(new Circle(4, Color.web(dotColor)));
		b.setTooltip(new Tooltip(tip));
		b.setOnAction(e -> value.set(!value.get()));
		Runnable refresh = () -> {
			b.getStyleClass().remove("on");
			if (value.get()) b.getStyleClass().add("on");
		};
		value.addListener((o, a, v) -> refresh.run());
		refresh.run();
		return b;
	}

	private static Button action(String text, String styleClass,
		javafx.event.EventHandler<javafx.event.ActionEvent> handler)
	{
		Button b = new Button(text);
		b.getStyleClass().addAll("strip-btn", "strip-act");
		if (styleClass != null) b.getStyleClass().add(styleClass);
		if (handler != null) b.setOnAction(handler);
		return b;
	}

	private static void keepWidth(Node n)
	{
		if (n instanceof Region r) r.setMinWidth(Region.USE_PREF_SIZE);
		HBox.setHgrow(n, Priority.NEVER);
	}

	private static HBox group(Node... nodes)
	{
		HBox g = new HBox(2, nodes);
		g.getStyleClass().add("strip-group");
		g.setAlignment(Pos.CENTER_LEFT);
		return g;
	}

	private static Region sep()
	{
		Region r = new Region();
		r.getStyleClass().add("strip-sep");
		return r;
	}

	// ---- ring-out ---------------------------------------------------------

	/**
	 * Ask TarsosDSP's YIN what the live mic is ringing at and log that, instead
	 * of the band centre the peak markers would round to. What it found goes to
	 * the rail.
	 */
	private void detectRing()
	{
		AudioSource mic = state.micSource;
		AudioDsp.Pitch p = mic != null ? mic.readPitch() : null;
		if (p == null)
		{
			state.detectStatus.set("No steady tone on the mic right now.");
			return;
		}
		state.detectStatus.set(String.format(Locale.US,
			"YIN · %s · %.0f%% confidence", Engine.fmtFreq(p.hz()), p.probability() * 100));
		logFeedback(p.hz(), suggestedCut());
	}

	/**
	 * Log a ring at {@code fc}, notched on the nearest ISO 1/3-octave GEQ band.
	 */
	private void logFeedback(double fc, int cut)
	{
		double[] iso = Engine.bandCenters(3);
		double band = iso[0];
		for (double b : iso)
		{
			if (Math.abs(log2(b / fc)) < Math.abs(log2(band / fc))) band = b;
		}

		FeedbackEntry entry = new FeedbackEntry(System.nanoTime(), fc, Engine.noteName(fc), band, cut,
			LocalTime.now().format(HHMM));
		state.feedbackLog.add(0, entry);
		state.history.recordRing(fc, entry.note(), band, cut);
		while (state.feedbackLog.size() > RING_LOG_SIZE)
			state.feedbackLog.remove(state.feedbackLog.size() - 1);
		state.locateFreq.set(fc);
	}

	/**
	 * How far the loudest band stands above the mic average, clamped to a sane
	 * cut.
	 */
	private int suggestedCut()
	{
		Stats st = state.stats.get();
		double prominence = st != null ? st.micPeak() - st.micAvg() : 6;
		return (int)Math.round(Math.clamp(prominence, 3, 12));
	}

	private static double log2(double x)
	{
		return Math.log(x) / Math.log(2);
	}
}
