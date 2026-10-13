package jeremymorren.opentelemetry

import com.intellij.xdebugger.XDebugProcess
import com.jetbrains.rider.debugger.DotNetDebugProcess

/** Creates an [OpenTelemetrySession] for each .NET debug process that starts. */
class OpenTelemetrySessionManager private constructor() {
    fun startSession(debugProcess: XDebugProcess?) {
        if (debugProcess !is DotNetDebugProcess)
            return

        val openTelemetrySession = OpenTelemetrySession(
            debugProcess
        )
        openTelemetrySession.startListeningToOtlpReceiver()
    }

    companion object {
        @JvmStatic
        val instance: OpenTelemetrySessionManager by lazy { OpenTelemetrySessionManager() }
    }
}
