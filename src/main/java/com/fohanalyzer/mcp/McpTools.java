package com.fohanalyzer.mcp;

import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.engine.Zone;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The read-only tools the MCP server offers over an {@link AnalyzerHistory}.
 *
 * <p>
 * Every result carries its data twice: as {@code structuredContent} matching
 * the tool's {@code outputSchema}, and serialised into a text block for clients
 * that only read text, as the MCP spec recommends. Numbers are rounded to what
 * the analyzer can actually resolve, which also keeps the payload small, and
 * anything non-finite becomes {@code null} because JSON has no NaN.
 */
public final class McpTools
{
	static final String RING_EVENTS = "get_ring_events";
	static final String STATS_HISTORY = "get_stats_history";
	static final String BAND_SNAPSHOT = "get_band_snapshot";

	static final int DEFAULT_MINUTES = 5;
	static final int DOMINANT_PEAKS = 5;

	private static final ToolAnnotations READ_ONLY = new ToolAnnotations(null, true, false, null, false, null);

	static final String RING_EVENTS_INPUT = """
		{
		  "type": "object",
		  "properties": {
		    "since_minutes": {
		      "type": "number",
		      "exclusiveMinimum": 0,
		      "description": "Only rings logged within this many minutes. Omit for all of them."
		    }
		  },
		  "additionalProperties": false
		}
		""";

	static final String RING_EVENTS_OUTPUT = """
		{
		  "type": "object",
		  "properties": {
		    "count": { "type": "integer" },
		    "events": {
		      "type": "array",
		      "items": {
		        "type": "object",
		        "properties": {
		          "time": { "type": "string", "description": "ISO-8601 instant" },
		          "freq_hz": { "type": "number" },
		          "note": { "type": "string" },
		          "zone": { "type": "string" },
		          "geq_band_hz": { "type": "number", "description": "Nearest ISO 1/3-octave GEQ band" },
		          "suggested_cut_db": { "type": "integer" }
		        },
		        "required": ["time", "freq_hz", "note", "zone", "geq_band_hz", "suggested_cut_db"]
		      }
		    }
		  },
		  "required": ["count", "events"]
		}
		""";

	static final String STATS_HISTORY_INPUT = """
		{
		  "type": "object",
		  "properties": {
		    "minutes": {
		      "type": "number",
		      "minimum": 0.1,
		      "maximum": 30,
		      "description": "How far back to look. Defaults to 5; at most 30."
		    }
		  },
		  "additionalProperties": false
		}
		""";

	static final String STATS_HISTORY_OUTPUT = """
		{
		  "type": "object",
		  "properties": {
		    "interval_seconds": { "type": "number" },
		    "count": { "type": "integer" },
		    "dominant_peaks": {
		      "type": "array",
		      "description": "Mic peak frequencies by how many samples they were the loudest band, most first",
		      "items": {
		        "type": "object",
		        "properties": {
		          "freq_hz": { "type": "number" },
		          "zone": { "type": "string" },
		          "samples": { "type": "integer" }
		        },
		        "required": ["freq_hz", "zone", "samples"]
		      }
		    },
		    "samples": {
		      "type": "array",
		      "items": {
		        "type": "object",
		        "properties": {
		          "time": { "type": "string" },
		          "peak_hz": { "type": ["number", "null"] },
		          "mic_peak_db": { "type": ["number", "null"] },
		          "mic_avg_db": { "type": ["number", "null"] },
		          "solo_avg_db": { "type": ["number", "null"] },
		          "solo_peak_db": { "type": ["number", "null"] },
		          "mic_rms_dbfs": { "type": ["number", "null"] },
		          "spl_db": { "type": ["number", "null"] },
		          "mic_live": { "type": "boolean" },
		          "solo_live": { "type": "boolean" }
		        },
		        "required": ["time", "mic_live", "solo_live"]
		      }
		    }
		  },
		  "required": ["interval_seconds", "count", "dominant_peaks", "samples"]
		}
		""";

	static final String BAND_SNAPSHOT_INPUT = """
		{ "type": "object", "properties": {}, "additionalProperties": false }
		""";

	static final String BAND_SNAPSHOT_OUTPUT = """
		{
		  "type": "object",
		  "properties": {
		    "available": { "type": "boolean" },
		    "time": { "type": "string" },
		    "resolution": { "type": "string" },
		    "mic_live": { "type": "boolean" },
		    "solo_live": { "type": "boolean" },
		    "bands": {
		      "type": "array",
		      "items": {
		        "type": "object",
		        "properties": {
		          "freq_hz": { "type": "number" },
		          "zone": { "type": "string" },
		          "mic_db": { "type": ["number", "null"] },
		          "solo_db": { "type": ["number", "null"] },
		          "transfer_db": { "type": ["number", "null"], "description": "mic minus solo" }
		        },
		        "required": ["freq_hz", "zone", "mic_db", "solo_db", "transfer_db"]
		      }
		    }
		  },
		  "required": ["available"]
		}
		""";

	private final AnalyzerHistory history;
	private final McpJsonMapper json;

	public McpTools(AnalyzerHistory history, McpJsonMapper json)
	{
		this.history = history;
		this.json = json;
	}

	public List<SyncToolSpecification> specifications()
	{
		return List.of(
			spec(RING_EVENTS, "Ring-out history",
				"Feedback rings the engineer logged with Detect during this session, oldest first."
					+ " A frequency that keeps coming back is a candidate for a permanent notch"
					+ " or a GEQ cut at the listed band.",
				RING_EVENTS_INPUT, RING_EVENTS_OUTPUT, this::ringEvents),
			spec(STATS_HISTORY, "Spectrum readout history",
				"Once-a-second readouts (loudest mic band, mic/solo averages and peaks, SPL) for"
					+ " the last minutes, plus the frequencies that were loudest most often."
					+ " Samples with mic_live=false come from the built-in simulator, not a real mic.",
				STATS_HISTORY_INPUT, STATS_HISTORY_OUTPUT, this::statsHistory),
			spec(BAND_SNAPSHOT, "Current band levels",
				"The per-band levels on screen right now for the measurement mic and the solo bus,"
					+ " and their transfer (mic minus solo), which shows what the room and the PA"
					+ " add to the source. Levels are relative dB, not SPL.",
				BAND_SNAPSHOT_INPUT, BAND_SNAPSHOT_OUTPUT, this::bandSnapshot));
	}

	private SyncToolSpecification spec(String name, String title, String description, String input,
		String output, Function<Map<String, Object>, Map<String, Object>> handler)
	{
		Tool tool = Tool.builder()
			.name(name)
			.title(title)
			.description(description)
			.inputSchema(json, input)
			.outputSchema(json, output)
			.annotations(READ_ONLY)
			.build();
		return SyncToolSpecification.builder()
			.tool(tool)
			.callHandler((exchange, request) -> result(handler.apply(arguments(request))))
			.build();
	}

	private static Map<String, Object> arguments(CallToolRequest request)
	{
		return request.arguments() != null ? request.arguments() : Map.of();
	}

	private CallToolResult result(Map<String, Object> structured)
	{
		try
		{
			return CallToolResult.builder()
				.structuredContent(structured)
				.addTextContent(json.writeValueAsString(structured))
				.build();
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	// ---- tools ------------------------------------------------------------

	Map<String, Object> ringEvents(Map<String, Object> args)
	{
		Double since = number(args.get("since_minutes"));
		Duration window = since != null && since > 0 ? minutes(since) : null;

		List<Map<String, Object>> events = new ArrayList<>();
		for (AnalyzerHistory.RingEvent e : history.ringEvents(window))
		{
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("time", e.at().toString());
			m.put("freq_hz", round(e.freq(), 1));
			m.put("note", e.note());
			m.put("zone", zone(e.freq()));
			m.put("geq_band_hz", round(e.band(), 1));
			m.put("suggested_cut_db", e.cut());
			events.add(m);
		}

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("count", events.size());
		out.put("events", events);
		return out;
	}

	Map<String, Object> statsHistory(Map<String, Object> args)
	{
		Double requested = number(args.get("minutes"));
		double mins = Math.clamp(requested != null ? requested : DEFAULT_MINUTES, 0.1,
			AnalyzerHistory.STATS_RETENTION.toMinutes());

		List<AnalyzerHistory.StatsSample> samples = history.stats(minutes(mins));
		List<Map<String, Object>> rows = new ArrayList<>();
		Map<Double, Integer> peakCounts = new TreeMap<>();
		for (AnalyzerHistory.StatsSample s : samples)
		{
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("time", s.at().toString());
			m.put("peak_hz", round(s.peakFreq(), 1));
			m.put("mic_peak_db", round(s.micPeak(), 1));
			m.put("mic_avg_db", round(s.micAvg(), 1));
			m.put("solo_avg_db", round(s.soloAvg(), 1));
			m.put("solo_peak_db", round(s.soloPeak(), 1));
			m.put("mic_rms_dbfs", s.micRmsDbfs() != null ? round(s.micRmsDbfs(), 1) : null);
			m.put("spl_db", s.splDb() != null ? round(s.splDb(), 1) : null);
			m.put("mic_live", s.micLive());
			m.put("solo_live", s.soloLive());
			rows.add(m);

			if (Double.isFinite(s.peakFreq())) peakCounts.merge(s.peakFreq(), 1, Integer::sum);
		}

		List<Map<String, Object>> dominant = peakCounts.entrySet().stream()
			.sorted(Map.Entry.<Double, Integer> comparingByValue(Comparator.reverseOrder()))
			.limit(DOMINANT_PEAKS)
			.map(en -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("freq_hz", round(en.getKey(), 1));
				m.put("zone", zone(en.getKey()));
				m.put("samples", en.getValue());
				return m;
			})
			.toList();

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("interval_seconds", AnalyzerHistory.STATS_INTERVAL.toMillis() / 1000.0);
		out.put("count", rows.size());
		out.put("dominant_peaks", dominant);
		out.put("samples", rows);
		return out;
	}

	Map<String, Object> bandSnapshot(Map<String, Object> args)
	{
		AnalyzerHistory.BandSnapshot snap = history.bands();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("available", snap != null);
		if (snap == null) return out;

		List<Map<String, Object>> bands = new ArrayList<>();
		for (int i = 0; i < snap.centers().length; i++)
		{
			double mic = snap.mic()[i];
			double solo = snap.solo()[i];
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("freq_hz", round(snap.centers()[i], 1));
			m.put("zone", zone(snap.centers()[i]));
			m.put("mic_db", round(mic, 1));
			m.put("solo_db", round(solo, 1));
			m.put("transfer_db", round(mic - solo, 1));
			bands.add(m);
		}

		out.put("time", snap.at().toString());
		out.put("resolution", snap.frac() == 1 ? "1 octave" : "1/" + snap.frac() + " octave");
		out.put("mic_live", snap.micLive());
		out.put("solo_live", snap.soloLive());
		out.put("bands", bands);
		return out;
	}

	// ---- helpers ----------------------------------------------------------

	/**
	 * The colour-strip zone {@code f} falls in, clamped to the ends of the
	 * strip.
	 */
	static String zone(double f)
	{
		List<Zone> zones = Engine.ZONES;
		for (Zone z : zones)
		{
			if (f < z.f1()) return z.name();
		}
		return zones.getLast().name();
	}

	private static Duration minutes(double m)
	{
		return Duration.ofMillis(Math.round(m * 60_000));
	}

	private static Double number(Object o)
	{
		return o instanceof Number n ? n.doubleValue() : null;
	}

	private static Double round(double v, int decimals)
	{
		if (!Double.isFinite(v)) return null;
		double scale = Math.pow(10, decimals);
		return Math.round(v * scale) / scale;
	}
}
