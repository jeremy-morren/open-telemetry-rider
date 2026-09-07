package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.otlp.OtlpEnvironmentVariables;
import org.junit.Test;

import java.net.URI;
import java.util.Map;

public class OtlpEnvironmentVariablesTests {
    @Test
    public void resolvesPlaceholdersIntoConcreteValues() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                String.join("\n",
                        "OTEL_EXPORTER_OTLP_ENDPOINT=${OTLP_ENDPOINT}",
                        "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf",
                        "CUSTOM=${OTLP_HOST}:${OTLP_PORT}"),
                URI.create("http://127.0.0.1:4318"),
                100
        );

        assert "http://127.0.0.1:4318".equals(resolved.get("OTEL_EXPORTER_OTLP_ENDPOINT"));
        assert "http/protobuf".equals(resolved.get("OTEL_EXPORTER_OTLP_PROTOCOL"));
        assert "127.0.0.1:4318".equals(resolved.get("CUSTOM"));
    }

    @Test
    public void resolvesConfiguredFlushInterval() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                String.join("\n",
                        "OTEL_BSP_SCHEDULE_DELAY=${OTLP_FLUSH_INTERVAL}",
                        "OTEL_BLRP_SCHEDULE_DELAY=${OTLP_FLUSH_INTERVAL}"),
                URI.create("http://127.0.0.1:4318"),
                2500
        );

        assert "2500".equals(resolved.get("OTEL_BSP_SCHEDULE_DELAY"));
        assert "2500".equals(resolved.get("OTEL_BLRP_SCHEDULE_DELAY"));
    }

    @Test
    public void defaultTemplateUsesTheConfiguredFlushInterval() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                OtlpEnvironmentVariables.DEFAULT_ENVIRONMENT_VARIABLES,
                URI.create("http://127.0.0.1:4318/scope-123"),
                750
        );

        assert "http://127.0.0.1:4318/scope-123".equals(resolved.get("OTEL_EXPORTER_OTLP_ENDPOINT"));
        assert "750".equals(resolved.get("OTEL_BSP_SCHEDULE_DELAY"));
        assert "750".equals(resolved.get("OTEL_BLRP_SCHEDULE_DELAY"));
        // The metric interval ships commented out, so OpenTelemetry's 60s default is left alone.
        assert !resolved.containsKey("OTEL_METRIC_EXPORT_INTERVAL");
    }
}
