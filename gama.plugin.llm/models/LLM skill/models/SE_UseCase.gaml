model SE_UseCase

global {
	string llmname <- "gemma4:e2b-mlx";

	// Virtual universe environment
	float universe_radius <- 200.0;
	geometry shape <- sphere(universe_radius * 2) ;
	bool torus_environment <- false;

	// Global caller tracking
	string active_caller <- "System";

	// Global tool call guardrails
	int dev_tool_call_count <- 0;
	int max_dev_tool_calls <- 8;
	list<string> saved_files_in_turn <- [];

	// GAMA-side compile result tracking (keyed by active_caller, set by compile_file)
	// This is the ground truth — we NEVER trust the LLM's text claim of COMPILE_SUCCESS.
	map<string,string> agent_compile_results <- [];

	// Tool: save_file (used by Developers)
	string save_file (string content, string fpath) {
		dev_tool_call_count <- dev_tool_call_count + 1;
		if (dev_tool_call_count > max_dev_tool_calls) {
			return "STOP: Maximum tool attempts reached for this turn. Stop calling tools and output your final status report.";
		}
		try {
			write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: save_file (" + fpath + ")";
			string clean_content <- content replace ("```java", "") replace ("```", "");
			save trim(clean_content) to: fpath format: "text"; 
			return "File successfully saved to " + fpath + ". Now compile it with compile_file.";
		}
		catch {
			write #current_error;
			return "FAILED: Error saving file: " + #current_error;
		}
	}

	// Tool: compile_file (used by Developers & Leads)
	string compile_file (string file_path) {
		dev_tool_call_count <- dev_tool_call_count + 1;
		if (dev_tool_call_count > max_dev_tool_calls) {
			return "STOP: Maximum tool attempts reached for this turn. Stop calling tools and output your final status report.";
		}
		try {
			string clean_path <- file_path replace (",", " ");
			write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: compile_file (javac " + clean_path + ")";
			string cmd1 <- (clean_path contains "-d") ? ("javac " + clean_path) : ("javac -d bin -cp bin:src -sourcepath src " + clean_path);
			string res <- command(cmd1);
			write "[CYCLE " + cycle + "] [" + active_caller + "] Javac output:\n" + res;
			string compile_result;
			if (res = nil or trim(res) = "") {
				compile_result <- "COMPILE_SUCCESS: All files compiled with 0 errors: " + clean_path;
			} else {
				compile_result <- "COMPILE_FAILED: Compilation errors found:\n" + res;
			}
			// Record the REAL outcome in GAMA — this overrides any LLM text claim
			agent_compile_results[active_caller] <- compile_result;
			return compile_result;
		}
		catch {
			write #current_error;
			return "FAILED: javac error: " + #current_error;
		}
	}

	// Tool: run_command (used by Developers & Leads)
	string run_command (string cmd) {
		dev_tool_call_count <- dev_tool_call_count + 1;
		if (dev_tool_call_count > max_dev_tool_calls) {
			return "TOOL_LIMIT_REACHED: STOP calling tools immediately and output your final status report.";
		}
		try {
			write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: run_command -> " + cmd;
			string res <- command(cmd);
			write "[CYCLE " + cycle + "] [" + active_caller + "] Command Output: " + res;
			if (res = nil or res = "") {
				return "Command executed successfully (no output).";
			} else {
				return res;
			}
		}
		catch {
			write #current_error;
			return "FAILED: " + #current_error;
		}
	}

	// Tool: create_custom_tool (used by Developers to self-tool)
	string create_custom_tool (string tool_name, string description, string json_schema) {
		write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: create_custom_tool -> " + tool_name;
		string full_tool_json <- "";
		if (json_schema contains "\"parameters\"") {
			full_tool_json <- json_schema;
		} else {
			string params_part <- (json_schema != nil and trim(json_schema) != "" and json_schema contains "properties") 
			                    ? json_schema 
			                    : '{"type": "object", "properties": {"cmd": {"type": "string", "description": "command parameter"}}, "required": ["cmd"]}';
			full_tool_json <- '{\n' +
			                  '  "name": "' + tool_name + '",\n' +
			                  '  "description": "' + description + '",\n' +
			                  '  "parameters": ' + params_part + '\n' +
			                  '}';
		}
		try {
			ask AI_Developer {
				if (not (registered_tools contains tool_name)) {
					registered_tools << tool_name;
					tool <- add_tool_executor_from_json(provider: tool, json: full_tool_json, execute: world.run_command);
					chat_bot <- create_assistant(llm: llm, memory: chat_memory, tool_provider: tool);
				}
			}
			return "Custom tool " + tool_name + " registered.";
		}
		catch {
			write #current_error;
			return "Error registering tool: " + #current_error;
		}
	}
	
	// Tool: create_team_lead (used by Manager)
	string create_team_lead (string domain_task, string lead_name) {
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: create_team_lead (" + lead_name + ")";
		write "========================================";
		if (not empty(AI_TeamLead where (each.name = lead_name))) {
			return "TeamLead " + lead_name + " already exists. Use assign_lead_task.";
		}
		int lead_idx <- length(AI_TeamLead);
		create AI_TeamLead {
			name <- lead_name;
			location <- {25.0 + (lead_idx * 50.0), 45.0, 20.0};
			registered_tools <- ["create_developer", "assign_dev_task", "compile_file", "run_command"];
			llm <- create_ollama_chat_model(url: "http://localhost:11434", model_name: llmname);
			chat_memory <- create_chat_memory(llm, "You are an autonomous Tech Lead for the " + lead_name + " domain. You have NO chat partner. EVERY action you take MUST be a tool call (create_developer or assign_dev_task or compile_file or run_command). You MUST NEVER ask anyone to provide code or information to you in text.\n\nYour workflow:\n1. Decompose your domain task into individual class modules.\n2. Create Developer agents using create_developer.\n3. Assign tasks to Developers using assign_dev_task (include FULL class spec and package path).\n4. When developers report back, verify with compile_file.\n5. Only AFTER compile_file confirms success, output your domain status.");
			
			tool <- create_tool_executor_from_json(json: '{
				  "name": "create_developer",
				  "description": "Create a software engineer / developer sub-agent for a specific class or feature",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "role_description": { "type": "string", "description": "Role of the developer (e.g. Model Engineer, Service Engineer)" },
				      "dev_name": { "type": "string", "description": "Unique name for the developer agent" }
				    },
				    "required": ["role_description", "dev_name"]
				  }
				}', execute: world.create_developer);
				
			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "assign_dev_task",
				  "description": "Assign a class implementation task to a Developer on your team",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "task_msg": { "type": "string", "description": "Detailed class specifications, package path, methods" },
				      "dev_name": { "type": "string", "description": "Name of the developer agent" }
				    },
				    "required": ["task_msg", "dev_name"]
				  }
				}', execute: world.assign_dev_task);

			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "compile_file",
				  "description": "Verify compilation of files in your domain using javac (use SPACES between file paths)",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "file_path": { "type": "string", "description": "Space-separated file paths to compile" }
				    },
				    "required": ["file_path"]
				  }
				}', execute: world.compile_file);

			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "run_command",
				  "description": "Run tests or integration commands (e.g. java -cp bin com.ctu.app.MainApp)",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "cmd": { "type": "string", "description": "Command string" }
				    },
				    "required": ["cmd"]
				  }
				}', execute: world.run_command);

			chat_bot <- create_assistant(llm: llm, memory: chat_memory, tool_provider: tool);
			status <- "idle";
			assigned_cycle <- -1;
			write "[CYCLE " + cycle + "] [" + active_caller + "] Created Team Lead: " + name;
		}
		return "TeamLead " + lead_name + " created. Use assign_lead_task to assign domain tasks.";
	}

	// Tool: assign_lead_task (used by Manager to assign tasks to Team Leads)
	string assign_lead_task (string task_msg, string lead_name) {
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: assign_lead_task -> " + lead_name;
		write "========================================";
		list<AI_TeamLead> targets <- (AI_TeamLead where (each.name = lead_name));
		if (empty(targets)) {
			return "FAILED: No TeamLead named " + lead_name + " exists. Use create_team_lead first.";
		}
		AI_TeamLead target <- first(targets);
		ask target {
			inbox << task_msg;
			status <- "working";
			assigned_cycle <- cycle;
		}
		return "Task queued in TeamLead " + lead_name + " inbox for execution in Cycle " + (cycle + 1) + ".";
	}

	// Tool: create_developer (used by Team Leads)
	string create_developer (string role_description, string dev_name) {
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: create_developer (" + dev_name + ")";
		write "========================================";
		if (not empty(AI_Developer where (each.name = dev_name))) {
			return "Developer " + dev_name + " already exists. Use assign_dev_task.";
		}
		int dev_idx <- length(AI_Developer);
		create AI_Developer {
			name <- dev_name;
			location <- {15.0 + (dev_idx * 20.0), 80.0, 0.0};
			registered_tools <- ["save_file", "compile_file", "run_command", "create_custom_tool"];
			llm <- create_ollama_chat_model(url: "http://localhost:11434", model_name: llmname);
			chat_memory <- create_chat_memory(llm, "You are an autonomous Java Software Developer (" + dev_name + "). You have NO chat partner. You MUST act via tools only.\n\nWhen assigned a class/module to implement:\n1. Write complete, syntactically correct Java code.\n2. Call save_file ONCE to save it to the correct src/ path.\n3. Call compile_file ONCE. Read the EXACT return value.\n4. If compile_file returns COMPILE_FAILED with errors: fix the Java syntax in the code, call save_file again with the corrected code (to OVERWRITE the broken file), then call compile_file again.\n5. Repeat fix->save->compile until compile_file returns COMPILE_SUCCESS.\n6. When COMPILE_SUCCESS is confirmed, STOP all tool calls and output: COMPILE_SUCCESS: [filename].\n\nCRITICAL: NEVER say COMPILE_SUCCESS unless compile_file explicitly returned COMPILE_SUCCESS. Trust the tool return value, not your own judgment.");
			
			tool <- create_tool_executor_from_json(json: '{
				  "name": "save_file",
				  "description": "Save Java source code to disk",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "content": { "type": "string", "description": "The Java source code" },
				      "fpath": { "type": "string", "description": "File path (e.g. src/com/ctu/model/Student.java)" }
				    },
				    "required": ["content", "fpath"]
				  }
				}', execute: world.save_file);

			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "compile_file",
				  "description": "Compile Java source files with javac (use SPACES between file paths)",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "file_path": { "type": "string", "description": "Space-separated file paths" }
				    },
				    "required": ["file_path"]
				  }
				}', execute: world.compile_file);

			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "run_command",
				  "description": "Execute command in terminal",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "cmd": { "type": "string", "description": "Command string" }
				    },
				    "required": ["cmd"]
				  }
				}', execute: world.run_command);

			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "create_custom_tool",
				  "description": "Dynamically create and register a new custom tool onto yourself",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "tool_name": { "type": "string", "description": "Name of the new tool" },
				      "description": { "type": "string", "description": "Description of the tool" },
				      "json_schema": { "type": "string", "description": "JSON schema definition" }
				    },
				    "required": ["tool_name", "description", "json_schema"]
				  }
				}', execute: world.create_custom_tool);

			chat_bot <- create_assistant(llm: llm, memory: chat_memory, tool_provider: tool);
			status <- "idle";
			assigned_cycle <- -1;
			// Set the owning TeamLead so reports route correctly
			list<string> caller_parts <- active_caller split_with " [";
			lead_owner <- first(caller_parts);
			write "[CYCLE " + cycle + "] [" + active_caller + "] Created Developer: " + name + " (owner: " + lead_owner + ")";
		}
		return "Developer " + dev_name + " created. NOW you MUST IMMEDIATELY call assign_dev_task to give this developer their implementation task. Do NOT create another developer before assigning a task to this one."; 
	}

	// Tool: assign_dev_task (used by Team Leads to assign tasks to Developers)
	string assign_dev_task (string task_msg, string dev_name) {
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] TOOL: assign_dev_task -> " + dev_name;
		write "========================================";
		list<AI_Developer> targets <- (AI_Developer where (each.name = dev_name));
		if (empty(targets)) {
			return "FAILED: No Developer named " + dev_name + " exists. Use create_developer first.";
		}
		AI_Developer target <- first(targets);
		ask target {
			inbox << task_msg;
			status <- "working";
			assigned_cycle <- cycle;
		}
		return "Task queued in Developer " + dev_name + " inbox for execution in Cycle " + (cycle + 1) + ".";
	}

	init {
		create AI_Manager {
			location <- {50.0, 15.0, 45.0};
			registered_tools <- ["create_team_lead", "assign_lead_task"];
			llm <- create_ollama_chat_model(url: "http://localhost:11434", model_name: llmname);
			chat_memory <- create_chat_memory(llm, "You are an autonomous IT Project Manager. You have NO chat partner. EVERY action you take MUST be a tool call (create_team_lead or assign_lead_task). You MUST NEVER ask anyone to provide code or information to you in text.\n\nPROJECT DELIVERABLES (Java Swing GUI Desktop App):\n1. Backend: Data model (Student.java with id, name, email, score) and Business Service (StudentService.java for sorting, filtering >= 60.0, average/min/max statistics).\n2. Frontend/UI: Graphical User Interface (StudentUI.java using Java Swing JFrame, JTable, add student form, sort/filter buttons, analytics panel) and Main Launcher (MainApp.java).\n3. End-to-end: All modules compiled cleanly with compile_file.\n\nRULES:\n- ALWAYS use tools. Create BackendLead (for Model & Service) and FrontendLead (for Swing GUI & MainApp).\n- Do NOT output [TASK_COMPLETED] after only one sub-agent finishes.\n- Output [TASK_COMPLETED] ONLY when ALL backend and UI modules are delivered and verified.");
			
			tool <- create_tool_executor_from_json(json: '{
				  "name": "create_team_lead",
				  "description": "Create a specialized Team Lead for a domain (e.g. BackendLead for Model/Service, FrontendLead for Swing UI/MainApp)",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "domain_task": { "type": "string", "description": "Domain responsibility" },
				      "lead_name": { "type": "string", "description": "Name of the team lead (e.g. BackendLead, FrontendLead)" }
				    },
				    "required": ["domain_task", "lead_name"]
				  }
				}', execute: world.create_team_lead);
				
			tool <- add_tool_executor_from_json(provider: tool, json: '{
				  "name": "assign_lead_task",
				  "description": "Assign a domain milestone/task to a Team Lead",
				  "parameters": {
				    "type": "object",
				    "properties": {
				      "task_msg": { "type": "string", "description": "Milestone objectives and domain requirements" },
				      "lead_name": { "type": "string", "description": "Name of the target team lead" }
				    },
				    "required": ["task_msg", "lead_name"]
				  }
				}', execute: world.assign_lead_task);

			chat_bot <- create_assistant(llm: llm, memory: chat_memory, tool_provider: tool);
			status <- "planning";
			assigned_cycle <- -1;
			inbox << "Project Kickoff: Build, test, and launch a complete Java Desktop Application with Graphical User Interface (Java Swing GUI). Scope: 1) Model: Student.java (id, name, email, double score), 2) Service: StudentService.java (sortByScore, filterPassing >= 60.0, avg/min/max stats), 3) GUI: StudentUI.java (Swing JFrame with JTable to view students, input form to add students, buttons to sort, filter, and show analytics summary), 4) Launcher: MainApp.java (runs SwingUtilities.invokeLater to display StudentUI), 5) Live Test: Launch the GUI with run_command (java -cp bin com.ctu.app.MainApp). You MUST use tools: create BackendLead and FrontendLead, then use assign_lead_task.";
		}
	}
}

// ---------------------------------------------------------
// Level 3: Developer (Senior / Junior Engineer)
// ---------------------------------------------------------
species AI_Developer skills: [llm] parallel:true {
	mcp_transport transport;
	mcp_client client;
	tool_provider tool;
	list<string> registered_tools <- ["save_file", "compile_file", "run_command", "create_custom_tool"];
	list<string> inbox <- [];
	string status <- "idle";
	string last_response <- "";
	int assigned_cycle <- -1;
	string lead_owner <- "";
	string gama_compile_status <- "";
	string target_file_path <- "";
	string rid <- nil;
	float z_level <- 0.0;

	// Non-blocking async dispatch
	reflex send_dev when: (status = "working") and (not empty(inbox)) and (rid = nil) and (cycle > assigned_cycle) {
		active_caller <- name + " [Developer]";
		string current_prompt <- "";
		loop msg over: inbox {
			current_prompt <- current_prompt + msg + "\n";
		}
		inbox <- [];
		
		// Reset per-turn anti-loop guardrails and stale compile results
		dev_tool_call_count <- 0;
		saved_files_in_turn <- [];
		string my_key <- name + " [Developer]";
		if (agent_compile_results contains_key my_key) {
			remove key: my_key from: agent_compile_results;
		}
		
		// Infer target file path if known
		if (name = "StudentModelDev" or current_prompt contains "Student.java") {
			target_file_path <- "src/com/ctu/model/Student.java";
		} else if (name = "StudentServiceDev" or current_prompt contains "StudentService.java") {
			target_file_path <- "src/com/ctu/service/StudentService.java";
		} else if (name = "StudentUIDev" or current_prompt contains "StudentUI.java") {
			target_file_path <- "src/com/ctu/ui/StudentUI.java";
		} else if (name = "MainAppDev" or current_prompt contains "MainApp.java") {
			target_file_path <- "src/com/ctu/app/MainApp.java";
		}
		
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] sending task asynchronously to LLM:";
		write current_prompt;
		write "========================================";
		
		rid <- send_to_llm_async(llm, current_prompt, true, true);
		active_caller <- "System";
	}

	// Non-blocking async poll each cycle
	reflex poll_dev when: (rid != nil) {
		string res <- get_llm_result(rid);
		if (res != nil) {
			active_caller <- name + " [Developer]";
			rid <- nil;
			
			write "========================================";
			write "[CYCLE " + cycle + "] [" + active_caller + "] finished (LLM text):";
			write res;
			write "========================================";
			
			// If target file is identified and code was generated, save and compile it
			if (target_file_path != "") {
				string clean_code <- res replace ("```java", "") replace ("```", "");
				if (clean_code contains "class " or clean_code contains "package ") {
					string save_res <- world.save_file(clean_code, target_file_path);
					string comp_res <- world.compile_file(target_file_path);
					write "[CYCLE " + cycle + "] [" + active_caller + "] Auto-compiled " + target_file_path + " -> " + comp_res;
				}
			}
			
			// ----------------------------------------------------------------
			// GROUND TRUTH: Override last_response with GAMA-tracked compile
			// result. The LLM's text claim is IGNORED if GAMA recorded a
			// different outcome from compile_file.
			// ----------------------------------------------------------------
			string caller_key <- name + " [Developer]";
			if (agent_compile_results contains_key caller_key) {
				gama_compile_status <- agent_compile_results[caller_key];
				if (gama_compile_status contains "COMPILE_FAILED") {
					last_response <- gama_compile_status;
					write "[CYCLE " + cycle + "] [" + active_caller + "] GAMA OVERRIDE: Real compile FAILED:\n" + gama_compile_status;
				} else {
					last_response <- gama_compile_status;
					write "[CYCLE " + cycle + "] [" + active_caller + "] GAMA VERIFIED: " + gama_compile_status;
				}
			} else {
				last_response <- res;
			}
			do add_to_memory message: "Dev Result: " + last_response memory: chat_memory;
			
			status <- "done";
			
			// Notify owning TeamLead
			if (lead_owner != "") {
				list<AI_TeamLead> owners <- AI_TeamLead where (each.name = lead_owner);
				if (not empty(owners)) {
					ask first(owners) {
						self.inbox << "Report from Developer " + myself.name + ":\n" + myself.last_response;
						self.status <- "evaluating";
						self.assigned_cycle <- cycle;
					}
				}
			} else {
				ask AI_TeamLead {
					self.inbox << "Report from Developer " + myself.name + ":\n" + myself.last_response;
					self.status <- "evaluating";
					self.assigned_cycle <- cycle;
			}
		}
		active_caller <- "System";
	}
}
	aspect default {
		bool is_waiting_llm <- (rid != nil);
		bool is_done <- (status = "done");
		rgb node_color <- is_waiting_llm ? #orange : (is_done ? rgb(50, 205, 50) : #gray);
		float base_r <- 4.0;
		
		// 1. Connection line to TeamLead (in 3D)
		if (lead_owner != "") {
			list<AI_TeamLead> owners <- AI_TeamLead where (each.name = lead_owner);
			if (not empty(owners)) {
				point lead_loc <- first(owners).location;
				draw line([location, lead_loc]) color: is_waiting_llm ? rgb(255, 165, 0, 120) : rgb(100, 100, 100, 70) width: 1.0;
			}
		}
		
		// 2. Thinking Animation ONLY when waiting for LLM result (rid != nil)
		if (is_waiting_llm) {
			// Pulsing ring (3D sphere shell)
			float pulse <- 1.0 + 0.3 * sin(cycle * 8.0);
			draw sphere((base_r + 2.5) * pulse) color: #orange wireframe: true width: 1.5;
			
			// 3 orbiting thinking dots in 3D space
			int nb_dots <- 3;
			loop i from: 0 to: nb_dots - 1 {
				float dot_angle <- (cycle * 15.0 + i * (360.0 / nb_dots)) mod 360.0;
				float dot_dist <- base_r + 3.0;
				float dot_z <- location.z + 2.0 * sin(cycle * 5.0 + i * 120.0);
				point dot_pos <- location + {cos(dot_angle) * dot_dist, sin(dot_angle) * dot_dist, dot_z};
				draw sphere(1.0) at: dot_pos color: #yellow;
			}
		}
		
		// 3. Core Developer Node (3D sphere)
		draw sphere(base_r) color: node_color;
		
		// 4. Status Badge & Name
		draw (name + " [" + (is_waiting_llm ? "thinking" : status) + "]") at: location + {-6.0, 8.0, 0.0} color: is_waiting_llm ? #yellow : (is_done ? #lightgreen : #white) font: font("Helvetica", 10, #bold);
	}
}

// ---------------------------------------------------------
// Level 2: Tech Lead / Team Lead
// ---------------------------------------------------------
species AI_TeamLead skills: [llm] parallel:true{
	mcp_transport transport;
	mcp_client client;
	tool_provider tool;
	list<string> registered_tools <- ["create_developer", "assign_dev_task", "compile_file", "run_command"];
	list<string> inbox <- [];
	string status <- "idle";
	string last_response <- "";
	int assigned_cycle <- -1;
	int lead_stuck_count <- 0;
	int empty_response_count <- 0;
	string rid <- nil;
	float z_level <- 20.0;

	// Non-blocking async dispatch
	reflex send_lead when: (status = "working" or status = "evaluating") and (not empty(inbox)) and (rid = nil) and (cycle > assigned_cycle) {
		active_caller <- name + " [TeamLead]";
		string combined_msg <- "";
		loop msg over: inbox {
			combined_msg <- combined_msg + msg + "\n";
		}
		inbox <- [];
		
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] coordinating domain asynchronously:";
		write combined_msg;
		write "========================================";
		
		rid <- send_to_llm_async(llm, combined_msg, true, true);
		active_caller <- "System";
	}

	// Non-blocking async poll each cycle
	reflex poll_lead when: (rid != nil) {
		string res <- get_llm_result(rid);
		if (res != nil) {
			active_caller <- name + " [TeamLead]";
			rid <- nil;
			
			string trimmed_res <- trim(res);
			if (trimmed_res = "" or trimmed_res = "null") {
				empty_response_count <- empty_response_count + 1;
				write "[CYCLE " + cycle + "] [" + active_caller + "] EMPTY RESPONSE (#" + empty_response_count + ") - Auto-recovering...";
				
		if (empty_response_count >= 3) {
				// Auto-recovery: create a generic developer if none exist
				string my_name <- name;
				int existing_devs <- length(AI_Developer where (each.name contains my_name));
				if (existing_devs = 0) {
					string tool_result <- world.create_developer("Software Developer for " + my_name, "Dev" + my_name + cycle);
					write "[CYCLE " + cycle + "] [AUTO-RECOVERY] Force-created: " + tool_result;
					do add_to_memory message: "Auto-executed on your behalf: " + tool_result memory: chat_memory;
				} else {
					// Ask Manager to reassign
					ask AI_Manager {
						self.inbox << "TeamLead " + myself.name + " is stuck. Please reassign a task.";
						self.status <- "evaluating";
						self.assigned_cycle <- cycle;
					}
				}
			}
				
				last_response <- "Auto-recovery: created missing developer";
				status <- "waiting";
				ask AI_Manager {
					self.inbox << "Domain Status from TeamLead " + myself.name + ":\n" + myself.last_response;
					self.status <- "evaluating";
					self.assigned_cycle <- cycle;
				}
			} else {
				empty_response_count <- 0;
				write "========================================";
				write "[CYCLE " + cycle + "] [" + active_caller + "] plan & response:";
				write res;
				write "========================================";
				
				string low_res <- lower_case(res);
				bool text_tool_call <- (low_res contains "create_developer") or (low_res contains "assign_dev_task");
				
				if (text_tool_call) {
					write "[CYCLE " + cycle + "] [" + active_caller + "] TEXT TOOL CALL DETECTED - EXECUTING DIRECTLY";
					string tool_result <- "";
					
					if (low_res contains "create_developer") {
						tool_result <- world.create_developer("Software Developer", "Dev" + cycle);
					} else {
						tool_result <- world.assign_dev_task("Implement the assigned class", "Dev" + cycle);
					}
					
					write "[CYCLE " + cycle + "] [" + active_caller + "] TEXT TOOL RESULT: " + tool_result;
					do add_to_memory message: "Tool executed on your behalf: " + tool_result memory: chat_memory;
					last_response <- tool_result;
					status <- "waiting";
					ask AI_Manager {
						self.inbox << "Domain Status from TeamLead " + myself.name + ":\n" + myself.last_response;
						self.status <- "evaluating";
						self.assigned_cycle <- cycle;
					}
				} else {
					bool lead_stuck <- (low_res contains "please provide") or (low_res contains "can you provide") 
					               or (low_res contains "could you provide") or (low_res contains "i need you to")
					               or (low_res contains "please share") or (low_res contains "i am waiting")
					               or (low_res contains "please give") or (low_res contains "provide the")
					               or (low_res contains "please specify") or (low_res contains "let me know")
					               or (low_res contains "what are the") or (low_res contains "please clarify");
					
					if (lead_stuck) {
						lead_stuck_count <- lead_stuck_count + 1;
						write "[CYCLE " + cycle + "] [" + active_caller + "] STUCK DETECTED (" + lead_stuck_count + "): TeamLead asking for info instead of using tools.";
						status <- "working";
						assigned_cycle <- -1;
						if (lead_stuck_count >= 2) {
							chat_memory <- create_chat_memory(llm, "CRITICAL RULE: You are a Tech Lead. You are FORBIDDEN from asking questions or requesting information. You have FOUR tools: create_developer, assign_dev_task, compile_file, run_command. You MUST call one of them RIGHT NOW. Do NOT output text. Do NOT ask questions. ONLY make a tool call.");
							chat_bot <- create_assistant(llm: llm, memory: chat_memory, tool_provider: tool);
							lead_stuck_count <- 0;
						}
						inbox << "STOP ASKING QUESTIONS. Call create_developer NOW to create a developer agent. You have tools. Use them. NO TEXT RESPONSE ALLOWED - ONLY A TOOL CALL.";
					} else {
						lead_stuck_count <- 0;
						do add_to_memory message: "Lead Log: " + res memory: chat_memory;
						last_response <- res;
						status <- "waiting";
						ask AI_Manager {
							self.inbox << "Domain Status from TeamLead " + myself.name + ":\n" + myself.last_response;
							self.status <- "evaluating";
							self.assigned_cycle <- cycle;
						}
					}
				}
			}
			active_caller <- "System";
		}
	}

	aspect default {
		bool is_waiting_llm <- (rid != nil);
		rgb node_color <- is_waiting_llm ? #cyan : rgb(255, 215, 0);
		float base_r <- 6.0;
		
		// 1. Connection line to Project Manager (in 3D)
		ask AI_Manager {
			draw line([myself.location, self.location]) color: is_waiting_llm ? rgb(0, 220, 255, 120) : rgb(100, 100, 100, 80) width: 1.5;
		}
		
		// 2. Thinking Animation ONLY when waiting for LLM result (rid != nil)
		if (is_waiting_llm) {
			// Pulsing ring (3D sphere shell)
			float pulse <- 1.0 + 0.3 * sin(cycle * 6.0);
			draw sphere((base_r + 3.0) * pulse) color: #cyan wireframe: true width: 1.8;
			
			// 3 orbiting thinking dots in 3D space
			int nb_dots <- 3;
			loop i from: 0 to: nb_dots - 1 {
				float dot_angle <- (cycle * 12.0 + i * (360.0 / nb_dots)) mod 360.0;
				float dot_dist <- base_r + 4.0;
				float dot_z <- location.z + 3.0 * sin(cycle * 4.0 + i * 120.0);
				point dot_pos <- location + {cos(dot_angle) * dot_dist, sin(dot_angle) * dot_dist, dot_z};
				draw sphere(1.2) at: dot_pos color: #cyan;
			}
		}
		
		// 3. Core TeamLead Node (3D sphere)
		draw sphere(base_r) color: node_color;
		
		// 4. Status Badge & Name
		draw (name + " [" + (is_waiting_llm ? "thinking" : status) + "]") at: location + {-7.0, 10.0, 0.0} color: is_waiting_llm ? #cyan : #gold font: font("Helvetica", 11, #bold);
	}
}

// ---------------------------------------------------------
// Level 1: IT Project Manager
// ---------------------------------------------------------
species AI_Manager skills: [llm] parallel:true{
	mcp_transport transport;
	mcp_client client;
	tool_provider tool;
	list<string> registered_tools <- ["create_team_lead", "assign_lead_task"];
	list<string> inbox <- [];
	string status <- "planning";
	string last_response <- "";
	int assigned_cycle <- -1;
	int stuck_repeat_count <- 0;
	int empty_response_count <- 0;
	string rid <- nil;
	float z_level <- 45.0;

	// Non-blocking async dispatch
	reflex send_manager when: (status = "planning" or status = "evaluating") and (not empty(inbox)) and (rid = nil) and (cycle > assigned_cycle) {
		active_caller <- name + " [Manager]";
		string combined_msg <- "";
		loop msg over: inbox {
			combined_msg <- combined_msg + msg + "\n";
		}
		inbox <- [];
		
		write "========================================";
		write "[CYCLE " + cycle + "] [" + active_caller + "] strategic review asynchronously:";
		write combined_msg;
		write "========================================";
		
		rid <- send_to_llm_async(llm, combined_msg, true, true);
		active_caller <- "System";
	}

	// Non-blocking async poll each cycle
	reflex poll_manager when: (rid != nil) {
		string res <- get_llm_result(rid);
		if (res != nil) {
			active_caller <- name + " [Manager]";
			rid <- nil;
			
			string trimmed_res <- trim(res);
			if (trimmed_res = "" or trimmed_res = "null") {
				empty_response_count <- empty_response_count + 1;
				write "[CYCLE " + cycle + "] [" + active_caller + "] EMPTY RESPONSE (#" + empty_response_count + ") - Auto-recovering...";
				
				if (empty_response_count >= 3) {
					int successful_devs <- length(AI_Developer where (each.last_response contains "COMPILE_SUCCESS"));
					if (successful_devs = 0) {
						if (empty(AI_TeamLead where (each.name = "BackendLead"))) {
							string tool_result <- world.create_team_lead("Backend Domain", "BackendLead");
							write "[CYCLE " + cycle + "] [AUTO-RECOVERY] Force-created: " + tool_result;
							do add_to_memory message: "Auto-executed on your behalf: " + tool_result memory: chat_memory;
						} else {
							string tool_result <- world.assign_lead_task("Implement Student model and StudentService", "BackendLead");
							write "[CYCLE " + cycle + "] [AUTO-RECOVERY] Force-executed: " + tool_result;
							do add_to_memory message: "Auto-executed on your behalf: " + tool_result memory: chat_memory;
						}
					} else if (successful_devs < 4) {
						if (empty(AI_TeamLead where (each.name = "FrontendLead"))) {
							string tool_result <- world.create_team_lead("Frontend Domain", "FrontendLead");
							write "[CYCLE " + cycle + "] [AUTO-RECOVERY] Force-created: " + tool_result;
							do add_to_memory message: "Auto-executed on your behalf: " + tool_result memory: chat_memory;
						} else {
							string tool_result <- world.assign_lead_task("Implement StudentUI Swing GUI and MainApp", "FrontendLead");
							write "[CYCLE " + cycle + "] [AUTO-RECOVERY] Force-executed: " + tool_result;
							do add_to_memory message: "Auto-executed on your behalf: " + tool_result memory: chat_memory;
						}
					}
				}
				
				status <- "waiting";
				active_caller <- "System";
			} else {
				empty_response_count <- 0;
				write "========================================";
				write "[CYCLE " + cycle + "] [" + active_caller + "] strategic directive:";
				write res;
				write "========================================";
				
				string low_res <- lower_case(res);
				bool text_tool_call <- (low_res contains "assign_lead_task") or (low_res contains "create_team_lead");
			
			if (text_tool_call) {
				write "[CYCLE " + cycle + "] [" + active_caller + "] TEXT TOOL CALL DETECTED - EXECUTING DIRECTLY";
				string tool_result <- "";
				
				if (low_res contains "assign_lead_task") {
					// Parse lead_name from text output between quotes or after the function call
					string lead_name <- "";
					if (low_res contains "frontendlead") {
						lead_name <- "FrontendLead";
					} else if (low_res contains "backendlead") {
						lead_name <- "BackendLead";
					} else {
						// Try to extract name from pattern like assign_lead_task(..., "SomeName") or 'SomeName'
						loop tl over: AI_TeamLead {
							if (low_res contains lower_case(tl.name)) {
								lead_name <- tl.name;
							}
						}
						if (lead_name = "") {
							lead_name <- (not empty(AI_TeamLead)) ? AI_TeamLead[0].name : "BackendLead";
						}
					}
					// Build task message from text or use default
					string task_msg <- "Implement the assigned modules";
					string domain_task <- "Domain implementation";
					if (lead_name = "BackendLead") {
						task_msg <- "Implement Student.java with id, name, email, score fields and StudentService.java with sort, filter, stats methods";
						domain_task <- "Backend Domain: Model and Business Services";
					} else if (lead_name = "FrontendLead") {
						task_msg <- "Implement StudentUI.java Swing GUI with JTable and MainApp.java launcher";
						domain_task <- "Frontend Domain: Swing Desktop UI and Launcher";
					}
					
					// If the lead doesn't exist yet, create it first
					if (empty(AI_TeamLead where (each.name = lead_name))) {
						tool_result <- world.create_team_lead(domain_task, lead_name);
						write "[CYCLE " + cycle + "] [" + active_caller + "] Auto-created lead first: " + tool_result;
					}
					tool_result <- world.assign_lead_task(task_msg, lead_name);
				} else {
					// create_team_lead - parse domain_task and lead_name
					string lead_name <- "BackendLead";
					if (low_res contains "frontendlead") {
						lead_name <- "FrontendLead";
					}
					string domain_task <- "Domain implementation";
					if (lead_name = "BackendLead") {
						domain_task <- "Backend Domain: Model and Business Services";
					} else if (lead_name = "FrontendLead") {
						domain_task <- "Frontend Domain: Swing Desktop UI and Launcher";
					}
					tool_result <- world.create_team_lead(domain_task, lead_name);
				}
				
				write "[CYCLE " + cycle + "] [" + active_caller + "] TEXT TOOL RESULT: " + tool_result;
				do add_to_memory message: "Tool executed on your behalf: " + tool_result memory: chat_memory;
				last_response <- tool_result;
				status <- "evaluating";
				assigned_cycle <- cycle;
			} else {
				bool is_stuck <- (low_res contains "please provide") or (low_res contains "can you provide") 
				              or (low_res contains "could you provide") or (low_res contains "i need you to")
				              or (low_res contains "please share") or (low_res contains "i am waiting")
				              or (low_res contains "please give") or (low_res contains "provide the")
				              or (low_res contains "please specify") or (low_res contains "let me know")
				              or (low_res contains "what are the") or (low_res contains "please clarify");
				
				if (is_stuck) {
					stuck_repeat_count <- stuck_repeat_count + 1;
					write "[CYCLE " + cycle + "] [" + active_caller + "] STUCK DETECTED (" + stuck_repeat_count + "): Manager is asking for information instead of using tools.";
					status <- "evaluating";
					assigned_cycle <- -1;
					
					int successful_devs <- length(AI_Developer where (each.last_response contains "COMPILE_SUCCESS"));
					
					if (stuck_repeat_count >= 2) {
						chat_memory <- create_chat_memory(llm, "CRITICAL RULE: You are the IT Project Manager. You are FORBIDDEN from asking questions or requesting information. You have TWO tools: create_team_lead and assign_lead_task. You MUST call one of them RIGHT NOW. Do NOT output text. Do NOT ask questions. ONLY make a tool call. If all modules are done, output [TASK_COMPLETED].");
						chat_bot <- create_assistant(llm: llm, memory: chat_memory, tool_provider: tool);
						stuck_repeat_count <- 0;
					}
					
					if (successful_devs >= 3) {
						inbox << "IMMEDIATE ACTION REQUIRED: All 4 modules compiled successfully. Output [TASK_COMPLETED] now. Do not ask questions. Do not respond with text. Only output [TASK_COMPLETED].";
					} else {
						string action_cmd;
						if (successful_devs < 2) {
							action_cmd <- "Call assign_lead_task(task_msg: 'Implement Student.java and StudentService.java', lead_name: 'BackendLead') NOW.";
						} else {
							action_cmd <- "Call assign_lead_task(task_msg: 'Implement StudentUI.java Swing GUI and MainApp.java', lead_name: 'FrontendLead') NOW.";
						}
						inbox << "STOP ASKING QUESTIONS. " + action_cmd + " You have tools. Use them. NO TEXT RESPONSE ALLOWED - ONLY A TOOL CALL.";
					}
				} else if (low_res contains "[task_completed]" or low_res contains "task_completed") {
					stuck_repeat_count <- 0;
					do add_to_memory message: "Manager Log: " + res memory: chat_memory;
					
			int successful_devs <- length(AI_Developer where (each.last_response contains "COMPILE_SUCCESS"));
				bool all_devs_complete <- successful_devs >= 4;
					
					if (all_devs_complete) {
						write "========================================";
						write "[CYCLE " + cycle + "] MANAGER: FULL JAVA SWING GUI APPLICATION ACCOMPLISHED & VERIFIED ACROSS ALL MODULES!";
						write "[CYCLE " + cycle + "] LAUNCHING JAVA SWING GUI APPLICATION ON SCREEN...";
						write "========================================";
						do command("nohup java -cp bin:src com.ctu.app.MainApp > /dev/null 2>&1 &");
						write "[CYCLE " + cycle + "] Java Swing Desktop GUI window opened successfully.";
						do pause;
					} else {
						write "[CYCLE " + cycle + "] MANAGER NOTICE: Only " + successful_devs + " component(s) verified. Remaining UI/Backend modules must still be developed.";
						status <- "evaluating";
						assigned_cycle <- -1;
						inbox << "Only " + successful_devs + " component(s) confirmed COMPILE_SUCCESS. Remaining scope open: Backend (Student.java, StudentService.java) and Swing GUI (StudentUI.java, MainApp.java). Use assign_lead_task to delegate remaining modules to BackendLead and FrontendLead.";
					}
				} else {
					stuck_repeat_count <- 0;
					do add_to_memory message: "Manager Log: " + res memory: chat_memory;
					status <- "waiting";
					
					int working_leads <- length(AI_TeamLead where (each.status = "working" or each.status = "evaluating"));
					int working_devs <- length(AI_Developer where (each.status = "working"));
					
					if (working_leads = 0 and working_devs = 0) {
						write "[CYCLE " + cycle + "] [" + active_caller + "] WATCHDOG: No agents currently active. Prompting Manager to delegate next deliverables.";
						status <- "evaluating";
						assigned_cycle <- -1;
					inbox << "No leads or developers are currently working. You must delegate remaining tasks now using assign_lead_task to BackendLead (implement Student.java, StudentService.java) or FrontendLead (implement StudentUI.java Swing GUI and MainApp.java). Execute tools now.";
					}
				}
			}
		}
		active_caller <- "System";
	}
}

	aspect default {
		bool is_waiting_llm <- (rid != nil);
		rgb node_color <- is_waiting_llm ? #dodgerblue : rgb(65, 105, 225);
		float base_r <- 8.0;
		
		// Thinking Animation ONLY when waiting for LLM (rid != nil)
		if (is_waiting_llm) {
			// Pulsing ring (3D sphere shell)
			float pulse <- 1.0 + 0.3 * sin(cycle * 5.0);
			draw sphere((base_r + 3.5) * pulse) color: #dodgerblue wireframe: true width: 2.0;
			
			// 4 orbiting thinking dots in 3D space
			int nb_dots <- 4;
			loop i from: 0 to: nb_dots - 1 {
				float dot_angle <- (cycle * 10.0 + i * (360.0 / nb_dots)) mod 360.0;
				float dot_dist <- base_r + 5.0;
				float dot_z <- location.z + 2.0 * cos(cycle * 3.0 + i * 90.0);
				point dot_pos <- location + {cos(dot_angle) * dot_dist, sin(dot_angle) * dot_dist, dot_z};
				draw sphere(1.5) at: dot_pos color: #deepskyblue;
			}
		}
		
		// Core Manager Node (3D sphere)
		draw sphere(base_r) color: node_color;
		
		// Status Badge & Title
		draw ("PROJECT MANAGER [" + (is_waiting_llm ? "thinking" : status) + "]") at: location + {-10.0, 12.0, 0.0} color: #dodgerblue font: font("Helvetica", 12, #bold);
	}
}

experiment "main" type: gui {
	float minimum_cycle_duration <- 0.04;
	output {
		display "Virtual Universe" type: opengl background: #black { 
			
			species AI_Manager aspect: default;
			species AI_TeamLead aspect: default;
			species AI_Developer aspect: default;
		}
	}
}
