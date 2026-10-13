package jeremymorren.opentelemetry.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.Property
import jeremymorren.opentelemetry.otlp.OtlpEnvironmentVariables
import jeremymorren.opentelemetry.settings.converters.BooleanPropertyConverter
import jeremymorren.opentelemetry.settings.converters.FilterTelemetryModePropertyConverter

/**
 * Application-wide plugin settings, persisted to `opentelemetry-debug-log-viewer.xml`.
 *
 * The settings are public fields (`@JvmField`), as the XML serializer binds fields rather than Kotlin
 * properties, and the Java UI code reads them directly.
 */
@State(
    name = "jeremymorren.opentelemetry.settings.AppSettingsState",
    storages = [Storage("opentelemetry-debug-log-viewer.xml")]
)
class AppSettingState : PersistentStateComponentWithModificationTracker<AppSettingState> {
    private val tracker = SimpleModificationTracker()

    @JvmField
    @field:OptionTag(converter = FilterTelemetryModePropertyConverter::class)
    val filterTelemetryMode: Property<FilterTelemetryMode> = Property(FilterTelemetryMode.Default)

    @JvmField
    @field:OptionTag(converter = BooleanPropertyConverter::class)
    val caseInsensitiveSearch: Property<Boolean> = Property(false)

    @JvmField
    @field:OptionTag(converter = BooleanPropertyConverter::class)
    val enableLoopbackOtlpReceiver: Property<Boolean> = Property(true)

    @JvmField
    @field:OptionTag(converter = BooleanPropertyConverter::class)
    val injectOtlpEnvironmentVariables: Property<Boolean> = Property(true)

    /** Whether copied curl commands end with --compressed, the way Chrome DevTools writes them. */
    @JvmField
    @field:OptionTag(converter = BooleanPropertyConverter::class)
    val appendCurlCompressed: Property<Boolean> = Property(true)

    @JvmField
    var otlpEnvironmentVariables: String = OtlpEnvironmentVariables.DEFAULT_ENVIRONMENT_VARIABLES

    /** Value of the `${OTLP_FLUSH_INTERVAL}` placeholder, in milliseconds. */
    @JvmField
    var otlpFlushIntervalMillis: Int = OtlpEnvironmentVariables.DEFAULT_FLUSH_INTERVAL_MILLIS

    /** Value of the `${OTLP_METRICS_FLUSH_INTERVAL}` placeholder, in seconds. */
    @JvmField
    var otlpMetricsFlushIntervalSeconds: Int = OtlpEnvironmentVariables.DEFAULT_METRICS_FLUSH_INTERVAL_SECONDS

    init {
        registerAllPropertyToIncrementTrackerOnChanges(this)
    }

    override fun getState(): AppSettingState = this

    override fun loadState(state: AppSettingState) {
        XmlSerializerUtil.copyBean(state, this)
        registerAllPropertyToIncrementTrackerOnChanges(state)
    }

    override fun getStateModificationCount(): Long = tracker.modificationCount

    private fun registerAllPropertyToIncrementTrackerOnChanges(state: AppSettingState) {
        incrementTrackerWhenPropertyChanges(state.caseInsensitiveSearch)
        incrementTrackerWhenPropertyChanges(state.filterTelemetryMode)
        incrementTrackerWhenPropertyChanges(state.enableLoopbackOtlpReceiver)
        incrementTrackerWhenPropertyChanges(state.injectOtlpEnvironmentVariables)
        incrementTrackerWhenPropertyChanges(state.appendCurlCompressed)
    }

    private fun <T> incrementTrackerWhenPropertyChanges(property: Property<T>) {
        property.advise(LifetimeDefinition()) { tracker.incModificationCount() }
    }

    companion object {
        @JvmStatic
        fun getInstance(): AppSettingState =
            ApplicationManager.getApplication().getService(AppSettingState::class.java)
    }
}
