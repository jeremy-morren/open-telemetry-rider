package jeremymorren.opentelemetry.otlp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import jeremymorren.opentelemetry.models.TelemetryItem
import jeremymorren.opentelemetry.settings.AppSettingState
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Service that manages an HTTP server for receiving OpenTelemetry OTLP protocol telemetry from .NET applications.
 * 
 * This is a singleton application service that:
 * - Starts a lightweight HTTP server on localhost with a random available port
 * - Listens for POST requests on /{projectKey}/v1/traces, /{projectKey}/v1/logs, and /{projectKey}/v1/metrics endpoints
 * - Decodes incoming protobuf OTLP payloads and converts them to domain models
 * - Publishes telemetry items to registered listeners (e.g., UI tool window, debug console)
 * - Maintains a bounded telemetry history per project scope for later debug sessions in that project
 * 
 * The server is created once per IDE session and reused for all debug/run configurations.
 * Uses a bounded fixed thread pool (4 threads) for handling concurrent HTTP requests from loopback.
 */
class OtlpHttpReceiverService : Disposable {
    private val logger = Logger.getInstance(OtlpHttpReceiverService::class.java)
    
    // Decoder: converts raw OTLP protobuf payloads into TelemetryItem domain models
    private val decoder = OtlpTelemetryDecoder()
    
    private val listenersByScope = ConcurrentHashMap<String, CopyOnWriteArrayList<(TelemetryItem) -> Unit>>()
    private val recentTelemetriesByScope = ConcurrentHashMap<String, ArrayDeque<TelemetryItem>>()
    
    // HTTP server instance; lazily initialized on first ensureStarted() call
    @Volatile
    private var server: HttpServer? = null

    // Endpoint URI (e.g., "http://127.0.0.1:4318"); null until server starts
    @Volatile
    private var endpoint: URI? = null

    /**
    * Registers a listener to receive newly published telemetry items for a project scope.
     * 
     * @param scopeKey project scope identifier
     * @param listener callback function invoked for each new telemetry item
     * @return AutoCloseable that unregisters the listener when closed
     */
    fun addListener(scopeKey: String, listener: (TelemetryItem) -> Unit): AutoCloseable {
        val scopeListeners = listenersByScope.computeIfAbsent(scopeKey) { CopyOnWriteArrayList() }
        scopeListeners.add(listener)

        val history = recentTelemetriesByScope.computeIfAbsent(scopeKey) { ArrayDeque() }
        synchronized(history) {
            history.forEach(listener)
        }

        return AutoCloseable {
            scopeListeners.remove(listener)
            if (scopeListeners.isEmpty()) {
                listenersByScope.remove(scopeKey, scopeListeners)
            }
        }
    }

    /**
     * Convenience overload for Java callers using Consumer interface.
     */
    fun addListener(scopeKey: String, listener: Consumer<TelemetryItem>): AutoCloseable =
        addListener(scopeKey) { telemetryItem -> listener.accept(telemetryItem) }

    fun clear(scopeKey: String) {
        val history = recentTelemetriesByScope[scopeKey] ?: return
        synchronized(history) {
            history.clear()
        }
    }

    /**
     * Ensures the HTTP server is started; starts it on first call, returns cached endpoint on subsequent calls.
     * 
     * Thread-safe via @Synchronized: Only one thread will create the server; others wait and get the result.
     * 
     * @return URI of the started server (e.g., "http://127.0.0.1:4318")
     * @throws IllegalStateException if loopback OTLP receiver is disabled in settings
     * 
     * Performance: O(1) after first call (checks cached endpoint variable)
     */
    @Synchronized
    fun ensureStarted(): URI {
        // Fast path: server already started; return cached endpoint immediately
        endpoint?.let { return it }

        val settings = AppSettingState.getInstance()
        if (!settings.enableLoopbackOtlpReceiver.value) {
            throw IllegalStateException("Loopback OTLP receiver is disabled in settings")
        }

        logger.info("Starting open telemetry loopback OTLP receiver...")

        // Create HTTP server bound to loopback interface (127.0.0.1) on any available port
        val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        
        // Register one root context so scoped endpoints like /<scope>/v1/traces are supported.
        httpServer.createContext("/") { exchange -> handle(exchange) }
        
        // Configure executor: Use fixed thread pool for bounded resource usage; 4 threads is sufficient for loopback receiver
        // All threads are daemon threads so they don't block IDE shutdown
        httpServer.executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "OpenTelemetry-OTLP-Receiver").apply { isDaemon = true }
        }
        
        // Start the server (non-blocking; server runs in background thread pool)
        httpServer.start()

        // Cache server instance and endpoint URI for future calls
        server = httpServer
        endpoint = URI("http://127.0.0.1:${httpServer.address.port}")
        logger.info("Loopback OTLP receiver listening on $endpoint")
        return endpoint!!
    }

    /**
     * Handles incoming HTTP requests to the OTLP endpoints.
     * 
     * Process:
     * 1. Validates HTTP method is POST
     * 2. Reads raw protobuf payload from request body
     * 3. Decodes protobuf into TelemetryItem domain models based on signal type
     * 4. Publishes each telemetry item to all registered listeners
     * 5. Sends HTTP response (200 on success, 400 on decode error, 405 for non-POST)
     * 
     * Runs on thread pool executor threads (bounded by fixed pool size).
     */
    private fun handle(exchange: HttpExchange) {
        try {
            // Validate HTTP method
            if (exchange.requestMethod != "POST") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return
            }

            val path = exchange.requestURI.path ?: ""
            val signalType = SignalType.fromPath(path)
            if (signalType == null) {
                sendResponse(exchange, 404, "Not Found")
                return
            }
            val scopeKey = OtlpProjectScope.tryExtractScopeKey(path) ?: DEFAULT_SCOPE

            // Read and decode OTLP protobuf payload
            val telemetries = exchange.requestBody.use { body ->
                val payload = body.readAllBytes()
                // Decode based on signal type
                when (signalType) {
                    SignalType.TRACES -> decoder.decodeTraces(payload)
                    SignalType.LOGS -> decoder.decodeLogs(payload)
                    SignalType.METRICS -> decoder.decodeMetrics(payload)
                }
            }

            // Publish each decoded telemetry item to listeners
            telemetries.forEach { publish(scopeKey, it) }
            sendResponse(exchange, 200, "")
        } catch (ex: Exception) {
            logger.warn("Failed to decode OTLP payload", ex)
            sendResponse(exchange, 400, ex.message ?: "Invalid OTLP payload")
        }
    }

    /**
     * Publishes a single telemetry item to all registered listeners.
     * 
     * Process:
     * 1. Notifies all listeners (typically: UI tool window, debug console, etc.)
     * 
     * Thread-safe: listeners is thread-safe (CopyOnWriteArrayList).
     * Listener exceptions are caught and logged but don't block other listeners.
     */
    private fun publish(scopeKey: String, telemetryItem: TelemetryItem) {
        val history = recentTelemetriesByScope.computeIfAbsent(scopeKey) { ArrayDeque() }
        synchronized(history) {
            history.addLast(telemetryItem)
            while (history.size > 100_000) {
                history.removeFirst()
            }
        }

        listenersByScope[scopeKey]?.forEach { listener ->
            try {
                listener(telemetryItem)
            } catch (ex: Exception) {
                // Log but don't propagate: prevent one listener's error from affecting others
                logger.warn("Failed to dispatch telemetry item to listener", ex)
            }
        }
    }

    /**
     * Sends an HTTP response back to the client.
     * 
     * @param exchange HTTP exchange object
     * @param statusCode HTTP status code (e.g., 200, 400, 405)
     * @param body response body (typically empty for OTLP endpoints)
     * 
     * Handles I/O errors gracefully; always closes the exchange in finally block.
     */
    private fun sendResponse(exchange: HttpExchange, statusCode: Int, body: String) {
        try {
            // Encode response body as UTF-8
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            // Send HTTP headers with response status and content length
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            // Write response body
            exchange.responseBody.use { responseBody ->
                responseBody.write(bytes)
            }
        } catch (ignored: IOException) {
            // Suppress I/O errors; client may have disconnected
        } finally {
            // Always close the exchange to free resources
            exchange.close()
        }
    }

    /**
     * Disposes the service: stops the HTTP server and cleans up resources.
     * 
     * Called by IntelliJ Platform when the plugin is unloaded (IDE shutdown, plugin reload, etc.)
     */
    override fun dispose() {
        // Stop server with zero-second timeout (immediate shutdown)
        server?.stop(0)
        server = null
        endpoint = null
        listenersByScope.clear()
        recentTelemetriesByScope.clear()
    }

    /**
     * Enum of OTLP signal types that can be received.
     */
    private enum class SignalType {
        TRACES,   // Distributed traces (spans)
        LOGS,     // Log records
        METRICS,  // Metrics (gauges, counters, histograms, etc.)

        ;

        companion object {
            fun fromPath(path: String): SignalType? = when {
                path.endsWith("/v1/traces") -> TRACES
                path.endsWith("/v1/logs") -> LOGS
                path.endsWith("/v1/metrics") -> METRICS
                else -> null
            }
        }
    }

    /**
     * Companion object providing static access to the singleton instance.
     */
    companion object {
        private const val DEFAULT_SCOPE = "default"

        @JvmStatic
        fun getInstance(): OtlpHttpReceiverService =
            ApplicationManager.getApplication().getService(OtlpHttpReceiverService::class.java)
    }
}