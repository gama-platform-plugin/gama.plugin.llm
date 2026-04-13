/*******************************************************************************************************
 *
 * PredicateType.java, in gama.extension.bdi, is part of the source code of the GAMA modeling and
 * simulation platform .
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.plugin.types;

import gama.annotations.doc;
import gama.annotations.type;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.GamaType;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.ITypesManager;
import gama.api.runtime.scope.IScope;
import gama.api.types.map.IMap;
import gama.plugin.constants.LLMConstants;

/**
 * The Class Memory.
 */
@type (
		name = "mcp_transport",
		id = MCPTransportType.id,
		wraps = { MCPTransport.class },
		concept = { IConcept.TYPE, LLMConstants.LLM_MODEL })
@doc ("represents a MCP Transport that defines how messages are exchanged between an agent and an assistant, allowing customizable communication mechanisms")
public class MCPTransportType extends GamaType<MCPTransport> {

	public MCPTransportType(ITypesManager typesManager) {
		super(typesManager);
		// TODO Auto-generated constructor stub
	}

	/** The Constant id. */
	public final static int id = IType.TYPE_ID + 985635221;

	@Override
	public boolean canCastToConst() {
		return true;
	}

	@Override
	@doc ("cast an object as a transport")
	public MCPTransport cast(final IScope scope, final Object obj, final Object val, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof MCPTransport p) return p;
		
		return null; 
	}

	@Override
	public MCPTransport getDefault() { return null; }

	@Override
	public MCPTransport deserializeFromJson(final IScope scope, final IMap<String, Object> map2) {
		return null;
	}

}
