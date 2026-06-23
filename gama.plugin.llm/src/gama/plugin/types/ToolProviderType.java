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
import gama.api.runtime.scope.IScope;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.types.map.IMap;
import gama.plugin.constants.LLMConstants;
import gama.api.gaml.types.GamaType;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.ITypesManager;

/**
 * The Class PredicateType.
 */
@type (
		name = "tool_provider",
		id = ToolProviderType.id,
		wraps = { ToolProvider.class },
		concept = { IConcept.TYPE, LLMConstants.LLM_MODEL })
@doc ("represents a tool provider that is in charge of executing a GAMA action or an external tool when it is invoked by the assistant during a conversation")
public class ToolProviderType extends GamaType<ToolProvider> {

	public ToolProviderType(ITypesManager typesManager) {
		super(typesManager);
		// TODO Auto-generated constructor stub
	}

	/** The Constant id. */
	public final static int id = IType.TYPE_ID + 146657;

	@Override
	public boolean canCastToConst() {
		return true;
	}

	@Override
	@doc ("cast an object as a tool provider")
	public ToolProvider cast(final IScope scope, final Object obj, final Object val, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof ToolProvider p) return p;
		
		return null; 
	}

	@Override
	public ToolProvider getDefault() { return null; }

	@Override
	public ToolProvider deserializeFromJson(final IScope scope, final IMap<String, Object> map2) {
		return null;
	}

}
