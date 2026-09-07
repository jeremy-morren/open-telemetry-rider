@file:Suppress(
    "PROVIDED_RUNTIME_TOO_LOW",  // See https://github.com/Kotlin/kotlinx.serialization/issues/993#issuecomment-984742051
    "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package jeremymorren.opentelemetry.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * A map of polymorphic objects (serialized in C# as Dictionary<string, object>).
 * @property json The raw JSON object.
 */
@Serializable(with = ObjectDictionary.ObjectDictionarySerializer::class)
class ObjectDictionary(private val json: JsonObject) {

    /** The deserialized values */
    val values: Map<String, Any?> = createMap(json)

    /** Check if the dictionary contains a key. */
    fun containsKey(key: String): Boolean = values.containsKey(key)

    /** Get a value from the dictionary as a string. */
    fun getString(key: String): String? {
        val value = values[key]
        if (value is String) {
            return value
        }
        if (value is Number) {
            return value.toString()
        }
        if (value is Boolean) {
            return value.toString()
        }
        return value?.toString();
    }

    /** Get a value from the dictionary as a string, or a default value if the key is not present. */
    fun getStringOrDefault(key: String, default: String): String {
        return getString(key) ?: default
    }

    /**
     * Gets primitive values from the dictionary as strings.
     * Any non-primitive values are ignored.
     */
    fun getPrimitiveValues(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in values) {
            when (value) {
                is String -> result[key] = value
                is Number -> result[key] = value.toString()
                is Boolean -> result[key] = value.toString()
                // Ignore other types
            }
        }
        return result
    }

    /**
     * Values formatted for display, including arrays of primitives - which is how the semantic
     * conventions carry HTTP headers (`http.request.header.<name>`), so those show up too.
     * Nested objects are still skipped; the Raw tab is the place for those.
     */
    fun getDisplayValues(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in values) {
            displayValue(value)?.let { result[key] = it }
        }
        return result
    }

    override fun toString(): String {
        return values.toString();
    }

    override fun equals(other: Any?): Boolean = other is ObjectDictionary && values == other.values

    override fun hashCode(): Int = values.hashCode()

    companion object {
        private fun createMap(json: JsonObject): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            for ((key, value) in json) {
                result[key] = createObject(value)
            }
            return result;
        }

        /** Formats a single value for display, or null when it has no sensible one-line form. */
        private fun displayValue(value: Any?): String? = when (value) {
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> value.mapNotNull(::displayValue).joinToString(", ").takeUnless { it.isEmpty() }
            else -> null
        }

        /** Create a native object from a JSON element. */
        private fun createObject(value: JsonElement?): Any? {
            if (value is JsonPrimitive) {
                return value.booleanOrNull
                    ?: value.intOrNull
                    ?: value.doubleOrNull
                    ?: value.contentOrNull?.replace("\r","") //Remove carriage returns from strings
            }
            if (value is JsonArray) {
                val result = mutableListOf<Any?>()
                for (element in value) {
                    result.add(createObject(element))
                }
                return result
            }
            if (value is JsonObject) {
                return createMap(value)
            }
            return null //Unknown type or null
        }
    }

    class ObjectDictionarySerializer : KSerializer<ObjectDictionary> {
        override val descriptor: SerialDescriptor = SerialDescriptor(
            "jeremymorren.opentelemetry.ObjectDictionary",
            JsonObject.serializer().descriptor)

        override fun deserialize(decoder: Decoder): ObjectDictionary {
            val obj = decoder.decodeSerializableValue(JsonObject.serializer())
            return ObjectDictionary(obj)
        }

        override fun serialize(encoder: Encoder, value: ObjectDictionary) {
            encoder.encodeSerializableValue(JsonObject.serializer(), value.json)
        }
    }
}