package jeremymorren.opentelemetry.models

import java.time.Instant

/**
 * One piece of received telemetry: an [activity], [metric] or [log] (normally only one is set), plus
 * the [resource] describing the process that sent it.
 */
data class Telemetry(
    val activity: Activity? = null,
    val metric: Metric? = null,
    val log: LogMessage? = null,
    val resource: ObjectDictionary? = null
)
{
    val traceIds: Map<String, String>? = activity?.traceIds ?: log?.traceIds

    val sql: String? = activity?.dbQuery

    val type: TelemetryType? = activity?.type ?: log?.type ?: metric?.type

    /**
     * The text the table shows for this telemetry, untruncated.
     *
     * Searching matches against this as well as the raw JSON, so a filter can match what is on screen
     * (including text the column had to cut off, and multi-line SQL flattened onto one line).
     */
    val displayText: String? = activity?.detailFull ?: metric?.detail ?: log?.displayMessage

    /** The timestamp of the telemetry. */
    val timestamp: Instant? get() {
        val ts = activity?.startTime
            ?: log?.timestamp
            ?: metric?.timestamp
        if (ts != null) {
            return ts
        }
        return null
    }

    /** Exception (formatted) if available. */
    val exception: String? = activity?.exception?.display ?: log?.exception?.display
}

/** Telemetry type (determined from properties of the activity). */
enum class TelemetryType {
    /** The telemetry is a generic activity. */
    Activity,
    /** The telemetry is a server request (i.e. ASP.NET Core). */
    Request,
    /** The telemetry is a dependency (e.g. HTTP, SQL). */
    Dependency,
    /** The telemetry is a metric. */
    Metric,
    /** The telemetry is a log message. */
    Message,
    /** The telemetry is an Azure Monitor custom event (a log carrying `microsoft.custom_event.name`). */
    Event,
    /** The telemetry is a log message or activity with an exception. */
    Exception
}
