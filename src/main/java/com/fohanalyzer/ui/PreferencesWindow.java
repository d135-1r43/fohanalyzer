package com.fohanalyzer.ui;

import com.fohanalyzer.dsp.SplLimit;
import com.fohanalyzer.mcp.McpHttpServer;
import com.fohanalyzer.ui.controls.ChannelSelect;
import com.fohanalyzer.ui.controls.Segmented;
import com.fohanalyzer.ui.controls.Toggle;
import com.fohanalyzer.ui.controls.Trim;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.collections.ListChangeListener;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every setting the app has: which device feeds each source and the solo trim,
 * the SPL calibration and Leq window, how the plot is drawn and computed, the
 * MCP server an AI assistant can read the analyzer through, and resetting it
 * all.
 *
 * <p>
 * The rail only shows and the strip above the plot only acts; anything that is
 * a setting is here, one page per area. Everything binds straight to
 * {@link AppState}, which {@link Settings} already persists — closing the
 * window is not a commit step, and there is nothing to cancel.
 */
public final class PreferencesWindow
{
	private static final Logger log = LoggerFactory.getLogger(PreferencesWindow.class);

	/**
	 * The window is not resizable: a dragged-in edge hides controls with no
	 * clue that they are there. So this is the one width the layout is drawn
	 * for, and the height is always whatever the content needs at that width.
	 */
	private static final double WIDTH = 420;
	private static final double NAV_WIDTH = 140;

	/** dB a click or scroll moves the spectrograph's colour range. */
	private static final double SPECTRO_RANGE_STEP = 5;

	private static final String[] PAGES = { "Sources", "SPL meter", "Display", "Analysis", "AI assistant",
		"General" };

	private static final int CLAUDE_CODE = 0;
	private static final int CLAUDE_DESKTOP = 1;
	private static final int CHATGPT = 2;

	private final AppState state;
	private final Settings settings;
	private final HostServices host;
	private Stage stage;
	private VBox body;
	/**
	 * The page shown, kept while the app runs so reopening lands on it again.
	 */
	private int page;

	public PreferencesWindow(AppState state, Settings settings, HostServices host)
	{
		this.state = state;
		this.settings = settings;
		this.host = host;
	}

	/** Opens the window, or brings it forward if it is already up. */
	public void show(Window owner)
	{
		if (stage != null)
		{
			stage.toFront();
			stage.requestFocus();
			return;
		}

		// No size on the scene — sizeToScene() below measures the content
		// instead. A number written down here would be wrong the moment a hint
		// wraps onto another line or the theme changes a padding.
		Scene scene = new Scene(buildRoot(), Color.web("#070a0f"));
		scene.getStylesheets().add(
			MainApp.class.getResource("/com/fohanalyzer/theme.css").toExternalForm());

		stage = new Stage();
		stage.setTitle("FOHanalyzer · Preferences");
		stage.initOwner(owner);
		stage.initModality(Modality.NONE);
		stage.setResizable(false);
		stage.setScene(scene);
		stage.sizeToScene();

		// Only the position is restored now that the size is not the user's to
		// choose. Restore onto the stage, not the scene: what was saved is the
		// outer window, and a scene is the inner area. Feeding one into the
		// other adds the title bar again every launch, and the window creeps
		// taller.
		Settings.WindowBounds saved = settings.prefsWindow();
		if (saved != null && onAScreen(saved))
		{
			stage.setX(saved.x());
			stage.setY(saved.y());
		}
		stage.show();

		// The channel stepper and the mono/stereo row only exist for a
		// multi-channel live input, and each page has its own height, so the
		// content changes height when a source is picked or a page is chosen.
		// Nothing can be dragged bigger any more, so the window has to
		// follow it. Deferred, to keep the resize out of the layout pass that
		// asked for it.
		body.heightProperty().addListener((o, a, b) -> Platform.runLater(() -> {
			if (stage != null) stage.sizeToScene();
		}));

		// Written as they change and flushed when the window goes away — a drag
		// fires these continuously, and flushing each frame would be silly.
		Runnable remember = () -> settings.putPrefsWindow(
			stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
		stage.xProperty().addListener((o, a, b) -> remember.run());
		stage.yProperty().addListener((o, a, b) -> remember.run());
		stage.setOnHidden(e -> {
			remember.run();
			settings.flush();
			stage = null;
		});
	}

	/** Which page to open on, before {@link #show}. */
	void selectPage(int index)
	{
		page = Math.clamp(index, 0, PAGES.length - 1);
	}

	/** The window's content while it is open, else null. */
	javafx.scene.Parent root()
	{
		return stage != null ? stage.getScene().getRoot() : null;
	}

	/**
	 * Whether the stored position still lands on a display. A window remembered
	 * on a second monitor would otherwise reopen off in space when that monitor
	 * is gone, which is the normal case for a laptop that gets carried to a
	 * different rig.
	 */
	private static boolean onAScreen(Settings.WindowBounds b)
	{
		return !Screen.getScreensForRectangle(b.x(), b.y(), b.w(), b.h()).isEmpty();
	}

	/**
	 * The window's contents, separate from staging them, so the layout can be
	 * built and measured without a {@link Stage}.
	 */
	VBox buildRoot()
	{
		VBox[] pages = { sourcesSection(), splSection(), displaySection(), analysisSection(), mcpSection(),
			generalSection() };
		body = new VBox();
		body.getStyleClass().add("prefs-body");
		// Width is the fixed one: a wrapped hint only has a height once it
		// knows what to wrap into, so pinning the width is what makes the
		// height measurable at all.
		body.setMinWidth(WIDTH);
		body.setPrefWidth(WIDTH);
		body.setMaxWidth(WIDTH);

		VBox nav = new VBox(2);
		nav.getStyleClass().add("prefs-nav");
		nav.setMinWidth(NAV_WIDTH);
		nav.setPrefWidth(NAV_WIDTH);
		Button[] navButtons = new Button[PAGES.length];
		Runnable showPage = () -> {
			body.getChildren().setAll(pages[page]);
			for (int i = 0; i < navButtons.length; i++)
			{
				navButtons[i].getStyleClass().remove("on");
				if (i == page) navButtons[i].getStyleClass().add("on");
			}
		};
		for (int i = 0; i < PAGES.length; i++)
		{
			int index = i;
			Button b = new Button(PAGES[i]);
			b.getStyleClass().add("prefs-nav-btn");
			b.setMaxWidth(Double.MAX_VALUE);
			b.setOnAction(e -> {
				page = index;
				showPage.run();
			});
			navButtons[i] = b;
			nav.getChildren().add(b);
		}
		showPage.run();

		HBox main = new HBox(nav, body);

		Button close = new Button("Done");
		close.getStyleClass().addAll("btn", "mini-btn");
		close.setOnAction(e -> {
			if (stage != null) stage.close();
		});
		HBox foot = new HBox(close);
		foot.setAlignment(Pos.CENTER_RIGHT);
		foot.getStyleClass().add("prefs-foot");

		VBox root = new VBox(main, foot);
		root.getStyleClass().add("prefs-root");
		return root;
	}

	// ---- Sources ----------------------------------------------------------
	private VBox sourcesSection()
	{
		return section("Sources",
			sourceRow("Measurement Mic", "#22d3ee", AppState.MIC_INPUTS,
				state.micChan, state.micChanIdx, state.micChanCount, null),
			sourceRow("Solo Bus", "#f5a524", AppState.SOLO_INPUTS,
				state.soloChan, state.soloChanIdx, state.soloChanCount, state.soloStereo),
			hint("Live inputs are listed first, simulated presets below. A"
				+ " multi-channel interface adds a channel stepper. Set the solo"
				+ " bus to Stereo to read the selected channel and the one above"
				+ " it as a pair, merged to one trace."),
			soloTrimRow(),
			hint("The mic and the solo bus arrive at whatever level the console gives them."
				+ " The trim shifts the solo trace onto the mic's, so the two can be compared"
				+ " by eye and the transfer curve's 0 dB line means something. Match lines up"
				+ " the averages."));
	}

	private HBox soloTrimRow()
	{
		Label lbl = new Label("Solo trim");
		lbl.getStyleClass().add("ctl-lbl");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Trim trim = new Trim(state.soloTrim, -AppState.SOLO_TRIM_MAX, AppState.SOLO_TRIM_MAX, true);
		Button match = miniBtn("Match", e -> {
			double t = Math.round(state.stats.get().matchingSoloTrim() * 10) / 10.0;
			state.soloTrim.set(Math.clamp(t, -AppState.SOLO_TRIM_MAX, AppState.SOLO_TRIM_MAX));
		});
		match.setTooltip(new Tooltip("Set the trim from the average Mic−Solo difference"));
		HBox row = new HBox(6, lbl, sp, trim, match);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private VBox sourceRow(String name, String color, java.util.List<InputPreset> options,
		javafx.beans.property.StringProperty chan,
		javafx.beans.property.IntegerProperty chanIdx,
		javafx.beans.property.IntegerProperty chanCount,
		javafx.beans.property.BooleanProperty stereo)
	{
		Label lbl = new Label(name);
		lbl.getStyleClass().add("ctl-lbl");
		lbl.setStyle("-fx-text-fill:" + color + ";");
		ChannelSelect select = new ChannelSelect(chan, options, state.audioDevices, color,
			chanIdx, chanCount, stereo);
		return new VBox(7, lbl, select);
	}

	// ---- SPL calibration --------------------------------------------------
	private VBox splSection()
	{
		TextField ref = new TextField(fmtRef(state.calRefSpl.get()));
		ref.getStyleClass().add("spl-input");
		ref.setPrefWidth(68);
		ref.textProperty().addListener((o, a, b) -> {
			try
			{
				state.calRefSpl.set(Double.parseDouble(b));
			}
			catch (NumberFormatException ignored)
			{
			}
		});
		// Keep the field in step when the value arrives from somewhere else — a
		// restored setting or a reset — without fighting the user's own typing.
		state.calRefSpl.addListener((o, a, b) -> {
			String text = fmtRef(b.doubleValue());
			if (!text.equals(ref.getText().trim())) ref.setText(text);
		});

		Label refLbl = new Label("dB SPL ref");
		refLbl.getStyleClass().add("hint");
		HBox.setHgrow(refLbl, Priority.ALWAYS);
		Button calBtn = new Button("Calibrate");
		calBtn.getStyleClass().add("mini-btn");
		calBtn.setOnAction(e -> calibrate());
		Button clearBtn = new Button("Clear");
		clearBtn.getStyleClass().add("mini-btn");
		clearBtn.setOnAction(e -> state.clearCalibration());
		HBox calRow = new HBox(8, ref, refLbl, calBtn, clearBtn);
		calRow.setAlignment(Pos.CENTER_LEFT);

		// The input in use and its calibration: an input that has none says
		// so, rather than borrowing another rig's offset.
		Label inputLbl = new Label();
		inputLbl.getStyleClass().add("ctl-lbl");
		inputLbl.setMaxWidth(Double.MAX_VALUE);
		Label calState = new Label();
		calState.getStyleClass().add("cal-state");

		// For when the reading drifts from a hand-held meter. It moves the
		// input's calibration itself, so there is one number, not two that
		// stack; an uncalibrated input has nothing to nudge.
		Label nudgeLbl = new Label("Offset");
		nudgeLbl.getStyleClass().add("ctl-lbl");
		Tooltip.install(nudgeLbl, new Tooltip("Nudge it to match a reference meter"));
		Region nudgeSp = new Region();
		HBox.setHgrow(nudgeSp, Priority.ALWAYS);
		HBox nudgeRow = new HBox(nudgeLbl, nudgeSp,
			new Trim(state.splOffset, Settings.MIN_SPL_OFFSET, Settings.MAX_SPL_OFFSET, Trim.STEP, Double.NaN, Trim::fmtDb));
		nudgeRow.setAlignment(Pos.CENTER_LEFT);

		Runnable refreshInput = () -> {
			Calibration cal = state.micCalibration();
			String id = state.micDeviceId();
			inputLbl.setText(id == null ? "Simulated input"
				: deviceName(id) + " · Ch " + (state.micChanIdx.get() + 1));
			calState.getStyleClass().removeAll("cal-none", "cal-stale");
			if (cal == null)
			{
				calState.setText("not calibrated · the readout shows dBFS");
				calState.getStyleClass().add("cal-none");
			}
			else
			{
				boolean stale = cal.isStale(LocalDate.now());
				String against = cal.refText();
				calState.setText((stale ? "⚠ " : "") + "calibrated " + cal.dateText()
					+ (against.isEmpty() ? "" : " · " + against));
				if (stale) calState.getStyleClass().add("cal-stale");
			}
			nudgeRow.setDisable(cal == null);
			clearBtn.setDisable(cal == null);
		};
		state.calibrations.addListener((ListChangeListener<Calibration>)c -> refreshInput.run());
		state.micChan.addListener((o, a, b) -> refreshInput.run());
		state.micChanIdx.addListener((o, a, b) -> refreshInput.run());
		refreshInput.run();

		Label howTo = hint("Play a known reference level, enter it, then tap Calibrate.");

		// Says why the controls are off, since a disabled row alone reads as
		// broken rather than as waiting for a live mic.
		Label needsLive = hint("Choose a live measurement mic above to calibrate — a"
			+ " simulated preset has no real level to calibrate against.");
		needsLive.getStyleClass().add("prefs-notice");

		// Not tied to a live mic like the calibration row: hiding the meter is
		// a preference, and it should be reachable before the rig is patched.
		Label showLbl = new Label("Show in main window");
		showLbl.getStyleClass().add("ctl-lbl");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox showRow = new HBox(showLbl, sp, new Toggle(state.showSpl, "#a3e635"));
		showRow.setAlignment(Pos.CENTER_LEFT);

		Label winLbl = new Label("Leq window");
		winLbl.getStyleClass().add("ctl-lbl");
		Map<Integer, String> winOpts = new LinkedHashMap<>();
		for (int w : AppState.LEQ_WINDOWS)
			winOpts.put(w, w < 60 ? w + " s" : w / 60 + " m");
		Segmented window = new Segmented(state.leqWindow, winOpts);

		// The venue limit: off unless a venue has one, so the rail stays as it
		// is for everyone else.
		Label limitLbl = new Label("Venue limit");
		limitLbl.getStyleClass().add("ctl-lbl");
		Region limitSp = new Region();
		HBox.setHgrow(limitSp, Priority.ALWAYS);
		HBox limitRow = new HBox(limitLbl, limitSp, new Toggle(state.splLimitOn, "#ff5b60"));
		limitRow.setAlignment(Pos.CENTER_LEFT);

		Label limitVal = new Label();
		limitVal.getStyleClass().add("ctl-lbl");
		Region limitValSp = new Region();
		HBox.setHgrow(limitValSp, Priority.ALWAYS);
		HBox limitValRow = new HBox(8, limitVal, limitValSp, new Trim(state.splLimit, AppState.SPL_LIMIT_MIN,
			AppState.SPL_LIMIT_MAX, Trim.STEP, Double.NaN, Trim.LEVEL));
		limitValRow.setAlignment(Pos.CENTER_LEFT);
		Label limitHint = hint("");
		Runnable limitText = () -> {
			int w = state.leqWindow.get();
			limitVal.setText("Limit, " + AppState.leqLabel(w));
			limitHint.setText(state.splOffset.get() == 0
				? "Calibrate first: a limit in dB SPL means nothing against an uncalibrated meter."
				: "Held against the Leq above. Amber within " + Math.round(SplLimit.NEAR_DB)
					+ " dB of the limit, red over it; the time over resets with the Leq.");
		};
		state.leqWindow.addListener((o, a, b) -> limitText.run());
		state.splOffset.addListener((o, a, b) -> limitText.run());
		limitText.run();
		limitValRow.disableProperty().bind(state.splLimitOn.not());

		Label logsLbl = new Label("Session logs");
		logsLbl.getStyleClass().add("ctl-lbl");
		Region logsSp = new Region();
		HBox.setHgrow(logsSp, Priority.ALWAYS);
		HBox logsRow = new HBox(logsLbl, logsSp, miniBtn("Show in Finder", e -> showSplLogs()));
		logsRow.setAlignment(Pos.CENTER_LEFT);
		Label logsHint = hint("While the meter is calibrated, every second is written to a CSV in "
			+ SplLog.defaultDir() + ", one file per session.");

		VBox sec = section("SPL meter", showRow, label("Calibration · this input"), inputLbl, calState,
			needsLive, nudgeRow, calRow, howTo, label("Saved calibrations"), savedCalibrations(), winLbl,
			window, limitRow, limitValRow, limitHint, logsRow, logsHint);

		// Only a live mic produces the RMS the calibration is computed from.
		// Just the controls are disabled: the notice and the status stay
		// readable, and the theme's disabled opacity is dimming enough.
		Runnable vis = () -> {
			boolean live = state.isMicLive();
			calRow.setDisable(!live);
			howTo.setDisable(!live);
			needsLive.setVisible(!live);
			needsLive.setManaged(!live);
		};
		state.micChan.addListener((o, a, b) -> vis.run());
		vis.run();
		return sec;
	}

	/**
	 * Every input calibrated so far. Clicking one that is plugged in switches
	 * the mic to it; × forgets it, for a rig that is gone for good.
	 */
	private VBox savedCalibrations()
	{
		VBox box = new VBox(2);
		Runnable rebuild = () -> {
			box.getChildren().clear();
			if (state.calibrations.isEmpty())
			{
				box.getChildren().add(hint("None yet. Each input keeps its own once calibrated."));
				return;
			}

			GridPane grid = new GridPane();
			grid.getStyleClass().add("cal-table");
			grid.setHgap(10);
			grid.setVgap(2);
			ColumnConstraints name = new ColumnConstraints();
			name.setHgrow(Priority.ALWAYS);
			name.setMinWidth(0);
			ColumnConstraints right = new ColumnConstraints();
			right.setHalignment(javafx.geometry.HPos.RIGHT);
			grid.getColumnConstraints().addAll(name, right, right, right, new ColumnConstraints());
			String[] heads = { "Interface", "Ch", "Offset", "Calibrated" };
			for (int i = 0; i < heads.length; i++)
			{
				Label h = new Label(heads[i]);
				h.getStyleClass().add("cal-head");
				grid.add(h, i, 0);
			}

			LocalDate today = LocalDate.now();
			Calibration current = state.micCalibration();
			int row = 1;
			for (Calibration cal : state.calibrations)
			{
				boolean present = state.audioDevices.stream().anyMatch(d -> d.id().equals(cal.deviceId()));
				Label dev = calCell(cal.deviceName());
				dev.setMinWidth(0);
				Label ch = calCell(Integer.toString(cal.channel() + 1));
				Label off = calCell(Trim.fmtDb(cal.offsetDb()));
				Label when = calCell(cal.date() == null ? "—" : (cal.isStale(today) ? "⚠ " : "") + cal.dateText());
				if (cal.isStale(today)) when.getStyleClass().add("cal-stale");
				Button forget = new Button("×");
				forget.getStyleClass().addAll("mini-btn", "cal-forget");
				Tooltip.install(forget, new Tooltip("Forget this calibration"));
				forget.setOnAction(e -> state.calibrations.remove(cal));

				Label[] cells = { dev, ch, off, when };
				for (int i = 0; i < cells.length; i++)
				{
					Label c = cells[i];
					if (cal.equals(current)) c.getStyleClass().add("cal-current");
					if (!present) c.getStyleClass().add("cal-absent");
					if (present && !cal.equals(current))
					{
						c.getStyleClass().add("cal-pick");
						c.setOnMouseClicked(e -> {
							state.micChan.set("live:" + cal.deviceId());
							state.micChanIdx.set(cal.channel());
						});
					}
					grid.add(c, i, row);
				}
				grid.add(forget, 4, row);
				row++;
			}
			box.getChildren().add(grid);
			box.getChildren().add(hint("Click an input that is plugged in to switch the mic to it."
				+ " A ⚠ date is over " + Calibration.STALE_AFTER_DAYS + " days old."));
		};
		state.calibrations.addListener((ListChangeListener<Calibration>)c -> rebuild.run());
		state.micChan.addListener((o, a, b) -> rebuild.run());
		state.micChanIdx.addListener((o, a, b) -> rebuild.run());
		state.audioDevices.addListener((ListChangeListener<com.fohanalyzer.audio.AudioDevice>)c -> rebuild.run());
		rebuild.run();
		return box;
	}

	private static Label calCell(String text)
	{
		Label l = new Label(text);
		l.getStyleClass().add("cal-cell");
		return l;
	}

	private String deviceName(String id)
	{
		return state.audioDevices.stream().filter(d -> d.id().equals(id)).findFirst()
			.map(com.fohanalyzer.audio.AudioDevice::label).orElse(id);
	}

	private void showSplLogs()
	{
		String file = state.splLogFile.get();
		Path dir = file != null ? Path.of(file).getParent() : SplLog.defaultDir();
		try
		{
			// The folder only appears with the first logged second.
			Files.createDirectories(dir);
		}
		catch (IOException e)
		{
			log.warn("could not create the SPL log folder {}", dir, e);
		}
		host.showDocument(dir.toUri().toString());
	}

	// ---- Display ----------------------------------------------------------
	private VBox displaySection()
	{
		// The marker source is a string in the state; bridge it to the
		// segmented control's integer.
		IntegerProperty markerIdx = new SimpleIntegerProperty(markerIndex());
		markerIdx.addListener((o, a, b) -> state.markerSource.set(b.intValue() == 1 ? "solo" : "mic"));
		state.markerSource.addListener((o, a, b) -> markerIdx.set(markerIndex()));
		Map<Integer, String> markerOpts = new LinkedHashMap<>();
		markerOpts.put(0, "Mic");
		markerOpts.put(1, "Solo");

		Map<Integer, String> srcOpts = new LinkedHashMap<>();
		srcOpts.put(AppState.SPECTRO_MIC, "Mic");
		srcOpts.put(AppState.SPECTRO_SOLO, "Solo");
		srcOpts.put(AppState.SPECTRO_DIFF, "Mic−Solo");

		Map<Integer, String> histOpts = new LinkedHashMap<>();
		histOpts.put(10, "10 s");
		histOpts.put(30, "30 s");
		histOpts.put(60, "60 s");

		AppState defaults = new AppState();
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
		Label diffHint = hint("Mic−Solo spans ±" + Math.round(Spectrograph.DIFF_RANGE)
			+ " dB, dark where the mic is quieter; the colour range does not apply to it.");

		// The level range means nothing for a difference, which has its own.
		Runnable vis = () -> {
			boolean diff = state.spectroSource.get() == AppState.SPECTRO_DIFF;
			rangeRow.setDisable(diff);
			diffHint.setVisible(diff);
			diffHint.setManaged(diff);
		};
		state.spectroSource.addListener((o, a, b) -> vis.run());
		vis.run();

		return section("Display",
			label("Peak markers follow"), new Segmented(markerIdx, markerOpts),
			label("Spectrograph source"), new Segmented(state.spectroSource, srcOpts),
			label("Spectrograph history"), new Segmented(state.spectroHistory, histOpts),
			label("Spectrograph colour range"), rangeRow, key, diffHint);
	}

	private int markerIndex()
	{
		return "solo".equals(state.markerSource.get()) ? 1 : 0;
	}

	/** The spectrograph's colour map as a CSS gradient, for its key. */
	private static String colourKeyGradient()
	{
		StringBuilder css = new StringBuilder("linear-gradient(to right");
		int steps = 12;
		for (int i = 0; i <= steps; i++)
		{
			Color c = Spectrograph.color(i / (double)steps);
			css.append(String.format(Locale.US, ", rgb(%d,%d,%d) %d%%",
				Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255),
				Math.round(100.0 * i / steps)));
		}
		return css.append(")").toString();
	}

	// ---- Analysis ---------------------------------------------------------
	private VBox analysisSection()
	{
		Map<Integer, String> fracOpts = new LinkedHashMap<>();
		fracOpts.put(1, "1/1");
		fracOpts.put(3, "1/3");
		fracOpts.put(6, "1/6");
		fracOpts.put(12, "1/12");
		fracOpts.put(24, "1/24");

		Label smoothLbl = label("Smoothing");
		Label smoothVal = new Label();
		smoothVal.getStyleClass().add("row-val");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox smoothRow = new HBox(smoothLbl, sp, smoothVal);
		Slider slider = new Slider(0, 0.95, state.smoothing.get());
		slider.valueProperty().bindBidirectional(state.smoothing);
		Runnable updateVal = () -> smoothVal.setText(Math.round(state.smoothing.get() * 100) + "%");
		state.smoothing.addListener((o, a, b) -> updateVal.run());
		updateVal.run();

		Map<Integer, String> avgOpts = new LinkedHashMap<>();
		avgOpts.put(1, "Off");
		avgOpts.put(2, "2");
		avgOpts.put(4, "4");
		avgOpts.put(8, "8");
		avgOpts.put(16, "16");

		return section("Analysis",
			label("Resolution"), new Segmented(state.frac, fracOpts),
			smoothRow, slider,
			label("Averaging"), new Segmented(state.avgN, avgOpts),
			hint("How the plot is computed. The rail shows what is in effect."));
	}

	// ---- General ----------------------------------------------------------
	private VBox generalSection()
	{
		Button reset = miniBtn("Reset saved settings…", e -> confirmReset());
		reset.getStyleClass().add("danger-btn");
		return section("General", reset,
			hint("Clears the SPL calibration and limit, the solo trim, the selected inputs and the display and"
				+ " analysis options."));
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
				+ "This clears the SPL calibration and limit, the solo trim, the selected inputs and the display and"
				+ " analysis options.",
			ButtonType.CANCEL, ButtonType.OK);
		alert.setHeaderText("Reset saved settings");
		alert.initOwner(stage);
		alert.showAndWait()
			.filter(b -> b == ButtonType.OK)
			.ifPresent(b -> settings.reset(state));
	}

	// ---- MCP server -------------------------------------------------------
	private VBox mcpSection()
	{
		Label onLbl = new Label("Serve on localhost");
		onLbl.getStyleClass().add("ctl-lbl");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox onRow = new HBox(onLbl, sp, new Toggle(state.mcpEnabled, "#b794f6"));
		onRow.setAlignment(Pos.CENTER_LEFT);

		// Committed on Enter or when the field loses focus, not per keystroke:
		// every new port restarts the server, and typing 8765 would otherwise
		// bind 8, 87 and 876 on the way.
		TextField port = new TextField(Integer.toString(state.mcpPort.get()));
		port.getStyleClass().add("spl-input");
		port.setPrefWidth(76);
		Runnable commit = () -> {
			try
			{
				int p = Integer.parseInt(port.getText().trim());
				if (Settings.isValidPort(p))
				{
					state.mcpPort.set(p);
					return;
				}
			}
			catch (NumberFormatException ignored)
			{
			}
			port.setText(Integer.toString(state.mcpPort.get()));
		};
		port.setOnAction(e -> commit.run());
		port.focusedProperty().addListener((o, a, focused) -> {
			if (!focused) commit.run();
		});
		state.mcpPort.addListener((o, a, b) -> port.setText(Integer.toString(b.intValue())));
		Label portLbl = new Label("port");
		portLbl.getStyleClass().add("hint");
		HBox portRow = new HBox(8, port, portLbl);
		portRow.setAlignment(Pos.CENTER_LEFT);

		Label status = new Label();
		status.getStyleClass().add("hint");
		status.textProperty().bind(state.mcpStatus);

		Label howTo = hint("Lets an AI assistant read the ring-out log, the readout history and"
			+ " the current band levels, to give mixing tips. Read-only, and reachable from this"
			+ " computer only. Set up your assistant:");

		// Which assistant the setup below is for. A view choice, not a setting:
		// it only picks which instructions to show.
		IntegerProperty client = new SimpleIntegerProperty(CLAUDE_CODE);
		Map<Integer, String> clients = new LinkedHashMap<>();
		clients.put(CLAUDE_CODE, "Claude Code");
		clients.put(CLAUDE_DESKTOP, "Claude Desktop");
		clients.put(CHATGPT, "ChatGPT");
		Segmented clientSelect = new Segmented(client, clients);

		Label clientHint = hint("");
		TextField command = new TextField();
		command.setEditable(false);
		command.getStyleClass().add("spl-input");
		HBox.setHgrow(command, Priority.ALWAYS);
		Runnable refreshCommand = () -> {
			String url = "http://127.0.0.1:" + state.mcpPort.get() + McpHttpServer.ENDPOINT;
			clientHint.setText(setupHint(client.get()));
			command.setText(setupText(client.get(), url));
			command.positionCaret(0);
		};
		state.mcpPort.addListener((o, a, b) -> refreshCommand.run());
		client.addListener((o, a, b) -> refreshCommand.run());
		refreshCommand.run();
		Button copy = new Button("Copy");
		copy.getStyleClass().add("mini-btn");
		copy.setOnAction(e -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(command.getText());
			Clipboard.getSystemClipboard().setContent(content);
		});
		HBox commandRow = new HBox(8, command, copy);
		commandRow.setAlignment(Pos.CENTER_LEFT);

		Runnable vis = () -> {
			boolean on = state.mcpEnabled.get();
			portRow.setDisable(!on);
			clientSelect.setDisable(!on);
			commandRow.setDisable(!on);
		};
		state.mcpEnabled.addListener((o, a, b) -> vis.run());
		vis.run();

		return section("AI assistant (MCP)", onRow, portRow, status, howTo, clientSelect,
			clientHint, commandRow);
	}

	private static String setupHint(int client)
	{
		return switch (client)
		{
			// Claude Desktop only launches local servers over stdio, so the
			// mcp-remote bridge turns its stdio into requests to this URL.
			case CLAUDE_DESKTOP -> "Needs Node.js. In Claude Desktop open Settings → Developer"
				+ " → Edit Config, paste this inside \"mcpServers\", save, and restart Claude.";
			// ChatGPT calls MCP servers from OpenAI's side, which cannot reach
			// this computer's loopback without the tunnel.
			case CHATGPT -> "ChatGPT reaches this computer only through OpenAI's Secure MCP"
				+ " Tunnel. Run tunnel-client here with this URL as --mcp-server-url, then add"
				+ " the tunnel in ChatGPT under Plugins → Add custom MCP server. Needs a"
				+ " workspace with tunnels enabled.";
			default -> "Run this in a terminal, then start a new Claude Code session.";
		};
	}

	private static String setupText(int client, String url)
	{
		return switch (client)
		{
			case CLAUDE_DESKTOP -> "\"fohanalyzer\": {\"command\": \"npx\", \"args\": [\"-y\","
				+ " \"mcp-remote\", \"" + url + "\"]}";
			case CHATGPT -> url;
			default -> "claude mcp add --transport http fohanalyzer " + url;
		};
	}

	private void calibrate()
	{
		Double rms = state.stats.get().micRmsDbfs();
		if (rms == null) return;
		double ref = state.calRefSpl.get();
		state.calibrate(ref - rms, ref, LocalDate.now());
		// Whoever calibrates wants to read the result.
		state.showSpl.set(true);
	}

	/**
	 * {@code 94.0 -> "94"}, so a restored whole number does not read as a
	 * measurement.
	 */
	private static String fmtRef(double v)
	{
		return v == Math.rint(v)
			? Long.toString(Math.round(v))
			: String.format(Locale.US, "%.1f", v);
	}

	// ---- shell ------------------------------------------------------------
	private static VBox section(String title, javafx.scene.Node... children)
	{
		Label t = new Label(title.toUpperCase());
		t.getStyleClass().add("sec-title");
		VBox sec = new VBox(9, t);
		sec.getChildren().addAll(children);
		sec.getStyleClass().add("section");
		return sec;
	}

	private static Label label(String text)
	{
		Label l = new Label(text);
		l.getStyleClass().add("row-lbl");
		return l;
	}

	private static Button miniBtn(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action)
	{
		Button b = new Button(text);
		b.getStyleClass().add("mini-btn");
		b.setOnAction(action);
		return b;
	}

	private static Label hint(String text)
	{
		Label l = new Label(text);
		l.getStyleClass().add("hint");
		l.setWrapText(true);
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}
}
