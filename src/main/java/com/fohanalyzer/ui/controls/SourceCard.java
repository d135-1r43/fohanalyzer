package com.fohanalyzer.ui.controls;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.ui.InputPreset;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

/**
 * A read-only source panel for the rail: indicator dot, name, level, what feeds
 * it, a level meter, and a line of detail the rail fills in.
 *
 * <p>
 * Nothing here is a control. Which device feeds the source is set in the
 * preferences window and whether the trace is drawn in the strip above the
 * plot; the card only shows the outcome — during a show you still need to see
 * at a glance whether a trace is a live input or a simulation, and on which
 * channel. A source switched off is dimmed.
 */
public final class SourceCard extends VBox
{
	public final Meter meter;
	/** The level figure beside the name. */
	public final Label level;
	/** Free detail under the meter, such as the solo trim in effect. */
	public final Label detailLeft;
	public final Label detailRight;

	/**
	 * @param stereo
	 *            when non-null and set, the selection line reads the pair the
	 *            source is merging. Null for an always-mono source.
	 */
	public SourceCard(String name, String color, BooleanProperty on,
		List<InputPreset> options, StringProperty chan,
		IntegerProperty chanIdx, IntegerProperty chanCount,
		ObservableList<AudioDevice> audioDevices, BooleanProperty stereo)
	{
		getStyleClass().add("src-card");
		setSpacing(7);

		Circle dot = new Circle(4.5, Color.web(color));
		Label nameLbl = new Label(name);
		nameLbl.getStyleClass().add("src-name");
		HBox.setHgrow(nameLbl, Priority.ALWAYS);
		nameLbl.setMaxWidth(Double.MAX_VALUE);
		level = new Label("—");
		level.getStyleClass().add("src-level");
		level.setStyle("-fx-text-fill:" + color + ";");
		HBox top = new HBox(10, dot, nameLbl, level);
		top.setAlignment(Pos.CENTER_LEFT);

		Label tag = new Label();
		tag.getStyleClass().add("chan-tag");
		Label selection = new Label();
		selection.getStyleClass().add("src-sel");
		HBox.setHgrow(selection, Priority.ALWAYS);
		selection.setMaxWidth(Double.MAX_VALUE);
		HBox sel = new HBox(8, tag, selection);
		sel.setAlignment(Pos.CENTER_LEFT);

		Runnable refreshSel = () -> {
			String value = chan.get();
			boolean live = SourceLabel.isLive(value);
			tag.setText(SourceLabel.tag(value));
			String tc = live ? "#a3e635" : color;
			tag.setStyle("-fx-text-fill:" + tc + "; -fx-border-color:" + tc + ";");
			// The channel only means anything on a multi-channel live input.
			String suffix = live && chanCount.get() > 1
				? "  ·  " + SourceLabel.channelShort(chanIdx.get(), stereo != null && stereo.get())
				: "";
			selection.setText(SourceLabel.of(value, options, audioDevices) + suffix);
		};
		chan.addListener((o, a, b) -> refreshSel.run());
		chanIdx.addListener((o, a, b) -> refreshSel.run());
		chanCount.addListener((o, a, b) -> refreshSel.run());
		if (stereo != null) stereo.addListener((o, a, b) -> refreshSel.run());
		audioDevices.addListener((javafx.collections.ListChangeListener<AudioDevice>)c -> refreshSel.run());
		refreshSel.run();

		meter = new Meter(color);

		detailLeft = new Label();
		detailLeft.getStyleClass().add("src-detail");
		detailRight = new Label();
		detailRight.getStyleClass().add("src-detail");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox detail = new HBox(detailLeft, sp, detailRight);
		detail.setAlignment(Pos.CENTER_LEFT);

		on.addListener((o, a, b) -> setOpacity(b ? 1.0 : 0.5));
		setOpacity(on.get() ? 1.0 : 0.5);

		getChildren().addAll(top, sel, meter, detail);
	}
}
