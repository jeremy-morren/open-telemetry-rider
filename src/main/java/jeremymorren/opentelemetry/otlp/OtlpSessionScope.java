package jeremymorren.opentelemetry.otlp;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Allocates a unique OTLP receiver scope for every launched process.
 *
 * <p>The OTLP receiver is a single application service shared by every open IDE window, so telemetry
 * has to be routed back to the exact debug session that produced it. Each launch is given an opaque
 * scope id which is embedded in the endpoint handed to the process
 * ({@code http://127.0.0.1:<port>/<scope>}), and the debug session claims the scope its launch was
 * patched with. A session therefore only ever sees its own telemetry, even when several projects of
 * the same solution are debugged simultaneously.
 */
public final class OtlpSessionScope {
    /**
     * A launch is patched immediately before its process starts, so a pending scope is claimed within
     * milliseconds. Anything older belongs to a launch we will never see a debug session for (a plain
     * "Run", or a cancelled launch) and is discarded so it can never be claimed by a later session.
     */
    private static final long PENDING_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(60);

    private static final Map<String, Deque<Pending>> PENDING_BY_PROJECT = new ConcurrentHashMap<>();

    private OtlpSessionScope() {
    }

    /**
     * Allocates the scope for a launch that is about to start.
     */
    @NotNull
    public static String registerPending(@NotNull Project project) {
        String scopeKey = UUID.randomUUID().toString();
        Deque<Pending> pending = PENDING_BY_PROJECT.computeIfAbsent(projectKey(project), key -> new ArrayDeque<>());
        synchronized (pending) {
            dropExpired(pending);
            pending.addLast(new Pending(scopeKey, System.nanoTime()));
        }
        return scopeKey;
    }

    /**
     * Claims the scope of the launch that has just started, or null when the launch was not patched by
     * this plugin (for example when attaching to an already running process).
     */
    @Nullable
    public static String claimPending(@NotNull Project project) {
        Deque<Pending> pending = PENDING_BY_PROJECT.get(projectKey(project));
        if (pending == null) {
            return null;
        }
        synchronized (pending) {
            dropExpired(pending);
            Pending claimed = pending.pollLast();
            return claimed == null ? null : claimed.scopeKey;
        }
    }

    /**
     * Builds the OTLP endpoint for a scope. Exporters append the signal path (e.g. {@code v1/traces})
     * to it, which is what {@link #tryExtractScopeKey} reads the scope back out of.
     */
    @NotNull
    public static URI buildScopedEndpoint(@NotNull URI endpoint, @NotNull String scopeKey) {
        return URI.create(endpoint.toString() + "/" + scopeKey);
    }

    /**
     * Reads the scope back out of a received request path, e.g. {@code /<scope>/v1/traces}.
     */
    @Nullable
    public static String tryExtractScopeKey(@NotNull String path) {
        if (!path.startsWith("/")) {
            return null;
        }

        int signalStart = path.indexOf("/v1/");
        if (signalStart <= 1) {
            return null;
        }

        return path.substring(1, signalStart);
    }

    private static void dropExpired(@NotNull Deque<Pending> pending) {
        long oldest = System.nanoTime() - PENDING_TIMEOUT_NANOS;
        while (!pending.isEmpty() && pending.peekFirst().registeredAt - oldest < 0) {
            pending.removeFirst();
        }
    }

    /**
     * Keyed by location hash rather than by the project itself, so a closed project is not retained.
     */
    @NotNull
    private static String projectKey(@NotNull Project project) {
        String locationHash = project.getLocationHash();
        return locationHash.isBlank() ? project.getName() : locationHash;
    }

    private record Pending(@NotNull String scopeKey, long registeredAt) {
    }
}
