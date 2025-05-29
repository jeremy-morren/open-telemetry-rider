@file:Suppress(
    "PROVIDED_RUNTIME_TOO_LOW",  // See https://github.com/Kotlin/kotlinx.serialization/issues/993#issuecomment-984742051
    "unused")

@file:OptIn(ExperimentalSerializationApi::class)

package jeremymorren.opentelemetry.models

import java.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.OptIn
import kotlin.Suppress

@Serializable
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
     * The timestamp of the telemetry.
     */
    val timestamp: Instant? get() {
        val ts = activity?.startTime
            ?: log?.timestamp
            ?: metric?.timestamp
        if (ts != null) {
            return ts
        }
        return null
    }

    /**
     * Exception (formatted) if available.
     */
    val exception: String? = activity?.exception?.display ?: log?.exception?.display
}

/**
 * Telemetry type (determined from properties of the activity).
 * @property Activity The telemetry is a generic activity.
 * @property Request The telemetry is a server request (i.e. ASP.NET Core).
 * @property Dependency The telemetry is a dependency (e.g. HTTP, SQL).
 * @property Metric The telemetry is a metric.
 * @property Message The telemetry is a log message
 * @property Exception The telemetry is a log message or activity with an exception.
 */
@Serializable
enum class TelemetryType {
    Activity,
    Request,
    Dependency,
    Metric,
    Message,
    Exception
}