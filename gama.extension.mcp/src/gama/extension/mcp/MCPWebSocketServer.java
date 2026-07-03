package gama.extension.mcp;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import gama.dev.DEBUG;

/**
 * A dedicated, isolated WebSocket server for the GAMA MCP protocol.
 * This does NOT extend GamaServer to avoid any interference with GAMA's
 * own WebSocket server infrastructure.
 */
public class MCPWebSocketServer extends WebSocketServer {

	static {
		DEBUG.ON();
	}

	private final MCPToolHandler toolHandler;
	private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());
	private final CountDownLatch startLatch = new CountDownLatch(1);
	private final AtomicBoolean startedSuccessfully = new AtomicBoolean(false);

	public MCPWebSocketServer(final int port, final MCPToolHandler handler) {
		super(new InetSocketAddress("localhost", port));
		this.toolHandler = handler;
		this.setReuseAddr(true);
	}

	@Override
	public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
		connections.add(conn);
		DEBUG.OUT("[MCP] Client connected: " + conn.getRemoteSocketAddress());
		DEBUG.OUT("[MCP] Sending connection success");
		// Send a connection confirmation compatible with what clients expect
		conn.send("{\"type\":\"ConnectionSuccessful\",\"content\":\"mcp_ready\"}");
	}

	@Override
	public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
		connections.remove(conn);
		DEBUG.OUT("[MCP] Client disconnected: " + conn.getRemoteSocketAddress());
	}

	@Override
	public void onMessage(final WebSocket conn, final String message) {
		DEBUG.OUT("[MCP] Received from " + conn.getRemoteSocketAddress() + ": " + message);
		toolHandler.handleRequest(conn.toString(), message);
	}

	@Override
	public void onError(final WebSocket conn, final Exception ex) {
		if (conn == null) {
			// Server-level error (e.g. BindException) - signal startup failure
			startLatch.countDown();
		}
		if (ex instanceof java.net.BindException) {
			DEBUG.ERR("[MCP] *** FATAL: Port " + getPort() + " is already in use! ***");
			DEBUG.ERR("[MCP] GAMA's own WebSocket server may be running on this port.");
			DEBUG.ERR("[MCP] Use a different port in your GAML model, e.g: do connect protocol: 'mcp_server' port: 8082;");
		} else {
			DEBUG.ERR("[MCP] Error: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	@Override
	public void onStart() {
		startedSuccessfully.set(true);
		startLatch.countDown();
		DEBUG.OUT("[MCP] WebSocket MCP server started on port " + getPort());
		setConnectionLostTimeout(100);
	}

	/**
	 * Block until the server has started (or failed) — up to timeoutMs milliseconds.
	 * @return true if the server bound successfully, false if it failed or timed out.
	 */
	public boolean waitForStartup(final long timeoutMs) {
		try {
			boolean reached = startLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
			return reached && startedSuccessfully.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Send a message to a specific client identified by its connection string.
	 */
	public void sendToClient(final String clientId, final String msg) {
		synchronized (connections) {
			for (WebSocket conn : connections) {
				if (conn.toString().equals(clientId)) {
					conn.send(msg);
					return;
				}
			}
		}
		// Fallback: broadcast if we can't find the specific client
		broadcast(msg);
	}

	/**
	 * Broadcast to all connected clients.
	 */
	public void broadcastMessage(final String msg) {
		broadcast(msg);
	}

	public Collection<WebSocket> getConnections() {
		return Collections.unmodifiableSet(connections);
	}
}
