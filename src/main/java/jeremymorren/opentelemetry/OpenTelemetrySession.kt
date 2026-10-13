package jeremymorren.opentelemetry

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader.getIcon
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DotNetDebugProcess
import jeremymorren.opentelemetry.models.TelemetryItem
import jeremymorren.opentelemetry.models.TelemetryType
import jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService.Companion.getInstance
import jeremymorren.opentelemetry.otlp.OtlpSessionScope
import jeremymorren.opentelemetry.settings.AppSettingState
import jeremymorren.opentelemetry.settings.FilterTelemetryMode
import jeremymorren.opentelemetry.settings.ProjectSettingsState
import jeremymorren.opentelemetry.ui.OpenTelemetryToolWindow
import java.lang.AutoCloseable
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Consumer

/**
 * The viewer state for one .NET debug session.
 *
 * Receives the telemetry routed to the OTLP scope its launch was patched with, keeps the full and
 * filtered (sorted, searched) lists, and pushes them to the session's tool window tab.
 */
class OpenTelemetrySession(
    private val dotNetDebugProcess: DotNetDebugProcess
) {
    private val telemetries: MutableList<TelemetryItem> = ArrayList()
    private val filteredTelemetries: MutableList<TelemetryItem> = ArrayList()
    private val lifetime: Lifetime = dotNetDebugProcess.sessionLifetime

    /**
     * Scope of the launch this session belongs to; null when the launch was not patched by this plugin
     * (e.g. attaching to an already running process), in which case no telemetry is routed here.
     */
    private val scopeKey: String? = OtlpSessionScope.claimPending(dotNetDebugProcess.project)
    var filter: String = ""
        private set

    /**  Filter string, escaped to JSON string */
    private var filterEscaped = ""

    /** Filter string in lower case, escaped to JSON string */
    private var filterLowerCaseEscaped = ""

    /** Filter string in lower case, unescaped (matched against the rendered row text) */
    private var filterLowerCase = ""

    private var openTelemetryToolWindow: OpenTelemetryToolWindow? = null
    private var telemetryListenerRegistration: AutoCloseable? = null
    private var firstMessage = true
    private val projectSettingsState: ProjectSettingsState = ProjectSettingsState.getInstance(dotNetDebugProcess.project)

    /**
     * Run configuration this session belongs to; telemetry type filters are remembered against it, so
     * two services of the same solution keep their own filters and keep them across restarts.
     */
    private var filterConfiguration: String? = null
        get() {
            if (field == null) {
                // Resolved lazily: the session name is not necessarily available while the process starts.
                var name: String? = null
                try {
                    name = dotNetDebugProcess.session.sessionName
                } catch (_: Exception) {
                }
                field = if (name.isNullOrBlank()) dotNetDebugProcess.project.name else name
            }
            return field
        }

    init {
        val appSettingsState = AppSettingState.getInstance()
        appSettingsState.filterTelemetryMode
            .advise(lifetime) { _ -> this.updateFilteredTelemetries() }
        appSettingsState.caseInsensitiveSearch
            .advise(lifetime) { _ -> this.updateFilteredTelemetries() }
    }

    fun startListeningToOtlpReceiver() {
        if (scopeKey == null) {
            LOG.info("No OTLP scope was allocated for this debug session; telemetry will not be captured")
            return
        }
        getInstance().ensureStarted()
        telemetryListenerRegistration = getInstance().addListener(
            scopeKey,
            Consumer { telemetry: TelemetryItem? -> this.addTelemetry(telemetry!!) })
        dotNetDebugProcess.processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                disposeTelemetryListener()
            }
        })
    }

    fun isTelemetryVisible(telemetryType: TelemetryType): Boolean {
        return projectSettingsState.getTelemetryVisible(this.filterConfiguration!!, telemetryType)
    }

    fun setTelemetryVisible(telemetryType: TelemetryType, visible: Boolean) {
        projectSettingsState.setTelemetryVisible(this.filterConfiguration!!, telemetryType, visible)
        updateFilteredTelemetries()
    }

    fun updateFilter(filter: String) {
        this.filter = filter

        // NB: We have to escape the string to JSON to allow filtering on special characters
        this.filterEscaped = escapeJson(filter)
        this.filterLowerCaseEscaped = filterEscaped.lowercase()
        this.filterLowerCase = filter.lowercase()

        updateFilteredTelemetries()
    }

    fun clear() {
        synchronized(telemetries) {
            this.telemetries.clear()
            this.filteredTelemetries.clear()
        }
        updateFilteredTelemetries()
    }

    private fun addTelemetry(telemetry: TelemetryItem) {
        // Compute data-model changes on the current (receiver) thread
        val isFirst: Boolean
        val index: Int
        val visible: Boolean
        synchronized(telemetries) {
            isFirst = firstMessage
            if (firstMessage) {
                firstMessage = false
            }
            telemetries.add(telemetry)
            var idx = -1
            var vis = false
            if (isTelemetryVisible(telemetry)) {
                val value = AppSettingState.getInstance().filterTelemetryMode.value
                when (value) {
                    FilterTelemetryMode.Timestamp -> {
                        idx = filteredTelemetries.binarySearch(telemetry, compareBy { getTimestamp(it) })
                        if (idx < 0) idx = idx.inv()
                        filteredTelemetries.add(idx, telemetry)
                    }

                    FilterTelemetryMode.Duration -> {
                        idx = filteredTelemetries.binarySearch(telemetry, compareBy { getDuration(it) })
                        if (idx < 0) idx = idx.inv()
                        filteredTelemetries.add(idx, telemetry)
                    }

                    else -> filteredTelemetries.add(telemetry)
                }
                vis = true
            }
            index = idx
            visible = vis
        }

        // All UI operations must happen on the EDT
        val mode = AppSettingState.getInstance().filterTelemetryMode.value
        ApplicationManager.getApplication().invokeLater {
            if (isFirst) {
                openTelemetryToolWindow = OpenTelemetryToolWindow(this, dotNetDebugProcess.project, lifetime)
                val ui = dotNetDebugProcess.session.ui
                ui?.addContent(
                    ui.createContent(
                        "opentelemetry",
                        openTelemetryToolWindow!!.content,
                        "Open Telemetry",
                        ICON,
                        null
                    )
                )
            }
            openTelemetryToolWindow?.addTelemetry(index, telemetry, visible, mode == FilterTelemetryMode.Default)
        }
    }

    private fun disposeTelemetryListener() {
        val registration = telemetryListenerRegistration
        telemetryListenerRegistration = null
        if (registration == null) {
            return
        }

        try {
            registration.close()
        } catch (_: Exception) {
        }
    }

    private fun updateFilteredTelemetries() {
        synchronized(telemetries) {
            filteredTelemetries.clear()
            val visible = telemetries.filter { isTelemetryVisible(it) }
            filteredTelemetries.addAll(
                when (AppSettingState.getInstance().filterTelemetryMode.value) {
                    FilterTelemetryMode.Duration -> visible.sortedBy { getDuration(it) }
                    FilterTelemetryMode.Timestamp -> visible.sortedBy { getTimestamp(it) }
                    else -> visible
                }
            )
        }
        openTelemetryToolWindow?.let { tw ->
            val snapshot = ArrayList(telemetries)
            val filteredSnapshot = ArrayList(filteredTelemetries)
            ApplicationManager.getApplication()
                .invokeLater { tw.setTelemetries(snapshot, filteredSnapshot) }
        }
    }

    private fun isTelemetryVisible(telemetry: TelemetryItem): Boolean {
        val type = telemetry.telemetry.type
        if (type != null && !isTelemetryVisible(type)) return false

        if (filter.isNotEmpty()) {
            return if (AppSettingState.getInstance().caseInsensitiveSearch.value)
                telemetry.lowerCaseDisplayText.contains(filterLowerCase)
                        || telemetry.lowerCaseJson.contains(filterLowerCaseEscaped)
            else
                telemetry.displayText.contains(filter)
                        || telemetry.json.contains(filterEscaped)
        }

        return true
    }

    companion object {
        private val LOG = Logger.getInstance(OpenTelemetrySession::class.java)
        private val ICON = getIcon("/icons/pluginIcon.svg", OpenTelemetrySession::class.java)
        private fun getDuration(telemetry: TelemetryItem): Duration =
            telemetry.duration ?: Duration.ZERO

        private fun getTimestamp(telemetry: TelemetryItem): Instant =
            telemetry.timestamp ?: Instant.EPOCH

        private fun escapeJson(input: String): String {
            return input.replace("\\", "\\\\")
        }
    }
}