package jeremymorren.opentelemetry.models

import java.time.Instant

data class LogMessage(
    val body: String? = null,
    val formattedMessage: String? = null,
    val logLevel: LogLevel? = null,
    val timestamp: Instant? = null,
    val exception: ExceptionInfo? = null,
    val attributes: ObjectDictionary? = null,
    val traceId: String? = null,
    val spanId: String? = null,
    val categoryName: String? = null,
    val eventId: EventId? = null
)
{
    /**
     * The telemetry type (determined from the log message).
     */
    val type: TelemetryType get() =
        if (exception != null) {
            TelemetryType.Exception
        }
        else {
            TelemetryType.Message
        }

    /**
     * Gets the trace IDs as a map.
     */
    val traceIds: Map<String, String>? get() {
        val traceIds = mutableMapOf<String, String>()
        if (traceId != null)
            traceIds["TraceID"] = traceId
        if (spanId != null)
            traceIds["SpanID"] = spanId
        if (traceIds.isEmpty()) {
            return null
        }
        return traceIds
    }

    /**
     * The display message.
     */
    val displayMessage: String? get() {
        if (logLevel == null || formattedMessage == null) {
            return null
        }
        val level = when(logLevel) {
            LogLevel.Trace -> "VRB"
            LogLevel.Debug -> "DBG"
            LogLevel.Information -> "INF"
            LogLevel.Warning -> "WRN"
            LogLevel.Error -> "ERR"
            LogLevel.Critical -> "FTL"
            else -> null
        }
        return "[$level] $formattedMessage"
            .replace("\r", "")
            .replace("\n", " ")
    }
}

data class ExceptionInfo(
    val message: String? = null,
    val display: String? = null,
    val type: String? = null,
    val innerException: ExceptionInfo? = null
)

data class EventId(
    val id: Int,
    val name: String? = null
)

enum class LogLevel {
    Trace,
    Debug,
    Information,
    Warning,
    Error,
    Critical,
    None
}