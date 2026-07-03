package gama.extension.mcp;

import java.io.IOException;

import gama.api.kernel.agent.IAgent;
import gama.api.runtime.scope.IScope;
import gama.extension.network.common.Connector;
import gama.extension.network.common.GamaNetworkException;
import gama.extension.network.common.socket.SocketService;

public class MCPConnector extends Connector {

	private SocketService socket;
	private final boolean isServer;

	public MCPConnector(final IScope scope, final boolean isServer) {
		this.isServer = isServer;
		this.setRaw(true);
	}

	@Override
	protected void connectToServer(final IAgent agent) throws GamaNetworkException {
		final int port = Integer.parseInt(this.getConfigurationParameter(SERVER_PORT));
		if (this.isServer) {
			socket = new MCPServerService(agent, port, this);
		} else {
			throw new UnsupportedOperationException("MCP Client mode is not supported. Use MCP Server mode.");
		}
		try {
			socket.startService();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		this.setConnected();
	}

	@Override
	protected boolean isAlive(final IAgent agent) throws GamaNetworkException {
		return socket != null && socket.isOnline();
	}

	@Override
	protected void subscribeToGroup(final IAgent agt, final String boxName) throws GamaNetworkException {
		// No groups in MCP
	}

	@Override
	protected void unsubscribeGroup(final IAgent agt, final String boxName) throws GamaNetworkException {
		// No groups in MCP
	}

	@Override
	protected void releaseConnection(final IScope scope) throws GamaNetworkException {
		if (socket != null) {
			socket.stopService();
			socket = null;
		}
		this.isConnected = false;
	}

	@Override
	protected void sendMessage(final IAgent sender, final String receiver, final String content)
			throws GamaNetworkException {
		try {
			if (socket != null) {
				socket.sendMessage(content);
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public SocketService getSocketService() {
		return socket;
	}

}
