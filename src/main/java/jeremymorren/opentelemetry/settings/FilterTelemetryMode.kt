package jeremymorren.opentelemetry.settings

/** How the telemetry table is sorted. */
enum class FilterTelemetryMode {
    /** In the order the telemetry was received. */
    Default,
    /** Shortest first. */
    Duration,
    /** Earliest first. */
    Timestamp
}
