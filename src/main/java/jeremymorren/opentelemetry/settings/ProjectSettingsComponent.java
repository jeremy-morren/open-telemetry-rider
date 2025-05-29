package jeremymorren.opentelemetry.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class ProjectSettingsComponent {

    private final JPanel minPanel;
    private final JBCheckBox caseInsensitiveFiltering = new JBCheckBox("Case insensitive log filtering");

    public ProjectSettingsComponent() {
        minPanel = FormBuilder.createFormBuilder()
                .addComponent(caseInsensitiveFiltering, 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return minPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return caseInsensitiveFiltering;
    }

    public boolean getCaseInsensitiveFiltering() {
        return caseInsensitiveFiltering.isSelected();
    }

    public void setCaseInsensitiveFiltering(boolean value) {
        caseInsensitiveFiltering.setSelected(value);
    }
}
