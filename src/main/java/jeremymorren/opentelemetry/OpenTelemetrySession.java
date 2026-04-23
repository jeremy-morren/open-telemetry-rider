package jeremymorren.opentelemetry;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.jetbrains.rd.util.lifetime.Lifetime;
import com.jetbrains.rider.debugger.DotNetDebugProcess;
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService;
import jeremymorren.opentelemetry.settings.AppSettingState;
import jeremymorren.opentelemetry.settings.FilterTelemetryMode;
import jeremymorren.opentelemetry.settings.ProjectSettingsState;
import jeremymorren.opentelemetry.ui.OpenTelemetryToolWindow;
import kotlin.Unit;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class OpenTelemetrySession {
    @NotNull
    private static final Icon ICON = IconLoader.getIcon("/icons/pluginIcon.svg", OpenTelemetrySession.class);
    @NotNull
    private final DotNetDebugProcess dotNetDebugProcess;
    @NotNull
    private final List<TelemetryItem> telemetries = new ArrayList<>();
    @NotNull
    private final List<TelemetryItem> filteredTelemetries = new ArrayList<>();
    @NotNull
    private final Lifetime lifetime;
    @NotNull
    private String filter = "";

    /**
     * Filter string, escaped to JSON string
     */
    private String filterEscaped = "";

    /**
     * Filter string in lower case, escaped to JSON string
     */
    private String filterLowerCaseEscaped = "";


    @Nullable
    private OpenTelemetryToolWindow openTelemetryToolWindow;
    private boolean firstMessage = true;
    private final ProjectSettingsState projectSettingsState;

    public OpenTelemetrySession(
            @NotNull DotNetDebugProcess dotNetDebugProcess
    ) {
        this.dotNetDebugProcess = dotNetDebugProcess;
        this.lifetime = dotNetDebugProcess.getSessionLifetime();

        projectSettingsState = ProjectSettingsState.getInstance(dotNetDebugProcess.getProject());

        AppSettingState.getInstance().filterTelemetryMode.advise(lifetime, (v) -> {
            this.updateFilteredTelemetries();
            return Unit.INSTANCE;
        });
        AppSettingState.getInstance().caseInsensitiveSearch.advise(lifetime, (v) -> {
            this.updateFilteredTelemetries();
            return Unit.INSTANCE;
        });
        projectSettingsState.caseInsensitiveFiltering.advise(lifetime, (v) -> {
            this.updateFilteredTelemetries();
            return Unit.INSTANCE;
        });
    }

    public void startListeningToOtlpReceiver() {
        OtlpHttpReceiverService.getInstance().ensureStarted();
        OtlpHttpReceiverService.getInstance().addListener(this::addTelemetry);
    }

    public boolean isTelemetryVisible(@NotNull TelemetryType telemetryType) {
        return projectSettingsState.getTelemetryVisible(telemetryType);
    }

    public void setTelemetryVisible(@NotNull TelemetryType telemetryType, boolean visible) {
        projectSettingsState.setTelemetryVisible(telemetryType, visible);
        updateFilteredTelemetries();
    }

    public void updateFilter(@NonNull String filter) {
        this.filter = filter;

        //NB: We have to escape the string to JSON to allow filtering on special characters
        this.filterEscaped = escapeJson(filter);
        this.filterLowerCaseEscaped = filterEscaped.toLowerCase(Locale.ROOT);

        updateFilteredTelemetries();
    }

    public void clear() {
        this.telemetries.clear();
        updateFilteredTelemetries();
    }

    private void addTelemetry(@NotNull TelemetryItem telemetry) {
        // Compute data-model changes on the current (receiver) thread
        final boolean isFirst;
        final int index;
        final boolean visible;
        synchronized (telemetries) {
            isFirst = firstMessage;
            if (firstMessage) {
                firstMessage = false;
            }
            telemetries.add(telemetry);
            int idx = -1;
            boolean vis = false;
            if (isTelemetryVisible(telemetry)) {
                FilterTelemetryMode value = AppSettingState.getInstance().filterTelemetryMode.getValue();
                switch (value) {
                    case Timestamp:
                        idx = Collections.binarySearch(filteredTelemetries, telemetry,
                                Comparator.comparing(OpenTelemetrySession::getDuration));
                        if (idx < 0)
                            idx = ~idx;
                        filteredTelemetries.add(idx, telemetry);
                        break;
                    case Duration:
                        idx = Collections.binarySearch(filteredTelemetries, telemetry,
                                Comparator.comparing(OpenTelemetrySession::getTimestamp));
                        if (idx < 0)
                            idx = ~idx;
                        filteredTelemetries.add(idx, telemetry);
                        break;
                    default:
                        filteredTelemetries.add(telemetry);
                        break;
                }
                vis = true;
            }
            index = idx;
            visible = vis;
        }

        // All UI operations must happen on the EDT
        final FilterTelemetryMode mode = AppSettingState.getInstance().filterTelemetryMode.getValue();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (isFirst) {
                openTelemetryToolWindow = new OpenTelemetryToolWindow(this, dotNetDebugProcess.getProject(), lifetime);
                Content content = dotNetDebugProcess.getSession().getUI().createContent(
                        "opentelemetry",
                        openTelemetryToolWindow.getContent(),
                        "Open Telemetry",
                        ICON,
                        null
                );
                dotNetDebugProcess.getSession().getUI().addContent(content);
            }
            if (openTelemetryToolWindow != null)
                openTelemetryToolWindow.addTelemetry(index, telemetry, visible,
                        mode == FilterTelemetryMode.Default);
        });
    }

    private void updateFilteredTelemetries() {
        synchronized (telemetries) {
            filteredTelemetries.clear();
            Stream<TelemetryItem> stream = telemetries.stream().filter(this::isTelemetryVisible);
            stream = switch (AppSettingState.getInstance().filterTelemetryMode.getValue()) {
                case Duration -> stream.sorted(Comparator.comparing(OpenTelemetrySession::getDuration));
                case Timestamp -> stream.sorted(Comparator.comparing(OpenTelemetrySession::getTimestamp));
                default -> stream;
            };
            filteredTelemetries.addAll(stream.toList());
        }
        if (openTelemetryToolWindow != null) {
            final var tw = openTelemetryToolWindow;
            final var snapshot = new ArrayList<>(telemetries);
            final var filteredSnapshot = new ArrayList<>(filteredTelemetries);
            ApplicationManager.getApplication().invokeLater(() ->
                    tw.setTelemetries(snapshot, filteredSnapshot));
        }
    }

    private boolean isTelemetryVisible(@NotNull TelemetryItem telemetry) {
        var type = telemetry.getTelemetry().getType();
        if (type != null && !projectSettingsState.getTelemetryVisible(type))
            return false;

        if (!filter.isEmpty()) {
            if (AppSettingState.getInstance().caseInsensitiveSearch.getValue())
                return telemetry.getLowerCaseJson().toLowerCase().contains(filterLowerCaseEscaped);
            else
                return telemetry.getJson().contains(filterEscaped);
        }

        return true;
    }

    private static Duration getDuration(TelemetryItem telemetry) {
        if (telemetry.getDuration() == null)
            return java.time.Duration.ZERO;
        return telemetry.getDuration();
    }

    private static Instant getTimestamp(TelemetryItem telemetry) {
        if (telemetry.getTimestamp() == null)
            return Instant.EPOCH;
        return telemetry.getTimestamp();
    }

    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\");
    }
}