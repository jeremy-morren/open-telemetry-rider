package jeremymorren.opentelemetry.ui.renderers

import com.intellij.ui.JBColor
import jeremymorren.opentelemetry.models.LogLevel
import jeremymorren.opentelemetry.models.Telemetry
import java.awt.Component
import javax.swing.JTable

class TelemetryRenderer : TelemetryRendererBase() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        super.setForeground(JBColor.foreground())

        if (value !is Telemetry) {
            return this
        }
        if (value.activity != null) {
            super.setText(value.activity.detail)
            val color =
                if (value.activity.isError) {
                    JBColor.namedColor("OpenTelemetry.SeverityLevel.Error", JBColor.red)
                }
                else {
                    JBColor.namedColor("OpenTelemetry.SeverityLevel.Default", JBColor.foreground())
                }
            super.setForeground(color)
        }
        if (value.metric != null) {
            super.setText(value.metric.detail)
        }
        if (value.log != null) {
            super.setText(value.log.displayMessage)
            val foreGround = JBColor.namedColor("OpenTelemetry.SeverityLevel.Default", JBColor.foreground())
            when (value.log.logLevel) {
                LogLevel.Warning -> {
                    val color = JBColor.namedColor("OpenTelemetry.SeverityLevel.Warning", JBColor.orange)
                    super.setForeground(color)
                }
                LogLevel.Error -> {
                    val color = JBColor.namedColor("OpenTelemetry.SeverityLevel.Error", JBColor.red)
                    super.setForeground(color)
                }
                LogLevel.Critical -> {
                    val color = JBColor.namedColor("OpenTelemetry.SeverityLevel.Critical", JBColor(0xA21319, 0x5B0006))
                    super.setForeground(foreGround)
                    if (!isSelected) {
                        super.setBackground(color)
                    }
                }
                else -> {
                    super.setForeground(foreGround)
                }
            }
        }

        return this
    }
}
