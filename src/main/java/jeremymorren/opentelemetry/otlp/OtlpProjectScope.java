package jeremymorren.opentelemetry.otlp;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class OtlpProjectScope {
    private OtlpProjectScope() {
    }

    @NotNull
    public static String getScopeKey(@NotNull Project project) {
        String locationHash = project.getLocationHash();
        if (!locationHash.isBlank()) {
            return locationHash;
        }

        return project.getName();
    }

    @NotNull
    public static URI buildScopedEndpoint(@NotNull URI endpoint, @NotNull Project project) {
        String encodedScopeKey = URLEncoder.encode(getScopeKey(project), StandardCharsets.UTF_8);
        return URI.create(endpoint.toString() + "/" + encodedScopeKey + "/v1");
    }

    @Nullable
    public static String tryExtractScopeKey(@NotNull String path) {
        if (!path.startsWith("/")) {
            return null;
        }

        int v1SegmentStart = path.indexOf("/v1/");
        if (v1SegmentStart <= 1) {
            return null;
        }

        return path.substring(1, v1SegmentStart);
    }
}