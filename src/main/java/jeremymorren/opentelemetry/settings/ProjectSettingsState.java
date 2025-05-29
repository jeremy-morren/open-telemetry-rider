package jeremymorren.opentelemetry.settings;

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.jetbrains.rd.util.lifetime.LifetimeDefinition;
import com.jetbrains.rd.util.reactive.Property;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.settings.converters.BooleanPropertyConverter;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// https://plugins.jetbrains.com/docs/intellij/settings-tutorial.html#the-appsettingscomponent-class
@State(
        name = "jeremymorren.opentelemetry.settings.ProjectSettingsState",
        storages = @Storage("opentelemetry-debug-log-viewer.xml")
)
public class ProjectSettingsState implements PersistentStateComponentWithModificationTracker<ProjectSettingsState> {
    private final SimpleModificationTracker tracker = new SimpleModificationTracker();

    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> caseInsensitiveFiltering = new Property<>(false);

    // Filters
    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> showMetrics = new Property<>(true);
    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> showExceptions = new Property<>(true);
    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> showMessages = new Property<>(true);
    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> showDependencies = new Property<>(true);
    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> showRequests = new Property<>(true);
    @OptionTag(converter = BooleanPropertyConverter.class)
    public final Property<Boolean> showActivities = new Property<>(true);

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


    public Boolean getTelemetryVisible(TelemetryType type) {
        return switch (type) {
            case Metric -> showMetrics.getValue();
            case Exception -> showExceptions.getValue();
            case Message -> showMessages.getValue();
            case Dependency -> showDependencies.getValue();
            case Request -> showRequests.getValue();
            case Activity -> showActivities.getValue();
        };
    }

    public void setTelemetryVisible(TelemetryType type, boolean value) {
        switch (type) {
            case Metric -> showMetrics.setValue(value);
            case Exception -> showExceptions.setValue(value);
            case Message -> showMessages.setValue(value);
            case Dependency -> showDependencies.setValue(value);
            case Request -> showRequests.setValue(value);
            case Activity -> showActivities.setValue(value);
        }
    }
}
