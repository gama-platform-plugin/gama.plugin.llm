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

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
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
public class Memory implements IValue {

	
	
	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType(),"memory", memory);
	}

	ChatMemory memory = null;
	/**
	 * Instantiates a new predicate.
	 */
	
	
	
	public Memory(ChatModel model, Integer maxTokens) {
		super();
		memory= TokenWindowChatMemory.withMaxTokens(((maxTokens != null) && (maxTokens > 0)) ? maxTokens : 1000, new OpenAiTokenCountEstimator(GPT_4_O_MINI));
		model.setMemory(this);
	}
	
	public synchronized ChatMemory getMemory() {
		return memory;
	}

	public synchronized void addToMemory(String msgToAdd) {
		if (msgToAdd == null) return;
		SystemMessage systemMessage = SystemMessage.from(msgToAdd);
		memory.add(systemMessage); 
	}
	
	public Memory(Memory p) {
		p.memory.messages();
		
		
	}
	



	@Override
	public String toString() { 
		return "memory(" + memory.messages().toString() +")";
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
	public Memory copy(final IScope scope) throws GamaRuntimeException {
		return new Memory(this);
	}

	

	@Override
	public int hashCode() {
		return memory.hashCode();
	} 

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		final Memory other = (Memory) obj;
		return memory.equals(other.memory); 
	}

	/**
	 * Method getType()
	 *
	 * @see gama.core.common.interfaces.ITyped#getGamlType()
	 */
	@Override
	public IType<?> getGamlType() { return Types.get(MemoryType.id); }

}
