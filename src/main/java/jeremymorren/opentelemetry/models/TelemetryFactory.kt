@file:Suppress(
    "PROVIDED_RUNTIME_TOO_LOW",  // See https://github.com/Kotlin/kotlinx.serialization/issues/993#issuecomment-984742051
    "unused")

@file:OptIn(ExperimentalSerializationApi::class)

package jeremymorren.opentelemetry.models

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class TelemetryFactory {
    // Very long lines can be split into multiple parts
    // We need to concatenate them before parsing
    private val currentValue: StringBuilder = StringBuilder()

    fun tryCreateFromDebugOutputLog(output: String): TelemetryItem? {
        val openTelemetryLogPrefix = "OpenTelemetry {\""

        val value = output.trimEnd()

        if (currentValue.isEmpty() && !value.startsWith(openTelemetryLogPrefix)) {
            // Not a telemetry log
            return null
        }

        currentValue.append(value)

        val json = currentValue.toString().substring(openTelemetryLogPrefix.length - 2)
        val jsonElement = tryParseJson(json)
            ?: return null  // Not a complete telemetry log

        currentValue.clear()

        return try {
            val serializer = Json {
                ignoreUnknownKeys = true
                decodeEnumsCaseInsensitive = true
                allowSpecialFloatingPointValues = true
            }
            val telemetry = serializer.decodeFromJsonElement<Telemetry>(jsonElement)
            return TelemetryItem(formatJson(json), telemetry)
        } catch (e: SerializationException) {
            val logger = Logger.getInstance(TelemetryFactory::class.java)
            logger.error("Failed to parse telemetry log", e)
            currentValue.clear()
            return null
        }
    }

    private fun formatJson(json: String): String {
        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        val obj = Json.decodeFromString<JsonObject>(json)
        return prettyJson.encodeToString(JsonObject.serializer(), obj)
    }

    private fun tryParseJson(json: String): JsonElement? {
        return try {
            return Json.decodeFromString<JsonElement>(json)
        } catch (e: SerializationException) {
            return null
        }
    }
}