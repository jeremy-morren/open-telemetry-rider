package jeremymorren.opentelemetry.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

/**
 * Serializer for duration that reads c# TimeSpan format (D.HH:mm:ss.ffffff)
 */
class TimeSpanSerializer : KSerializer<Duration>{

    override val descriptor = PrimitiveSerialDescriptor(
        "jeremymorren.opentelemetry.TimeSpan",
        PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) {
        throw NotImplementedError("Serializing TimeSpan is not supported")
    }

    override fun deserialize(decoder: Decoder): Duration {
        return parse(decoder.decodeString())
    }


    companion object {
        /**
         * Parse a TimeSpan from a string (D.HH:mm:ss.ffffff)
         */
        public fun parse(value: String): Duration {
            if (value[0] == '-') {
                return parseInternal(value.substring(1)).negated();
            }
            return parseInternal(value);
        }

        private fun parseInternal(value: String): Duration {
            val split = value.split(':', limit = 3).toTypedArray()
            val hoursSplit = split[0].split('.', limit = 2).toTypedArray()

            val hours =
                if (hoursSplit.size > 1)
                    (hoursSplit[0].toInt() * 24) + hoursSplit[1].toInt()
                else
                    hoursSplit[0].toInt()
            val minutes = split[1].toInt()
            val seconds = split[2].toDouble()

            val totalSeconds = (hours * 3_600) + (minutes * 60) + seconds
            return totalSeconds.toDuration(DurationUnit.SECONDS).toJavaDuration()
        }
    }
}