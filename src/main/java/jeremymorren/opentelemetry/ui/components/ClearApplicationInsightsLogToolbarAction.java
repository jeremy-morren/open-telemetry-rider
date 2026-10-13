package jeremymorren.opentelemetry.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import jeremymorren.opentelemetry.OpenTelemetryBundle;

/** Toolbar button that clears the telemetry log; the subclass supplies what clearing does. */
public abstract class ClearApplicationInsightsLogToolbarAction extends AnAction {
    public ClearApplicationInsightsLogToolbarAction() {
        this.getTemplatePresentation().setText(OpenTelemetryBundle.message("ClearLogs.text"));
        this.getTemplatePresentation().setDescription(OpenTelemetryBundle.message("ClearLogs.description"));
        this.getTemplatePresentation().setIcon(AllIcons.Actions.GC);
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
