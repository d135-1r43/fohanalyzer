package com.fohanalyzer.ui;

import com.fohanalyzer.mcp.McpHttpServer;
import com.fohanalyzer.ui.controls.ChannelSelect;
import com.fohanalyzer.ui.controls.Segmented;
import com.fohanalyzer.ui.controls.Toggle;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The set-once settings: which device feeds each source, the SPL calibration,
 * and the MCP server an AI assistant can read the analyzer through.
 *
 * <p>
 * Both are decided when the rig is patched and then left alone, so they sit
 * here instead of taking up the rail that has to stay readable during a show.
 * Everything binds straight to {@link AppState}, which {@link Settings} already
 * persists — closing the window is not a commit step, and there is nothing to
 * cancel.
 */
public final class PreferencesWindow
{
	/**
	 * The window is not resizable: a dragged-in edge hides controls with no
	 * clue that they are there. So this is the one width the layout is drawn
	 * for, and the height is always whatever the content needs at that width.
	 */
	private static final double WIDTH = 420;

	private static final int CLAUDE_CODE = 0;
	private static final int CLAUDE_DESKTOP = 1;
	private static final int CHATGPT = 2;

	private final AppState state;
	private final Settings settings;
	private Stage stage;
	private VBox body;

	public PreferencesWindow(AppState state, Settings settings)
	{
		this.state = state;
		this.settings = settings;
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
		// multi-channel live input, so the content gets taller once a source is
		// picked. Nothing can be dragged bigger any more, so the window has to
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
		body = new VBox(18, sourcesSection(), splSection(), mcpSection());
		body.getStyleClass().add("prefs-body");

		Button close = new Button("Done");
		close.getStyleClass().addAll("btn", "mini-btn");
		close.setOnAction(e -> {
			if (stage != null) stage.close();
		});
		HBox foot = new HBox(close);
		foot.setAlignment(Pos.CENTER_RIGHT);
		foot.getStyleClass().add("prefs-foot");

		VBox root = new VBox(body, foot);
		root.getStyleClass().add("prefs-root");
		// Width is the fixed one of the two: a wrapped hint only has a height
		// once it knows what to wrap into, so pinning the width is what makes
		// the height measurable at all.
		root.setMinWidth(WIDTH);
		root.setPrefWidth(WIDTH);
		root.setMaxWidth(WIDTH);
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
				+ " it as a pair, merged to one trace."));
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
		HBox calRow = new HBox(8, ref, refLbl, calBtn);
		calRow.setAlignment(Pos.CENTER_LEFT);

		Label status = new Label();
		status.getStyleClass().add("hint");
		Runnable refreshStatus = () -> {
			double off = state.splOffset.get();
			status.setText(off == 0
				? "Not calibrated — the readout shows dBFS + 0."
				: String.format(Locale.US, "Calibrated · offset %+.1f dB", off));
		};
		state.splOffset.addListener((o, a, b) -> refreshStatus.run());
		refreshStatus.run();

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

		VBox sec = section("SPL meter", showRow, needsLive, calRow, howTo, status);

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
		state.splOffset.set(state.calRefSpl.get() - rms);
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

	private static Label hint(String text)
	{
		Label l = new Label(text);
		l.getStyleClass().add("hint");
		l.setWrapText(true);
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}
}
