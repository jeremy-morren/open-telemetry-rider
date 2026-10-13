package jeremymorren.opentelemetry.listeners

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManagerListener
import jeremymorren.opentelemetry.OpenTelemetrySessionManager

/** Starts an OpenTelemetry session whenever a debug process starts in the project. */
class DebugMessageListener : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
        OpenTelemetrySessionManager.instance.startSession(debugProcess)
    }
}
