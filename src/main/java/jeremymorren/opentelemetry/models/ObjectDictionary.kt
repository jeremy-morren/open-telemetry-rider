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
import kotlin.collections.get
import kotlin.collections.iterator

/**
 * A map of polymorphic objects (serialized in C# as Dictionary<string, object>).
 * @property json The raw JSON object.
 */
@Serializable(with = ObjectDictionary.ObjectDictionarySerializer::class)
class ObjectDictionary(private val json: JsonObject) {

    /**
     * The deserialized values
     */
    val values: Map<String, Any?> = createMap(json)

    /**
     * Check if the dictionary contains a key.
     */
    fun containsKey(key: String): Boolean = values.containsKey(key)

    /**
     * Get a value from the dictionary as a string.
     */
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

    /**
     * Get a value from the dictionary as a string, or a default value if the key is not present.
     */
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

    override fun toString(): String {
        return values.toString();
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ObjectDictionary) {
            return false
        }
        return valueEquals(values, other.values)
    }

    override fun hashCode(): Int {
        return 0 //Not implemented
    }

    companion object {
        private fun createMap(json: JsonObject): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            for ((key, value) in json) {
                result[key] = createObject(value)
            }
            return result;
        }

        /**
         * Create a native object from a JSON element.
         */
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

        /**
         * Compare two values for equality.
         */
        private fun valueEquals(left: Any?, right: Any?) : Boolean {
            if (left == null) {
                return right == null
            }
            if (left is String) {
                return right is String && left == right
            }
            if (left is Boolean) {
                return right is Boolean && left == right
            }
            if (left is Int) {
                return right is Int && left == right
            }
            if (left is Double) {
                return right is Double && left == right
            }
            if (left is List<*>) {
                return right is List<*> &&
                        left.size == right.size &&
                        left.zip(right).all { (a, b) -> valueEquals(a, b) }
            }
            if (left is Map<*, *>) {
                return right is Map<*, *> &&
                        left.size == right.size &&
                        left.all { (k, v) -> valueEquals(v, right[k]) }
            }
            return false
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