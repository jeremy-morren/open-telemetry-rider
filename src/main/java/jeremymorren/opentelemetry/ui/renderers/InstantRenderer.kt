package jeremymorren.opentelemetry.ui.renderers

import java.awt.Component
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import javax.swing.JTable

class InstantRenderer : TelemetryRendererBase() {
    private val simpleDateFormat = SimpleDateFormat("HH:mm:ss.SSS")

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (value is Instant) {
            val date = Date.from(value)
            super.setText(simpleDateFormat.format(date))
        }

        return this
    }
}
