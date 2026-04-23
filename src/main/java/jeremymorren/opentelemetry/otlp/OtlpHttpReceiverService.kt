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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Service that manages an HTTP server for receiving OpenTelemetry OTLP protocol telemetry from .NET applications.
 * 
 * This is a singleton application service that:
 * - Starts a lightweight HTTP server on localhost with a random available port
 * - Listens for POST requests on /v1/traces, /v1/logs, and /v1/metrics endpoints
 * - Decodes incoming protobuf OTLP payloads and converts them to domain models
 * - Publishes telemetry items to registered listeners (e.g., UI tool window, debug console)
 * - Maintains a deque of recent telemetry for new listeners
 * 
 * The server is created once per IDE session and reused for all debug/run configurations.
 * Uses a bounded fixed thread pool (4 threads) for handling concurrent HTTP requests from loopback.
 */
class OtlpHttpReceiverService : Disposable {
    private val logger = Logger.getInstance(OtlpHttpReceiverService::class.java)
    
    // Decoder: converts raw OTLP protobuf payloads into TelemetryItem domain models
    private val decoder = OtlpTelemetryDecoder()
    
    // Thread-safe list of listeners that receive newly published telemetry items
    private val listeners = CopyOnWriteArrayList<(TelemetryItem) -> Unit>()
    
    // Circular buffer (deque) of recent telemetry items, capped at 100_000 items per signal type
    // Used to replay telemetry history when new listeners register
    private val recentTelemetries = ArrayDeque<TelemetryItem>()

    // HTTP server instance; lazily initialized on first ensureStarted() call
    @Volatile
    private var server: HttpServer? = null

    // Endpoint URI (e.g., "http://127.0.0.1:4318"); null until server starts
    @Volatile
    private var endpoint: URI? = null

    /**
     * Registers a listener to receive newly published telemetry items.
     * 
     * @param listener callback function invoked for each new telemetry item
     * @return AutoCloseable that unregisters the listener when closed
     * 
     * Note: This immediately replays all recent telemetry items to the listener
     * so it can synchronize with the current state.
     */
    fun addListener(listener: (TelemetryItem) -> Unit): AutoCloseable {
        listeners.add(listener)
        // Replay history: send all existing telemetry to the new listener
        synchronized(recentTelemetries) {
            recentTelemetries.forEach(listener)
        }
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Convenience overload for Java callers using Consumer interface.
     */
    fun addListener(listener: Consumer<TelemetryItem>): AutoCloseable =
        addListener { telemetryItem -> listener.accept(telemetryItem) }

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
        
        // Register endpoints for the three OTLP signal types
        httpServer.createContext("/v1/traces") { exchange -> handle(exchange, SignalType.TRACES) }
        httpServer.createContext("/v1/logs") { exchange -> handle(exchange, SignalType.LOGS) }
        httpServer.createContext("/v1/metrics") { exchange -> handle(exchange, SignalType.METRICS) }
        
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
    private fun handle(exchange: HttpExchange, signalType: SignalType) {
        try {
            // Validate HTTP method
            if (exchange.requestMethod != "POST") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return
            }

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
            telemetries.forEach(this::publish)
            sendResponse(exchange, 200, "")
        } catch (ex: Exception) {
            logger.warn("Failed to decode OTLP ${signalType.name.lowercase()} payload", ex)
            sendResponse(exchange, 400, ex.message ?: "Invalid OTLP payload")
        }
    }

    /**
     * Publishes a single telemetry item to all registered listeners.
     * 
     * Process:
     * 1. Adds item to recent telemetry deque (circular buffer, max 100_000 items)
     * 2. Notifies all listeners (typically: UI tool window, debug console, etc.)
     * 
     * Thread-safe: recentTelemetries access is synchronized; listeners is thread-safe (CopyOnWriteArrayList).
     * Listener exceptions are caught and logged but don't block other listeners.
     */
    private fun publish(telemetryItem: TelemetryItem) {
        // Add to history buffer for new listeners
        synchronized(recentTelemetries) {
            recentTelemetries.addLast(telemetryItem)
            // Maintain circular buffer: remove oldest when exceeding 100_000 items
            while (recentTelemetries.size > 100_000) {
                recentTelemetries.removeFirst()
            }
        }

        // Notify all listeners in parallel (using CopyOnWriteArrayList for thread safety)
        listeners.forEach { listener ->
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
        listeners.clear()
        // Clear recent telemetry history
        synchronized(recentTelemetries) {
            recentTelemetries.clear()
        }
    }

    /**
     * Enum of OTLP signal types that can be received.
     */
    private enum class SignalType {
        TRACES,   // Distributed traces (spans)
        LOGS,     // Log records
        METRICS,  // Metrics (gauges, counters, histograms, etc.)
    }

    /**
     * Companion object providing static access to the singleton instance.
     */
    companion object {
        @JvmStatic
        fun getInstance(): OtlpHttpReceiverService =
            ApplicationManager.getApplication().getService(OtlpHttpReceiverService::class.java)
    }
}