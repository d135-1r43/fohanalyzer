package com.fohanalyzer.ui;

import com.fohanalyzer.Version;
import com.fohanalyzer.audio.AudioDsp;
import com.fohanalyzer.audio.AudioSource;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.ui.controls.Meter;
import com.fohanalyzer.ui.controls.Segmented;
import com.fohanalyzer.ui.controls.SourceCard;
import com.fohanalyzer.ui.controls.Toggle;
import com.fohanalyzer.ui.controls.Trim;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The control rail (right panel): every settings section and its handlers.
 */
public final class ControlRail extends ScrollPane
{
	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
	private final AppState state;
	private final Settings settings;
	private final PreferencesWindow preferences;

	private SourceCard micCard;
	private SourceCard soloCard;

	public ControlRail(AppState state, Settings settings)
	{
		this.state = state;
		this.settings = settings;
		this.preferences = new PreferencesWindow(state, settings);
		getStyleClass().addAll("scroll-pane", "control-rail");
		setFitToWidth(true);
		setHbarPolicy(ScrollBarPolicy.NEVER);

		VBox rail = new VBox();
		rail.getStyleClass().add("rail");

		// Ordered by how often a hand reaches for it mid-show: the traces and
		// what they read, then the feedback tool, then the overlays, and the
		// analysis settings last. Anything set once per rig is in the
		// preferences window instead.
		rail.getChildren().addAll(
			sourcesSection(),
			splSection(),
			ringoutSection(),
			viewSection(),
			overlaysSection(),
			referenceSection(),
			analysisSection(),
			railFoot());

		// Push live stats into the meters / SPL readout.
		state.stats.addListener((o, a, st) -> onStats(st));

		setContent(rail);
	}

	// ---- Sources ----------------------------------------------------------
	private VBox sourcesSection()
	{
		micCard = new SourceCard("Measurement Mic", "#22d3ee",
			state.micOn, state.markerSource, "mic", AppState.MIC_INPUTS,
			state.micChan, state.micChanIdx, state.micChanCount, state.audioDevices);
		soloCard = new SourceCard("Solo Bus", "#f5a524",
			state.soloOn, state.markerSource, "solo", AppState.SOLO_INPUTS,
			state.soloChan, state.soloChanIdx, state.soloChanCount, state.audioDevices,
			state.soloStereo);
		soloCard.getChildren().add(soloTrimRow());
		return section("Sources", micCard, soloCard);
	}

	/**
	 * Offsets the solo trace onto the mic's level. One side is enough to line
	 * the two up, and the mic is the one whose level means something.
	 */
	private HBox soloTrimRow()
	{
		Label lbl = new Label("Trim");
		lbl.getStyleClass().add("ctl-lbl");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Trim trim = new Trim(state.soloTrim, -AppState.SOLO_TRIM_MAX, AppState.SOLO_TRIM_MAX, true);
		Button match = miniBtn("Match", e -> matchSoloTrim());
		match.setTooltip(new Tooltip("Set the trim from the average Mic−Solo difference"));
		HBox row = new HBox(6, lbl, sp, trim, match);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void matchSoloTrim()
	{
		double t = Math.round(state.stats.get().matchingSoloTrim() * 10) / 10.0;
		state.soloTrim.set(Math.clamp(t, -AppState.SOLO_TRIM_MAX, AppState.SOLO_TRIM_MAX));
	}

	private void onStats(Stats st)
	{
		micCard.meter.setValue(st.micAvg());
		soloCard.meter.setValue(st.soloAvg());
		if (splValue != null)
		{
			Double spl = st.micRmsDbfs() != null ? st.micRmsDbfs() + state.splOffset.get() : null;
			splValue.setText(spl != null ? String.format(java.util.Locale.US, "%.1f", spl) : "—");
		}
	}

	// ---- SPL --------------------------------------------------------------
	/** The range {@link Settings} accepts for the calibration offset. */
	private static final double SPL_OFFSET_MIN = -200;
	private static final double SPL_OFFSET_MAX = 200;

	private Label splValue;
	private Label splBadge;

	private VBox splSection()
	{
		splValue = new Label("—");
		splValue.getStyleClass().add("spl-val");
		Label unit = new Label("dB SPL");
		unit.getStyleClass().add("spl-unit");
		HBox readout = new HBox(6, splValue, unit);
		readout.setAlignment(Pos.BASELINE_LEFT);
		HBox.setHgrow(readout, Priority.ALWAYS);
		splBadge = new Label("UNCAL");
		splBadge.getStyleClass().add("spl-badge");
		HBox display = new HBox(readout, splBadge);
		display.getStyleClass().add("spl-display");
		display.setAlignment(Pos.CENTER_LEFT);

		// Calibrating is a set-once job and lives in the preferences
		// window; what stays here is the reading itself, and a nudge for when
		// it drifts from a hand-held meter. The nudge moves the calibration
		// offset itself, so there is one number, not two that stack.
		Label nudgeLbl = new Label("Match a reference meter");
		nudgeLbl.getStyleClass().add("ctl-lbl");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Trim nudge = new Trim(state.splOffset, SPL_OFFSET_MIN, SPL_OFFSET_MAX, false);
		HBox nudgeRow = new HBox(nudgeLbl, sp, nudge);
		nudgeRow.setAlignment(Pos.CENTER_LEFT);
		// Nudging an uncalibrated meter would quietly turn dBFS into "SPL".
		nudgeRow.disableProperty().bind(state.splOffset.isEqualTo(0));

		VBox sec = section("SPL Meter", display, nudgeRow);

		// visible only when the mic source is live and the meter is wanted
		Runnable vis = () -> {
			boolean show = state.isMicLive() && state.showSpl.get();
			sec.setVisible(show);
			sec.setManaged(show);
		};
		state.micChan.addListener((o, a, b) -> vis.run());
		state.showSpl.addListener((o, a, b) -> vis.run());
		state.splOffset.addListener((o, a, b) -> updateSplBadge());
		vis.run();
		updateSplBadge();
		return sec;
	}

	private void updateSplBadge()
	{
		boolean cal = state.splOffset.get() != 0;
		splBadge.setText(cal ? "CAL" : "UNCAL");
		splBadge.getStyleClass().remove("spl-badge-cal");
		if (cal) splBadge.getStyleClass().add("spl-badge-cal");
	}

	// ---- Analysis ---------------------------------------------------------
	/**
	 * Resolution, smoothing and averaging were three sections deep in the rail;
	 * they are all "how the plot is computed" and read better as one block at
	 * the foot of it.
	 */
	private VBox analysisSection()
	{
		Map<Integer, String> fracOpts = new LinkedHashMap<>();
		fracOpts.put(1, "1/1");
		fracOpts.put(3, "1/3");
		fracOpts.put(6, "1/6");
		fracOpts.put(12, "1/12");
		fracOpts.put(24, "1/24");
		Label resLbl = new Label("Resolution");
		resLbl.getStyleClass().add("row-lbl");
		Segmented frac = new Segmented(state.frac, fracOpts);

		Label lbl = new Label("Smoothing");
		lbl.getStyleClass().add("row-lbl");
		Label val = new Label();
		val.getStyleClass().add("row-val");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox row = new HBox(lbl, spacer, val);

		Slider slider = new Slider(0, 0.95, state.smoothing.get());
		slider.valueProperty().bindBidirectional(state.smoothing);
		Runnable updateVal = () -> val.setText(Math.round(state.smoothing.get() * 100) + "%");
		state.smoothing.addListener((o, a, b) -> updateVal.run());
		updateVal.run();

		Label avgLbl = new Label("Averaging");
		avgLbl.getStyleClass().add("row-lbl");
		Map<Integer, String> avgOpts = new LinkedHashMap<>();
		avgOpts.put(1, "Off");
		avgOpts.put(2, "2");
		avgOpts.put(4, "4");
		avgOpts.put(8, "8");
		avgOpts.put(16, "16");
		Segmented avg = new Segmented(state.avgN, avgOpts);

		return section("Analysis", resLbl, frac, row, slider, avgLbl, avg);
	}

	// ---- View -------------------------------------------------------------
	/** dB a click or scroll moves the spectrograph's colour range. */
	private static final double SPECTRO_RANGE_STEP = 5;

	/**
	 * RTA, spectrograph, or both stacked. The spectrograph's own options only
	 * show while it is on screen.
	 */
	private VBox viewSection()
	{
		Map<Integer, String> viewOpts = new LinkedHashMap<>();
		viewOpts.put(AppState.VIEW_RTA, "RTA");
		viewOpts.put(AppState.VIEW_SPLIT, "Split");
		viewOpts.put(AppState.VIEW_SPECTROGRAPH, "Spectro");
		Segmented view = new Segmented(state.view, viewOpts);

		Label srcLbl = new Label("Spectrograph source");
		srcLbl.getStyleClass().add("row-lbl");
		Map<Integer, String> srcOpts = new LinkedHashMap<>();
		srcOpts.put(AppState.SPECTRO_MIC, "Mic");
		srcOpts.put(AppState.SPECTRO_SOLO, "Solo");
		srcOpts.put(AppState.SPECTRO_DIFF, "Mic−Solo");
		Segmented src = new Segmented(state.spectroSource, srcOpts);

		Label histLbl = new Label("History");
		histLbl.getStyleClass().add("row-lbl");
		Map<Integer, String> histOpts = new LinkedHashMap<>();
		histOpts.put(10, "10 s");
		histOpts.put(30, "30 s");
		histOpts.put(60, "60 s");
		Segmented hist = new Segmented(state.spectroHistory, histOpts);

		AppState defaults = new AppState();
		Label rangeLbl = new Label("Range");
		rangeLbl.getStyleClass().add("row-lbl");
		Trim floor = new Trim(state.spectroFloor, AppState.SPECTRO_FLOOR_MIN, AppState.SPECTRO_FLOOR_MAX,
			SPECTRO_RANGE_STEP, defaults.spectroFloor.get(), true);
		Trim ceiling = new Trim(state.spectroCeiling, AppState.SPECTRO_CEILING_MIN,
			AppState.SPECTRO_CEILING_MAX, SPECTRO_RANGE_STEP, defaults.spectroCeiling.get(), true);
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox rangeRow = new HBox(floor, sp, ceiling);
		rangeRow.setAlignment(Pos.CENTER_LEFT);

		Region key = new Region();
		key.getStyleClass().add("spectro-key");
		key.setStyle("-fx-background-color: " + colourKeyGradient() + ";");
		Label diffHint = new Label("Mic−Solo spans ±" + Math.round(Spectrograph.DIFF_RANGE)
			+ " dB, dark where the mic is quieter.");
		diffHint.getStyleClass().add("hint");
		diffHint.setWrapText(true);

		VBox spectroOpts = new VBox(8, srcLbl, src, histLbl, hist, rangeLbl, rangeRow, key, diffHint);
		VBox sec = section("View", view, spectroOpts);

		Runnable vis = () -> {
			boolean shown = state.view.get() != AppState.VIEW_RTA;
			spectroOpts.setVisible(shown);
			spectroOpts.setManaged(shown);
			// The level range means nothing for a difference, which has its
			// own.
			boolean diff = state.spectroSource.get() == AppState.SPECTRO_DIFF;
			rangeRow.setDisable(diff);
			diffHint.setVisible(diff);
			diffHint.setManaged(diff);
		};
		state.view.addListener((o, a, b) -> vis.run());
		state.spectroSource.addListener((o, a, b) -> vis.run());
		vis.run();
		return sec;
	}

	/** The spectrograph's colour map as a CSS gradient, for its key. */
	private static String colourKeyGradient()
	{
		StringBuilder css = new StringBuilder("linear-gradient(to right");
		int steps = 12;
		for (int i = 0; i <= steps; i++)
		{
			javafx.scene.paint.Color c = Spectrograph.color(i / (double)steps);
			css.append(String.format(java.util.Locale.US, ", rgb(%d,%d,%d) %d%%",
				Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255),
				Math.round(100.0 * i / steps)));
		}
		return css.append(")").toString();
	}

	// ---- Overlays ---------------------------------------------------------
	private VBox overlaysSection()
	{
		// peak hold row with conditional reset button
		Label phLbl = new Label("Peak hold");
		phLbl.getStyleClass().add("ctl-lbl");
		Region s1 = new Region();
		HBox.setHgrow(s1, Priority.ALWAYS);
		Button reset = new Button("Reset");
		reset.getStyleClass().add("mini-btn");
		reset.setOnAction(e -> state.holdReset.set(state.holdReset.get() + 1));
		Toggle phToggle = new Toggle(state.peakHold, "#a3e635");
		HBox phRight = new HBox(9, reset, phToggle);
		phRight.setAlignment(Pos.CENTER_RIGHT);
		HBox phRow = new HBox(phLbl, s1, phRight);
		phRow.setAlignment(Pos.CENTER_LEFT);
		Runnable phVis = () -> {
			reset.setVisible(state.peakHold.get());
			reset.setManaged(state.peakHold.get());
		};
		state.peakHold.addListener((o, a, b) -> phVis.run());
		phVis.run();

		HBox markerRow = ctlRow("Peak markers", new Toggle(state.markers, "#a3e635"));
		HBox tfRow = ctlRow("Transfer fn  (Mic−Solo)", new Toggle(state.showTransfer, "#b794f6"));

		return section("Overlays", phRow, markerRow, tfRow);
	}

	private HBox ctlRow(String label, javafx.scene.Node control)
	{
		Label l = new Label(label);
		l.getStyleClass().add("ctl-lbl");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox row = new HBox(l, sp, control);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	// ---- Reference capture ------------------------------------------------
	private VBox referenceSection()
	{
		VBox body = new VBox(9);
		Label hint = new Label("Dashed ghost of both traces — A/B before vs after EQ.");
		hint.getStyleClass().add("hint");
		VBox sec = section("Reference capture", body, hint);

		Runnable rebuild = () -> {
			body.getChildren().clear();
			Draw.Reference ref = state.reference.get();
			if (ref == null)
			{
				Button capture = new Button("⊕  Capture reference");
				capture.getStyleClass().addAll("btn", "btn-capture");
				capture.setMaxWidth(Double.MAX_VALUE);
				capture.setOnAction(e -> doCapture());
				body.getChildren().add(capture);
			}
			else
			{
				Label info = new Label("Snapshot " + ref.time());
				info.getStyleClass().add("row-lbl");
				Button hideShow = miniBtn(state.showReference.get() ? "Hide" : "Show",
					e -> state.showReference.set(!state.showReference.get()));
				Button recap = miniBtn("Re-cap", e -> doCapture());
				Button clear = miniBtn("Clear", e -> state.reference.set(null));
				HBox actions = new HBox(7, hideShow, recap, clear);
				body.getChildren().addAll(info, actions);
			}
		};
		state.reference.addListener((o, a, b) -> rebuild.run());
		state.showReference.addListener((o, a, b) -> rebuild.run());
		rebuild.run();
		return sec;
	}

	private void doCapture()
	{
		state.captureNonce.set(state.captureNonce.get() + 1);
	}

	// ---- Ring-out assist --------------------------------------------------
	private Label detectHint;

	private VBox ringoutSection()
	{
		Label copy = new Label("Find what the live mic is ringing at and get the GEQ band to cut.");
		copy.getStyleClass().add("hint");
		copy.setWrapText(true);
		detectHint = new Label();
		detectHint.getStyleClass().add("hint");
		detectHint.setWrapText(true);
		Button detect = new Button("⌖  Detect ring from mic");
		detect.getStyleClass().addAll("btn", "btn-detect");
		detect.setMaxWidth(Double.MAX_VALUE);
		detect.setOnAction(e -> detectRing());
		VBox controls = new VBox(10, detect, detectHint);
		VBox log = new VBox(6);
		VBox sec = section("Ring-out assist", copy, controls, log);

		// Detection only works against a live mic, so the section only shows
		// with one.
		Runnable showForLiveMic = () -> {
			boolean live = state.isMicLive();
			sec.setVisible(live);
			sec.setManaged(live);
			detectHint.setText("");
		};
		state.micChan.addListener((o, a, b) -> showForLiveMic.run());

		Runnable rebuildLog = () -> {
			log.getChildren().clear();
			if (state.feedbackLog.isEmpty()) return;
			Label head = new Label("Feedback log");
			head.getStyleClass().add("sec-title");
			Button clearAll = miniBtn("Clear all", e -> {
				state.feedbackLog.clear();
				state.locateFreq.set(null);
			});
			Region sp = new Region();
			HBox.setHgrow(sp, Priority.ALWAYS);
			HBox header = new HBox(head, sp, clearAll);
			header.setAlignment(Pos.CENTER_LEFT);
			log.getChildren().add(header);
			for (FeedbackEntry en : state.feedbackLog)
				log.getChildren().add(fbRow(en));
		};

		state.feedbackLog.addListener((javafx.collections.ListChangeListener<FeedbackEntry>)c -> rebuildLog.run());
		state.locateFreq.addListener((o, a, b) -> rebuildLog.run());
		showForLiveMic.run();
		rebuildLog.run();
		return sec;
	}

	private HBox fbRow(FeedbackEntry en)
	{
		Label freq = new Label(Engine.fmtFreq(en.freq()));
		freq.getStyleClass().add("fb-freq");
		Label note = new Label(en.note());
		note.getStyleClass().add("fb-note");
		VBox left = new VBox(2, freq, note);
		left.setMinWidth(64);
		Label eq = new Label("GEQ " + Engine.fmtShort(en.band()) + " · −" + en.cut() + " dB");
		eq.getStyleClass().add("fb-eq");
		eq.setMaxWidth(Double.MAX_VALUE);
		eq.setAlignment(Pos.CENTER_RIGHT);
		HBox.setHgrow(eq, Priority.ALWAYS);
		Button x = new Button("×");
		x.getStyleClass().add("mini-btn");
		x.setOnAction(e -> removeFb(en));
		HBox row = new HBox(8, left, eq, x);
		row.getStyleClass().add("fb-row");
		row.setAlignment(Pos.CENTER_LEFT);
		row.setStyle("-fx-border-width:0 0 0 2; -fx-border-color:#ff5b60;");
		boolean on = state.locateFreq.get() != null && state.locateFreq.get() == en.freq();
		if (on) row.getStyleClass().add("on");
		row.setOnMouseClicked(e -> toggleLocate(en.freq()));
		return row;
	}

	/**
	 * Ask TarsosDSP's YIN what the live mic is ringing at and log that, instead
	 * of the band centre the peak markers would round to.
	 */
	private void detectRing()
	{
		AudioSource mic = state.micSource;
		AudioDsp.Pitch p = mic != null ? mic.readPitch() : null;
		if (p == null)
		{
			detectHint.setText("No steady tone on the mic right now.");
			return;
		}
		detectHint.setText(String.format(java.util.Locale.US,
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
		while (state.feedbackLog.size() > 6)
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

	private void removeFb(FeedbackEntry en)
	{
		if (state.locateFreq.get() != null && state.locateFreq.get() == en.freq()) state.locateFreq.set(null);
		state.feedbackLog.remove(en);
	}

	private void toggleLocate(double freq)
	{
		Double cur = state.locateFreq.get();
		state.locateFreq.set(cur != null && cur == freq ? null : freq);
	}

	private static double log2(double x)
	{
		return Math.log(x) / Math.log(2);
	}

	// ---- helpers ----------------------------------------------------------
	private VBox section(String title, javafx.scene.Node... children)
	{
		Label t = new Label(title.toUpperCase());
		t.getStyleClass().add("sec-title");
		VBox sec = new VBox(8, t);
		sec.getChildren().addAll(children);
		sec.getStyleClass().add("section");
		return sec;
	}

	private String footText()
	{
		return "FOHanalyzer " + Version.VALUE + " · " + state.signalStatus();
	}

	private VBox railFoot()
	{
		Label f = new Label(footText());
		f.getStyleClass().add("rail-foot");
		// A version is as long as it wants to be — "2.4.0-SNAPSHOT" against the
		// two-source status already overruns the 298px column — so let the line
		// wrap rather than ellipsise the status away.
		f.setWrapText(true);
		f.setMaxWidth(Double.MAX_VALUE);
		f.setTextAlignment(TextAlignment.CENTER);
		state.micChan.addListener((o, a, b) -> f.setText(footText()));
		state.soloChan.addListener((o, a, b) -> f.setText(footText()));

		Button prefs = new Button("⚙  Preferences…");
		prefs.getStyleClass().addAll("btn", "btn-prefs");
		prefs.setMaxWidth(Double.MAX_VALUE);
		prefs.setOnAction(e -> showPreferences());

		Button resetAll = miniBtn("Reset saved settings", e -> confirmReset());
		VBox box = new VBox(10, prefs, f, resetAll);
		box.setAlignment(Pos.CENTER);
		VBox.setVgrow(box, Priority.ALWAYS);
		box.setStyle("-fx-padding:16 0 14 0;");
		return box;
	}

	/** Opens the preferences window over this rail's window. */
	public void showPreferences()
	{
		preferences.show(getScene() != null ? getScene().getWindow() : null);
	}

	/**
	 * Confirm before discarding stored settings — SPL calibration is the
	 * expensive one, and an accidental click would mean re-measuring against a
	 * reference source.
	 */
	private void confirmReset()
	{
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
			"Discard saved settings and return to defaults?\n\n"
				+ "This clears the SPL calibration, the solo trim, the selected inputs and the analysis options.",
			ButtonType.CANCEL, ButtonType.OK);
		alert.setHeaderText("Reset saved settings");
		alert.initOwner(getScene() != null ? getScene().getWindow() : null);
		alert.showAndWait()
			.filter(b -> b == ButtonType.OK)
			.ifPresent(b -> settings.reset(state));
	}

	private Button miniBtn(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action)
	{
		Button b = new Button(text);
		b.getStyleClass().add("mini-btn");
		b.setOnAction(action);
		return b;
	}
}
