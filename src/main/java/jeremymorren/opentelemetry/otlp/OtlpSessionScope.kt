package jeremymorren.opentelemetry.otlp

import com.intellij.openapi.project.Project
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Allocates a unique OTLP receiver scope for every launched process.
 *
 * The OTLP receiver is a single application service shared by every open IDE window, so telemetry
 * has to be routed back to the exact debug session that produced it. Each launch is given an opaque
 * scope id which is embedded in the endpoint handed to the process
 * (`http://127.0.0.2:<port>/<scope>`), and the debug session claims the scope its launch was
 * patched with. A session therefore only ever sees its own telemetry, even when several projects of
 * the same solution are debugged simultaneously.
 */
object OtlpSessionScope {
    /**
     * A launch is patched immediately before its process starts, so a pending scope is claimed within
     * milliseconds. Anything older belongs to a launch we will never see a debug session for (a plain
     * "Run", or a cancelled launch) and is discarded so it can never be claimed by a later session.
     */
    private val PENDING_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(60)

    private val pendingByProject = ConcurrentHashMap<String, ArrayDeque<Pending>>()

    /** Allocates the scope for a launch that is about to start. */
    @JvmStatic
    fun registerPending(project: Project): String {
        val scopeKey = UUID.randomUUID().toString()
        val pending = pendingByProject.computeIfAbsent(projectKey(project)) { ArrayDeque() }
        synchronized(pending) {
            dropExpired(pending)
            pending.addLast(Pending(scopeKey, System.nanoTime()))
        }
        return scopeKey
    }

    /**
     * Claims the scope of the launch that has just started, or null when the launch was not patched by
     * this plugin (for example when attaching to an already running process).
     */
    @JvmStatic
    fun claimPending(project: Project): String? {
        val pending = pendingByProject[projectKey(project)] ?: return null
        synchronized(pending) {
            dropExpired(pending)
            return pending.removeLastOrNull()?.scopeKey
        }
    }

    /**
     * Builds the OTLP endpoint for a scope. Exporters append the signal path (e.g. `v1/traces`)
     * to it, which is what [tryExtractScopeKey] reads the scope back out of.
     */
    @JvmStatic
    fun buildScopedEndpoint(endpoint: URI, scopeKey: String): URI = URI.create("$endpoint/$scopeKey")

    /** Reads the scope back out of a received request path, e.g. `/<scope>/v1/traces`. */
    @JvmStatic
    fun tryExtractScopeKey(path: String): String? {
        if (!path.startsWith("/")) {
            return null
        }

        val signalStart = path.indexOf("/v1/")
        if (signalStart <= 1) {
            return null
        }

        return path.substring(1, signalStart)
    }

    private fun dropExpired(pending: ArrayDeque<Pending>) {
        val oldest = System.nanoTime() - PENDING_TIMEOUT_NANOS
        while (pending.isNotEmpty() && pending.first().registeredAt - oldest < 0) {
            pending.removeFirst()
        }
    }

    /** Keyed by location hash rather than by the project itself, so a closed project is not retained. */
    private fun projectKey(project: Project): String = project.locationHash.ifBlank { project.name }

    /** A scope registered for a launch that no debug session has claimed yet. */
    private class Pending(val scopeKey: String, val registeredAt: Long)
}
