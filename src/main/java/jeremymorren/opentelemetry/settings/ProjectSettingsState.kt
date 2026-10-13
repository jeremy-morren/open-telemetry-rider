package jeremymorren.opentelemetry.settings

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import jeremymorren.opentelemetry.models.TelemetryType

/**
 * Per-project plugin settings: which telemetry types are hidden for each run configuration.
 *
 * See https://plugins.jetbrains.com/docs/intellij/settings-tutorial.html#the-appsettingscomponent-class
 */
@State(
    name = "jeremymorren.opentelemetry.settings.ProjectSettingsState",
    storages = [Storage("opentelemetry-debug-log-viewer.xml")]
)
class ProjectSettingsState : PersistentStateComponentWithModificationTracker<ProjectSettingsState> {
    private val tracker = SimpleModificationTracker()

    /**
     * Telemetry types hidden per run configuration, as a comma separated list of [TelemetryType]
     * names. Keyed by configuration rather than held once per project, so that hiding metrics while
     * debugging one service does not hide them for another service of the same solution; keyed by name
     * rather than per session, so the choice survives restarting that configuration.
     */
    @JvmField
    @field:MapAnnotation(
        surroundWithTag = false, keyAttributeName = "configuration", valueAttributeName = "hidden",
        entryTagName = "hiddenTelemetryTypes"
    )
    var hiddenTelemetryTypes: MutableMap<String, String> = LinkedHashMap()

    override fun getState(): ProjectSettingsState = this

    override fun loadState(state: ProjectSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun getStateModificationCount(): Long = tracker.modificationCount

    fun getTelemetryVisible(configuration: String, type: TelemetryType): Boolean =
        type !in hidden(configuration)

    fun setTelemetryVisible(configuration: String, type: TelemetryType, value: Boolean) {
        val hidden = hidden(configuration)
        val changed = if (value) hidden.remove(type) else hidden.add(type)
        if (!changed) {
            return
        }

        if (hidden.isEmpty()) {
            hiddenTelemetryTypes.remove(configuration)
        } else {
            hiddenTelemetryTypes[configuration] = hidden.joinToString(",") { it.name }
        }
        tracker.incModificationCount()
    }

    private fun hidden(configuration: String): MutableSet<TelemetryType> {
        val stored = hiddenTelemetryTypes[configuration]
        if (stored.isNullOrBlank()) {
            return LinkedHashSet()
        }

        return stored.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNullTo(LinkedHashSet()) { parse(it) }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ProjectSettingsState =
            project.getService(ProjectSettingsState::class.java)

        private fun parse(name: String): TelemetryType? =
            try {
                TelemetryType.valueOf(name)
            } catch (_: IllegalArgumentException) {
                // A type that no longer exists; drop it rather than failing to load the settings.
                null
            }
    }
}
