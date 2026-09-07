package jeremymorren.opentelemetry.ui;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import jeremymorren.opentelemetry.models.Activity;
import jeremymorren.opentelemetry.models.Telemetry;
import jeremymorren.opentelemetry.util.DurationFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Opens the SQL of a database dependency in a scratch file, so it can be read, formatted and run
 * against a data source like any other query.
 *
 * <p>Everything the span knows about the connection is written into a comment header, because that
 * context is gone once the query leaves the telemetry table.
 */
public final class SqlScratchFile {
    private static final Logger LOG = Logger.getInstance(SqlScratchFile.class);

    /** CRC32 in base 36 never exceeds this; names are padded to it so they all look alike. */
    private static final int HASH_LENGTH = 7;

    /**
     * Tag prefixes worth carrying over: the database, the server it lives on, and the connection.
     */
    private static final String[] INTERESTING_TAG_PREFIXES = {"db.", "server.", "network.", "net.", "peer."};

    /**
     * Carried in the body rather than the header.
     */
    private static final String[] QUERY_TEXT_TAGS = {"db.query.text", "db.statement"};

    private SqlScratchFile() {
    }

    public static void open(@NotNull Project project, @NotNull Telemetry telemetry) {
        String sql = telemetry.getSql();
        if (sql == null) {
            return;
        }

        String text = buildContent(telemetry, sql);
        Language sqlLanguage = Language.findLanguageByID("SQL");

        try {
            VirtualFile file = ScratchRootType.getInstance().createScratchFile(
                    project,
                    fileName(telemetry, sql),
                    sqlLanguage != null ? sqlLanguage : Language.ANY,
                    text);
            if (file != null) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to open SQL in a scratch file", ex);
        }
    }

    /**
     * Names the file after the database system and a hash of the statement, so the same query always
     * lands on the same name and different queries never share one. The hash covers the statement only,
     * not the comment header, whose timings differ on every execution - which is what makes the numeric
     * suffix below meaningful: popping the same query out twice keeps both executions.
     */
    @NotNull
    private static String fileName(@NotNull Telemetry telemetry, @NotNull String sql) {
        String base = system(telemetry) + "-" + shortHash(sql);

        String name = base + ".sql";
        for (int suffix = 2; exists(name); suffix++) {
            name = base + "-" + suffix + ".sql";
        }
        return name;
    }

    @NotNull
    private static String system(@NotNull Telemetry telemetry) {
        Activity activity = telemetry.getActivity();
        String system = activity == null || activity.getTags() == null
                ? null
                : firstNonBlank(activity.getTags().getString("db.system.name"),
                                activity.getTags().getString("db.system"));
        if (system == null || system.isBlank()) {
            return "sql";
        }
        return system.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * A seven character, filename safe digest. CRC32 spreads small edits well, which is all that is
     * needed to tell one query from another, and base 36 keeps it two characters shorter than hex.
     */
    @NotNull
    private static String shortHash(@NotNull String text) {
        CRC32 crc = new CRC32();
        crc.update(text.getBytes(StandardCharsets.UTF_8));
        String encoded = Long.toString(crc.getValue(), 36);
        return "0".repeat(HASH_LENGTH - encoded.length()) + encoded;
    }

    private static boolean exists(@NotNull String name) {
        String root = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance());
        return LocalFileSystem.getInstance().findFileByPath(root + "/" + name) != null;
    }

    @Nullable
    private static String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    @NotNull
    private static String buildContent(@NotNull Telemetry telemetry, @NotNull String sql) {
        String newLine = System.lineSeparator();
        StringBuilder content = new StringBuilder();

        for (Map.Entry<String, String> entry : header(telemetry).entrySet()) {
            content.append("-- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(newLine);
        }
        if (!content.isEmpty()) {
            content.append(newLine);
        }

        // Normalise line endings so the scratch file matches the platform, not the wire format.
        content.append(sql.replace("\r\n", "\n").replace("\r", "\n").replace("\n", newLine));
        if (!sql.endsWith("\n")) {
            content.append(newLine);
        }
        return content.toString();
    }

    @NotNull
    private static Map<String, String> header(@NotNull Telemetry telemetry) {
        Map<String, String> header = new LinkedHashMap<>();
        Activity activity = telemetry.getActivity();
        if (activity == null) {
            return header;
        }

        put(header, "Operation", activity.getDisplayName());
        put(header, "Source", activity.getSource() == null ? null : activity.getSource().getName());
        if (telemetry.getTimestamp() != null) {
            put(header, "Timestamp", telemetry.getTimestamp().toString());
        }
        if (activity.getDuration() != null) {
            put(header, "Duration", DurationFormatter.Companion.format(activity.getDuration()));
        }
        if (activity.getDbQueryTime() != null) {
            put(header, "DB time", DurationFormatter.Companion.format(activity.getDbQueryTime()));
        }
        if (activity.isError()) {
            put(header, "Error", activity.getErrorDisplay());
        }

        // Everything the instrumentation recorded about the connection: data source, database, server
        // address and port, connection id, driver, and so on - whatever tags happen to be present.
        if (activity.getTags() != null) {
            for (Map.Entry<String, String> tag : activity.getTags().getDisplayValues().entrySet()) {
                if (isQueryText(tag.getKey()) || !isInteresting(tag.getKey())) {
                    continue;
                }
                put(header, tag.getKey(), tag.getValue());
            }
        }

        for (Map.Entry<String, String> entry : header.entrySet()) {
            entry.setValue(entry.getValue().replace("\r", "").replace("\n", " "));
        }
        return header;
    }

    private static boolean isQueryText(@NotNull String key) {
        for (String tag : QUERY_TEXT_TAGS) {
            if (tag.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInteresting(@NotNull String key) {
        for (String prefix : INTERESTING_TAG_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void put(@NotNull Map<String, String> header, @NotNull String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            header.putIfAbsent(key, value);
        }
    }
}
