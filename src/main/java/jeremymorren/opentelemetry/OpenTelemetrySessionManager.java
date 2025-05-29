package jeremymorren.opentelemetry;

import com.intellij.xdebugger.XDebugProcess;
import com.jetbrains.rider.debugger.DotNetDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenTelemetrySessionManager {
    @Nullable
    private static OpenTelemetrySessionManager instance;

    @NotNull
    public static OpenTelemetrySessionManager getInstance() {
        if (instance == null)
            instance = new OpenTelemetrySessionManager();
        return instance;
    }

    private OpenTelemetrySessionManager() {
    }

    public void startSession(XDebugProcess debugProcess) {
        if (!(debugProcess instanceof DotNetDebugProcess))
            return;

        OpenTelemetrySession opentelemetrySession = new OpenTelemetrySession(
                (DotNetDebugProcess) debugProcess
        );
        opentelemetrySession.startListeningToOutputDebugMessage();
    }
}
