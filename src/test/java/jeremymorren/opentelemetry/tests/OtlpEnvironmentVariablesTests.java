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
                100,
                60
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
                2500,
                60
        );

        assert "2500".equals(resolved.get("OTEL_BSP_SCHEDULE_DELAY"));
        assert "2500".equals(resolved.get("OTEL_BLRP_SCHEDULE_DELAY"));
    }

    @Test
    public void resolvesMetricsFlushIntervalIntoMilliseconds() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                "OTEL_METRIC_EXPORT_INTERVAL=${OTLP_METRICS_FLUSH_INTERVAL}",
                URI.create("http://127.0.0.1:4318"),
                100,
                15
        );

        // The setting is in seconds; OpenTelemetry reads the variable in milliseconds.
        assert "15000".equals(resolved.get("OTEL_METRIC_EXPORT_INTERVAL"));
    }

    @Test
    public void metricsPlaceholderIsNotTakenForTheGeneralFlushInterval() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                String.join("\n",
                        "GENERAL=${OTLP_FLUSH_INTERVAL}",
                        "METRICS=${OTLP_METRICS_FLUSH_INTERVAL}"),
                URI.create("http://127.0.0.1:4318"),
                250,
                30
        );

        assert "250".equals(resolved.get("GENERAL"));
        assert "30000".equals(resolved.get("METRICS"));
    }

    @Test
    public void defaultTemplateUsesTheConfiguredIntervals() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                OtlpEnvironmentVariables.DEFAULT_ENVIRONMENT_VARIABLES,
                URI.create("http://127.0.0.1:4318/scope-123"),
                750,
                45
        );

        assert "http://127.0.0.1:4318/scope-123".equals(resolved.get("OTEL_EXPORTER_OTLP_ENDPOINT"));
        assert "750".equals(resolved.get("OTEL_BSP_SCHEDULE_DELAY"));
        assert "750".equals(resolved.get("OTEL_BLRP_SCHEDULE_DELAY"));
        assert "45000".equals(resolved.get("OTEL_METRIC_EXPORT_INTERVAL"));
    }
}
