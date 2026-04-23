package jeremymorren.opentelemetry.otlp;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public final class OtlpEnvironmentVariables {
    public static final String DEFAULT_ENVIRONMENT_VARIABLES = String.join("\n",
            "OTEL_EXPORTER_OTLP_ENDPOINT=${OTLP_ENDPOINT}",
            "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf",
            "OTEL_BSP_SCHEDULE_DELAY=100",
            "OTEL_BLRP_SCHEDULE_DELAY=100"
    );

    private OtlpEnvironmentVariables() {
    }

    @NotNull
    public static Map<String, String> resolve(@NotNull String template, @NotNull URI endpoint) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();

        Map<String, String> replacements = Map.of(
                "${OTLP_ENDPOINT}", endpoint.toString(),
                "${OTLP_HOST}", endpoint.getHost(),
                "${OTLP_PORT}", Integer.toString(endpoint.getPort())
        );

        for (String rawLine : template.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                value = value.replace(replacement.getKey(), replacement.getValue());
            }

            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }

        return result;
    }
}