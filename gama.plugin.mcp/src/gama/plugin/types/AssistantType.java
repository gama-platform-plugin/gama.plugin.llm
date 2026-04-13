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
		name = "assistant",
		id = AssistantType.id,
		wraps = { Assistant.class },
		concept = { IConcept.TYPE, LLMConstants.LLM_MODEL })
@doc ("represents a LLM assistant, enabling structured interactions with chat models by incorporating memory, RAG, and executor tools")
public class AssistantType extends GamaType<Assistant> {

	public AssistantType(ITypesManager typesManager) {
		super(typesManager);
		// TODO Auto-generated constructor stub
	}

	/** The Constant id. */
	public final static int id = IType.TYPE_ID + 51715234;

	@Override
	public boolean canCastToConst() {
		return true;
	}

	@Override
	@doc ("cast an object as an assistant")
	public Assistant cast(final IScope scope, final Object obj, final Object val, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof Assistant p) return p;
		
		return null; 
	}

	@Override
	public Assistant getDefault() { return null; }

	@Override
	public Assistant deserializeFromJson(final IScope scope, final IMap<String, Object> map2) {
		return null;
	}

}
