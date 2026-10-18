package com.fohanalyzer.mcp;

import com.fohanalyzer.Version;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The MCP server: Streamable HTTP at {@code http://127.0.0.1:<port>/mcp},
 * hosted in embedded Jetty, serving {@link McpTools} over an
 * {@link AnalyzerHistory}.
 *
 * <p>
 * It binds to loopback only and checks the {@code Origin} and {@code Host}
 * headers, as the MCP spec asks of a local HTTP server: a web page open in a
 * browser on the same machine could otherwise reach it through DNS rebinding. A
 * command-line client sends no {@code Origin}, which is allowed.
 */
public final class McpHttpServer
{
	private static final Logger log = LoggerFactory.getLogger(McpHttpServer.class);

	public static final String ENDPOINT = "/mcp";

	private static final String INSTRUCTIONS = """
		FOHanalyzer is a real-time dual-channel spectrum analyzer used by a front-of-house \
		sound engineer during soundcheck and a show. "mic" is the measurement microphone in \
		the room; "solo" is the console's solo bus, i.e. the source being listened to. \
		Transfer (mic minus solo) is what the PA and the room add. Ring events are feedback \
		frequencies the engineer logged. Data flagged not live comes from a built-in \
		simulator and must not be read as a real measurement. Use these tools to give short, \
		practical mixing tips: recurring rings, build-up in a frequency region, level trends.""";

	private final AnalyzerHistory history;
	private Server jetty;
	private McpSyncServer mcp;

	public McpHttpServer(AnalyzerHistory history)
	{
		this.history = history;
	}

	/**
	 * Start listening on {@code port} (0 picks a free one). Stops a running
	 * instance first, so this also serves as a restart.
	 */
	public synchronized void start(int port) throws Exception
	{
		stop();

		McpJsonMapper json = McpJsonDefaults.getMapper();
		HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider
			.builder()
			.jsonMapper(json)
			.mcpEndpoint(ENDPOINT)
			.securityValidator(DefaultServerTransportSecurityValidator.builder()
				.allowedOrigins(List.of("http://localhost:*", "http://127.0.0.1:*"))
				.allowedHosts(List.of("localhost:*", "127.0.0.1:*"))
				.build())
			.build();

		McpSyncServer server = McpServer.sync(transport)
			.serverInfo("fohanalyzer", Version.VALUE)
			.instructions(INSTRUCTIONS)
			.capabilities(ServerCapabilities.builder().tools(false).build())
			.tools(new McpTools(history, json).specifications())
			.build();

		// Daemon threads, so a server that somehow outlives the window cannot
		// keep the JVM from exiting.
		QueuedThreadPool threads = new QueuedThreadPool();
		threads.setName("mcp-http");
		threads.setDaemon(true);
		Server http = new Server(threads);
		ServerConnector connector = new ServerConnector(http);
		connector.setHost("127.0.0.1");
		connector.setPort(port);
		http.addConnector(connector);

		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		ServletHolder holder = new ServletHolder(transport);
		// The transport streams responses as server-sent events.
		holder.setAsyncSupported(true);
		context.addServlet(holder, ENDPOINT);
		http.setHandler(context);

		try
		{
			http.start();
		}
		catch (Exception e)
		{
			server.close();
			http.stop();
			throw e;
		}
		jetty = http;
		mcp = server;
		log.info("MCP server listening on {}", url());
	}

	public synchronized void stop()
	{
		if (mcp != null)
		{
			mcp.closeGracefully();
			mcp = null;
		}
		if (jetty != null)
		{
			try
			{
				jetty.stop();
			}
			catch (Exception e)
			{
				log.warn("could not stop the MCP server", e);
			}
			jetty = null;
		}
	}

	public synchronized boolean isRunning()
	{
		return jetty != null;
	}

	/** The bound port, or -1 when not running. */
	public synchronized int port()
	{
		return jetty != null ? ((ServerConnector)jetty.getConnectors()[0]).getLocalPort() : -1;
	}

	/** The URL a client connects to, or {@code null} when not running. */
	public synchronized String url()
	{
		return jetty != null ? "http://127.0.0.1:" + port() + ENDPOINT : null;
	}
}
