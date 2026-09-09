package jeremymorren.opentelemetry.otlp;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public final class OtlpEnvironmentVariables {
    /**
     * How often a debugged process pushes telemetry, in milliseconds. Low by default: the point of the
     * viewer is to see telemetry as it happens, not to batch it efficiently.
     */
    public static final int DEFAULT_FLUSH_INTERVAL_MILLIS = 100;

    /**
     * How often a debugged process pushes metrics, in seconds. Metrics are cumulative rather than
     * per-event, so pushing them as often as traces and logs is pure noise; this is OpenTelemetry's own
     * default interval.
     */
    public static final int DEFAULT_METRICS_FLUSH_INTERVAL_SECONDS = 60;

    public static final String DEFAULT_ENVIRONMENT_VARIABLES = String.join("\n",
            "OTEL_EXPORTER_OTLP_ENDPOINT=${OTLP_ENDPOINT}",
            "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf",
            "OTEL_BSP_SCHEDULE_DELAY=${OTLP_FLUSH_INTERVAL}",
            "OTEL_BLRP_SCHEDULE_DELAY=${OTLP_FLUSH_INTERVAL}",
            "OTEL_METRIC_EXPORT_INTERVAL=${OTLP_METRICS_FLUSH_INTERVAL}"
    );

    private OtlpEnvironmentVariables() {
    }

    @NotNull
    public static Map<String, String> resolve(
            @NotNull String template,
            @NotNull URI endpoint,
            int flushIntervalMillis,
            int metricsFlushIntervalSeconds
    ) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();

        Map<String, String> replacements = Map.of(
                "${OTLP_ENDPOINT}", endpoint.toString(),
                "${OTLP_HOST}", endpoint.getHost(),
                "${OTLP_PORT}", Integer.toString(endpoint.getPort()),
                "${OTLP_FLUSH_INTERVAL}", Integer.toString(flushIntervalMillis),
                // Configured in seconds, but OTEL_METRIC_EXPORT_INTERVAL - like every other OpenTelemetry
                // interval - is read in milliseconds.
                "${OTLP_METRICS_FLUSH_INTERVAL}", Integer.toString(metricsFlushIntervalSeconds * 1000)
        );

        for (String rawLine : template.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                value = value.replace(replacement.getKey(), replacement.getValue());
            }

            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }

        return result;
    }
}
