package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.otlp.OtlpEnvironmentVariables;
import org.junit.Test;

import java.net.URI;
import java.util.Map;

public class OtlpEnvironmentVariablesTests {
    @Test
    public void resolvesPlaceholdersIntoConcreteValues() {
        Map<String, String> resolved = OtlpEnvironmentVariables.resolve(
                "OTEL_EXPORTER_OTLP_ENDPOINT=${OTLP_ENDPOINT}\nOTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf\nCUSTOM=${OTLP_HOST}:${OTLP_PORT}",
                URI.create("http://127.0.0.1:4318")
        );

        assert "http://127.0.0.1:4318".equals(resolved.get("OTEL_EXPORTER_OTLP_ENDPOINT"));
        assert "http/protobuf".equals(resolved.get("OTEL_EXPORTER_OTLP_PROTOCOL"));
        assert "127.0.0.1:4318".equals(resolved.get("CUSTOM"));
    }
}