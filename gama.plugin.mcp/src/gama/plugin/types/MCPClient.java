/*******************************************************************************************************
 *
 * Predicate.java, in gama.extension.bdi, is part of the source code of the GAMA modeling and simulation
 * platform .
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.plugin.types;

import dev.langchain4j.mcp.client.McpClient;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.misc.IValue;
import gama.api.utils.json.IJson;
import gama.api.utils.json.IJsonValue;
import gama.core.util.json.Json;
import gama.core.util.json.JsonValue;

/**
 * The Class Predicate.
 */
public class MCPClient implements IValue {

	
	
	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType(),"client", client);
	}

	McpClient client = null;
	/**
	 * Instantiates a new predicate.
	 */
	
	public MCPClient(ChatModel model, Integer maxTokens) {
		super();
	}
	
		
	public MCPClient(MCPClient p) {
		
		
		
	}
	



	@Override
	public String toString() { 
		return "memory(" + client.toString() +")";
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return toString();
	}

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		return toString();
	}

	@Override
	public MCPClient copy(final IScope scope) throws GamaRuntimeException {
		return new MCPClient(this);
	}

	

	@Override
	public int hashCode() {
		return client.hashCode();
	} 

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		final MCPClient other = (MCPClient) obj;
		return client.equals(other.client); 
	}

	/**
	 * Method getType()
	 *
	 * @see gama.core.common.interfaces.ITyped#getGamlType()
	 */
	@Override
	public IType<?> getGamlType() { return Types.get(MCPClientType.id); }

}
