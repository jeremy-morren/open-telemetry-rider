@file:Suppress(
    "PROVIDED_RUNTIME_TOO_LOW",  // See https://github.com/Kotlin/kotlinx.serialization/issues/993#issuecomment-984742051
    "unused")

package jeremymorren.opentelemetry.models

import jeremymorren.opentelemetry.util.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Serializable
import java.time.Duration


/**
 * A metric.
 */
@Serializable
data class Metric(
    val metricType: String? = null,
    val temporality: String? = null,
    val name: String? = null,
    val description: String? = null,
    val unit: String? = null,
    val meterName: String? = null,
    val meterVersion: String? = null,
    val meterTags: ObjectDictionary? = null,
    val points: List<MetricPoint>? = null
)
{
    /**
     * Telemetry type (always [TelemetryType.Metric]).
     */
    val type: TelemetryType get() = TelemetryType.Metric

    /**
     * The timestamp of the metric, if available.
     */
    val timestamp: Instant? get() {
        if (points == null) {
            return null
        }
        for (point in points) {
            if (point.startTime != null) {
                return point.startTime
            }
        }
        return null
    }

    /**
     * The last metric point for each tag.
     */
    val taggedPoints: List<MetricPoint>? get() {
        if (points == null) {
            return null
        }
        val map = mutableMapOf<ObjectDictionary?, MetricPoint>()
        for (point in points) {
            map[point.tags] = point
        }
        return map.values.toList()
    }

    /**
     * Detail display string for the metric.
     */
    val detail: String? get() {
        val parts = mutableListOf<String>()
        if (!name.isNullOrEmpty()) {
            parts.add(name)
        }
        if (!description.isNullOrEmpty()) {
            parts.add(description)
        }
        if (!meterName.isNullOrEmpty()) {
            parts.add(meterName)
        }
        if (parts.size == 0) {
            return null
        }
        return parts.joinToString(" - ")
    }

    /**
     * The meter display string.
     */
    val meter: String? get() {
        if (meterName.isNullOrEmpty()) {
            return null
        }
        if (meterVersion.isNullOrEmpty()) {
            return meterName
        }
        return "$meterName ($meterVersion)"
    }

    /**
     * The measurement duration.
     */
    val duration: Duration? get() = points?.firstNotNullOf { it.duration }
}

/**
 * A metric point.
 */
@Serializable
data class MetricPoint(
    @Serializable(with = InstantSerializer::class)
    val startTime: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val endTime: Instant? = null,
    val tags: ObjectDictionary? = null,
    val longSum: Long? = null,
    val doubleSum: Double? = null,
    val longGauge: Long? = null,
    val doubleGauge: Double? = null,
    val histogramCount: Long? = null,
    val histogramSum: Double? = null
)
{
    val duration: Duration? get() {
        if (startTime == null || endTime == null) return null
        return Duration.between(startTime, endTime)
    }
}
