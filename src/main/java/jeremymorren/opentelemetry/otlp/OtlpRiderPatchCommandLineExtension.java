package jeremymorren.opentelemetry.otlp;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.jetbrains.rd.util.lifetime.Lifetime;
import com.jetbrains.rider.run.PatchCommandLineExtension;
import com.jetbrains.rider.run.WorkerRunInfo;
import com.jetbrains.rider.runtime.DotNetExecutable;
import com.jetbrains.rider.runtime.DotNetRuntime;
import jeremymorren.opentelemetry.settings.AppSettingState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

/**
 * Rider extension that patches .NET run/debug command lines to inject OTLP environment variables.
 *
 * The Java implementation intentionally overrides only the latest non-deprecated API variants.
 */
public final class OtlpRiderPatchCommandLineExtension implements PatchCommandLineExtension {
    @Nullable
    @Override
    public ProcessListener patchRunCommandLine(
            @NotNull GeneralCommandLine commandLine,
            @NotNull DotNetRuntime dotNetRuntime,
            @Nullable DotNetExecutable dotNetExecutable,
            @NotNull Project project
    ) {
        patchEnvironment(commandLine);
        return null;
    }

    @NotNull
    @Override
    public Promise<WorkerRunInfo> patchDebugCommandLine(
            @NotNull Lifetime lifetime,
            @NotNull WorkerRunInfo workerRunInfo,
            @Nullable ProcessInfo processInfo,
            @Nullable DotNetExecutable dotNetExecutable,
            @NotNull Project project,
            @Nullable DataContext dataContext
    ) {
        patchEnvironment(workerRunInfo.getCommandLine());
        AsyncPromise<WorkerRunInfo> promise = new AsyncPromise<>();
        promise.setResult(workerRunInfo);
        return promise;
    }

    private static void patchEnvironment(@NotNull GeneralCommandLine commandLine) {
        AppSettingState settings = AppSettingState.getInstance();
        if (!settings.enableLoopbackOtlpReceiver.getValue() || !settings.injectOtlpEnvironmentVariables.getValue()) {
            return;
        }

        var endpoint = OtlpHttpReceiverService.getInstance().ensureStarted();
        OtlpCommandLinePatcher.patchEnvironment(commandLine, settings, endpoint);
    }
}
