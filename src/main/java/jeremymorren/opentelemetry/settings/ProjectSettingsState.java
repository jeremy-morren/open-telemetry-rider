package jeremymorren.opentelemetry.settings;

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.jetbrains.rd.util.lifetime.LifetimeDefinition;
import com.jetbrains.rd.util.reactive.Property;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.settings.converters.BooleanPropertyConverter;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// https://plugins.jetbrains.com/docs/intellij/settings-tutorial.html#the-appsettingscomponent-class
@State(
        name = "jeremymorren.opentelemetry.settings.ProjectSettingsState",
        storages = @Storage("opentelemetry-debug-log-viewer.xml")
)
public class ProjectSettingsState implements PersistentStateComponentWithModificationTracker<ProjectSettingsState> {
    private final SimpleModificationTracker tracker = new SimpleModificationTracker();

    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> caseInsensitiveFiltering = new Property<>(false);

    /**
     * Telemetry types hidden per run configuration, as a comma separated list of {@link TelemetryType}
     * names. Keyed by configuration rather than held once per project, so that hiding metrics while
     * debugging one service does not hide them for another service of the same solution; keyed by name
     * rather than per session, so the choice survives restarting that configuration.
     */
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "configuration", valueAttributeName = "hidden",
            entryTagName = "hiddenTelemetryTypes")
    public Map<String, String> hiddenTelemetryTypes = new LinkedHashMap<>();

    public ProjectSettingsState() {
        registerAllPropertyToIncrementTrackerOnChanges(this);
    }

    public static ProjectSettingsState getInstance(Project project) {
        return project.getService(ProjectSettingsState.class);
    }

    @Nullable
    @Override
    public ProjectSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ProjectSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
        registerAllPropertyToIncrementTrackerOnChanges(state);
    }

    private void registerAllPropertyToIncrementTrackerOnChanges(@NotNull ProjectSettingsState state) {
        incrementTrackerWhenPropertyChanges(state.caseInsensitiveFiltering);
    }

    private <T> void incrementTrackerWhenPropertyChanges(Property<T> property) {
        property.advise(new LifetimeDefinition(), v -> {
            this.tracker.incModificationCount();
            return Unit.INSTANCE;
        });
    }

    @Override
    public long getStateModificationCount() {
        return this.tracker.getModificationCount();
    }

    public boolean getTelemetryVisible(@NotNull String configuration, @NotNull TelemetryType type) {
        return !hidden(configuration).contains(type);
    }

    public void setTelemetryVisible(@NotNull String configuration, @NotNull TelemetryType type, boolean value) {
        Set<TelemetryType> hidden = hidden(configuration);
        if (value ? !hidden.remove(type) : !hidden.add(type)) {
            return;
        }

        if (hidden.isEmpty()) {
            hiddenTelemetryTypes.remove(configuration);
        } else {
            hiddenTelemetryTypes.put(configuration, hidden.stream().map(Enum::name).collect(Collectors.joining(",")));
        }
        tracker.incModificationCount();
    }

    @NotNull
    private Set<TelemetryType> hidden(@NotNull String configuration) {
        String stored = hiddenTelemetryTypes.get(configuration);
        if (stored == null || stored.isBlank()) {
            return new LinkedHashSet<>();
        }

        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(ProjectSettingsState::parse)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Nullable
    private static TelemetryType parse(@NotNull String name) {
        try {
            return TelemetryType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            // A type that no longer exists; drop it rather than failing to load the settings.
            return null;
        }
    }
}
