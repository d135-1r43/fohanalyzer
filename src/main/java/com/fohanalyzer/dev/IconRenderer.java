package com.fohanalyzer.dev;

import com.fohanalyzer.ui.controls.Logo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build tool: renders the app icon from the in-app {@link Logo} artwork, so the
 * icon cannot drift from the logo in the header. Writes a macOS
 * {@code .iconset} directory that {@code iconutil} turns into an {@code .icns}.
 *
 * <p>
 * Each size is rendered from the vector artwork rather than downscaled from one
 * large bitmap — the bars are thin, and resampling them turns the small sizes
 * to mush.
 *
 * <p>
 * Run via {@code scripts/package-mac.sh}, or directly:
 * {@code mvn javafx:run -Dapp.mainClass=com.fohanalyzer.dev.IconRenderer}
 */
public class IconRenderer extends Application
{
	private static final Logger log = LoggerFactory.getLogger(IconRenderer.class);

	private static final Path APP_OUT = Path.of("target/FOHanalyzer.iconset");
	private static final Path VOLUME_OUT = Path.of("target/FOHanalyzer-volume.iconset");

	/**
	 * {@code {pixels, filename}} — the set {@code iconutil} expects for a macOS
	 * icon.
	 */
	private static final Object[][] VARIANTS = {
		{ 16, "icon_16x16.png" },
		{ 32, "icon_16x16@2x.png" },
		{ 32, "icon_32x32.png" },
		{ 64, "icon_32x32@2x.png" },
		{ 128, "icon_128x128.png" },
		{ 256, "icon_128x128@2x.png" },
		{ 256, "icon_256x256.png" },
		{ 512, "icon_256x256@2x.png" },
		{ 512, "icon_512x512.png" },
		{ 1024, "icon_512x512@2x.png" },
	};

	/**
	 * Artwork inset inside the canvas, so the icon is not full-bleed against
	 * the Dock.
	 */
	private static final double FILL = 0.88;

	@Override
	public void start(Stage stage) throws Exception
	{
		Files.createDirectories(APP_OUT);
		Files.createDirectories(VOLUME_OUT);
		for (Object[] v : VARIANTS)
		{
			int px = (Integer)v[0];
			write(new Logo(px * FILL), px, APP_OUT.resolve((String)v[1]).toFile());
			write(drive(px), px, VOLUME_OUT.resolve((String)v[1]).toFile());
		}
		log.info("iconsets written to {} and {}", APP_OUT.toAbsolutePath(), VOLUME_OUT.toAbsolutePath());
		Platform.exit();
	}

	/**
	 * An upright portable drive seen from the front, the logo on its face —
	 * drawn in a 100-unit square and scaled to {@code px}.
	 */
	private static Node drive(int px)
	{
		Rectangle body = new Rectangle(18, 6, 64, 88);
		body.setArcWidth(16);
		body.setArcHeight(16);
		body.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
			new Stop(0, Color.web("#2b3746")),
			new Stop(1, Color.web("#121922"))));
		body.setStroke(Color.web("#3b4b5e"));
		body.setStrokeWidth(0.8);

		// Front bezel: a seam across the foot of the face and an activity
		// LED in the logo's lime.
		Line seam = new Line(19, 78, 81, 78);
		seam.setStroke(Color.web("#0a0e14"));
		seam.setStrokeWidth(0.8);
		Circle led = new Circle(50, 86, 1.8, Color.web("#a3e635"));

		Logo logo = new Logo(44);
		logo.relocate(28, 20);

		Group art = new Group(body, seam, led, logo);
		art.getTransforms().add(new Scale(px / 100.0, px / 100.0));
		Pane canvas = new Pane(art);
		canvas.setPrefSize(px, px);
		canvas.setMinSize(px, px);
		canvas.setMaxSize(px, px);
		return canvas;
	}

	private static void write(Node icon, int px, File target) throws Exception
	{
		StackPane holder = new StackPane(icon);
		holder.setPrefSize(px, px);
		holder.setStyle("-fx-background-color: transparent;");
		// A Scene is needed for layout, but never shown — this renders
		// offscreen.
		new Scene(holder, px, px, Color.TRANSPARENT);
		holder.applyCss();
		holder.layout();

		SnapshotParameters params = new SnapshotParameters();
		params.setFill(Color.TRANSPARENT);
		WritableImage img = holder.snapshot(params, new WritableImage(px, px));
		ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png", target);
	}
}
