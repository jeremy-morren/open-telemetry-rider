package jeremymorren.opentelemetry.otlp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessInfo
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.PatchCommandLineExtension
import com.jetbrains.rider.run.WorkerRunInfo
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import jeremymorren.opentelemetry.settings.AppSettingState
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * Rider extension that patches .NET run and debug command lines to inject OTLP environment variables.
 * 
 * This extension is registered in plugin.xml as a <rider.patchCommandLine> and is called by Rider
 * whenever a .NET application is about to be run or debugged.
 * 
 * Responsibilities:
 * - Check if OTLP loopback receiver is enabled in settings
 * - Start (or reuse) the HTTP server for receiving telemetry from the .NET app
 * - Inject environment variables (OTEL_EXPORTER_OTLP_ENDPOINT, etc.) into the child process
 */
class OtlpRiderPatchCommandLineExtension : PatchCommandLineExtension {
    /**
     * Called by Rider before launching a .NET application in normal run mode (not debugging).
     * 
     * @param commandLine the command line that will be executed
     * @param dotNetRuntime the .NET runtime being used
     * @param dotNetExecutable the .NET executable (if known)
     * @param project the IntelliJ project
     * 
     * @return null (no custom process listener needed)
     */
    override fun patchRunCommandLine(
        commandLine: GeneralCommandLine,
        dotNetRuntime: DotNetRuntime,
        dotNetExecutable: DotNetExecutable?,
        project: Project,
    ): ProcessListener? {
        patchEnvironment(commandLine)
        return null
    }

    /**
     * Called by Rider before launching a .NET application in debug mode.
     * 
     * @param lifetime lifetime scope for the debugger session
     * @param workerRunInfo debug worker process information (includes command line)
     * @param processInfo process information (if known)
     * @param dotNetExecutable the .NET executable (if known)
     * @param project the IntelliJ project
     * @param dataContext the action context
     * 
     * @return a resolved promise with the (potentially modified) WorkerRunInfo
     */
    override fun patchDebugCommandLine(
        lifetime: Lifetime,
        workerRunInfo: WorkerRunInfo,
        processInfo: ProcessInfo?,
        dotNetExecutable: DotNetExecutable?,
        project: Project,
        dataContext: DataContext?
    ): Promise<WorkerRunInfo> {
        patchEnvironment(workerRunInfo.commandLine)
        return AsyncPromise<WorkerRunInfo>().apply { setResult(workerRunInfo) }
    }

    /**
     * Patches the command line with OTLP environment variables if enabled.
     * 
     * Process:
     * 1. Load app settings
     * 2. Early exit if OTLP receiver or injection is disabled (performance optimization)
     * 3. Start (or reuse) the OTLP HTTP server to get the endpoint URI
     * 4. Patch the command line environment with resolved OTEL variables
     * 
     * @param commandLine the command line to patch (modified in-place)
     */
    private fun patchEnvironment(commandLine: GeneralCommandLine) {
        val settings = AppSettingState.getInstance()
        
        // Early exit: Skip expensive server startup if OTLP receiver or injection is disabled
        if (!settings.enableLoopbackOtlpReceiver.value || !settings.injectOtlpEnvironmentVariables.value) {
            return
        }
        
        // Start HTTP server (or get existing endpoint if already started)
        // The server is a singleton and persists for the IDE session
        val endpoint = OtlpHttpReceiverService.getInstance().ensureStarted()
        
        // Patch environment variables: replaces ${OTLP_ENDPOINT}, ${OTLP_HOST}, ${OTLP_PORT} templates
        OtlpCommandLinePatcher.patchEnvironment(commandLine, settings, endpoint)
    }
}