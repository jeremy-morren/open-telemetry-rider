package jeremymorren.opentelemetry.settings.converters

import com.intellij.util.xmlb.Converter
import com.jetbrains.rd.util.reactive.Property

/** Persists a `Property<Boolean>` setting as `true` or `false`. */
class BooleanPropertyConverter : Converter<Property<Boolean>>() {
    override fun fromString(value: String): Property<Boolean> = Property(value == "true")

    override fun toString(value: Property<Boolean>): String = value.value.toString()
}
