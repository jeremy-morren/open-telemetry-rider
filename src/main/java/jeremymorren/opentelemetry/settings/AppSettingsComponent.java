package jeremymorren.opentelemetry.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;

public class AppSettingsComponent {
    private final JPanel panel;
    private final JBCheckBox enableLoopbackOtlpReceiver = new JBCheckBox("Enable loopback OTLP/HTTP receiver (binds to 127.0.0.1 only)");
    private final JBCheckBox injectOtlpEnvironmentVariables = new JBCheckBox("Inject OTLP environment variables into launched processes");
    private final JBTextArea otlpEnvironmentVariables = new JBTextArea(10, 80);

    public AppSettingsComponent() {
        otlpEnvironmentVariables.setLineWrap(false);
        otlpEnvironmentVariables.setWrapStyleWord(false);

        panel = FormBuilder.createFormBuilder()
                .addComponent(enableLoopbackOtlpReceiver, 1)
                .addComponent(injectOtlpEnvironmentVariables, 1)
                .addLabeledComponent(new JBLabel("Environment variables (KEY=VALUE, supports ${OTLP_ENDPOINT}, ${OTLP_HOST}, ${OTLP_PORT})"), new JBScrollPane(otlpEnvironmentVariables), 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return panel;
    }

    public JComponent getPreferredFocusedComponent() {
        return enableLoopbackOtlpReceiver;
    }

    public boolean getEnableLoopbackOtlpReceiver() {
        return enableLoopbackOtlpReceiver.isSelected();
    }

    public void setEnableLoopbackOtlpReceiver(boolean value) {
        enableLoopbackOtlpReceiver.setSelected(value);
    }

    public boolean getInjectOtlpEnvironmentVariables() {
        return injectOtlpEnvironmentVariables.isSelected();
    }

    public void setInjectOtlpEnvironmentVariables(boolean value) {
        injectOtlpEnvironmentVariables.setSelected(value);
    }

    public String getOtlpEnvironmentVariables() {
        return otlpEnvironmentVariables.getText();
    }

    public void setOtlpEnvironmentVariables(String value) {
        otlpEnvironmentVariables.setText(value);
    }
}