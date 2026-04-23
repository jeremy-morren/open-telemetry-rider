package jeremymorren.opentelemetry.otlp

import com.intellij.execution.configurations.GeneralCommandLine
import jeremymorren.opentelemetry.settings.AppSettingState
import java.net.URI

object OtlpCommandLinePatcher {
    @JvmStatic
    fun patchEnvironment(commandLine: GeneralCommandLine, settings: AppSettingState, endpoint: URI) {
        if (!settings.enableLoopbackOtlpReceiver.value || !settings.injectOtlpEnvironmentVariables.value) {
            return
        }

        val resolved = OtlpEnvironmentVariables.resolve(settings.otlpEnvironmentVariables, endpoint)
        commandLine.withEnvironment(resolved)
    }
}