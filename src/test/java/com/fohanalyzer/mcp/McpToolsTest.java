package com.fohanalyzer.mcp;

import com.fohanalyzer.dsp.Stats;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tool output, checked against the schema each tool advertises. */
class McpToolsTest
{
	private final McpJsonMapper json = McpJsonDefaults.getMapper();
	private final JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();
	private final MutableClock clock = new MutableClock();
	private final AnalyzerHistory history = new AnalyzerHistory(clock);
	private final McpTools tools = new McpTools(history, json);

	@Test
	void emptyHistoryMatchesTheSchemas() throws IOException
	{
		assertValid(McpTools.RING_EVENTS_OUTPUT, tools.ringEvents(Map.of()));
		assertValid(McpTools.STATS_HISTORY_OUTPUT, tools.statsHistory(Map.of()));
		Map<String, Object> bands = tools.bandSnapshot(Map.of());
		assertValid(McpTools.BAND_SNAPSHOT_OUTPUT, bands);
		assertEquals(false, bands.get("available"));
	}

	@Test
	void ringEventsCarryZoneAndBand() throws IOException
	{
		history.recordRing(3162.3, "G7", 3150, 9);

		Map<String, Object> out = tools.ringEvents(Map.of());
		assertValid(McpTools.RING_EVENTS_OUTPUT, out);
		assertEquals(1, out.get("count"));
		Map<?, ?> e = (Map<?, ?>)((List<?>)out.get("events")).getFirst();
		assertEquals(3162.3, e.get("freq_hz"));
		assertEquals("UPPER-MID", e.get("zone"));
		assertEquals(9, e.get("suggested_cut_db"));
	}

	@Test
	void statsHistoryRanksDominantPeaks() throws IOException
	{
		for (int i = 0; i < 5; i++)
		{
			history.recordStats(new Stats(i < 3 ? 250 : 4000, -20, -40, -45, -25, 0, -18.0), 97.0, true, true);
			clock.advance(Duration.ofSeconds(1));
		}

		Map<String, Object> out = tools.statsHistory(Map.of("minutes", 1));
		assertValid(McpTools.STATS_HISTORY_OUTPUT, out);
		assertEquals(5, out.get("count"));
		List<?> dominant = (List<?>)out.get("dominant_peaks");
		assertEquals(250.0, ((Map<?, ?>)dominant.getFirst()).get("freq_hz"));
		assertEquals(3, ((Map<?, ?>)dominant.getFirst()).get("samples"));
	}

	@Test
	void outOfRangeMinutesAreClamped()
	{
		history.recordStats(new Stats(250, -20, -40, -45, -25, 0, null), null, false, false);
		assertEquals(1, tools.statsHistory(Map.of("minutes", 9999)).get("count"));
	}

	@Test
	void bandSnapshotComputesTransferAndHidesNonFiniteValues() throws IOException
	{
		history.recordBands(3, new double[] { 100, 1000 }, new float[] { -30, Float.NEGATIVE_INFINITY },
			new float[] { -36, -25 }, true, false);

		Map<String, Object> out = tools.bandSnapshot(Map.of());
		assertValid(McpTools.BAND_SNAPSHOT_OUTPUT, out);
		assertEquals("1/3 octave", out.get("resolution"));
		List<?> bands = (List<?>)out.get("bands");
		assertEquals(6.0, ((Map<?, ?>)bands.get(0)).get("transfer_db"));
		assertNull(((Map<?, ?>)bands.get(1)).get("mic_db"));
		assertNull(((Map<?, ?>)bands.get(1)).get("transfer_db"));
	}

	@Test
	void zonesClampToTheEndsOfTheStrip()
	{
		assertEquals("SUB", McpTools.zone(10));
		assertEquals("MID", McpTools.zone(1000));
		assertEquals("AIR", McpTools.zone(22000));
	}

	/** Round-trip through JSON first, as the client would see it. */
	private void assertValid(String schema, Map<String, Object> content) throws IOException
	{
		Map<String, Object> schemaMap = json.readValue(schema, new TypeRef<Map<String, Object>>()
		{
		});
		Object parsed = json.readValue(json.writeValueAsString(content), Object.class);
		JsonSchemaValidator.ValidationResponse r = validator.validate(schemaMap, parsed);
		assertTrue(r.valid(), r.errorMessage());
	}
}
