package jeremymorren.opentelemetry.otlp

import java.net.URI

/**
 * The environment variables that point a .NET process's OpenTelemetry exporter at the loopback receiver,
 * resolved from a user-editable template with `${...}` placeholders.
 */
object OtlpEnvironmentVariables {
    /**
     * How often a debugged process pushes telemetry, in milliseconds. Low by default: the point of the
     * viewer is to see telemetry as it happens, not to batch it efficiently.
     */
    const val DEFAULT_FLUSH_INTERVAL_MILLIS = 100

    /**
     * How often a debugged process pushes metrics, in seconds. Metrics are cumulative rather than
     * per-event, so pushing them as often as traces and logs is pure noise; this is OpenTelemetry's own
     * default interval.
     */
    const val DEFAULT_METRICS_FLUSH_INTERVAL_SECONDS = 60

    private const val ENDPOINT = "${'$'}{OTLP_ENDPOINT}"
    private const val HOST = "${'$'}{OTLP_HOST}"
    private const val PORT = "${'$'}{OTLP_PORT}"
    private const val FLUSH_INTERVAL = "${'$'}{OTLP_FLUSH_INTERVAL}"
    private const val METRICS_FLUSH_INTERVAL = "${'$'}{OTLP_METRICS_FLUSH_INTERVAL}"

    const val DEFAULT_ENVIRONMENT_VARIABLES =
        "OTEL_EXPORTER_OTLP_ENDPOINT=$ENDPOINT" + "\n" +
            "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf" + "\n" +
            "OTEL_BSP_SCHEDULE_DELAY=$FLUSH_INTERVAL" + "\n" +
            "OTEL_BLRP_SCHEDULE_DELAY=$FLUSH_INTERVAL" + "\n" +
            "OTEL_METRIC_EXPORT_INTERVAL=$METRICS_FLUSH_INTERVAL"

    @JvmStatic
    fun resolve(
        template: String,
        endpoint: URI,
        flushIntervalMillis: Int,
        metricsFlushIntervalSeconds: Int,
    ): Map<String, String> {
        val replacements = mapOf(
            ENDPOINT to endpoint.toString(),
            HOST to endpoint.host,
            PORT to endpoint.port.toString(),
            FLUSH_INTERVAL to flushIntervalMillis.toString(),
            // Configured in seconds, but OTEL_METRIC_EXPORT_INTERVAL - like every other OpenTelemetry
            // interval - is read in milliseconds.
            METRICS_FLUSH_INTERVAL to (metricsFlushIntervalSeconds * 1000).toString(),
        )

        val result = LinkedHashMap<String, String>()
        for (rawLine in template.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }

            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) {
                continue
            }

            val key = line.substring(0, separatorIndex).trim()
            var value = line.substring(separatorIndex + 1).trim()
            for ((placeholder, replacement) in replacements) {
                value = value.replace(placeholder, replacement)
            }

            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }
}
