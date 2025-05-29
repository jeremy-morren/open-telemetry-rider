package jeremymorren.opentelemetry.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import jeremymorren.opentelemetry.OpenTelemetryBundle;

public abstract class ClearApplicationInsightsLogToolbarAction extends AnAction {
    public ClearApplicationInsightsLogToolbarAction() {
        String message = OpenTelemetryBundle.message("ClearLogs.text");
        this.getTemplatePresentation().setDescription(message);
        this.getTemplatePresentation().setText(message);
        this.getTemplatePresentation().setIcon(AllIcons.Actions.GC);
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
