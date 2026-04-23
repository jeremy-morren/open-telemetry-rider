package jeremymorren.opentelemetry.tests;

import com.intellij.execution.configurations.GeneralCommandLine;
import jeremymorren.opentelemetry.otlp.OtlpCommandLinePatcher;
import jeremymorren.opentelemetry.settings.AppSettingState;
import org.junit.Test;

import java.net.URI;

public class OtlpCommandLinePatcherTests {
    @Test
    public void patchesResolvedEnvironmentVariablesWhenInjectionEnabled() {
        AppSettingState settings = new AppSettingState();
        settings.enableLoopbackOtlpReceiver.setValue(true);
        settings.injectOtlpEnvironmentVariables.setValue(true);
        settings.otlpEnvironmentVariables = String.join("\n",
                "OTEL_EXPORTER_OTLP_ENDPOINT=${OTLP_ENDPOINT}",
                "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf",
                "CUSTOM_ENDPOINT=${OTLP_HOST}:${OTLP_PORT}"
        );

        GeneralCommandLine commandLine = new GeneralCommandLine("dotnet", "run");

        OtlpCommandLinePatcher.patchEnvironment(commandLine, settings, URI.create("http://127.0.0.1:4318"));

        assert "http://127.0.0.1:4318".equals(commandLine.getEnvironment().get("OTEL_EXPORTER_OTLP_ENDPOINT"));
        assert "http/protobuf".equals(commandLine.getEnvironment().get("OTEL_EXPORTER_OTLP_PROTOCOL"));
        assert "127.0.0.1:4318".equals(commandLine.getEnvironment().get("CUSTOM_ENDPOINT"));
    }

    @Test
    public void doesNotPatchEnvironmentVariablesWhenInjectionDisabled() {
        AppSettingState settings = new AppSettingState();
        settings.enableLoopbackOtlpReceiver.setValue(true);
        settings.injectOtlpEnvironmentVariables.setValue(false);
        settings.otlpEnvironmentVariables = "OTEL_EXPORTER_OTLP_ENDPOINT=${OTLP_ENDPOINT}";

        GeneralCommandLine commandLine = new GeneralCommandLine("dotnet", "run");

        OtlpCommandLinePatcher.patchEnvironment(commandLine, settings, URI.create("http://127.0.0.1:4318"));

        assert !commandLine.getEnvironment().containsKey("OTEL_EXPORTER_OTLP_ENDPOINT");
    }
}