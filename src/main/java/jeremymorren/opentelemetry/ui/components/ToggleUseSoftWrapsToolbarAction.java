package jeremymorren.opentelemetry.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import jeremymorren.opentelemetry.OpenTelemetryBundle;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ToggleUseSoftWrapsToolbarAction extends ToggleAction {
    @NotNull
    private final Supplier<Editor> editorSupplier;
    @NotNull
    private final Runnable onChange;

    public ToggleUseSoftWrapsToolbarAction(@NotNull Supplier<Editor> editorSupplier, @NotNull Runnable onChange) {
        this.editorSupplier = editorSupplier;
        this.onChange = onChange;

        String text = OpenTelemetryBundle.message("ToggleUseSoftWraps.text");
        String description = OpenTelemetryBundle.message("ToggleUseSoftWraps.description");
        getTemplatePresentation().setText(text);
        getTemplatePresentation().setDescription(description);
        getTemplatePresentation().setIcon(AllIcons.Actions.ToggleSoftWrap);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return PropertiesComponent.getInstance().getBoolean("jeremymorren.opentelemetry.useSoftWrap");
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue("jeremymorren.opentelemetry.useSoftWrap", state);
        editorSupplier.get().getSettings().setUseSoftWraps(state);
        onChange.run();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}