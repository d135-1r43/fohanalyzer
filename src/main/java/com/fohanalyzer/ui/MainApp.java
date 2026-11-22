package com.fohanalyzer.ui;

import com.fohanalyzer.Version;
import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.audio.AudioInputs;
import com.fohanalyzer.audio.AudioSource;
import com.fohanalyzer.dev.Demo;
import com.fohanalyzer.dsp.LeqMeter;
import com.fohanalyzer.mcp.McpHttpServer;
import com.fohanalyzer.ui.controls.Logo;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application root: header + analyzer + control rail, with live-audio wiring.
 */
public class MainApp extends Application
{
	private static final Logger log = LoggerFactory.getLogger(MainApp.class);

	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
	private final AppState state = new AppState();
	private final Settings settings = new Settings();
	private final McpHttpServer mcpServer = new McpHttpServer(state.history);
	private final SplLog splLog = new SplLog();
	/**
	 * Starts and stops run here, one at a time and in order: binding a port can
	 * block, and a toggle flicked twice must not race its own restart.
	 */
	private final ExecutorService mcpControl = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "mcp-control");
		t.setDaemon(true);
		return t;
	});

	/**
	 * Input changes are applied here, one at a time and in the order they were
	 * made. Restoring settings picks the device and then its channel, which is
	 * two requests in quick succession; on a thread each, the stale one could
	 * land last and quietly move the line back to the old channel while the
	 * card shows the new one.
	 */
	private final ExecutorService audioControl = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "audio-connect");
		t.setDaemon(true);
		return t;
	});

	private Label clockLabel;
	/** A staged show for screenshots: no inputs, no stored settings, no log. */
	private final boolean demo = Demo.enabled();

	@Override
	public void start(Stage stage)
	{
		// register the bundled families before any stylesheet resolves them
		Fonts.install();
		if (demo) Demo.stage(state);
		AnalyzerView analyzer = new AnalyzerView(state);
		// same 18px gutter as the header/rail
		// The strip of actions sits on top of the plot it acts on.
		VBox center = new VBox(10, new ActionStrip(state), analyzer);
		VBox.setVgrow(analyzer, Priority.ALWAYS);
		BorderPane.setMargin(center, new Insets(12, 18, 14, 18));

		BorderPane root = new BorderPane();
		root.getStyleClass().add("root");
		root.setTop(buildHeader());
		root.setCenter(center);
		ControlRail rail = new ControlRail(state, settings, getHostServices());
		root.setRight(rail);

		Scene scene = new Scene(root, 1366, 820, Color.web("#070a0f"));
		scene.getStylesheets().add(getClass().getResource("/com/fohanalyzer/theme.css").toExternalForm());
		// no-op unless FOH_DEV=true
		com.fohanalyzer.dev.CssWatcher.installIfEnabled(scene);
		// The platform's preferences shortcut: Cmd+, on macOS, Ctrl+,
		// elsewhere.
		scene.getAccelerators().put(
			new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN), rail::showPreferences);
		stage.setTitle("FOHanalyzer");
		stage.setScene(scene);
		stage.centerOnScreen();
		stage.show();
		// Launched from a CLI/Maven process on macOS the window can open behind
		// the terminal; pull it to the front and give it focus.
		stage.setAlwaysOnTop(true);
		stage.toFront();
		stage.requestFocus();
		stage.setAlwaysOnTop(false);

		startClock();
		if (!demo)
		{
			wireLiveAudio();
			refreshDevices();
			// After enumeration: a stored live input can only be restored once
			// we know whether its interface is actually present.
			settings.bind(state);
			wireMcp();
		}

		stage.setOnCloseRequest(e -> {
			settings.flush();
			splLog.close();
			mcpServer.stop();
			if (state.micSource != null) state.micSource.disconnect();
			if (state.soloSource != null) state.soloSource.disconnect();
		});

		String probe = System.getenv("PROBE");
		if (probe != null && !probe.isBlank()) maybeSnapshot(root, rail, probeDelaySeconds(probe));
	}

	/**
	 * {@code PROBE=true} keeps the default 2 s; {@code PROBE=8} waits 8 s
	 * before snapshotting.
	 */
	private static double probeDelaySeconds(String probe)
	{
		try
		{
			return Double.parseDouble(probe.trim());
		}
		catch (NumberFormatException e)
		{
			return 2;
		}
	}

	/**
	 * Dev aid: with PROBE set, snapshot the whole window to
	 * target/probe-full.png at twice its size, and with PROBE_PREFS=n also
	 * preferences page n to target/probe-prefs.png, then exit.
	 */
	private void maybeSnapshot(BorderPane root, ControlRail rail, double delaySeconds)
	{
		String prefsPage = System.getenv("PROBE_PREFS");
		var wait = new javafx.animation.PauseTransition(Duration.seconds(delaySeconds));
		wait.setOnFinished(e -> {
			boolean exitNow = true;
			try
			{
				writeSnapshot(root, "target/probe-full.png");
				if (prefsPage != null && !prefsPage.isBlank())
				{
					rail.showPreferences(Integer.parseInt(prefsPage.trim()));
					exitNow = false;
					// A beat for the window to lay out and size itself.
					var settle = new javafx.animation.PauseTransition(Duration.seconds(1));
					settle.setOnFinished(ev -> {
						try
						{
							writeSnapshot(rail.preferencesRoot(), "target/probe-prefs.png");
						}
						catch (Exception ex)
						{
							log.warn("probe snapshot of the preferences failed", ex);
						}
						finally
						{
							Platform.exit();
						}
					});
					settle.play();
				}
			}
			catch (Exception ex)
			{
				log.warn("probe snapshot failed", ex);
			}
			finally
			{
				if (exitNow) Platform.exit();
			}
		});
		wait.play();
	}

	private static void writeSnapshot(javafx.scene.Node node, String path) throws java.io.IOException
	{
		var params = new javafx.scene.SnapshotParameters();
		// Twice the size, so the image stays sharp on a high-density display.
		params.setTransform(javafx.scene.transform.Transform.scale(2, 2));
		var img = node.snapshot(params, null);
		javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png",
			new java.io.File(path));
		log.info("probe wrote {}", path);
	}

	// ---- header -----------------------------------------------------------
	private static HBox tracked(String text, double tracking, String styleClass)
	{
		HBox box = new HBox(tracking);
		box.setAlignment(Pos.BASELINE_LEFT);
		box.setMouseTransparent(true);

		for (int i = 0; i < text.length(); i++)
		{
			Label glyph = new Label(String.valueOf(text.charAt(i)));
			glyph.getStyleClass().add(styleClass);
			glyph.setMinWidth(Region.USE_PREF_SIZE);
			box.getChildren().add(glyph);
		}

		return box;
	}

	private HBox buildHeader()
	{
		Label top = new Label("FOHanalyzer");
		top.getStyleClass().add("wm-top");
		// The website sets this line with 0.14em tracking; JavaFX has no letter
		// spacing, so each glyph gets its own label and the box spaces them.
		HBox sub = tracked("DUAL SPECTRUM · RTA", 1.4, "wm-sub");
		Label ver = new Label(Version.VALUE);
		ver.getStyleClass().add("ver-chip");
		// Chip on the title's own line, so it sits at cap height instead of
		// floating between the two wordmark lines.
		HBox title = new HBox(10, top, ver);
		title.setAlignment(Pos.CENTER_LEFT);
		VBox wordmark = new VBox(3, title, sub);
		wordmark.setAlignment(Pos.CENTER_LEFT);
		HBox brand = new HBox(13, new Logo(), wordmark);
		brand.setAlignment(Pos.CENTER_LEFT);
		// Without this the logo is stretched to the row height and its artwork
		// draws against the top edge instead of beside the wordmark.
		brand.setFillHeight(false);

		// The header says what this is and what state it is in. Readouts live
		// in the rail, once each, rather than repeated up here.
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Circle led = new Circle(4.5, Color.web("#ff4d4d"));
		Label liveTxt = new Label("LIVE");
		liveTxt.getStyleClass().add("st-txt");
		Label rate = new Label("48 kHz / 24-bit");
		rate.getStyleClass().add("st-mono");
		clockLabel = new Label("");
		clockLabel.getStyleClass().add("st-mono");
		HBox status = new HBox(10, led, liveTxt, sep(), rate, sep(), clockLabel);
		status.setAlignment(Pos.CENTER_LEFT);

		HBox header = new HBox(24, brand, spacer, status);
		header.getStyleClass().add("header");
		header.setAlignment(Pos.CENTER_LEFT);
		// Each group keeps its natural height so the row's center-left
		// alignment can actually center it; otherwise they fill the bar and pin
		// their content to the top.
		header.setFillHeight(false);
		return header;
	}

	private Region sep()
	{
		Region r = new Region();
		r.setStyle("-fx-background-color:#1c2733;");
		r.setMinWidth(1);
		r.setPrefSize(1, 16);
		r.setMaxHeight(16);
		return r;
	}

	// ---- live audio wiring (ports the two $effect blocks) -----------------
	private void wireLiveAudio()
	{
		Runnable mic = () -> updateSource(true);
		Runnable solo = () -> updateSource(false);
		state.micChan.addListener((o, a, b) -> mic.run());
		state.micChanIdx.addListener((o, a, b) -> mic.run());
		state.soloChan.addListener((o, a, b) -> solo.run());
		state.soloChanIdx.addListener((o, a, b) -> solo.run());
		state.soloStereo.addListener((o, a, b) -> solo.run());
		mic.run();
		solo.run();
	}

	private void updateSource(boolean isMic)
	{
		String chan = (isMic ? state.micChan : state.soloChan).get();
		int idx = (isMic ? state.micChanIdx : state.soloChanIdx).get();
		// Solo can be read as a stereo pair; the measurement mic is always
		// mono.
		boolean stereo = !isMic && state.soloStereo.get();
		if (chan.startsWith("live:"))
		{
			String id = chan.substring(5);
			Optional<AudioDevice> dev = state.audioDevices.stream()
				.filter(d -> d.id().equals(id)).findFirst();
			if (dev.isEmpty()) return;
			AudioSource src = isMic ? state.micSource : state.soloSource;
			if (src == null)
			{
				src = new AudioSource();
				if (isMic)
				{
					// The SPL meter integrates every sample the mic captures.
					src.setLevelMeter(state.leq);
					state.micSource = src;
				}
				else
				{
					state.soloSource = src;
				}
			}
			AudioSource source = src;
			// Open the line off the FX thread; report the channel count back.
			audioControl.execute(() -> {
				int count = source.connect(dev.get(), idx, stereo);
				Platform.runLater(() -> {
					(isMic ? state.micChanCount : state.soloChanCount).set(count);
					// A restored channel index can outrun the device it lands
					// on — e.g. Ch 8 saved against an 18i20, reopened on a
					// 2-in interface. A stereo pair needs one channel more,
					// so the last selectable index is one lower.
					int last = Math.max(0, count - (stereo ? 2 : 1));
					if (idx > last)
					{
						(isMic ? state.micChanIdx : state.soloChanIdx).set(last);
					}
				});
			});
		}
		else
		{
			AudioSource src = isMic ? state.micSource : state.soloSource;
			// Queued behind any connect still pending, so that one cannot
			// reopen the line after this closes it.
			if (src != null) audioControl.execute(src::disconnect);
			if (isMic) state.micSource = null;
			else
				state.soloSource = null;
			(isMic ? state.micChanCount : state.soloChanCount).set(1);
		}
	}

	// ---- MCP server -------------------------------------------------------
	/**
	 * After {@link Settings#bind}, so a server left on last time comes back on
	 * its stored port.
	 */
	private void wireMcp()
	{
		state.mcpEnabled.addListener((o, a, b) -> applyMcp());
		state.mcpPort.addListener((o, a, b) -> applyMcp());
		applyMcp();
	}

	private void applyMcp()
	{
		boolean on = state.mcpEnabled.get();
		int port = state.mcpPort.get();
		state.mcpStatus.set(on ? "Starting…" : "Off");
		mcpControl.execute(() -> {
			String status = switchMcp(on, port);
			Platform.runLater(() -> state.mcpStatus.set(status));
		});
	}

	/** Runs on {@link #mcpControl}; returns the status line to show. */
	private String switchMcp(boolean on, int port)
	{
		if (!on)
		{
			mcpServer.stop();
			return "Off";
		}

		try
		{
			mcpServer.start(port);
			return "Listening on " + mcpServer.url();
		}
		catch (Exception e)
		{
			log.warn("could not start the MCP server on port {}", port, e);
			return "Could not listen on port " + port + " — is it already in use?";
		}
	}

	private void refreshDevices()
	{
		state.audioDevices.setAll(AudioInputs.enumerate());
	}

	/**
	 * One row a second into the session's SPL log, while the meter is showing a
	 * calibrated live mic. The interval is drained either way, so a row never
	 * covers a stretch when the meter was off.
	 */
	private void logSpl()
	{
		if (demo) return;

		LeqMeter.Interval interval = state.leq.drainInterval();
		double off = state.splOffset.get();
		boolean logging = state.isMicLive() && state.showSpl.get() && off != 0;
		if (interval == null || !logging) return;

		LeqMeter.Reading reading = state.leq.reading(state.leqWindow.get());
		// Ticked here rather than per frame: the drained interval is exactly
		// the time that passed, and it stops when the meter does.
		state.timeOverLimit.tick(interval.seconds(), reading.leqDb() + off, state.activeSplLimit());
		Path file = splLog.append(interval, reading, off);
		state.splLogFile.set(file.toString());
	}

	private void startClock()
	{
		Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
			clockLabel.setText(LocalTime.now().format(CLOCK));
			logSpl();
		}));
		tl.setCycleCount(Animation.INDEFINITE);
		tl.play();
		clockLabel.setText(LocalTime.now().format(CLOCK));
	}
}
