package jeremymorren.opentelemetry.util

import java.text.DecimalFormat
import java.time.Duration

class DurationFormatter {

    companion object {


        /**
         * Formats a duration as a string.
         */
        public fun format(duration: Duration): String {
            if (duration.isNegative) {
                return "-" + formatInternal(duration.negated())
            }
            return formatInternal(duration)
        }

        val decFormat = DecimalFormat("0.0")

        private fun formatInternal(duration: Duration): String {
            if (duration == Duration.ZERO) {
                return "-" // Zero duration
            }

            // Format the first significant part

            if (duration.toHours() > 0) {
                val hours = duration.toHours()
                val minutes = totalMinutes(duration) - (hours * 60)
                return "${hours}h ${decFormat.format(minutes)}m"
            }

            if (duration.toMinutes() > 0) {
                val minutes = duration.toMinutes()
                val seconds = totalSeconds(duration) - (minutes * 60)
                return "${minutes}m ${decFormat.format(seconds)}s"
            }

            if (duration.toSeconds() > 0) {
                val seconds = totalSeconds(duration)
                return "${decFormat.format(seconds)} s"
            }
            if (duration.toMillis() > 0) {
                val milliseconds = totalMilliseconds(duration)
                return "${decFormat.format(milliseconds)} ms"
            }
            if (duration.toNanos() / 1_000 > 0) {
                val microseconds = totalMicroseconds(duration)
                return "${decFormat.format(microseconds)} µs"
            }

            // NB: Don't need to format nanoseconds, since they will never have a decimal part
            return "${duration.toNanos()} ns"
        }

        private fun totalMinutes(duration: Duration): Double {
            return totalSeconds(duration) / 60.0
        }

        private fun totalSeconds(duration: Duration): Double {
            return duration.toSeconds() + (duration.toNanosPart() / 1_000_000_000.0)
        }

        private fun totalMilliseconds(duration: Duration): Double {
            return duration.toNanos() / 1_000_000.0
        }

        private fun totalMicroseconds(duration: Duration): Double {
            return duration.toNanos() / 1_000.0
        }
    }
}