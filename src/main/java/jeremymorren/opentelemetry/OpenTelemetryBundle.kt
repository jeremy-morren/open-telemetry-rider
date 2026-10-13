package jeremymorren.opentelemetry

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.OpenTelemetryBundle"

/** Localized UI strings for the plugin, loaded from `messages/OpenTelemetryBundle.properties`. */
object OpenTelemetryBundle : DynamicBundle(BUNDLE) {
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
