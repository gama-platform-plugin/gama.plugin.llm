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
		name = "chat_model",
		id = ChatModelType.id,
		wraps = { ChatModel.class },
		concept = { IConcept.TYPE, LLMConstants.LLM_MODEL })
@doc ("represents a chat model (e.g., LLaMA, OpenAI) that allows sending messages and receiving responses in a conversational manner")
public class ChatModelType extends GamaType<ChatModel> {

	public ChatModelType(ITypesManager typesManager) {
		super(typesManager);
		// TODO Auto-generated constructor stub
	}

	/** The Constant id. */
	public final static int id = IType.TYPE_ID + 833237362;

	@Override
	public boolean canCastToConst() {
		return true;
	}

	@Override
	@doc ("cast an object as a chat_model")
	public ChatModel cast(final IScope scope, final Object obj, final Object val, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof ChatModel p) return p;
		
		return null; 
	}

	@Override
	public ChatModel getDefault() { return null; }

	@Override
	public ChatModel deserializeFromJson(final IScope scope, final IMap<String, Object> map2) {
		return null;
	}

}
