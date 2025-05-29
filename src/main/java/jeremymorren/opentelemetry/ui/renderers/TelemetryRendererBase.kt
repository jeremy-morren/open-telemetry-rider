package jeremymorren.opentelemetry.ui.renderers

import com.intellij.ui.JBColor
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

open class TelemetryRendererBase : JLabel(), TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.setOpaque(true)
        if (isSelected) {
            super.setBackground(JBColor.namedColor("Table.selectionBackground", JBColor.blue))
        } else {
            super.setBackground(JBColor.namedColor("Table.background", JBColor.gray))
        }
        super.setText(value?.toString() ?: "")
        return this
    }
}
