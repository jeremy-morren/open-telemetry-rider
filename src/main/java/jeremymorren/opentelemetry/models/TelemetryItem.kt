@file:Suppress(
    "PROVIDED_RUNTIME_TOO_LOW",  // See https://github.com/Kotlin/kotlinx.serialization/issues/993#issuecomment-984742051
    "unused")

package jeremymorren.opentelemetry.models

import java.time.Duration
import java.time.Instant
import java.util.*

data class TelemetryItem(
    val json: String,
    val rawJson: String,
    val telemetry: Telemetry
)
{
    val lowerCaseJson: String = json.lowercase(Locale.getDefault())

    val timestamp: Instant? = telemetry.timestamp

    val duration: Duration? = telemetry.activity?.duration
}