package gama.plugin.types;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Manages asynchronous LLM requests. Each request runs on a background thread
 * and results are stored in a thread-safe map for polling.
 */
public class AsyncLLMManager {

	private static final AsyncLLMManager INSTANCE = new AsyncLLMManager();

	private final ExecutorService executor;
	private final Map<String, Future<String>> pendingRequests;
	private final Map<String, String> completedResults;
	private final Map<String, Boolean> requestErrors;

	private AsyncLLMManager() {
		this.executor = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "llm-async-worker");
			t.setDaemon(true);
			return t;
		});
		this.pendingRequests = new ConcurrentHashMap<>();
		this.completedResults = new ConcurrentHashMap<>();
		this.requestErrors = new ConcurrentHashMap<>();
	}

	public static AsyncLLMManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Submit an async LLM request. Returns a unique request ID immediately.
	 * The actual LLM call runs on a background thread.
	 */
	public String submit(Runnable task) {
		String requestId = UUID.randomUUID().toString();
		Future<String> future = executor.submit(() -> {
			try {
				task.run();
				completedResults.put(requestId, "");
			} catch (Exception e) {
				requestErrors.put(requestId, true);
				completedResults.put(requestId, "ERROR: " + e.getMessage());
			} finally {
				pendingRequests.remove(requestId);
			}
			return "";
		});
		pendingRequests.put(requestId, future);
		return requestId;
	}

	/**
	 * Submit an async LLM request with a result producer. Returns a unique request ID immediately.
	 * The producer runs on a background thread and its return value is stored.
	 */
	public String submit(java.util.concurrent.Callable<String> task) {
		String requestId = UUID.randomUUID().toString();
		Future<String> future = executor.submit(() -> {
			try {
				String result = task.call();
				completedResults.put(requestId, result != null ? result : "");
			} catch (Exception e) {
				requestErrors.put(requestId, true);
				completedResults.put(requestId, "ERROR: " + e.getMessage());
			} finally {
				pendingRequests.remove(requestId);
			}
			return "";
		});
		pendingRequests.put(requestId, future);
		return requestId;
	}

	/**
	 * Check if a result is ready for the given request ID.
	 */
	public boolean isReady(String requestId) {
		return completedResults.containsKey(requestId);
	}

	/**
	 * Get the result for the given request ID. Returns null if not ready yet.
	 * Once retrieved, the result is removed from the cache (consumed).
	 */
	public String getResult(String requestId) {
		if (completedResults.containsKey(requestId)) {
			String result = completedResults.remove(requestId);
			requestErrors.remove(requestId);
			return result;
		}
		return null;
	}

	/**
	 * Peek at the result without consuming it. Returns null if not ready.
	 */
	public String peekResult(String requestId) {
		return completedResults.get(requestId);
	}

	/**
	 * Cancel a pending request.
	 */
	public boolean cancel(String requestId) {
		Future<String> future = pendingRequests.remove(requestId);
		if (future != null) {
			future.cancel(true);
			return true;
		}
		return false;
	}

	/**
	 * Check if a request had an error.
	 */
	public boolean hasError(String requestId) {
		return requestErrors.containsKey(requestId);
	}

	/**
	 * Get the number of pending requests.
	 */
	public int getPendingCount() {
		return pendingRequests.size();
	}

	/**
	 * Shutdown the executor. Called when the simulation ends.
	 */
	public void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
		}
	}

	/**
	 * Clear all completed results. Useful for cleanup between simulation runs.
	 */
	public void clearCompleted() {
		completedResults.clear();
		requestErrors.clear();
	}
}
