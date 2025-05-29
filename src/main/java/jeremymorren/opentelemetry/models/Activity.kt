@file:Suppress(
    "PROVIDED_RUNTIME_TOO_LOW",  // See https://github.com/Kotlin/kotlinx.serialization/issues/993#issuecomment-984742051
    "unused")

@file:OptIn(ExperimentalSerializationApi::class)

package jeremymorren.opentelemetry.models

import jeremymorren.opentelemetry.util.InstantSerializer
import jeremymorren.opentelemetry.util.TimeSpanSerializer
import java.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.*
import java.time.Duration

@Serializable
data class Activity(
    val rootId: String? = null,
    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null,
    val activityTraceFlags: String? = null,
    val traceStateString: String? = null,
    val source: ActivitySource? = null,
    val displayName: String? = null,
    val kind: ActivityKind? = null,
    @Serializable(with = InstantSerializer::class)
    val startTime: Instant? = null,
    @Serializable(with = TimeSpanSerializer::class)
    val duration: Duration? = null,
    val tags: ObjectDictionary? = null,
    val operationName: String? = null,
    val status: ActivityStatusCode? = null,
    val statusDescription: String? = null,
    val events: List<ActivityEvent>? = null,
) {
    /**
     * The type of the activity.
     */
    val type: TelemetryType
        get() =
            if (exception != null) TelemetryType.Exception
            else if (kind == ActivityKind.Server && tags != null && tags.containsKey("url.path")) TelemetryType.Request
            else if (kind == ActivityKind.Client) TelemetryType.Dependency
            else TelemetryType.Activity

    /**
     * The dependency type (HTTP or SQL) if the activity is a dependency.
     */
    val dependencyType: DependencyType?
        get() =
            if (kind == ActivityKind.Client)
            {
                if (tags == null) null
                else if (tags.containsKey("http.request.method")) DependencyType.HTTP
                else if (tags.containsKey("db.system")) DependencyType.SQL
                else null
            }
            else {
                null
            }

    /**
     * True if the activity is an error.
     * See https://github.com/Azure/azure-sdk-for-net/blob/46819bd8a4bd0db2e2d3ab0a5fada5d82bb7295d/sdk/monitor/Azure.Monitor.OpenTelemetry.Exporter/src/Internals/TraceHelper.cs#L229
     */

    val isError: Boolean
        get() = status == ActivityStatusCode.Error ||
                tags?.containsKey("error.type") == true ||
                tags?.getString("otel.status_code") == "ERROR"

    /**
     * The error display string.
     */
    val errorDisplay: String? get() =
        exception?.message?.replace("\n", " ") ?:
        tags?.getString("otel.status_description") ?:
        tags?.getString("otel.status_code") ?:
        tags?.getString("db.response.status_code") ?:
        tags?.getString("error.type")

    /**
     * Exception (extracted from [events])
     */
    val exception: ActivityException?
        get() {
            for (event in events ?: emptyList()) {
                if (event.name == "exception" && event.tags != null && event.timestamp != null) {
                    val tags = event.tags.getPrimitiveValues()
                    val message = tags["exception.message"]
                    val stacktrace = tags["exception.stacktrace"]
                    val type = tags["exception.type"]
                    if (message != null && stacktrace != null && type != null) {
                        return ActivityException(event.timestamp, message, stacktrace, type)
                    }
                }
            }
            return null
        }

    /**
     * The display string for the activity type (type and subtype).
     */
    val typeDisplay: String
        get() {
            if (dependencyType == null) {
                return type.name
            }
            return "${type.name} - ${dependencyType!!.name}"
        }

    /**
     * Request path for request activity (request i.e. server side)
     */
    val requestPath: String?
        get() {
            if (tags == null) return null
            val sb = StringBuilder()
            sb.append(tags.getStringOrDefault("url.path", ""))
            if (tags.containsKey("url.query")) {
                sb.append("?")
                sb.append(tags.getString("url.query"))
            }
            if (sb.isEmpty()) return null
            return sb.toString()
        }

    /**
     * The URL path for HTTP request (dependency i.e. client side)
     */
    val urlPath: String?
        get() {
            val value = tags?.getString("url.full") ?: return null
            try {
                // Try to parse the URL and return the path and query
                val uri = URI(value)
                if (uri.query == null) {
                    return uri.path
                }
                return uri.path + uri.query
            } catch (e: Exception) {
                return value
            }
        }

    /**
     * The database query for SQL activity.
     */
    val dbQuery: String? get() = tags?.getString("db.query.text") ?: tags?.getString("db.statement")

    /**
     * The database name for SQL activity.
     */
    val dbName: String? get() = tags?.getString("db.name")

    /**
     * The response status code for HTTP request activity.
     */
    val responseStatusCode: String? get() = tags?.getString("http.response.status_code")

    /**
     * Gets the trace IDs as a map.
     */
    val traceIds: Map<String, String> get() {
        val traceIds = mutableMapOf<String, String>()
        if (rootId != null)
            traceIds["Root ID"] = rootId
        if (traceId != null)
            traceIds["Trace ID"] = traceId
        if (spanId != null)
            traceIds["Span ID"] = spanId
        if (parentSpanId != null)
            traceIds["Parent Span ID"] = parentSpanId
        if (activityTraceFlags != null)
            traceIds["Flags"] = activityTraceFlags
        return traceIds
    }

    /**
     * Gets the time spent in the database (i.e. time between start of activity and first response received)
     */
    val dbQueryTime: Duration? get() {
        if (events == null || startTime == null) {
            return null
        }
        // Find the event called "received-first-response"
        for (event in events) {
            if (event.name == "received-first-response" && event.timestamp != null) {
                return Duration.between(startTime, event.timestamp)
            }
        }
        return null
    }

    /**
     * Gets the time spent reading from the database (i.e. time between first response received and end of activity)
     */
    val dbReadTime: Duration? get() {
        if (dbQueryTime == null || duration == null) {
            return null
        }
        return duration - dbQueryTime!!
    }

    /**
     * Detail string for the activity.
     */
    val detail: String? get() {
        val parts = mutableListOf<String>()
        if (dependencyType != null) {
            parts.add(dependencyType!!.name)
        }
        //Show the source if the type is activity (i.e. not request or dependency)
        if (source != null && type == TelemetryType.Activity) {
            parts.add(source.name)
        }
        if (!displayName.isNullOrEmpty()) {
            // SqlClient sends the database name as the display name (which is not useful)
            if (displayName != dbName) {
                parts.add(displayName)
            }
            //If the request does not match a controller, display name will only be method
            //For those, add the request path to the detail
            if (type == TelemetryType.Request && requestPath != null && !displayName.contains(' ')) {
                parts.add(requestPath!!)
            }
        }

        if (responseStatusCode != null) {
            parts.add(responseStatusCode!!)
        }
        if (isError) {
            if (statusDescription != null) {
                parts.add(statusDescription)
            }
            if (errorDisplay != null) {
                parts.add(errorDisplay!!)
            }
        }
        if (dbQuery != null) {
            parts.add(dbQuery!!)
        }
        if (urlPath != null) {
            parts.add(urlPath!!)
        }
        if (parts.size == 0) {
            return null
        }
        val str = parts.joinToString(" - ")
            .replace("\r", "")
            .replace("\n", " ")
        if (str.length > 100) {
            return str.substring(0, 100) + "..."
        }
        return str
    }
}

@Serializable
data class ActivitySource(
    val name: String,
    val version: String? = null
)
{
    val nameLower: String = name.lowercase(Locale.ROOT)
}

/**
 * An activity event.
 */
@Serializable
data class ActivityEvent(
    val name: String? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant? = null,
    val tags: ObjectDictionary? = null,
)

@Serializable
data class ActivityException(
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val message: String,
    val stacktrace: String,
    val type: String
) {
    val display: String = stacktrace
}

/**
 * Activity status code.
 * See [ActivityStatusCode Enum](https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.activitystatuscode)
 */
@Serializable
enum class ActivityStatusCode {
    Unset,
    Ok,
    Error,
}

/**
 * Activity kind.
 * See [ActivityKind Enum](https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.activitykind)
 */
@Serializable
enum class ActivityKind {
    Internal,
    Server,
    Client,
    Producer,
    Consumer,
}

/**
 * Dependency type. Determined from the activity tags.
 * @property HTTP The activity is an HTTP request.
 * @property SQL The activity is a SQL query.
 */
@Serializable
enum class DependencyType {
    HTTP,
    SQL
}
