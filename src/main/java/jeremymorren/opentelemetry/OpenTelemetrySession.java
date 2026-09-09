package jeremymorren.opentelemetry;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.rd.util.lifetime.Lifetime;
import com.jetbrains.rider.debugger.DotNetDebugProcess;
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService;
import jeremymorren.opentelemetry.otlp.OtlpSessionScope;
import jeremymorren.opentelemetry.settings.AppSettingState;
import jeremymorren.opentelemetry.settings.FilterTelemetryMode;
import jeremymorren.opentelemetry.settings.ProjectSettingsState;
import jeremymorren.opentelemetry.ui.OpenTelemetryToolWindow;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class OpenTelemetrySession {
    private static final Logger LOG = Logger.getInstance(OpenTelemetrySession.class);
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
    /**
     * Scope of the launch this session belongs to; null when the launch was not patched by this plugin
     * (e.g. attaching to an already running process), in which case no telemetry is routed here.
     */
    @Nullable
    private final String scopeKey;
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

    /**
     * Filter string in lower case, unescaped (matched against the rendered row text)
     */
    private String filterLowerCase = "";


    @Nullable
    private OpenTelemetryToolWindow openTelemetryToolWindow;
    @Nullable
    private AutoCloseable telemetryListenerRegistration;
    private boolean firstMessage = true;
    private final ProjectSettingsState projectSettingsState;
    /**
     * Run configuration this session belongs to; telemetry type filters are remembered against it, so
     * two services of the same solution keep their own filters and keep them across restarts.
     */
    @Nullable
    private String filterConfiguration;

    public OpenTelemetrySession(
            @NotNull DotNetDebugProcess dotNetDebugProcess
    ) {
        this.dotNetDebugProcess = dotNetDebugProcess;
        this.lifetime = dotNetDebugProcess.getSessionLifetime();
        this.scopeKey = OtlpSessionScope.claimPending(dotNetDebugProcess.getProject());

        projectSettingsState = ProjectSettingsState.getInstance(dotNetDebugProcess.getProject());

        AppSettingState.getInstance().filterTelemetryMode.advise(lifetime, (v) -> {
            this.updateFilteredTelemetries();
            return Unit.INSTANCE;
        });
        AppSettingState.getInstance().caseInsensitiveSearch.advise(lifetime, (v) -> {
            this.updateFilteredTelemetries();
            return Unit.INSTANCE;
        });
    }

    public void startListeningToOtlpReceiver() {
        if (scopeKey == null) {
            LOG.info("No OTLP scope was allocated for this debug session; telemetry will not be captured");
            return;
        }
        OtlpHttpReceiverService.getInstance().ensureStarted();
        telemetryListenerRegistration = OtlpHttpReceiverService.getInstance().addListener(scopeKey, this::addTelemetry);
        dotNetDebugProcess.getProcessHandler().addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                disposeTelemetryListener();
            }
        });
    }

    public boolean isTelemetryVisible(@NotNull TelemetryType telemetryType) {
        return projectSettingsState.getTelemetryVisible(getFilterConfiguration(), telemetryType);
    }

    public void setTelemetryVisible(@NotNull TelemetryType telemetryType, boolean visible) {
        projectSettingsState.setTelemetryVisible(getFilterConfiguration(), telemetryType, visible);
        updateFilteredTelemetries();
    }

    @NotNull
    private String getFilterConfiguration() {
        if (filterConfiguration == null) {
            // Resolved lazily: the session name is not necessarily available while the process starts.
            String name = null;
            try {
                name = dotNetDebugProcess.getSession().getSessionName();
            } catch (Exception ignored) {
            }
            filterConfiguration = name == null || name.isBlank()
                    ? dotNetDebugProcess.getProject().getName()
                    : name;
        }
        return filterConfiguration;
    }

    public void updateFilter(@NotNull String filter) {
        this.filter = filter;

        //NB: We have to escape the string to JSON to allow filtering on special characters
        this.filterEscaped = escapeJson(filter);
        this.filterLowerCaseEscaped = filterEscaped.toLowerCase(Locale.ROOT);
        this.filterLowerCase = filter.toLowerCase(Locale.ROOT);

        updateFilteredTelemetries();
    }

    public void clear() {
        synchronized (telemetries) {
            this.telemetries.clear();
            this.filteredTelemetries.clear();
        }
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
                                Comparator.comparing(OpenTelemetrySession::getTimestamp));
                        if (idx < 0)
                            idx = ~idx;
                        filteredTelemetries.add(idx, telemetry);
                        break;
                    case Duration:
                        idx = Collections.binarySearch(filteredTelemetries, telemetry,
                                Comparator.comparing(OpenTelemetrySession::getDuration));
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
                dotNetDebugProcess.getSession().getUI().addContent(
                        dotNetDebugProcess.getSession().getUI().createContent(
                                "opentelemetry",
                                openTelemetryToolWindow.getContent(),
                                "Open Telemetry",
                                ICON,
                                null
                        )
                );
            }
            if (openTelemetryToolWindow != null) {
                openTelemetryToolWindow.addTelemetry(index, telemetry, visible,
                        mode == FilterTelemetryMode.Default);
            }
        });
    }

    private void disposeTelemetryListener() {
        AutoCloseable registration = telemetryListenerRegistration;
        telemetryListenerRegistration = null;
        if (registration == null) {
            return;
        }

        try {
            registration.close();
        }
        catch (Exception ignored) {
        }
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

    @NotNull
    public String getFilter() {
        return filter;
    }

    private boolean isTelemetryVisible(@NotNull TelemetryItem telemetry) {
        var type = telemetry.getTelemetry().getType();
        if (type != null && !isTelemetryVisible(type))
            return false;

        if (!filter.isEmpty()) {
            if (AppSettingState.getInstance().caseInsensitiveSearch.getValue())
                return telemetry.getLowerCaseDisplayText().contains(filterLowerCase)
                        || telemetry.getLowerCaseJson().contains(filterLowerCaseEscaped);
            else
                return telemetry.getDisplayText().contains(filter)
                        || telemetry.getJson().contains(filterEscaped);
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