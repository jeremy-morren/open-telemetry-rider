package jeremymorren.opentelemetry.otlp

import com.intellij.execution.configurations.GeneralCommandLine
import jeremymorren.opentelemetry.settings.AppSettingState
import java.net.URI

/**
 * Adds the OTLP exporter environment variables to a launched process, so its telemetry is sent to the
 * loopback receiver.
 */
object OtlpCommandLinePatcher {
    @JvmStatic
    fun patchEnvironment(commandLine: GeneralCommandLine, settings: AppSettingState, endpoint: URI) {
        if (!settings.enableLoopbackOtlpReceiver.value || !settings.injectOtlpEnvironmentVariables.value) {
            return
        }

        val resolved = OtlpEnvironmentVariables.resolve(
            settings.otlpEnvironmentVariables,
            endpoint,
            settings.otlpFlushIntervalMillis,
            settings.otlpMetricsFlushIntervalSeconds,
        )
        commandLine.withEnvironment(resolved)
    }
}