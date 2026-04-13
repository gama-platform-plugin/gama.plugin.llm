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


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import gama.annotations.doc;
import gama.annotations.getter;
import gama.annotations.variable;
import gama.annotations.vars;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.misc.IValue;
import gama.api.utils.json.IJson;
import gama.api.utils.json.IJsonValue;
import gama.core.util.json.Json;
import gama.core.util.json.JsonValue;
import gama.plugin.constants.LLMConstants;

/**
 * The Class Predicate.
 */
@vars ({ @variable(name = LLMConstants.MODEL_TYPE, type = IType.STRING, doc = @doc("model_type specifies the chat model type: 'openai' or 'ollama'")),
	@variable(name = LLMConstants.MODEL_NAME, type = IType.STRING, doc = @doc("model_name specifies the exact name or identifier of the language model to be used for generating responses (e.g. 'gpt-4o-mini') ")),
	@variable(name = LLMConstants.MEMORY, type = IType.STRING, doc = @doc("memory represents the integrated memory")),
	@variable(name = LLMConstants.OLLAMA_URL, type = IType.STRING, doc = @doc("url specifies the endpoint URL of the local or remote Ollama server that the model communicates with (for Ollama)")), // "http://localhost:11434"
	@variable(name = LLMConstants.API_KEY, type = IType.STRING, doc = @doc("key refers to the API key used to authenticate requests to the OpenAI service (for OpenAi)")),
	@variable(name = LLMConstants.RESPONSE_FORMAT, type = IType.STRING, doc = @doc("response_format specifies the format in which the model should return its output, such as plain text or structured JSON. 2 possible values: 'json' or 'text' (by default)")),
	@variable(name = LLMConstants.NUM_CTX, type = IType.INT, doc = @doc("num_ctx specifies the maximum number of context tokens the model can use to process a prompt, including instructions, documents, and conversation history (for Ollama)")),
	@variable(name = LLMConstants.NUM_PREDICT, type = IType.INT, doc = @doc("num_predict specifies the maximum number of tokens the model is allowed to generate in its response (for Ollama)")),
	@variable(name = LLMConstants.REPEAT_PENALTY, type = IType.FLOAT, doc = @doc("repeat_penalty controls how strongly the model is discouraged from repeating the same tokens or phrases in its response (for Ollama)")),
	@variable(name = LLMConstants.SEED, type = IType.INT, doc = @doc("seed sets the random number generator seed to make the model’s output deterministic and reproducible")),
	@variable(name = LLMConstants.TEMPERATURE, type = IType.FLOAT, doc = @doc("temperature controls the randomness of the model’s output, with higher values producing more creative and varied responses")),
	@variable(name = LLMConstants.TOP_K, type = IType.INT, doc = @doc("top_k limits the model’s token selection to the top K most probable tokens, influencing the diversity and focus of the generated output (for Ollama)")),
	@variable(name = LLMConstants.TOP_P, type = IType.FLOAT, doc = @doc("top_p (nucleus sampling) sets the probability threshold for choosing the next token, allowing the model to sample from the most likely tokens whose cumulative probability exceeds this value")),
	@variable(name = LLMConstants.FREQUENCY_PENALTY, type = IType.FLOAT, doc = @doc("frequency_penalty reduces the likelihood of the model repeating tokens by penalizing tokens based on their frequency in the generated text (for OpenAI)")),
	@variable(name = LLMConstants.MAX_COMPLETION_TOKENS, type = IType.INT, doc = @doc("max_completion_tokens sets the maximum number of tokens the model can generate in its completion or response (for OpenAI)")),
	@variable(name = LLMConstants.MAX_RETRIES, type = IType.INT, doc = @doc("max_retries specifies the maximum number of times the system will retry a failed request to the model (for OpenAI)")),
	@variable(name = LLMConstants.MAX_TOKENS, type = IType.INT, doc = @doc("max_tokens defines the total maximum number of tokens allowed for both the input (prompt) and the output (completion) combined (for OpenAI)")),
	@variable(name = LLMConstants.PRESENCE_PENALTY, type = IType.FLOAT, doc = @doc("presence_penalty reduces the likelihood of the model mentioning new topics or tokens that have already appeared, encouraging more diverse and novel content (for OpenAI)")),
	@variable(name = LLMConstants.STORE, type = IType.BOOL, doc = @doc("store is a boolean that indicates whether the generated data (such as embeddings or chat history) should be saved or not (for OpenAI)")),
	@variable(name = LLMConstants.TIME_OUT, type = IType.INT, doc = @doc("timeout specifies the maximum amount of time the system will wait for a response from the model before aborting the request (for OpenAI)"))})
public class ChatModel implements IValue {

	private String modelType;
	private String modelName;
	private String OllamaUrl;
	private String ApiKey;
	private String responseFormat;
	private Integer numCtx;
	private Integer numPredict;
	private Double repeatPenalty;
	private Integer seed;
	private Double temperature;
	private Integer topK;
	private Double topP;
	private Double frequencyPenalty;
	private Integer maxCompletionTokens;
	private Integer maxRetries;
	private Integer maxTokens;
	private Double presencePenalty;
	private Boolean store;
	private Integer timeOut;
	private dev.langchain4j.model.chat.ChatModel model;
	private Memory memory;
	 
	
	public ChatModel(String modelnameToBuild, String urlToBuild, String responseFormat,Integer numCtx, Integer seed, Double temperature, Double topP, Integer numPredict, Double repeatPenalty,  Integer topK) {
		super();
		OllamaChatModelBuilder modelTobuild = OllamaChatModel.builder().baseUrl(urlToBuild)
				.modelName(modelnameToBuild);// "llama3.2"

		if (responseFormat != null) {
			modelTobuild = modelTobuild.responseFormat(responseFormat.toString().toLowerCase().equals("json")
					? dev.langchain4j.model.chat.request.ResponseFormat.JSON
					: dev.langchain4j.model.chat.request.ResponseFormat.TEXT);

			this.responseFormat = responseFormat;
		}
		
		if (numCtx != null) {
			modelTobuild = modelTobuild.numCtx(numCtx);
			this.numCtx = numCtx;
			
		}
		if (numPredict != null) {
			modelTobuild = modelTobuild.numPredict(numPredict);
			this.numPredict = numPredict;
		}
		if (repeatPenalty != null) {
			modelTobuild = modelTobuild.repeatPenalty(repeatPenalty);
			this.repeatPenalty = repeatPenalty;
		}
		
		if (seed != null) {
			modelTobuild = modelTobuild.seed(seed);
			this.seed = seed;
		}
		
		if (temperature != null) {
			modelTobuild = modelTobuild.temperature(temperature);
			this.temperature = temperature;
		}
		if (topP != null) {
			modelTobuild = modelTobuild.topP(topP);
			this.topP = topP;
		}
		if (topK != null) {
			modelTobuild = modelTobuild.topK(topK);
			this.topK = topK;
		}
		modelTobuild=modelTobuild.timeout(Duration.ofMillis(1200000));
		model = modelTobuild.logRequests(true).build();
		

	} 
	
	public ChatModel(String keyToBuild, String modelnameToBuild, String responseFormat, Double frequencyPenalty, Integer maxCompletionTokens, Integer maxRetries, Integer maxTokens, Double presencePenalty, Integer seed, Boolean store,  Double temperature, Integer timeout,  Double topP  ) {
		super();

		OpenAiChatModelBuilder modelTobuild = OpenAiChatModel.builder().apiKey(keyToBuild)
				.modelName(modelnameToBuild); // "gpt-4o-mini"

		if (responseFormat != null) {
			modelTobuild = modelTobuild.responseFormat(responseFormat);
			this.responseFormat = responseFormat;
		} 
		if (frequencyPenalty != null) {
			modelTobuild = modelTobuild.frequencyPenalty(frequencyPenalty);
			this.frequencyPenalty = frequencyPenalty;
		}
		if (maxCompletionTokens != null) {
			modelTobuild = modelTobuild.maxCompletionTokens(maxCompletionTokens);
			this.maxCompletionTokens = maxCompletionTokens;
		}
		if (maxRetries != null) {
			modelTobuild = modelTobuild.maxRetries(maxRetries);
			this.maxRetries = maxRetries;
		}
		if (maxTokens !=null) {
			modelTobuild = modelTobuild.maxTokens(maxTokens);
			this.maxTokens = maxTokens;
		}
		if (presencePenalty != null) {
			modelTobuild = modelTobuild.presencePenalty(presencePenalty);
			this.presencePenalty = presencePenalty;
		}
		if (seed != null) {
			modelTobuild = modelTobuild.seed(seed);
			this.seed = seed;
		}
		if (store != null) {
			modelTobuild = modelTobuild.store(store);
			this.store = store;
		}
		if (temperature != null) {
			modelTobuild = modelTobuild.temperature(temperature);
			this.temperature = temperature;
		}
		if (timeout != null) {
			modelTobuild = modelTobuild.timeout(Duration.ofSeconds(timeout));
			this.timeOut = timeout;
		}
		if (topP != null) { 
			modelTobuild = modelTobuild.topP(topP);
			this.topP = topP;
		}

		model = modelTobuild.logRequests(true).build();
	}
	
	
	public void setMemory(Memory memory) {
		this.memory = memory;
	}

	public String askQuestion(String prompt, boolean addPromptToMemory, boolean addAnswerToMemory, boolean UseMemory) {
			
			if (UseMemory && memory != null && addPromptToMemory) 
				memory.addToMemory(prompt);
			List<ChatMessage> fullConversation = (UseMemory && memory != null) ? new ArrayList<>(memory.getMemory().messages()) : new ArrayList<>();
			UserMessage currentMessage = UserMessage.from(prompt);
			fullConversation.add(currentMessage); 
			
			ChatResponse response = model.chat(fullConversation);
			if(UseMemory && memory != null && addAnswerToMemory) {
				memory.getMemory().add(response.aiMessage());
			}
			return response.aiMessage().text();
	
	}

	@getter (LLMConstants.MEMORY)
	public Memory getMemory() {
		return memory;
	}

	
	@getter (LLMConstants.MODEL_TYPE)
	public String getModelType() {
		return modelType;
	}


	@getter (LLMConstants.MODEL_NAME)
	public String getModelName() {
		return modelName;
	}


	@getter (LLMConstants.OLLAMA_URL)
	public String getOllamaUrl() {
		return OllamaUrl;
	}


	@getter (LLMConstants.API_KEY)
	public String getApiKey() {
		return ApiKey;
	}


	@getter (LLMConstants.RESPONSE_FORMAT)
	public String getResponseFormat() {
		return responseFormat;
	}


	@getter (LLMConstants.NUM_CTX)
	public Integer getNumCtx() {
		return numCtx;
	}


	@getter (LLMConstants.NUM_PREDICT)
	public Integer getNiumPredict() {
		return numPredict;
	}


	@getter (LLMConstants.REPEAT_PENALTY)
	public Double getRepeatPenalty() {
		return repeatPenalty;
	}


	@getter (LLMConstants.SEED)
	public Integer getSeed() {
		return seed;
	}


	@getter (LLMConstants.TEMPERATURE)
	public Double getTemperature() {
		return temperature;
	}


	@getter (LLMConstants.TOP_K)
	public Integer getTopK() {
		return topK;
	}


	@getter (LLMConstants.TOP_P)
	public Double getTopP() {
		return topP;
	}


	@getter (LLMConstants.FREQUENCY_PENALTY)
	public Double getFrequencyPenalty() {
		return frequencyPenalty;
	}


	@getter (LLMConstants.MAX_COMPLETION_TOKENS)
	public Integer getMaxCompletionTokens() {
		return maxCompletionTokens;
	}


	@getter (LLMConstants.MAX_RETRIES)
	public Integer getMaxRetries() {
		return maxRetries;
	}


	@getter (LLMConstants.MAX_TOKENS)
	public Integer getMaxTokens() {
		return maxTokens;
	}


	@getter (LLMConstants.PRESENCE_PENALTY)
	public Double getPresencePenalty() {
		return presencePenalty;
	}


	@getter (LLMConstants.STORE)
	public Boolean getStore() {
		return store;
	}


	@getter (LLMConstants.TIME_OUT)
	public Integer getTimeOut() {
		return timeOut;
	}



	
	public dev.langchain4j.model.chat.ChatModel getModel() {
		return model;
	}


	public void setModel(dev.langchain4j.model.chat.ChatModel model) {
		this.model = model;
	}


	
	/**
	 * Instantiates a new predicate.
	 */
	
	public ChatModel() {
		super();
	}
	
	
	public ChatModel(ChatModel p) {
		
	}
	
	
	

	@Override
	public String toString() {
		return "ChatModel(" + model.toString() +")";
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
	public ChatModel copy(final IScope scope) throws GamaRuntimeException {
		return new ChatModel(this);
	}

	

	@Override
	public int hashCode() {
		return model.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		final ChatModel other = (ChatModel) obj;
		return model.equals(other.model);
	}

	/**
	 * Method getType()
	 *
	 * @see gama.core.common.interfaces.ITyped#getGamlType()
	 */ 
	@Override
	public IType<?> getGamlType() { return Types.get(ChatModelType.id); }

	@Override
	public IJsonValue serializeToJson(IJson json) {
		// TODO Auto-generated method stub
		return null;
	}

}
