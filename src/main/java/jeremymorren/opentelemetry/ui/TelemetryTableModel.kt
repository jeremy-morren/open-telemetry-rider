package jeremymorren.opentelemetry.ui

import jeremymorren.opentelemetry.models.Telemetry
import jeremymorren.opentelemetry.models.TelemetryItem
import jeremymorren.opentelemetry.models.TelemetryType
import java.time.Instant
import java.util.*
import javax.swing.table.AbstractTableModel
import kotlin.math.max
import java.time.Duration

class TelemetryTableModel : AbstractTableModel() {
    private val columnNames = arrayOf(
        "timestamp", "duration", "type", "detail"
    )
    private val columnClass = arrayOf<Class<*>>(
        Instant::class.java, Duration::class.java, TelemetryType::class.java, Telemetry::class.java
    )

    private val telemetries: MutableList<TelemetryItem> = ArrayList()

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = columnClass[columnIndex]

    override fun getRowCount(): Int = telemetries.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val telemetry = telemetries[rowIndex]
        return when (columnIndex) {
            0 -> telemetry.timestamp
            1 -> telemetry.duration
            2 -> telemetry.telemetry.type
            3 -> telemetry.telemetry
            else -> null
        }
    }

    fun addRow(telemetry: TelemetryItem) {
        telemetries.add(telemetry)
        this.fireTableRowsInserted(telemetries.size - 1, telemetries.size - 1)
    }

    fun addRow(i: Int, telemetry: TelemetryItem) {
        telemetries.add(i, telemetry)
        this.fireTableRowsInserted(telemetries.size - 1, telemetries.size - 1)
    }

    fun setRows(telemetries: List<TelemetryItem>) {
        val previousSize = this.telemetries.size
        this.telemetries.clear()
        this.fireTableRowsDeleted(0, max(0.0, (previousSize - 1).toDouble()).toInt())
        this.telemetries.addAll(telemetries)
        this.fireTableRowsInserted(0, max(0.0, (this.telemetries.size - 1).toDouble()).toInt())
    }

    fun getRow(selectedRow: Int): TelemetryItem? {
        if (selectedRow < 0) return null
        if (selectedRow >= this.rowCount) return null
        return telemetries[selectedRow]
    }
}
