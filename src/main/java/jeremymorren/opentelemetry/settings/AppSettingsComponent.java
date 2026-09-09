package jeremymorren.opentelemetry.settings;

import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import jeremymorren.opentelemetry.otlp.OtlpEnvironmentVariables;
import jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService;

import javax.swing.*;
import java.awt.Font;
import java.awt.event.ActionListener;

public class AppSettingsComponent {
    /** One tick a day is already absurd for a debug viewer; this is only here to keep the spinner sane. */
    private static final int MAX_FLUSH_INTERVAL_MILLIS = 600_000;

    /** An hour between metric pushes is well past the point of being a live view. */
    private static final int MAX_METRICS_FLUSH_INTERVAL_SECONDS = 3_600;

    /** Bundled with every JetBrains IDE. */
    private static final String MONOSPACE_FONT_NAME = "JetBrains Mono";

    /** Wide enough for the longest line of the default template, and no wider. */
    private static final int ENVIRONMENT_COLUMNS = 52;

    private final JPanel panel;
    private final JBCheckBox enableLoopbackOtlpReceiver = new JBCheckBox("Enable loopback OTLP/HTTP receiver (binds to " + OtlpHttpReceiverService.BIND_ADDRESS + " only)");
    private final JBCheckBox injectOtlpEnvironmentVariables = new JBCheckBox("Inject OTLP environment variables into launched processes");
    private final JBCheckBox appendCurlCompressed = new JBCheckBox("Append --compressed to copied curl commands");
    private final JBIntSpinner flushIntervalMillis = new JBIntSpinner(
            OtlpEnvironmentVariables.DEFAULT_FLUSH_INTERVAL_MILLIS, 1, MAX_FLUSH_INTERVAL_MILLIS, 50);
    private final JBIntSpinner metricsFlushIntervalSeconds = new JBIntSpinner(
            OtlpEnvironmentVariables.DEFAULT_METRICS_FLUSH_INTERVAL_SECONDS, 1, MAX_METRICS_FLUSH_INTERVAL_SECONDS, 5);
    private final JBTextArea otlpEnvironmentVariables = new JBTextArea(10, ENVIRONMENT_COLUMNS);

    public AppSettingsComponent() {
        otlpEnvironmentVariables.setLineWrap(false);
        otlpEnvironmentVariables.setWrapStyleWord(false);
        otlpEnvironmentVariables.setFont(monospaceFont());

        ActionListener resetAction =
                event -> otlpEnvironmentVariables.setText(OtlpEnvironmentVariables.DEFAULT_ENVIRONMENT_VARIABLES);
        ActionLink resetEnvironmentVariables = new ActionLink("Reset to defaults", resetAction);

        // Hints and the environment variables caption go on their own rows rather than into the label
        // column: a long label there sets the width of the whole settings page.
        panel = FormBuilder.createFormBuilder()
                .addComponent(enableLoopbackOtlpReceiver, 1)
                .addComponent(injectOtlpEnvironmentVariables, 1)
                .addComponent(appendCurlCompressed, 1)
                .addLabeledComponent(new JBLabel("Flush interval (ms):"), flushIntervalMillis, 1, false)
                .addComponentToRightColumn(hint("How often a process pushes traces and logs."), 0)
                .addLabeledComponent(new JBLabel("Metrics flush interval (s):"), metricsFlushIntervalSeconds, 1, false)
                .addComponentToRightColumn(hint("How often a process pushes metrics."), 0)
                .addComponent(new JBLabel("Environment variables (KEY=VALUE):"), 8)
                .addComponent(new JBScrollPane(otlpEnvironmentVariables))
                .addComponent(hint("Placeholders: ${OTLP_ENDPOINT}, ${OTLP_HOST}, ${OTLP_PORT},"), 0)
                .addComponent(hint("${OTLP_FLUSH_INTERVAL}, ${OTLP_METRICS_FLUSH_INTERVAL} (both milliseconds)"), 0)
                .addComponent(resetEnvironmentVariables, 0)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    /**
     * The Swing default for a text area is the logical {@code Monospaced} family, which lands on Courier.
     */
    private static Font monospaceFont() {
        int size = JBUI.Fonts.label().getSize();
        Font font = new Font(MONOSPACE_FONT_NAME, Font.PLAIN, size);
        // An unavailable family silently resolves to Dialog, which is not monospaced at all.
        return MONOSPACE_FONT_NAME.equals(font.getFamily())
                ? font
                : new Font(Font.MONOSPACED, Font.PLAIN, size);
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

    public int getMetricsFlushIntervalSeconds() {
        return metricsFlushIntervalSeconds.getNumber();
    }

    public void setMetricsFlushIntervalSeconds(int value) {
        metricsFlushIntervalSeconds.setNumber(Math.clamp(value, 1, MAX_METRICS_FLUSH_INTERVAL_SECONDS));
    }

    public String getOtlpEnvironmentVariables() {
        return otlpEnvironmentVariables.getText();
    }

    public void setOtlpEnvironmentVariables(String value) {
        otlpEnvironmentVariables.setText(value);
    }
}
