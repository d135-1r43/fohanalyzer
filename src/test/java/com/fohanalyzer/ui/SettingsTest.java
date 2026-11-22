package com.fohanalyzer.ui;

import com.fohanalyzer.audio.AudioDevice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@link Settings} against a throwaway {@link Preferences} node rather
 * than the real user store, so the suite neither reads nor leaves behind
 * anyone's actual calibration. Only {@code javafx.base} properties are involved
 * — no toolkit, so this stays a plain unit test.
 */
class SettingsTest
{
	private Preferences node;
	private Settings settings;

	@BeforeEach
	void setUp()
	{
		node = Preferences.userRoot().node("fohanalyzer-test/" + UUID.randomUUID());
		settings = new Settings(node);
	}

	@AfterEach
	void tearDown() throws Exception
	{
		Preferences parent = node.parent();
		node.removeNode();
		// the removal is only persistent once an ancestor is flushed
		parent.flush();
	}

	@Test
	void storedValuesComeBackOnTheNextLaunch()
	{
		AppState first = new AppState();
		settings.bind(first);
		first.calRefSpl.set(100);
		first.frac.set(24);
		first.smoothing.set(0.4);
		first.avgN.set(8);
		first.peakHold.set(true);
		first.markers.set(false);
		first.markerSource.set("solo");
		first.showTransfer.set(true);
		first.showSpl.set(true);
		first.soloTrim.set(-12.5);
		first.view.set(AppState.VIEW_SPLIT);
		first.spectroSource.set(AppState.SPECTRO_DIFF);
		first.spectroHistory.set(60);
		first.spectroFloor.set(-95);
		first.spectroCeiling.set(-20);
		first.leqWindow.set(900);

		AppState next = new AppState();
		new Settings(node).bind(next);

		assertEquals(100, next.calRefSpl.get(), 1e-9);
		assertEquals(24, next.frac.get());
		assertEquals(0.4, next.smoothing.get(), 1e-9);
		assertEquals(8, next.avgN.get());
		assertTrue(next.peakHold.get());
		assertFalse(next.markers.get());
		assertEquals("solo", next.markerSource.get());
		assertTrue(next.showTransfer.get());
		assertTrue(next.showSpl.get());
		assertEquals(-12.5, next.soloTrim.get(), 1e-9);
		assertEquals(AppState.VIEW_SPLIT, next.view.get());
		assertEquals(AppState.SPECTRO_DIFF, next.spectroSource.get());
		assertEquals(60, next.spectroHistory.get());
		assertEquals(-95, next.spectroFloor.get(), 1e-9);
		assertEquals(-20, next.spectroCeiling.get(), 1e-9);
		assertEquals(900, next.leqWindow.get());
	}

	@Test
	void theVenueLimitComesBackAndIsClearedByReset()
	{
		AppState first = new AppState();
		settings.bind(first);
		first.splLimitOn.set(true);
		first.splLimit.set(98.5);

		AppState next = new AppState();
		Settings again = new Settings(node);
		again.bind(next);

		assertTrue(next.splLimitOn.get());
		assertEquals(98.5, next.splLimit.get(), 1e-9);

		again.reset(next);

		assertFalse(next.splLimitOn.get());
		assertEquals(new AppState().splLimit.get(), next.splLimit.get(), 1e-9);
	}

	@Test
	void aLimitOutsideTheStepperFallsBackToTheDefault()
	{
		node.putDouble("splLimit", 400);

		AppState st = new AppState();
		settings.bind(st);

		assertEquals(new AppState().splLimit.get(), st.splLimit.get(), 1e-9);
	}

	@Test
	void anLeqWindowTheMeterDoesNotOfferFallsBackToTheDefault()
	{
		node.putInt("leqWindow", 120);

		AppState st = new AppState();
		settings.bind(st);

		assertEquals(new AppState().leqWindow.get(), st.leqWindow.get());
	}

	@Test
	void spectrographValuesTheControlsCannotShowFallBackToDefaults()
	{
		node.putInt("view", 7);
		node.putInt("spectroSource", -1);
		node.putInt("spectroHistory", 45);
		node.putDouble("spectroFloor", 10);
		node.putDouble("spectroCeiling", -500);

		AppState st = new AppState();
		settings.bind(st);

		AppState d = new AppState();
		assertEquals(d.view.get(), st.view.get());
		assertEquals(d.spectroSource.get(), st.spectroSource.get());
		assertEquals(d.spectroHistory.get(), st.spectroHistory.get());
		assertEquals(d.spectroFloor.get(), st.spectroFloor.get(), 1e-9);
		assertEquals(d.spectroCeiling.get(), st.spectroCeiling.get(), 1e-9);
	}

	@Test
	void aSoloTrimOutsideTheControlsRangeFallsBackToNone()
	{
		node.putDouble("soloTrim", 500);

		AppState st = new AppState();
		settings.bind(st);

		assertEquals(0, st.soloTrim.get(), 1e-9);
	}

	@Nested
	class SplMeterVisibility
	{
		@Test
		void hiddenOnAFirstRun()
		{
			AppState st = new AppState();
			settings.bind(st);

			assertFalse(st.showSpl.get(), "without a calibration the readout is only dBFS");
		}

		@Test
		void aCalibrationFromBeforeTheSettingExistedKeepsTheMeterShown()
		{
			// Written by a build without the toggle: calibrated, no showSpl
			// key.
			node.putDouble("splOffset", 112.5);

			AppState st = new AppState();
			settings.bind(st);

			assertTrue(st.showSpl.get(), "an update should not hide a meter someone calibrated");
		}

		@Test
		void hidingItIsRememberedEvenWhenCalibrated()
		{
			AppState first = new AppState();
			settings.bind(first);
			first.splOffset.set(112.5);
			first.showSpl.set(true);
			first.showSpl.set(false);

			AppState next = new AppState();
			new Settings(node).bind(next);

			assertFalse(next.showSpl.get());
		}

		@Test
		void resetHidesItAgain()
		{
			AppState st = new AppState();
			settings.bind(st);
			st.showSpl.set(true);

			settings.reset(st);

			assertFalse(st.showSpl.get());
		}
	}

	@Nested
	class CalibrationPerInput
	{
		static final String SCARLETT = "Scarlett 18i20";
		static final String WING = "WING";

		/** A launch with {@code devices} plugged in. */
		private AppState launch(String... devices)
		{
			AppState st = new AppState();
			for (String d : devices)
				st.audioDevices.add(new AudioDevice(d, d + " USB", null));
			new Settings(node).bind(st);
			return st;
		}

		private void select(AppState st, String device, int channel)
		{
			st.micChan.set("live:" + device);
			st.micChanIdx.set(channel);
		}

		@Test
		void aCalibrationComesBackWithItsInput()
		{
			AppState first = launch(SCARLETT);
			select(first, SCARLETT, 2);
			first.calibrate(118.4, 94, LocalDate.of(2026, 11, 3));

			AppState next = launch(SCARLETT);

			assertEquals(118.4, next.splOffset.get(), 1e-9);
			Calibration cal = next.micCalibration();
			assertEquals(LocalDate.of(2026, 11, 3), cal.date());
			assertEquals(94, cal.refDb(), 1e-9);
			assertEquals("Scarlett 18i20 USB", cal.deviceName());
		}

		@Test
		void anotherInputNeverInheritsTheOffset()
		{
			AppState st = launch(SCARLETT, WING);
			select(st, SCARLETT, 2);
			st.calibrate(118.4, 94, LocalDate.now());

			st.micChanIdx.set(3);
			assertEquals(0, st.splOffset.get(), "another channel of the same interface");

			select(st, WING, 2);
			assertEquals(0, st.splOffset.get(), "the same channel of another interface");

			st.micChan.set(AppState.MIC_INPUTS.getFirst().id());
			assertEquals(0, st.splOffset.get(), "a simulated input");

			select(st, SCARLETT, 2);
			assertEquals(118.4, st.splOffset.get(), 1e-9, "switching back brings it back");
		}

		@Test
		void aMissingInterfaceKeepsItsCalibrationStored()
		{
			AppState first = launch(SCARLETT);
			select(first, SCARLETT, 2);
			first.calibrate(118.4, 94, LocalDate.now());

			AppState away = launch(WING);
			assertFalse(away.isMicLive());
			assertEquals(0, away.splOffset.get());
			assertEquals(1, away.calibrations.size(), "still listed for when it comes back");

			AppState back = launch(SCARLETT);
			assertEquals(118.4, back.splOffset.get(), 1e-9);
		}

		@Test
		void aNudgeMovesTheInputsCalibration()
		{
			AppState first = launch(SCARLETT);
			select(first, SCARLETT, 2);
			first.calibrate(118.4, 94, LocalDate.of(2026, 11, 3));
			first.splOffset.set(119.0);

			AppState next = launch(SCARLETT);

			assertEquals(119.0, next.splOffset.get(), 1e-9);
			assertEquals(LocalDate.of(2026, 11, 3), next.micCalibration().date(), "a nudge is not a new calibration");
		}

		@Test
		void clearingForgetsOnlyThatInput()
		{
			AppState st = launch(SCARLETT, WING);
			select(st, WING, 31);
			st.calibrate(121.7, 94, LocalDate.now());
			select(st, SCARLETT, 2);
			st.calibrate(118.4, 94, LocalDate.now());

			st.clearCalibration();

			assertEquals(0, st.splOffset.get());
			AppState next = launch(SCARLETT, WING);
			assertEquals(1, next.calibrations.size());
			assertEquals(WING, next.calibrations.getFirst().deviceId());
		}

		@Test
		void theOldSingleOffsetMovesToTheInputInUse()
		{
			node.putDouble("splOffset", 112.5);
			node.put("micChan", "live:" + SCARLETT);
			node.putInt("micChanIdx", 5);

			AppState st = launch(SCARLETT);

			assertEquals(112.5, st.splOffset.get(), 1e-9);
			Calibration cal = st.micCalibration();
			assertEquals(5, cal.channel());
			assertNull(cal.date(), "when it was made was never stored");
			assertNull(node.get("splOffset", null), "migrated once, then gone");
		}

		@Test
		void theOldSingleOffsetWaitsForALiveMic()
		{
			node.putDouble("splOffset", 112.5);

			AppState st = launch(SCARLETT);
			assertEquals(0, st.splOffset.get(), "a simulated input is not calibrated");
			assertTrue(st.calibrations.isEmpty());

			select(st, SCARLETT, 0);

			assertEquals(112.5, st.splOffset.get(), 1e-9);
			assertNull(node.get("splOffset", null));
		}

		@Test
		void resetClearsEveryCalibration()
		{
			Settings s = new Settings(node);
			AppState st = new AppState();
			st.audioDevices.add(new AudioDevice(SCARLETT, SCARLETT, null));
			s.bind(st);
			select(st, SCARLETT, 2);
			st.calibrate(118.4, 94, LocalDate.now());

			s.reset(st);

			assertTrue(st.calibrations.isEmpty());
			assertEquals(0, st.splOffset.get());
			assertTrue(launch(SCARLETT).calibrations.isEmpty());
		}
	}

	@Test
	void mcpServerSettingsComeBack()
	{
		AppState first = new AppState();
		settings.bind(first);
		first.mcpEnabled.set(true);
		first.mcpPort.set(9123);

		AppState next = new AppState();
		new Settings(node).bind(next);

		assertTrue(next.mcpEnabled.get());
		assertEquals(9123, next.mcpPort.get());
	}

	@Test
	void aPrivilegedOrOutOfRangePortFallsBackToTheDefault()
	{
		node.putInt("mcpPort", 80);
		AppState st = new AppState();
		settings.bind(st);
		assertEquals(AppState.DEFAULT_MCP_PORT, st.mcpPort.get());

		node.putInt("mcpPort", 70000);
		AppState again = new AppState();
		new Settings(node).bind(again);
		assertEquals(AppState.DEFAULT_MCP_PORT, again.mcpPort.get());
	}

	@Test
	void anUntouchedStoreLeavesTheDefaultsAlone()
	{
		AppState st = new AppState();
		AppState defaults = new AppState();
		settings.bind(st);

		assertEquals(defaults.frac.get(), st.frac.get());
		assertEquals(defaults.smoothing.get(), st.smoothing.get(), 1e-9);
		assertEquals(defaults.micChan.get(), st.micChan.get());
		assertEquals(defaults.splOffset.get(), st.splOffset.get(), 1e-9);
	}

	@Test
	void valuesTheControlsCannotDisplayFallBackToDefaults()
	{
		node.putInt("frac", 7); // no such resolution button
		node.putInt("avgN", 3); // no such averaging button
		node.putDouble("smoothing", 4.2); // outside the slider's [0, 0.95]
		node.put("markerSource", "banana");

		AppState st = new AppState();
		AppState defaults = new AppState();
		settings.bind(st);

		assertEquals(defaults.frac.get(), st.frac.get());
		assertEquals(defaults.avgN.get(), st.avgN.get());
		assertEquals(defaults.smoothing.get(), st.smoothing.get(), 1e-9);
		assertEquals(defaults.markerSource.get(), st.markerSource.get());
	}

	@Nested
	class PrefsWindowBounds
	{
		@Test
		void roundTripsWhatWasStored()
		{
			settings.putPrefsWindow(120, 80, 500, 640);

			Settings.WindowBounds b = new Settings(node).prefsWindow();
			assertNotNull(b);
			assertEquals(120, b.x(), 1e-9);
			assertEquals(80, b.y(), 1e-9);
			assertEquals(500, b.w(), 1e-9);
			assertEquals(640, b.h(), 1e-9);
		}

		@Test
		void nothingStoredMeansNoBounds()
		{
			assertNull(settings.prefsWindow(), "a first run should fall back to the default size");
		}

		@Test
		void anUnusableStoredSizeIsIgnored()
		{
			// Hand-edited or written by a build that allowed it: too small to
			// show the controls, so the default is the better answer.
			node.putDouble("prefsWinX", 100);
			node.putDouble("prefsWinY", 100);
			node.putDouble("prefsWinW", 40);
			node.putDouble("prefsWinH", 30);

			assertNull(settings.prefsWindow());
		}

		@Test
		void aPartialRecordIsIgnored()
		{
			// Size but no position — all four or nothing.
			node.putDouble("prefsWinW", 500);
			node.putDouble("prefsWinH", 640);

			assertNull(settings.prefsWindow());
		}

		@Test
		void nonsenseIsNeverWritten()
		{
			settings.putPrefsWindow(Double.NaN, 0, 500, 640);
			settings.putPrefsWindow(0, 0, Double.POSITIVE_INFINITY, 640);

			assertNull(settings.prefsWindow());
		}

		@Test
		void resetForgetsTheWindowToo()
		{
			settings.putPrefsWindow(120, 80, 500, 640);
			settings.reset(new AppState());

			assertNull(settings.prefsWindow());
		}
	}

	@Test
	void savedLiveInputIsDroppedWhenTheInterfaceIsGone()
	{
		node.put("micChan", "live:Scarlett 18i20");
		node.putInt("micChanIdx", 7);

		// nothing enumerated: different venue, device absent
		AppState st = new AppState();
		settings.bind(st);

		assertEquals(new AppState().micChan.get(), st.micChan.get(),
			"a missing device should leave the simulated preset selected");
		assertFalse(st.isMicLive());
		assertEquals(0, st.micChanIdx.get(), "the channel index goes with the device");
	}

	@Test
	void savedLiveInputIsRestoredWhenTheInterfaceIsPresent()
	{
		node.put("micChan", "live:Scarlett 18i20");
		node.putInt("micChanIdx", 5);

		AppState st = new AppState();
		st.audioDevices.add(new AudioDevice("Scarlett 18i20", "Focusrite Scarlett 18i20", null));
		settings.bind(st);

		assertEquals("live:Scarlett 18i20", st.micChan.get());
		assertTrue(st.isMicLive());
		assertEquals(5, st.micChanIdx.get());
	}

	@Test
	void savedPresetThatNoLongerExistsIsIgnored()
	{
		node.put("soloChan", "preset-from-an-older-build");

		AppState st = new AppState();
		settings.bind(st);

		assertEquals(new AppState().soloChan.get(), st.soloChan.get());
	}

	@Test
	void resetRestoresDefaultsAndEmptiesTheStore() throws Exception
	{
		AppState st = new AppState();
		settings.bind(st);
		st.splOffset.set(99);
		st.frac.set(1);
		st.markerSource.set("solo");
		st.soloTrim.set(9);

		settings.reset(st);

		AppState defaults = new AppState();
		assertEquals(defaults.splOffset.get(), st.splOffset.get(), 1e-9);
		assertEquals(defaults.frac.get(), st.frac.get());
		assertEquals(defaults.markerSource.get(), st.markerSource.get());
		assertEquals(defaults.soloTrim.get(), st.soloTrim.get(), 1e-9);
		// Applying the defaults fires the persist listeners, so this also pins
		// down that the store is wiped afterwards rather than left holding
		// explicit copies of the defaults.
		assertEquals(0, node.keys().length, "reset should leave nothing stored");
	}
}
