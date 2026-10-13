package jeremymorren.opentelemetry.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import jeremymorren.opentelemetry.OpenTelemetryBundle;
import jeremymorren.opentelemetry.settings.FilterTelemetryMode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Supplier;

/** Toolbar button that pops up the sort and search options, and a link to the settings page. */
public class OptionsToolbarAction extends AnAction {
    private final Supplier<Component> toolbarComponent;

    public OptionsToolbarAction(Supplier<Component> toolbarComponent) {
        super();
        this.toolbarComponent = toolbarComponent;

        this.getTemplatePresentation().setText(OpenTelemetryBundle.message("ShowOptionsMenu.text"));
        this.getTemplatePresentation().setDescription(OpenTelemetryBundle.message("ShowOptionsMenu.description"));
        this.getTemplatePresentation().setIcon(AllIcons.General.Gear);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new ChangeFilterModeToolbarAction(FilterTelemetryMode.Default));
        actionGroup.add(new ChangeFilterModeToolbarAction(FilterTelemetryMode.Duration));
        actionGroup.add(new ChangeFilterModeToolbarAction(FilterTelemetryMode.Timestamp));
        actionGroup.add(new Separator());
        actionGroup.add(new ToggleCaseInsensitiveSearchToolbarAction());
        actionGroup.add(new Separator());
        actionGroup.add(new OpenSettingsToolbarAction());

        ActionManager.getInstance().createActionPopupMenu("AIDLV_OpenSettingsMenu", actionGroup)
                .getComponent()
                .show(toolbarComponent.get(), 15, 15);
    }
}
