package gama.extension.mcp;

import java.io.IOException;
import java.net.UnknownHostException;

import gama.api.kernel.agent.IAgent;
import gama.dev.DEBUG;
import gama.extension.network.common.IConnector;
import gama.extension.network.common.socket.SocketService;

/**
 * MCPServerService manages the lifecycle of the GAMA MCP WebSocket server.
 * It implements SocketService directly (NOT extending ServerService which is TCP-specific).
 */
public class MCPServerService implements SocketService {

	static {
		DEBUG.ON();
	}

	private MCPWebSocketServer mcpWebSocketServer;
	private final MCPToolHandler toolHandler;
	private final int port;
	private final IConnector connector;
	private volatile boolean online = false;

	public MCPServerService(final IAgent agent, final int port, final IConnector conn) {
		this.port = port;
		this.connector = conn;
		this.toolHandler = new MCPToolHandler(this);
	}

	@Override
	public void startService() throws UnknownHostException, IOException {
		DEBUG.OUT("[MCP] Attempting to start MCP WebSocket server on port " + port + " ...");
		try {
			this.mcpWebSocketServer = new MCPWebSocketServer(port, toolHandler);
			this.mcpWebSocketServer.start();
			// Wait up to 3 seconds for the server to actually bind
			boolean started = this.mcpWebSocketServer.waitForStartup(3000);
			if (started) {
				this.online = true;
				DEBUG.OUT("[MCP] MCP WebSocket server is running on port " + port);
			} else {
				this.online = false;
				DEBUG.ERR("[MCP] *** MCP server FAILED to start on port " + port + " ***");
				DEBUG.ERR("[MCP] Port may already be in use. Try a different port.");
				throw new IOException("MCP Server failed to bind to port " + port);
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			DEBUG.ERR("[MCP] *** Failed to start MCP server: " + e.getMessage() + " ***");
			throw new IOException("MCP Server failed to start", e);
		}
	}

	@Override
	public void stopService() {
		this.online = false;
		if (mcpWebSocketServer != null) {
			try {
				mcpWebSocketServer.stop(1000);
				DEBUG.OUT("[MCP] MCP Server stopped.");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public void receivedMessage(final String sender, final String message) {
		toolHandler.handleRequest(sender, message);
	}

	@Override
	public void sendMessage(final String msg) throws IOException {
		if (mcpWebSocketServer == null || !online) return;
		mcpWebSocketServer.broadcastMessage(msg);
	}

	@Override
	public void sendMessage(final String msg, final String receiver) throws IOException {
		if (mcpWebSocketServer == null || !online) return;
		mcpWebSocketServer.sendToClient(receiver, msg);
	}

	public void sendMessageToClient(final String clientId, final String msg) {
		if (mcpWebSocketServer == null || !online) return;
		mcpWebSocketServer.sendToClient(clientId, msg);
	}

	@Override
	public boolean isOnline() {
		return online;
	}

	@Override
	public String getRemoteAddress() {
		return "mcp://localhost:" + port;
	}

	@Override
	public String getLocalAddress() {
		return "mcp://localhost:" + port;
	}

	public IConnector getConnector() {
		return connector;
	}
}
