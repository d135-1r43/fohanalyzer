package com.fohanalyzer.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The real server on a free loopback port, driven by the SDK's own client. */
class McpHttpServerTest
{
	private final AnalyzerHistory history = new AnalyzerHistory();
	private final McpHttpServer server = new McpHttpServer(history);

	@BeforeEach
	void setUp() throws Exception
	{
		server.start(0);
	}

	@AfterEach
	void tearDown()
	{
		server.stop();
	}

	@Test
	void listsAndCallsEveryTool()
	{
		history.recordRing(2500, "D#7", 2500, 6);

		try (McpSyncClient client = client())
		{
			client.initialize();
			assertEquals("fohanalyzer", client.getServerInfo().name());

			List<String> names = client.listTools().tools().stream().map(Tool::name).toList();
			assertEquals(List.of(McpTools.RING_EVENTS, McpTools.STATS_HISTORY, McpTools.BAND_SNAPSHOT), names);

			CallToolResult rings = client.callTool(new CallToolRequest(McpTools.RING_EVENTS, Map.of()));
			assertNotEquals(Boolean.TRUE, rings.isError());
			assertEquals(1, ((Map<?, ?>)rings.structuredContent()).get("count"));

			CallToolResult stats = client.callTool(new CallToolRequest(McpTools.STATS_HISTORY, Map.of("minutes", 2)));
			assertNotEquals(Boolean.TRUE, stats.isError());

			CallToolResult bands = client.callTool(new CallToolRequest(McpTools.BAND_SNAPSHOT, Map.of()));
			assertEquals(false, ((Map<?, ?>)bands.structuredContent()).get("available"));
		}
	}

	@Test
	void rejectsAForeignOrigin() throws Exception
	{
		HttpRequest request = HttpRequest.newBuilder(URI.create(server.url()))
			.header("Origin", "http://evil.example")
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
			.build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request,
			HttpResponse.BodyHandlers.ofString());
		assertEquals(403, response.statusCode());
	}

	@Test
	void bindsLoopbackOnly()
	{
		assertTrue(server.url().startsWith("http://127.0.0.1:"));
		assertTrue(server.port() > 0);
	}

	@Test
	void stopReleasesThePort()
	{
		server.stop();
		assertFalse(server.isRunning());
		assertNull(server.url());
	}

	private McpSyncClient client()
	{
		return McpClient.sync(HttpClientStreamableHttpTransport
			.builder("http://127.0.0.1:" + server.port())
			.endpoint(McpHttpServer.ENDPOINT)
			.build())
			.build();
	}
}
