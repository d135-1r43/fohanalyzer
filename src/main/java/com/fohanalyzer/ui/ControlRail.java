package com.fohanalyzer.ui;

import com.fohanalyzer.Version;
import com.fohanalyzer.dsp.LeqMeter;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.ui.controls.SourceCard;
import com.fohanalyzer.ui.controls.Trim;
import javafx.application.HostServices;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.nio.file.Path;
import java.util.Locale;

/**
 * The rail on the right: everything there is to read, once, and nothing to set.
 *
 * <p>
 * Settings live in the preferences window and the actions pressed during a show
 * in the {@link ActionStrip} above the plot. What a setting changes is shown
 * here as text — the solo trim, the Leq window, the resolution — so the rail
 * still says what the plot and the meter are computed with. The one thing that
 * reacts to a click is a logged ring, which locates it on the plot.
 */
public final class ControlRail extends ScrollPane
{
	private final AppState state;
	private final PreferencesWindow preferences;

	private SourceCard micCard;
	private SourceCard soloCard;

	public ControlRail(AppState state, Settings settings, HostServices host)
	{
		this.state = state;
		this.preferences = new PreferencesWindow(state, settings, host);
		getStyleClass().addAll("scroll-pane", "control-rail");
		setFitToWidth(true);
		setHbarPolicy(ScrollBarPolicy.NEVER);

		VBox rail = new VBox();
		rail.getStyleClass().add("rail");
		rail.getChildren().addAll(
			sourcesSection(),
			splSection(),
			ringsSection(),
			inEffectSection(),
			railFoot());

		state.stats.addListener((o, a, st) -> onStats(st));
		onStats(state.stats.get());

		setContent(rail);
	}

	// ---- Sources ----------------------------------------------------------
	private VBox sourcesSection()
	{
		micCard = new SourceCard("Measurement Mic", "#22d3ee", state.micOn, AppState.MIC_INPUTS,
			state.micChan, state.micChanIdx, state.micChanCount, state.audioDevices, null);
		micCard.detailLeft.setText("loudest band");
		micCard.detailRight.getStyleClass().add("src-detail-peak");

		soloCard = new SourceCard("Solo Bus", "#f5a524", state.soloOn, AppState.SOLO_INPUTS,
			state.soloChan, state.soloChanIdx, state.soloChanCount, state.audioDevices, state.soloStereo);
		// An offset trace must not pass for the bus's own level.
		Runnable trim = () -> {
			double t = state.soloTrim.get();
			soloCard.detailLeft.setText(t != 0 ? "trim " + Trim.fmtDb(t) : "no trim");
			soloCard.detailLeft.getStyleClass().remove("src-detail-on");
			if (t != 0) soloCard.detailLeft.getStyleClass().add("src-detail-on");
		};
		state.soloTrim.addListener((o, a, b) -> trim.run());
		trim.run();
		Runnable markers = () -> soloCard.detailRight.setText(
			"markers follow " + ("solo".equals(state.markerSource.get()) ? "solo" : "mic"));
		state.markerSource.addListener((o, a, b) -> markers.run());
		markers.run();

		return section("Sources", micCard, soloCard);
	}

	private void onStats(Stats st)
	{
		micCard.meter.setValue(st.micAvg());
		micCard.level.setText(fmt(st.micPeak()));
		micCard.detailRight.setText(Engine.fmtFreq(st.peakFreq()));
		soloCard.meter.setValue(st.soloAvg());
		soloCard.level.setText(fmt(st.soloPeak()));
		if (splValue != null) updateSpl();
	}

	// ---- SPL --------------------------------------------------------------
	private Label splValue;
	private Label splUnit;
	private Label splBadge;
	private Label splFast;
	private Label splMax;
	private Label splPeak;
	private ProgressBar splProgressBar;
	private Label splProgress;

	private VBox splSection()
	{
		splValue = new Label("—");
		splValue.getStyleClass().add("spl-val");
		splUnit = new Label();
		splUnit.getStyleClass().add("spl-unit");
		HBox readout = new HBox(8, splValue, splUnit);
		readout.setAlignment(Pos.BASELINE_LEFT);
		HBox.setHgrow(readout, Priority.ALWAYS);
		splBadge = new Label("UNCAL");
		splBadge.getStyleClass().add("spl-badge");
		HBox top = new HBox(readout, splBadge);
		top.setAlignment(Pos.CENTER_LEFT);

		// The live level, and the loudest moments since a reset: Fast catches
		// what the ear hears as loud, the peak what a limiter would.
		String w = LeqMeter.WEIGHTING;
		splFast = new Label("—");
		splMax = new Label("—");
		splPeak = new Label("—");
		HBox cells = new HBox(6, splCell("L" + w + "F", splFast), splCell("L" + w + "Fmax", splMax),
			splCell("L" + w + "peak", splPeak));

		splProgressBar = new ProgressBar(0);
		splProgressBar.getStyleClass().add("spl-prog");
		splProgressBar.setMaxWidth(Double.MAX_VALUE);
		splProgress = new Label();
		splProgress.getStyleClass().add("spl-progress");

		Label logging = new Label();
		logging.getStyleClass().add("hint");
		logging.setWrapText(true);
		Runnable logLine = () -> {
			String file = state.splLogFile.get();
			logging.setVisible(file != null);
			logging.setManaged(file != null);
			if (file != null) logging.setText("● logging " + Path.of(file).getFileName());
		};
		state.splLogFile.addListener((o, a, b) -> logLine.run());
		logLine.run();

		VBox display = new VBox(8, top, cells, splProgressBar, splProgress, logging);
		display.getStyleClass().add("spl-display");
		VBox sec = section("SPL Meter", display);

		// Only a live mic has a level worth metering, and only if it is wanted.
		Runnable vis = () -> {
			boolean show = state.isMicLive() && state.showSpl.get();
			sec.setVisible(show);
			sec.setManaged(show);
		};
		state.micChan.addListener((o, a, b) -> vis.run());
		state.showSpl.addListener((o, a, b) -> vis.run());
		state.splOffset.addListener((o, a, b) -> updateSpl());
		state.leqWindow.addListener((o, a, b) -> updateSpl());
		vis.run();
		updateSpl();
		return sec;
	}

	private static VBox splCell(String label, Label value)
	{
		Label l = new Label(label);
		l.getStyleClass().add("spl-cell-lbl");
		value.getStyleClass().add("spl-cell-val");
		VBox cell = new VBox(1, l, value);
		cell.getStyleClass().add("spl-cell");
		cell.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(cell, Priority.ALWAYS);
		return cell;
	}

	/** The Leq, Fast, max and peak, from the meter as it stands. */
	private void updateSpl()
	{
		int window = state.leqWindow.get();
		LeqMeter.Reading r = state.leq.reading(window);
		double off = state.splOffset.get();
		boolean cal = off != 0;

		// Uncalibrated it is dBFS, and says so rather than pass for SPL.
		splValue.setText(fmtLevel(r.leqDb(), off));
		splUnit.setText((cal ? "dB " : "dBFS ") + AppState.leqLabel(window));
		splBadge.setText(cal ? "CAL" : "UNCAL");
		splBadge.getStyleClass().remove("spl-badge-cal");
		if (cal) splBadge.getStyleClass().add("spl-badge-cal");
		splFast.setText(fmtLevel(r.fastDb(), off));
		splMax.setText(fmtLevel(r.maxDb(), off));
		splPeak.setText(fmtLevel(r.peakDb(), off));
		splProgressBar.setProgress(Math.min(1, r.integratedSeconds() / window));
		splProgress.setText(r.isFilled()
			? "rolling over the last " + fmtDuration(window)
			: "integrating " + fmtDuration(r.integratedSeconds()) + " / " + fmtDuration(window));
	}

	// ---- Rings ------------------------------------------------------------
	private VBox ringsSection()
	{
		Label status = new Label();
		status.getStyleClass().add("hint");
		status.setWrapText(true);
		status.textProperty().bind(state.detectStatus);
		status.visibleProperty().bind(state.detectStatus.isNotEmpty());
		status.managedProperty().bind(status.visibleProperty());

		Label empty = new Label("No rings logged. Detect ring above the plot logs what the mic is ringing at.");
		empty.getStyleClass().add("hint");
		empty.setWrapText(true);

		VBox log = new VBox(5);
		VBox sec = section("Rings", status, empty, log);
		Label clickHint = new Label("click to locate");
		clickHint.getStyleClass().add("sec-aside");
		HBox title = (HBox)sec.getChildren().getFirst();
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		title.getChildren().addAll(sp, clickHint);

		Runnable rebuild = () -> {
			log.getChildren().clear();
			for (FeedbackEntry en : state.feedbackLog)
				log.getChildren().add(ringRow(en));
			boolean none = state.feedbackLog.isEmpty();
			empty.setVisible(none);
			empty.setManaged(none);
			clickHint.setVisible(!none);
		};
		state.feedbackLog.addListener((ListChangeListener<FeedbackEntry>)c -> rebuild.run());
		state.locateFreq.addListener((o, a, b) -> rebuild.run());
		rebuild.run();

		// Rings are only ever detected from a live mic; a log left from one
		// stays readable after switching away.
		Runnable vis = () -> {
			boolean show = state.isMicLive() || !state.feedbackLog.isEmpty();
			sec.setVisible(show);
			sec.setManaged(show);
		};
		state.micChan.addListener((o, a, b) -> vis.run());
		state.feedbackLog.addListener((ListChangeListener<FeedbackEntry>)c -> vis.run());
		vis.run();
		return sec;
	}

	private HBox ringRow(FeedbackEntry en)
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
		HBox.setHgrow(eq, Priority.ALWAYS);
		Label time = new Label(en.time());
		time.getStyleClass().add("fb-note");
		HBox row = new HBox(8, left, eq, time);
		row.getStyleClass().add("fb-row");
		row.setAlignment(Pos.CENTER_LEFT);
		row.setStyle("-fx-border-width:0 0 0 2; -fx-border-color:#ff5b60;");
		boolean on = state.locateFreq.get() != null && state.locateFreq.get() == en.freq();
		if (on) row.getStyleClass().add("on");
		row.setOnMouseClicked(e -> {
			Double cur = state.locateFreq.get();
			state.locateFreq.set(cur != null && cur == en.freq() ? null : en.freq());
		});
		return row;
	}

	// ---- In effect --------------------------------------------------------
	/**
	 * What the plot is computed with right now, so nobody has to open the
	 * preferences to find out why it looks the way it does.
	 */
	private VBox inEffectSection()
	{
		Label reference = kvValue();
		Label resolution = kvValue();
		Label smoothing = kvValue();
		Label spectro = kvValue();

		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(4);
		ColumnConstraints keys = new ColumnConstraints();
		ColumnConstraints values = new ColumnConstraints();
		values.setHgrow(Priority.ALWAYS);
		values.setHalignment(javafx.geometry.HPos.RIGHT);
		grid.getColumnConstraints().addAll(keys, values);
		addKv(grid, 0, "Reference", reference);
		addKv(grid, 1, "Resolution", resolution);
		addKv(grid, 2, "Smoothing · avg", smoothing);
		addKv(grid, 3, "Spectrograph", spectro);

		Runnable refresh = () -> {
			Draw.Reference ref = state.reference.get();
			reference.setText(ref == null ? "none"
				: ref.time() + " · " + (state.showReference.get() ? "shown" : "hidden"));
			resolution.setText("1/" + state.frac.get() + " oct");
			int avg = state.avgN.get();
			smoothing.setText(Math.round(state.smoothing.get() * 100) + " % · " + (avg > 1 ? avg : "off"));
			String src = switch (state.spectroSource.get())
			{
				case AppState.SPECTRO_SOLO -> "Solo";
				case AppState.SPECTRO_DIFF -> "Mic−Solo";
				default -> "Mic";
			};
			spectro.setText(src + " · " + state.spectroHistory.get() + " s");
		};
		state.reference.addListener((o, a, b) -> refresh.run());
		state.showReference.addListener((o, a, b) -> refresh.run());
		state.frac.addListener((o, a, b) -> refresh.run());
		state.smoothing.addListener((o, a, b) -> refresh.run());
		state.avgN.addListener((o, a, b) -> refresh.run());
		state.spectroSource.addListener((o, a, b) -> refresh.run());
		state.spectroHistory.addListener((o, a, b) -> refresh.run());
		refresh.run();

		return section("In effect", grid);
	}

	private static Label kvValue()
	{
		Label l = new Label();
		l.getStyleClass().add("row-val");
		return l;
	}

	private static void addKv(GridPane grid, int row, String key, Label value)
	{
		Label k = new Label(key);
		k.getStyleClass().add("row-lbl");
		grid.add(k, 0, row);
		grid.add(value, 1, row);
	}

	// ---- foot -------------------------------------------------------------
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

		Button prefs = new Button("⚙  Preferences…  ⌘,");
		prefs.getStyleClass().addAll("btn", "btn-prefs");
		prefs.setMaxWidth(Double.MAX_VALUE);
		prefs.setOnAction(e -> showPreferences());

		VBox box = new VBox(10, prefs, f);
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

	// ---- helpers ----------------------------------------------------------
	private static VBox section(String title, Node... children)
	{
		Label t = new Label(title.toUpperCase());
		t.getStyleClass().add("sec-title");
		HBox head = new HBox(t);
		head.setAlignment(Pos.CENTER_LEFT);
		VBox sec = new VBox(8, head);
		sec.getChildren().addAll(children);
		sec.getStyleClass().add("section");
		return sec;
	}

	private static String fmt(double db)
	{
		return Double.isFinite(db) ? String.format(Locale.US, "%.1f", db) : "—";
	}

	private static String fmtLevel(double dbfs, double offset)
	{
		return Double.isFinite(dbfs) ? String.format(Locale.US, "%.1f", dbfs + offset) : "—";
	}

	/** {@code 192 -> "03:12"}, {@code 3600 -> "60:00"}. */
	static String fmtDuration(double seconds)
	{
		long s = (long)Math.floor(seconds);
		return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
	}
}
