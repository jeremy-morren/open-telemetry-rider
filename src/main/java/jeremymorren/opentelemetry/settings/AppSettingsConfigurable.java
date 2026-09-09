package jeremymorren.opentelemetry.settings;

import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class AppSettingsConfigurable implements SearchableConfigurable {
    private AppSettingsComponent settingsComponent;

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsComponent = new AppSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        AppSettingState settings = AppSettingState.getInstance();
        return settingsComponent.getEnableLoopbackOtlpReceiver() != settings.enableLoopbackOtlpReceiver.getValue()
                || settingsComponent.getInjectOtlpEnvironmentVariables() != settings.injectOtlpEnvironmentVariables.getValue()
                || settingsComponent.getAppendCurlCompressed() != settings.appendCurlCompressed.getValue()
                || settingsComponent.getFlushIntervalMillis() != settings.otlpFlushIntervalMillis
                || settingsComponent.getMetricsFlushIntervalSeconds() != settings.otlpMetricsFlushIntervalSeconds
                || !Objects.equals(settingsComponent.getOtlpEnvironmentVariables(), settings.otlpEnvironmentVariables);
    }

    @Override
    public void apply() {
        AppSettingState settings = AppSettingState.getInstance();
        settings.enableLoopbackOtlpReceiver.setValue(settingsComponent.getEnableLoopbackOtlpReceiver());
        settings.injectOtlpEnvironmentVariables.setValue(settingsComponent.getInjectOtlpEnvironmentVariables());
        settings.appendCurlCompressed.setValue(settingsComponent.getAppendCurlCompressed());
        settings.otlpFlushIntervalMillis = settingsComponent.getFlushIntervalMillis();
        settings.otlpMetricsFlushIntervalSeconds = settingsComponent.getMetricsFlushIntervalSeconds();
        settings.otlpEnvironmentVariables = settingsComponent.getOtlpEnvironmentVariables();
    }

    @Override
    public void reset() {
        AppSettingState settings = AppSettingState.getInstance();
        settingsComponent.setEnableLoopbackOtlpReceiver(settings.enableLoopbackOtlpReceiver.getValue());
        settingsComponent.setInjectOtlpEnvironmentVariables(settings.injectOtlpEnvironmentVariables.getValue());
        settingsComponent.setAppendCurlCompressed(settings.appendCurlCompressed.getValue());
        settingsComponent.setFlushIntervalMillis(settings.otlpFlushIntervalMillis);
        settingsComponent.setMetricsFlushIntervalSeconds(settings.otlpMetricsFlushIntervalSeconds);
        settingsComponent.setOtlpEnvironmentVariables(settings.otlpEnvironmentVariables);
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }

    @Override
    public @NotNull @NonNls String getId() {
        return "jeremymorren.opentelemetry.settings.AppSettingsConfigurable";
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getDisplayName() {
        return "OpenTelemetry: Global Settings";
    }
}