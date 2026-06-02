package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.otlp.OtlpEnvironmentVariables;
import jeremymorren.opentelemetry.otlp.OtlpProjectScope;
import org.junit.Assert;
import org.junit.Test;

import com.intellij.openapi.project.Project;
import java.lang.reflect.Proxy;
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

    @Test
    public void buildsScopedEndpointFromProjectLocationHash() {
        Project project = (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class[]{Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocationHash" -> "scope-123";
                    case "getName" -> "MyProject";
                    case "toString" -> "Project(scope-123)";
                    default -> null;
                }
        );

        URI scoped = OtlpProjectScope.buildScopedEndpoint(URI.create("http://127.0.0.1:4318"), project);

        Assert.assertEquals("http://127.0.0.1:4318/scope-123/v1", scoped.toString());
    }

    @Test
    public void extractsScopeKeyFromScopedSignalPath() {
        Assert.assertEquals("scope-123", OtlpProjectScope.tryExtractScopeKey("/scope-123/v1/logs"));
    }

    @Test
    public void returnsNullWhenScopedSignalPathIsInvalid() {
        Assert.assertNull(OtlpProjectScope.tryExtractScopeKey("/v1/logs"));
    }
}