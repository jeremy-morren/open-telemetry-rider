package jeremymorren.opentelemetry.tests;

import com.intellij.openapi.project.Project;
import jeremymorren.opentelemetry.otlp.OtlpSessionScope;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.net.URI;

public class OtlpSessionScopeTests {
    @Test
    public void buildsScopedEndpointExporterCanAppendSignalPathTo() {
        URI scoped = OtlpSessionScope.buildScopedEndpoint(URI.create("http://127.0.0.1:4318"), "scope-123");

        Assert.assertEquals("http://127.0.0.1:4318/scope-123", scoped.toString());
    }

    @Test
    public void extractsScopeKeyFromScopedSignalPath() {
        Assert.assertEquals("scope-123", OtlpSessionScope.tryExtractScopeKey("/scope-123/v1/logs"));
    }

    @Test
    public void returnsNullWhenScopedSignalPathIsInvalid() {
        Assert.assertNull(OtlpSessionScope.tryExtractScopeKey("/v1/logs"));
        Assert.assertNull(OtlpSessionScope.tryExtractScopeKey("v1/logs"));
    }

    @Test
    public void everyLaunchOfTheSameProjectGetsItsOwnScope() {
        Project project = project("project-one");

        String first = OtlpSessionScope.registerPending(project);
        String second = OtlpSessionScope.registerPending(project);

        Assert.assertNotEquals(first, second);
    }

    @Test
    public void debugSessionClaimsTheScopeOfTheLaunchThatJustStarted() {
        Project project = project("project-two");

        String scope = OtlpSessionScope.registerPending(project);

        Assert.assertEquals(scope, OtlpSessionScope.claimPending(project));
    }

    @Test
    public void scopeIsClaimedOnlyOnce() {
        Project project = project("project-three");

        OtlpSessionScope.registerPending(project);
        OtlpSessionScope.claimPending(project);

        Assert.assertNull(OtlpSessionScope.claimPending(project));
    }

    @Test
    public void projectsDoNotClaimEachOthersScopes() {
        Project one = project("project-four");
        Project two = project("project-five");

        String scope = OtlpSessionScope.registerPending(one);

        Assert.assertNull(OtlpSessionScope.claimPending(two));
        Assert.assertEquals(scope, OtlpSessionScope.claimPending(one));
    }

    private static Project project(String locationHash) {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class[]{Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocationHash" -> locationHash;
                    case "getName" -> locationHash;
                    case "toString" -> "Project(" + locationHash + ")";
                    default -> null;
                }
        );
    }
}
