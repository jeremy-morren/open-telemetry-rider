package jeremymorren.opentelemetry.ui.renderers

import com.intellij.ui.JBColor
import jeremymorren.opentelemetry.models.TelemetryType
import java.awt.Component
import javax.swing.JTable

class TelemetryTypeRenderer : TelemetryRendererBase() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (value !is TelemetryType) {
            return this
        }

        super.setText(value.toString());

        when (value) {
            TelemetryType.Activity -> super.setForeground(JBColor.namedColor("OpenTelemetry.TelemetryColor.Activity", JBColor.cyan))
            TelemetryType.Request -> super.setForeground(JBColor.namedColor("OpenTelemetry.TelemetryColor.Request", JBColor.green))
            TelemetryType.Dependency -> super.setForeground(JBColor.namedColor("OpenTelemetry.TelemetryColor.Dependency", JBColor.blue))
            TelemetryType.Metric -> super.setForeground(JBColor.namedColor("OpenTelemetry.TelemetryColor.Metric", JBColor.gray))
            TelemetryType.Message -> super.setForeground(JBColor.namedColor("OpenTelemetry.TelemetryColor.Log", JBColor.orange))
            TelemetryType.Exception -> super.setForeground(JBColor.namedColor("OpenTelemetry.TelemetryColor.Exception", JBColor.red))
        }

        return this
    }
}
