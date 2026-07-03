package gama.extension.mcp;

import gama.api.GAMA;
import gama.core.util.json.ParseException;
import gama.api.utils.json.IJsonObject;
import gama.api.utils.json.IJsonValue;
import java.util.List;

public class MCPToolHandler {

	private MCPServerService service;

	public MCPToolHandler(MCPServerService service) {
		this.service = service;
	}

	public void handleRequest(String sender, String message) {
		try {
			IJsonValue parsed = GAMA.getJsonEncoder().parse(message);
			if (parsed.isObject()) {
				IJsonObject req = parsed.asObject();
				IJsonValue methodVal = req.get("method");
				String method = methodVal != null ? methodVal.asString() : null;
				IJsonValue idVal = req.get("id");
				String id = idVal != null ? idVal.toString() : null;

				if (method != null) {
					if (method.equals("tools/list")) {
						handleToolsList(sender, id);
					} else if (method.equals("tools/call")) {
						IJsonValue paramsVal = req.get("params");
						IJsonObject params = paramsVal != null && paramsVal.isObject() ? paramsVal.asObject() : null;
						String name = params != null && params.get("name") != null ? params.get("name").asString() : null;
						IJsonValue argsVal = params != null ? params.get("arguments") : null;
						IJsonObject args = argsVal != null && argsVal.isObject() ? argsVal.asObject() : null;
						handleToolCall(sender, id, name, args);
					} else {
						if (id != null) sendError(sender, id, -32601, "Method not found");
					}
				}
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void handleToolsList(String sender, String id) {
		String response = "{" +
				"\"jsonrpc\": \"2.0\"," +
				"\"id\": " + id + "," +
				"\"result\": {" +
				"\"tools\": [" +
				"{" +
				"\"name\": \"validate_model\"," +
				"\"description\": \"Validates a GAML model file\"," +
				"\"inputSchema\": {" +
				"\"type\": \"object\"," +
				"\"properties\": {" +
				"\"file_path\": { \"type\": \"string\" }" +
				"}," +
				"\"required\": [\"file_path\"]" +
				"}" +
				"}," +
				"{" +
				"\"name\": \"launch_simulation\"," +
				"\"description\": \"Launches a simulation from a GAML file\"," +
				"\"inputSchema\": {" +
				"\"type\": \"object\"," +
				"\"properties\": {" +
				"\"file_path\": { \"type\": \"string\" }," +
				"\"experiment_name\": { \"type\": \"string\" }" +
				"}," +
				"\"required\": [\"file_path\", \"experiment_name\"]" +
				"}" +
				"}" +
				"]" +
				"}" +
				"}";
		service.sendMessageToClient(sender, response);
	}

	private void handleToolCall(String sender, String id, String name, IJsonObject args) {
		if ("validate_model".equals(name)) {
			String filePath = args != null && args.get("file_path") != null ? args.get("file_path").asString() : null;
			if (filePath == null) {
				sendError(sender, id, -32602, "Missing file_path");
				return;
			}
			try {
				Class<?> builderClass = Class.forName("gaml.compiler.validation.GamlModelBuilder");
				Object builderInstance = builderClass.getMethod("getInstance").invoke(null);
				java.io.File modelFile = new java.io.File(filePath);
				List<Object> errors = new java.util.ArrayList<>();
				Class<?> metaPropsClass = Class.forName("gama.api.utils.GamlProperties");
				
				Object modelSpecies = builderClass.getMethod("compile", java.io.File.class, List.class, metaPropsClass)
												  .invoke(builderInstance, modelFile, errors, null);
												  
				if (errors.isEmpty() && modelSpecies != null) {
					sendSuccess(sender, id, "Model validated successfully.");
				} else {
					sendSuccess(sender, id, "Validation errors: " + errors.toString());
				}
			} catch (Exception e) {
				sendError(sender, id, -32000, "Failed to compile model: " + e.getMessage());
			}
		} else if ("launch_simulation".equals(name)) {
			String filePath = args != null && args.get("file_path") != null ? args.get("file_path").asString() : null;
			String expName = args != null && args.get("experiment_name") != null ? args.get("experiment_name").asString() : null;
			if (filePath == null || expName == null) {
				sendError(sender, id, -32602, "Missing file_path or experiment_name");
				return;
			}
			try {
				Class<?> builderClass = Class.forName("gaml.compiler.validation.GamlModelBuilder");
				Object builderInstance = builderClass.getMethod("getInstance").invoke(null);
				java.io.File modelFile = new java.io.File(filePath);
				List<Object> errors = new java.util.ArrayList<>();
				Class<?> metaPropsClass = Class.forName("gama.api.utils.GamlProperties");
				
				Object modelObj = builderClass.getMethod("compile", java.io.File.class, List.class, metaPropsClass)
												  .invoke(builderInstance, modelFile, errors, null);
				
				if (modelObj != null) {
					gama.api.kernel.species.IModelSpecies model = (gama.api.kernel.species.IModelSpecies) modelObj;
					gama.api.kernel.species.IExperimentSpecies exp = model.getExperiment(expName);
					if (exp != null) {
						gama.api.kernel.simulation.HeadlessExperimentController controller = new gama.api.kernel.simulation.HeadlessExperimentController(exp);
						exp.setController(controller);
						exp.setHeadless(true);
						exp.open();
						gama.api.kernel.simulation.IExperimentAgent agent = exp.getAgent();
						controller.schedule(agent);
						boolean result = controller.processStart(true);
						controller.close();
						
						sendSuccess(sender, id, "Simulation '" + expName + "' finished with result: " + result);
					} else {
						sendError(sender, id, -32000, "Experiment '" + expName + "' not found in model.");
					}
				} else {
					sendError(sender, id, -32000, "Model compilation failed: " + errors.toString());
				}
			} catch (Exception e) {
				e.printStackTrace();
				sendError(sender, id, -32000, "Failed to launch simulation: " + e.getMessage());
			}
		} else {
			sendError(sender, id, -32601, "Tool not found");
		}
	}
	private void sendSuccess(String sender, String id, String text) {
		String response = "{" +
				"\"jsonrpc\": \"2.0\"," +
				"\"id\": " + id + "," +
				"\"result\": {" +
				"\"content\": [" +
				"{" +
				"\"type\": \"text\"," +
				"\"text\": \"" + escapeJson(text) + "\"" +
				"}" +
				"]" +
				"}" +
				"}";
		service.sendMessageToClient(sender, response);
	}

	private void sendError(String sender, String id, int code, String msg) {
		String response = "{" +
				"\"jsonrpc\": \"2.0\"," +
				"\"id\": " + id + "," +
				"\"error\": {" +
				"\"code\": " + code + "," +
				"\"message\": \"" + escapeJson(msg) + "\"" +
				"}" +
				"}";
		service.sendMessageToClient(sender, response);
	}
	
	private String escapeJson(String s) {
		if (s == null) return "";
		return s.replace("\"", "\\\"");
	}
}
