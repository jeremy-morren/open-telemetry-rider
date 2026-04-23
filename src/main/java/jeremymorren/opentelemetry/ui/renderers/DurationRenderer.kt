package jeremymorren.opentelemetry.ui.renderers

import jeremymorren.opentelemetry.util.DurationFormatter
import java.awt.Component
import java.time.Duration
import javax.swing.JTable

class DurationRenderer : TelemetryRendererBase() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (value is Duration) {
            val str = DurationFormatter.format(value)
            super.setText(str)
        }

        return this
    }
}
