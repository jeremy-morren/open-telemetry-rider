package jeremymorren.opentelemetry.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

class TelemetryFactory {
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun createFromTelemetry(telemetry: Telemetry, rawJson: String): TelemetryItem {
        val json = objectMapper.writeValueAsString(telemetry)
        return TelemetryItem(json, rawJson, telemetry)
    }
}