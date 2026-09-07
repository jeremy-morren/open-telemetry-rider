package jeremymorren.opentelemetry.settings;

import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import jeremymorren.opentelemetry.otlp.OtlpEnvironmentVariables;

import javax.swing.*;
import java.awt.event.ActionListener;

public class AppSettingsComponent {
    /** One tick a day is already absurd for a debug viewer; this is only here to keep the spinner sane. */
    private static final int MAX_FLUSH_INTERVAL_MILLIS = 600_000;

    private final JPanel panel;
    private final JBCheckBox enableLoopbackOtlpReceiver = new JBCheckBox("Enable loopback OTLP/HTTP receiver (binds to 127.0.0.1 only)");
    private final JBCheckBox injectOtlpEnvironmentVariables = new JBCheckBox("Inject OTLP environment variables into launched processes");
    private final JBCheckBox appendCurlCompressed = new JBCheckBox("Append --compressed to copied curl commands");
    private final JBIntSpinner flushIntervalMillis = new JBIntSpinner(
            OtlpEnvironmentVariables.DEFAULT_FLUSH_INTERVAL_MILLIS, 1, MAX_FLUSH_INTERVAL_MILLIS, 50);
    private final JBTextArea otlpEnvironmentVariables = new JBTextArea(10, 80);

    public AppSettingsComponent() {
        otlpEnvironmentVariables.setLineWrap(false);
        otlpEnvironmentVariables.setWrapStyleWord(false);

        ActionListener resetAction =
                event -> otlpEnvironmentVariables.setText(OtlpEnvironmentVariables.DEFAULT_ENVIRONMENT_VARIABLES);
        ActionLink resetEnvironmentVariables = new ActionLink("Reset to defaults", resetAction);

        panel = FormBuilder.createFormBuilder()
                .addComponent(enableLoopbackOtlpReceiver, 1)
                .addComponent(injectOtlpEnvironmentVariables, 1)
                .addComponent(appendCurlCompressed, 1)
                .addLabeledComponent(new JBLabel("Flush frequency (ms):"), flushIntervalMillis, 1, false)
                .addComponentToRightColumn(hint(
                        "How often a debugged process pushes telemetry to the viewer. Lower is more "
                                + "responsive but noisier; the value fills the ${OTLP_FLUSH_INTERVAL} "
                                + "placeholder below."), 0)
                .addLabeledComponent(new JBLabel("Environment variables (KEY=VALUE, supports ${OTLP_ENDPOINT}, ${OTLP_HOST}, ${OTLP_PORT}, ${OTLP_FLUSH_INTERVAL})"), new JBScrollPane(otlpEnvironmentVariables), 1, false)
                .addComponentToRightColumn(resetEnvironmentVariables, 0)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private static JBLabel hint(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        return label;
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

    public boolean getAppendCurlCompressed() {
        return appendCurlCompressed.isSelected();
    }

    public void setAppendCurlCompressed(boolean value) {
        appendCurlCompressed.setSelected(value);
    }

    public int getFlushIntervalMillis() {
        return flushIntervalMillis.getNumber();
    }

    public void setFlushIntervalMillis(int value) {
        flushIntervalMillis.setNumber(Math.clamp(value, 1, MAX_FLUSH_INTERVAL_MILLIS));
    }

    public String getOtlpEnvironmentVariables() {
        return otlpEnvironmentVariables.getText();
    }

    public void setOtlpEnvironmentVariables(String value) {
        otlpEnvironmentVariables.setText(value);
    }
}
