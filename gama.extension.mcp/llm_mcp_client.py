import asyncio
import websockets
import json
import urllib.request
import sys

OLLAMA_URL = "http://localhost:11434/api/chat"
MODEL = "llama3.2:latest"

async def run_llm_mcp(port, model_path):
    uri = f"ws://localhost:{port}"
    print(f"Connecting to GAMA MCP server at {uri}...")
    try:
        async with websockets.connect(uri) as websocket:
            # 1. Wait for connection success message from GAMA MCP
            greeting = json.loads(await websocket.recv())
            print(f"Connected! Server greeting: {greeting}")
            
            if greeting.get("type") != "ConnectionSuccessful":
                print(f"Unexpected greeting, expected ConnectionSuccessful: {greeting}")
                return
            
            # Detect if we accidentally hit GAMA's own server instead of the MCP one
            if str(greeting.get("content", "")).isdigit():
                print("\n*** ERROR: Connected to GAMA's built-in WebSocket server, NOT the MCP server! ***")
                print(f"*** GAMA's own server is running on port {port}. ***")
                print(f"*** Use a different port in your GAML model, e.g.: do connect protocol: 'mcp_server' port: {int(port)+2}; ***")
                return
            
            # 2. Get tools list from MCP
            req_tools = {"jsonrpc": "2.0", "method": "tools/list", "id": 1}
            await websocket.send(json.dumps(req_tools))
            res_tools = json.loads(await websocket.recv())
            
            if "error" in res_tools:
                print(f"Error getting tools: {res_tools['error']}")
                return
                
            mcp_tools = res_tools["result"]["tools"]
            print(f"\nRetrieved {len(mcp_tools)} tools from GAMA MCP:")
            for t in mcp_tools:
                print(f"  - {t['name']}: {t['description']}")
            
            # 3. Format tools for Ollama
            ollama_tools = []
            for t in mcp_tools:
                ollama_tools.append({
                    "type": "function",
                    "function": {
                        "name": t["name"],
                        "description": t["description"],
                        "parameters": t["inputSchema"]
                    }
                })
                
            # 4. Ask Ollama to use the validate_model tool
            source_code = ""
            try:
                with open(model_path, 'r', encoding='utf-8') as f:
                    source_code = f.read()
            except Exception as e:
                print(f"Warning: could not read model file {model_path}: {e}")
                
            prompt = f"Please validate the GAML model file at: '{model_path}'. Use the validate_model tool to check it.\n\nHere is the current source code for reference:\n```gaml\n{source_code}\n```"
            print(f"\nSending prompt to Ollama ({MODEL})...")
            
            data = {
                "model": MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "tools": ollama_tools,
                "stream": False
            }
            
            req = urllib.request.Request(
                OLLAMA_URL, 
                data=json.dumps(data).encode('utf-8'), 
                headers={'Content-Type': 'application/json'}
            )
            with urllib.request.urlopen(req, timeout=60) as response:
                result = json.loads(response.read().decode('utf-8'))
                
            message = result.get("message", {})
            if "tool_calls" in message and len(message["tool_calls"]) > 0:
                tool_call = message["tool_calls"][0]
                func = tool_call["function"]
                print(f"\nOllama decided to call tool: '{func['name']}' with:")
                print(f"  Arguments: {json.dumps(func['arguments'], indent=2)}")
                
                # 5. Execute the tool call on GAMA MCP
                req_call = {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 2,
                    "params": {
                        "name": func["name"],
                        "arguments": func["arguments"]
                    }
                }
                print("\nForwarding tool call to GAMA MCP server...")
                await websocket.send(json.dumps(req_call))
                res_call = json.loads(await websocket.recv())
                
                print(f"\nGAMA MCP result:")
                print(json.dumps(res_call, indent=2))
                
                # 6. Send result back to Ollama for a final summary and potential fix
                print("\nAsking Ollama to summarize the result and provide fixes if needed...")
                data2 = {
                    "model": MODEL,
                    "messages": [
                        {"role": "user", "content": prompt},
                        {"role": "assistant", "content": None, "tool_calls": message["tool_calls"]},
                        {"role": "tool", "content": json.dumps(res_call.get("result", {}).get("content", [{}])[0].get("text", ""))},
                        {"role": "user", "content": "Based on the validation results, please summarize the outcome. If there are any validation errors or syntax issues, please explain what went wrong and provide the corrected GAML code to fix them."}
                    ],
                    "stream": False
                }
                req2 = urllib.request.Request(
                    OLLAMA_URL, 
                    data=json.dumps(data2).encode('utf-8'), 
                    headers={'Content-Type': 'application/json'}
                )
                with urllib.request.urlopen(req2, timeout=60) as response2:
                    result2 = json.loads(response2.read().decode('utf-8'))
                print(f"\nOllama summary: {result2.get('message', {}).get('content', '')}")
            else:
                print(f"\nOllama responded without calling a tool (no tool_calls in response).")
                print(f"LLM reply: {message.get('content', '')}")
                
    except ConnectionRefusedError:
        print(f"Connection refused - GAMA MCP server is not running on port {port}")
        print(f"Make sure your GAML model is running with: do connect protocol: 'mcp_server' port: {port};")
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 llm_mcp_client.py <mcp_port> <path_to_gaml_model>")
        print("Example: python3 llm_mcp_client.py 8082 /path/to/model.gaml")
        sys.exit(1)
    port = sys.argv[1]
    path = sys.argv[2]
    asyncio.run(run_llm_mcp(port, path))
