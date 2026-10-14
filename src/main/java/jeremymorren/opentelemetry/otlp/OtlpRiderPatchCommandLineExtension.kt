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

/**
 * Rider extension that patches .NET run/debug command lines to inject OTLP environment variables.
 *
 * Only the suspending overloads of [PatchCommandLineExtension] (Rider 2026.2+) are overridden; the
 * Promise/ProcessListener overloads are deprecated and would fail plugin verification.
 * [JvmDefaultWithoutCompatibility] stops the compiler emitting bridges for the deprecated overloads,
 * which the plugin verifier would otherwise report as deprecated API usages.
 */
@JvmDefaultWithoutCompatibility
class OtlpRiderPatchCommandLineExtension : PatchCommandLineExtension {
    override suspend fun patchRunCommandLineSuspending(
        commandLine: GeneralCommandLine,
        dotNetRuntime: DotNetRuntime,
        dotNetExecutable: DotNetExecutable?,
        project: Project,
    ): ProcessListener? {
        patchEnvironment(commandLine, project)
        return null
    }

    override suspend fun patchDebugCommandLineSuspending(
        lifetime: Lifetime,
        workerRunInfo: WorkerRunInfo,
        processInfo: ProcessInfo?,
        dotNetExecutable: DotNetExecutable?,
        project: Project,
        dataContext: DataContext?,
    ): WorkerRunInfo {
        patchEnvironment(workerRunInfo.commandLine, project)
        return workerRunInfo
    }

    private fun patchEnvironment(commandLine: GeneralCommandLine, project: Project) {
        val settings = AppSettingState.getInstance()
        if (!settings.enableLoopbackOtlpReceiver.value || !settings.injectOtlpEnvironmentVariables.value) {
            return
        }

        // Every launch gets its own scope so a debug session only ever sees telemetry from its own process.
        val endpoint = OtlpSessionScope.buildScopedEndpoint(
            OtlpHttpReceiverService.getInstance().ensureStarted(),
            OtlpSessionScope.registerPending(project)
        )
        OtlpCommandLinePatcher.patchEnvironment(commandLine, settings, endpoint)
    }
}
