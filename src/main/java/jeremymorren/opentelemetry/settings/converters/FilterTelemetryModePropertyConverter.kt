package jeremymorren.opentelemetry.settings.converters

import com.intellij.util.xmlb.Converter
import com.jetbrains.rd.util.reactive.Property
import jeremymorren.opentelemetry.settings.FilterTelemetryMode

/** Persists a `Property<FilterTelemetryMode>` setting by its enum name. */
class FilterTelemetryModePropertyConverter : Converter<Property<FilterTelemetryMode>>() {
    override fun fromString(value: String): Property<FilterTelemetryMode> =
        Property(FilterTelemetryMode.valueOf(value))

    override fun toString(value: Property<FilterTelemetryMode>): String = value.value.name
}
