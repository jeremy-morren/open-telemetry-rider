package jeremymorren.opentelemetry.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import jeremymorren.opentelemetry.OpenTelemetryBundle;
import jeremymorren.opentelemetry.settings.AppSettingState;
import jeremymorren.opentelemetry.settings.FilterTelemetryMode;
import org.jetbrains.annotations.NotNull;

public class ChangeFilterModeToolbarAction extends ToggleAction {
    private final FilterTelemetryMode mode;

    public ChangeFilterModeToolbarAction(FilterTelemetryMode mode) {
        super();
        this.mode = mode;

        String message = OpenTelemetryBundle.message("action.OpenTelemetry.SortTelemetry." + mode);
        this.getTemplatePresentation().setText(message);
        this.getTemplatePresentation().setIcon(AllIcons.RunConfigurations.SortbyDuration);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent actionEvent) {
        return AppSettingState.getInstance().filterTelemetryMode.getValue() == mode;
    }

    public void setSelected(@NotNull AnActionEvent actionEvent, boolean state) {
        AppSettingState.getInstance().filterTelemetryMode.setValue(mode);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
