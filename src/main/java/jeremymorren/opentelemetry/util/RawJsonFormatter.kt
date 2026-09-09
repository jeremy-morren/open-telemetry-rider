package jeremymorren.opentelemetry.util

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pretty prints the raw OTLP JSON shown on the Raw tab.
 *
 * Protobuf's own printer opens an array and its first element on the same line (`[{`), and spreads
 * every wrapper object over three lines. OTLP is mostly wrapper objects - an attribute's value is
 * `{"stringValue": "..."}` and nothing else - so its output is far taller than the data in it. This
 * keeps a single scalar property on the line it belongs to and gives array elements a line of their
 * own, which is both shorter and easier to scan.
 */
object RawJsonFormatter {
    private const val INDENT = "  "

    @JvmStatic
    fun format(json: String): String {
        val element = try {
            Json.parseToJsonElement(json)
        } catch (ex: SerializationException) {
            // Not something we can read; better to show it as it came than to lose it.
            return json
        }

        return buildString(json.length) { append(element, "") }
    }

    private fun StringBuilder.append(element: JsonElement, indent: String) {
        when (element) {
            is JsonObject -> appendObject(element, indent)
            is JsonArray -> appendArray(element, indent)
            else -> append(element.toString())
        }
    }

    private fun StringBuilder.appendObject(obj: JsonObject, indent: String) {
        if (obj.isEmpty()) {
            append("{}")
            return
        }

        // A wrapper around one scalar reads better on the line that names it.
        val only = obj.entries.singleOrNull()
        if (only != null && only.value is JsonPrimitive) {
            append("{ ").append(key(only.key)).append(": ").append(only.value.toString()).append(" }")
            return
        }

        val inner = indent + INDENT
        append("{\n")
        obj.entries.forEachIndexed { index, (name, value) ->
            if (index > 0) {
                append(",\n")
            }
            append(inner).append(key(name)).append(": ")
            append(value, inner)
        }
        append("\n").append(indent).append("}")
    }

    private fun StringBuilder.appendArray(array: JsonArray, indent: String) {
        if (array.isEmpty()) {
            append("[]")
            return
        }

        val inner = indent + INDENT
        append("[\n")
        array.forEachIndexed { index, value ->
            if (index > 0) {
                append(",\n")
            }
            append(inner)
            append(value, inner)
        }
        append("\n").append(indent).append("]")
    }

    private fun key(name: String): String = JsonPrimitive(name).toString()
}
