package jeremymorren.opentelemetry.ui

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import jeremymorren.opentelemetry.models.Telemetry
import jeremymorren.opentelemetry.util.DurationFormatter.Companion.format
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Opens the SQL of a database dependency in a scratch file, so it can be read, formatted and run
 * against a data source like any other query.
 *
 * Everything the span knows about the connection is written into a comment header, because that
 * context is gone once the query leaves the telemetry table.
 */
object SqlScratchFile {
    private val LOG = Logger.getInstance(SqlScratchFile::class.java)

    /** CRC32 in base 36 never exceeds this; names are padded to it so they all look alike. */
    private const val HASH_LENGTH = 7

    /**
     * Tag prefixes worth carrying over: the database, the server it lives on, and the connection.
     */
    private val INTERESTING_TAG_PREFIXES = listOf("db.", "server.", "network.", "net.", "peer.")

    /**
     * Carried in the body rather than the header.
     */
    private val QUERY_TEXT_TAGS = setOf("db.query.text", "db.statement")

    @JvmStatic
    fun open(project: Project, telemetry: Telemetry) {
        val sql = telemetry.sql ?: return

        val text = buildContent(telemetry, sql)
        val sqlLanguage = Language.findLanguageByID("SQL")

        try {
            val file = ScratchRootType.getInstance().createScratchFile(
                project,
                fileName(telemetry, sql),
                sqlLanguage ?: Language.ANY,
                text
            )
            if (file != null) {
                FileEditorManager.getInstance(project).openFile(file, true)
            }
        } catch (ex: Exception) {
            LOG.warn("Failed to open SQL in a scratch file", ex)
        }
    }

    /**
     * Names the file after the database system and a hash of the statement, so the same query always
     * lands on the same name and different queries never share one. The hash covers the statement only,
     * not the comment header, whose timings differ on every execution - which is what makes the numeric
     * suffix below meaningful: popping the same query out twice keeps both executions.
     */
    private fun fileName(telemetry: Telemetry, sql: String): String {
        val base = "${system(telemetry)}-${shortHash(sql)}"

        var name = "$base.sql"
        var suffix = 2
        while (exists(name)) {
            name = "$base-$suffix.sql"
            suffix++
        }
        return name
    }

    private fun system(telemetry: Telemetry): String {
        val tags = telemetry.activity?.tags
        val system = firstNonBlank(tags?.getString("db.system.name"), tags?.getString("db.system"))
        if (system.isNullOrBlank()) {
            return "sql"
        }
        return system.replace("[^A-Za-z0-9._-]".toRegex(), "_")
    }

    /**
     * A seven character, filename safe digest. CRC32 spreads small edits well, which is all that is
     * needed to tell one query from another, and base 36 keeps it two characters shorter than hex.
     */
    private fun shortHash(text: String): String {
        val crc = CRC32()
        crc.update(text.toByteArray(StandardCharsets.UTF_8))
        val encoded = crc.value.toString(36)
        return "0".repeat(HASH_LENGTH - encoded.length) + encoded
    }

    private fun exists(name: String): Boolean {
        val root = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance())
        return LocalFileSystem.getInstance().findFileByPath("$root/$name") != null
    }

    private fun firstNonBlank(first: String?, second: String?): String? =
        if (!first.isNullOrBlank()) first else second

    private fun buildContent(telemetry: Telemetry, sql: String): String {
        val newLine = System.lineSeparator()
        val content = StringBuilder()

        for ((key, value) in header(telemetry)) {
            content.append("-- ").append(key).append(": ").append(value).append(newLine)
        }
        if (content.isNotEmpty()) {
            content.append(newLine)
        }

        // Normalise line endings so the scratch file matches the platform, not the wire format.
        content.append(sql.replace("\r\n", "\n").replace("\r", "\n").replace("\n", newLine))
        if (!sql.endsWith("\n")) {
            content.append(newLine)
        }
        return content.toString()
    }

    private fun header(telemetry: Telemetry): Map<String, String> {
        val header = LinkedHashMap<String, String>()
        val activity = telemetry.activity ?: return header

        put(header, "Operation", activity.displayName)
        put(header, "Source", activity.source?.name)
        put(header, "Timestamp", telemetry.timestamp?.toString())
        put(header, "Duration", activity.duration?.let { format(it) })
        put(header, "DB time", activity.dbQueryTime?.let { format(it) })
        if (activity.isError) {
            put(header, "Error", activity.errorDisplay)
        }

        // Everything the instrumentation recorded about the connection: data source, database, server
        // address and port, connection id, driver, and so on - whatever tags happen to be present.
        if (activity.tags != null) {
            for ((key, value) in activity.tags.getDisplayValues()) {
                if (key in QUERY_TEXT_TAGS || INTERESTING_TAG_PREFIXES.none { key.startsWith(it) }) {
                    continue
                }
                put(header, key, value)
            }
        }
        return header
    }

    /** Adds a non-blank value, flattened onto one line so it fits in a `--` comment. */
    private fun put(header: MutableMap<String, String>, key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            header.putIfAbsent(key, value.replace("\r", "").replace("\n", " "))
        }
    }
}
