package jeremymorren.opentelemetry.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import jeremymorren.opentelemetry.OpenTelemetryBundle;
import jeremymorren.opentelemetry.settings.FilterTelemetryMode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Supplier;

public class OptionsToolbarAction extends AnAction {
    private final Supplier<Component> toolbarComponent;

    public OptionsToolbarAction(Supplier<Component> toolbarComponent) {
        super();
        this.toolbarComponent = toolbarComponent;

        String message = OpenTelemetryBundle.message("ShowOptionsMenu");
        this.getTemplatePresentation().setDescription(message);
        this.getTemplatePresentation().setText(message);
        this.getTemplatePresentation().setIcon(AllIcons.General.Gear);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new Separator());
        actionGroup.add(new ChangeFilterModeToolbarAction(FilterTelemetryMode.Default));
        actionGroup.add(new ChangeFilterModeToolbarAction(FilterTelemetryMode.Duration));
        actionGroup.add(new ChangeFilterModeToolbarAction(FilterTelemetryMode.Timestamp));
        actionGroup.add(new Separator());
        actionGroup.add(new OpenSettingsToolbarAction());

        ActionManager.getInstance().createActionPopupMenu("AIDLV_OpenSettingsMenu", actionGroup)
                .getComponent()
                .show(toolbarComponent.get(), 15, 15);
    }
}
