package jeremymorren.opentelemetry.ui;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.icons.AllIcons;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.jetbrains.rider.stacktrace.RiderStacktraceUtil;
import com.jetbrains.rider.unitTesting.RiderUnitTestConsoleHyperlinkFilter;
import com.jetbrains.rd.util.lifetime.Lifetime;
import jeremymorren.opentelemetry.OpenTelemetryBundle;
import jeremymorren.opentelemetry.OpenTelemetrySession;
import jeremymorren.opentelemetry.settings.AppSettingState;
import jeremymorren.opentelemetry.http.HttpTelemetryRequest;
import jeremymorren.opentelemetry.models.Activity;
import jeremymorren.opentelemetry.models.LogMessage;
import jeremymorren.opentelemetry.models.Telemetry;
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.ui.components.*;
import jeremymorren.opentelemetry.ui.renderers.DurationRenderer;
import jeremymorren.opentelemetry.ui.renderers.InstantRenderer;
import jeremymorren.opentelemetry.ui.renderers.TelemetryRenderer;
import jeremymorren.opentelemetry.ui.renderers.TelemetryTypeRenderer;
import jeremymorren.opentelemetry.util.DurationFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@SuppressWarnings({"NotNullFieldNotInitialized", "unused"})
public class OpenTelemetryToolWindow {
    private static final Logger LOG = Logger.getInstance(OpenTelemetryToolWindow.class);

    // Tabs of the details pane, in the order the form declares them. Raw JSON sits last: it is the
    // fallback for when the tabs that interpret the telemetry have nothing to show.
    private static final int FORMATTED_TAB = 0;
    private static final int SQL_TAB = 1;
    private static final int EXCEPTION_TAB = 2;

    // UI Designer can call createUIComponents() before constructor assigns fields.
    @SuppressWarnings("ConstantValue")
    private Project getUiProjectOrDefault() {
        return project != null ? project : ProjectManager.getInstance().getDefaultProject();
    }

    @NotNull
    private JPanel mainPanel;
    @NotNull
    private JBTable logsTable;
    @NotNull
    private JSplitPane splitPane;
    @NotNull
    private ExtendableTextField filter;
    @NotNull
    private JScrollPane logsScrollPane;
    @NotNull
    private JComponent toolbar;
    @NotNull
    private JComponent jsonPanel;
    private JComponent sqlPanel;
    private JTabbedPane tabbedPane;
    private DetailsPanel formattedInfo;
    private JScrollPane formattedInfoScrollPane;

    private JCheckBox activityCheckBox;
    private JCheckBox dependencyCheckBox;
    private JCheckBox requestCheckBox;
    private JLabel activityCounter;
    private JLabel dependencyCounter;
    private JLabel requestCounter;
    @NotNull
    private ColorBox activityColorBox;
    @NotNull
    private ColorBox dependencyColorBox;
    @NotNull
    private ColorBox requestColorBox;
    private ColorBox metricColorBox;
    private JCheckBox metricCheckBox;
    private JLabel metricCounter;
    private ColorBox exceptionColorBox;
    private JCheckBox exceptionCheckBox;
    private JLabel exceptionCounter;
    private ColorBox messageColorBox;
    private JCheckBox messageCheckBox;
    private JLabel messageCounter;
    private ColorBox eventColorBox;
    private JCheckBox eventCheckBox;
    private JLabel eventCounter;
    private JComponent exceptionPanel;

    @NotNull
    private final Project project;
    @NotNull
    private final OpenTelemetrySession openTelemetrySession;

    @NotNull
    private Editor jsonEditor;
    @NotNull
    private Editor sqlEditor;
    @NotNull
    private ConsoleView exceptionConsole;
    @Nullable
    private Project exceptionConsoleProject;

    @NotNull
    private Document jsonPreviewDocument;

    @NotNull
    private Document sqlPreviewDocument;


    @NotNull
    private final TelemetryTableModel telemetryTableModel;

    @NotNull
    private final ArrayList<JLabel> telemetryTypesCounter = new ArrayList<>();
    @NotNull
    private final Map<TelemetryType, Integer> telemetryCountPerType = new HashMap<>();

    private boolean autoScrollToTheEnd;

    /** Builds the Formatted tab; created on first use, since the form supplies its container. */
    private TelemetryDetailsPanel details;

    public OpenTelemetryToolWindow(
            @NotNull OpenTelemetrySession opentelemetrySession,
            @NotNull Project project,
            Lifetime lifetime) {
        this.project = project;
        this.openTelemetrySession = opentelemetrySession;

        ensureExceptionConsoleUsesProject();

        initTelemetryTypeFilters();

        splitPane.setDividerLocation(0.5);
        splitPane.setResizeWeight(0.5);
        ReadAction.nonBlocking(() -> {
                updateFoldRegions(jsonEditor);
                return null;
            })
                .submit(AppExecutorUtil.getAppExecutorService())
                .onError((Throwable ex) -> LOG.warn("Failed to build initial JSON foldings", ex));

        //Increase scroll speed
        formattedInfoScrollPane.getVerticalScrollBar().setUnitIncrement(12);
        formattedInfoScrollPane.getHorizontalScrollBar().setUnitIncrement(12);

        logsTable.setDefaultRenderer(Telemetry.class, new TelemetryRenderer());
        logsTable.setDefaultRenderer(Instant.class, new InstantRenderer());
        logsTable.setDefaultRenderer(Duration.class, new DurationRenderer());
        logsTable.setDefaultRenderer(TelemetryType.class, new TelemetryTypeRenderer());
        logsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        telemetryTableModel = new TelemetryTableModel();
        logsTable.setModel(telemetryTableModel);
        logsTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        logsTable.getColumnModel().getColumn(0).setMaxWidth(130);
        logsTable.getColumnModel().getColumn(1).setPreferredWidth(75);
        logsTable.getColumnModel().getColumn(1).setMaxWidth(100);
        logsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        logsTable.getColumnModel().getColumn(2).setMaxWidth(130);
        logsTable.getTableHeader().setUI(null);

        filter.setExtensions(ExtendableTextComponent.Extension.create(
                AllIcons.Actions.Close, AllIcons.Actions.CloseHovered, "Clear", () -> filter.setText("")));

        // Listening to the document rather than to key events catches every way the text can change:
        // typing, cut/paste (including from the context menu), undo, and the clear extension above.
        filter.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                openTelemetrySession.updateFilter(filter.getText());
            }
        });

        installTelemetryContextMenu();

        logsTable.getSelectionModel().addListSelectionListener(e -> {
            // Ignore intermediate events while selection is still changing.
            if (e.getValueIsAdjusting()) {
                return;
            }
            selectTelemetry(telemetryTableModel.getRow(logsTable.getSelectedRow()));
        });
    }

    /**
     * Adds the "copy as request" actions to the telemetry table. They are only shown for HTTP telemetry
     * (requests and dependencies), the only telemetry a request can be reconstructed from.
     */
    private void installTelemetryContextMenu() {
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(new CopyHttpRequestAction("CopyCurlBash.text", request -> request.toCurlBash(curlCompressed())));
        actions.add(new CopyHttpRequestAction("CopyCurlCmd.text", request -> request.toCurlCmd(curlCompressed())));
        actions.add(new CopyHttpRequestAction("CopyHttpRequest.text", HttpTelemetryRequest::toHttpRequest));
        actions.add(new PopOutSqlAction());

        logsTable.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component component, int x, int y) {
                // Right-clicking a row acts on that row, as everywhere else in the IDE.
                int row = logsTable.rowAtPoint(new Point(x, y));
                if (row >= 0) {
                    logsTable.setRowSelectionInterval(row, row);
                }
                ActionManager.getInstance()
                        .createActionPopupMenu(ActionPlaces.POPUP, actions)
                        .getComponent()
                        .show(component, x, y);
            }
        });
    }

    private static boolean curlCompressed() {
        return AppSettingState.getInstance().appendCurlCompressed.getValue();
    }

    @Nullable
    private Telemetry getSelectedTelemetry() {
        TelemetryItem selected = telemetryTableModel.getRow(logsTable.getSelectedRow());
        return selected == null ? null : selected.getTelemetry();
    }

    /**
     * Opens the selected database dependency's SQL in a scratch file, with the connection details the
     * span recorded written into a comment header.
     */
    private final class PopOutSqlAction extends AnAction {
        private PopOutSqlAction() {
            String message = OpenTelemetryBundle.message("PopOutSql.text");
            getTemplatePresentation().setText(message);
            getTemplatePresentation().setDescription(message);
        }

        @NotNull
        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            Telemetry telemetry = getSelectedTelemetry();
            event.getPresentation().setEnabledAndVisible(telemetry != null && telemetry.getSql() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            Telemetry telemetry = getSelectedTelemetry();
            if (telemetry == null || telemetry.getSql() == null) {
                return;
            }
            SqlScratchFile.open(project, telemetry);
        }
    }

    @Nullable
    private HttpTelemetryRequest getSelectedHttpRequest() {
        TelemetryItem selected = telemetryTableModel.getRow(logsTable.getSelectedRow());
        if (selected == null) {
            return null;
        }
        return HttpTelemetryRequest.from(selected.getTelemetry().getActivity());
    }

    private final class CopyHttpRequestAction extends AnAction {
        @NotNull
        private final Function<HttpTelemetryRequest, String> format;

        private CopyHttpRequestAction(@NotNull String messageKey, @NotNull Function<HttpTelemetryRequest, String> format) {
            this.format = format;
            String message = OpenTelemetryBundle.message(messageKey);
            getTemplatePresentation().setText(message);
            getTemplatePresentation().setDescription(message);
        }

        @NotNull
        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            event.getPresentation().setEnabledAndVisible(getSelectedHttpRequest() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            HttpTelemetryRequest request = getSelectedHttpRequest();
            if (request == null) {
                return;
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(format.apply(request)));
        }
    }

    private void selectTelemetry(@Nullable TelemetryItem telemetry) {
        if (telemetry == null)
        {
            return;
        }
        updateJsonPreview(telemetry.getRawJson());
        updateSqlPreview(telemetry.getTelemetry().getSql());
        updateExceptionView(telemetry.getTelemetry());
        updateFormattedDisplay(telemetry.getTelemetry());
    }

    @NotNull
    public JPanel getContent() {
        return mainPanel;
    }

    public void setTelemetries(
            @NotNull List<TelemetryItem> telemetries,
            @NotNull List<TelemetryItem> visibleTelemetries
    ) {
        syncControlsFromSession();
        rebuildTelemetryTypeCounter(telemetries);
        telemetryTableModel.setRows(visibleTelemetries);
    }

    public void dispose() {
        if (jsonEditor != null && !jsonEditor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(jsonEditor);
        }
        if (sqlEditor != null && !sqlEditor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(sqlEditor);
        }
        if (exceptionConsole != null) {
            exceptionConsole.dispose();
        }
    }

    public void addTelemetry(
            int index,
            @NotNull TelemetryItem telemetry,
            boolean visible,
            boolean shouldScroll
    ) {
        if (visible) {
            if (index != -1)
                telemetryTableModel.addRow(index, telemetry);
            else
                telemetryTableModel.addRow(telemetry);
            SwingUtilities.invokeLater(() -> {
                if (autoScrollToTheEnd && shouldScroll) {
                    performAutoScrollToTheEnd();
                }
            });
        }
        updateTelemetryTypeCounter(telemetry);
    }

    private void performAutoScrollToTheEnd() {
        logsTable.scrollRectToVisible(
                logsTable.getCellRect(telemetryTableModel.getRowCount() - 1, 0, true));
    }

    private void createUIComponents() {
        var toolbar = createToolbar();
        this.toolbar = toolbar.getComponent();
        toolbar.setTargetComponent(mainPanel);

        var uiProject = getUiProjectOrDefault();
        var json = createEditor(uiProject, JsonLanguage.INSTANCE);
        var sql = createEditor(uiProject, Language.findLanguageByID("SQL"));

        jsonPreviewDocument = json.getFirst();
        sqlPreviewDocument = sql.getFirst();
        jsonEditor = json.getSecond();
        sqlEditor = sql.getSecond();
        jsonPanel = jsonEditor.getComponent();
        sqlPanel = sqlEditor.getComponent();

        // Exception tab uses Rider's own stacktrace-aware console pipeline.
        // This is intentionally different from a plain TextConsoleBuilder, because we need:
        //  - stack trace frame parsing/navigation
        //  - Rider unit-test style hyperlinks
        //  - heavy filter support
        exceptionConsole = createExceptionConsole(uiProject);
        exceptionConsoleProject = uiProject;
        configureExceptionConsoleFilters(exceptionConsole, uiProject);
        applySoftWrapSettingToExceptionConsole();
        exceptionPanel = exceptionConsole.getComponent();

        formattedInfo = new DetailsPanel();

        metricColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Metric", JBColor.gray));
        exceptionColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Exception", JBColor.red));
        messageColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Message", JBColor.orange));
        dependencyColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Dependency", JBColor.blue));
        requestColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Request", JBColor.green));
        eventColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Event", JBColor.magenta));
        activityColorBox = new ColorBox(JBColor.namedColor("OpenTelemetry.TelemetryColor.Activity", JBColor.cyan));
    }

    private void ensureExceptionConsoleUsesProject() {
        if (exceptionConsoleProject == project) {
            return;
        }

        if (exceptionConsole != null) {
            exceptionConsole.dispose();
        }

        // Recreate the console against the real project (not the fallback/default project)
        // so hyperlinks and file navigation resolve correctly in the current solution.
        exceptionConsole = createExceptionConsole(project);
        exceptionConsoleProject = project;
        configureExceptionConsoleFilters(exceptionConsole, project);
        applySoftWrapSettingToExceptionConsole();
        exceptionPanel = exceptionConsole.getComponent();

        if (tabbedPane != null && tabbedPane.getTabCount() > EXCEPTION_TAB) {
            tabbedPane.setComponentAt(EXCEPTION_TAB, exceptionPanel);
        }
    }

    @NotNull
    private static ConsoleView createExceptionConsole(@NotNull Project project) {
        // RiderStacktraceUtil wires the same console internals Rider uses for stacktrace analysis.
        // Keeping this path avoids regressions we saw with generic console builders.
        return RiderStacktraceUtil.INSTANCE.createRiderConsoleView(project, false);
    }

    private static void configureExceptionConsoleFilters(@NotNull ConsoleView console, @NotNull Project project) {
        // Keep Rider unit-test hyperlinks (settings links, actions, and related output links).
        console.addMessageFilter(new RiderUnitTestConsoleHyperlinkFilter(project));
        // Extra file-path filter: if a physical file path is present in output, make it clickable.
        console.addMessageFilter(new ExistingFilePathFilter(project));
        if (console instanceof ConsoleViewImpl) {
            // Required for stacktrace parsing/filtering over larger chunks of output.
            ((ConsoleViewImpl) console).allowHeavyFilters();
        }
    }

    @NotNull
    private static Pair<Document, Editor> createEditor(Project project, Language language) {
        var document = new LanguageTextField.SimpleDocumentCreator().createDocument("", language, project);
        var editor = EditorFactory.getInstance().createViewer(document, project, EditorKind.MAIN_EDITOR);
        if (editor instanceof EditorEx) {
            var fileType = language.getAssociatedFileType();
            if (fileType != null)
                ((EditorEx) editor).setHighlighter(
                        EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));
            ((EditorEx) editor).getFoldingModel().setFoldingEnabled(true);
        }
        editor.getSettings().setIndentGuidesShown(true);
        editor.getSettings().setAdditionalLinesCount(3);
        editor.getSettings().setFoldingOutlineShown(true);
        editor.getSettings().setUseSoftWraps(
                PropertiesComponent.getInstance().getBoolean("jeremymorren.opentelemetry.useSoftWrap"));

        return Pair.create(document, editor);
    }

    @NotNull
    private ActionToolbar createToolbar() {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();

        actionGroup.add(new OptionsToolbarAction(() -> toolbar, openTelemetrySession));
        actionGroup.addSeparator();

        autoScrollToTheEnd = PropertiesComponent.getInstance()
                .getBoolean("jeremymorren.opentelemetry.autoScrollToTheEnd");

        actionGroup.add(new AutoScrollToTheEndToolbarAction(this::acceptScrollToEnd, autoScrollToTheEnd));

        // Case insensitive search lives in the options menu, alongside the sort modes it belongs with.
        actionGroup.add(new ToggleUseSoftWrapsToolbarAction(this::getPrimaryEditor, this::applySoftWrapSettingToExceptionConsole));

        actionGroup.add(new ClearApplicationInsightsLogToolbarAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                openTelemetrySession.clear();
                clearTelemetryTypeCounter();
            }
        });

        return ActionManager.getInstance().createActionToolbar("OpenTelemetry", actionGroup, false);
    }

    @NotNull
    private Editor getPrimaryEditor() {
        return jsonEditor;
    }

    private void acceptScrollToEnd(Boolean selected) {
        autoScrollToTheEnd = selected;
        PropertiesComponent.getInstance().setValue("jeremymorren.opentelemetry.autoScrollToTheEnd", selected);
        if (autoScrollToTheEnd) {
            performAutoScrollToTheEnd();
        }
    }

    private void updateTelemetryTypeCounter(@Nullable TelemetryItem telemetry)
    {
        if (telemetry == null) return;
        var type = telemetry.getTelemetry().getType();
        if (type == null) return;
        var count = telemetryCountPerType.getOrDefault(type, 0);
        count++;
        telemetryCountPerType.put(type, count);

        for (JLabel counter: telemetryTypesCounter)
        {
            TelemetryType telemetryType = (TelemetryType) counter.getClientProperty("TelemetryType");
            if (telemetryType == type) {
                counter.setText(format(count));
                break;
            }
        }
    }

    /**
     * Clears the telemetry type counters and resets the labels to "0"
     */
    private void clearTelemetryTypeCounter() {
        telemetryCountPerType.clear();
        for (JLabel counter: telemetryTypesCounter) {
            counter.setText("0");
        }
    }

    private void rebuildTelemetryTypeCounter(@NotNull List<TelemetryItem> telemetries) {
        clearTelemetryTypeCounter();
        for (TelemetryItem telemetry : telemetries) {
            updateTelemetryTypeCounter(telemetry);
        }
    }

    private void initTelemetryTypeFilters() {
        setTelemetryType(metricCounter, metricCheckBox, TelemetryType.Metric);
        setTelemetryType(exceptionCounter, exceptionCheckBox, TelemetryType.Exception);
        setTelemetryType(messageCounter, messageCheckBox, TelemetryType.Message);
        setTelemetryType(eventCounter, eventCheckBox, TelemetryType.Event);
        setTelemetryType(dependencyCounter, dependencyCheckBox, TelemetryType.Dependency);
        setTelemetryType(requestCounter, requestCheckBox, TelemetryType.Request);
        setTelemetryType(activityCounter, activityCheckBox, TelemetryType.Activity);

        telemetryTypesCounter.addAll(Arrays.asList(metricCounter, exceptionCounter, messageCounter, eventCounter, dependencyCounter, requestCounter, activityCounter));

        for (JCheckBox checkBox: telemetryTypeCheckBoxes())
        {
            var type = (TelemetryType) checkBox.getClientProperty("TelemetryType");
            checkBox.setSelected(openTelemetrySession.isTelemetryVisible(type));
            checkBox.addItemListener(e ->
                    openTelemetrySession.setTelemetryVisible(type, e.getStateChange() == ItemEvent.SELECTED));
        }
    }

    private void syncControlsFromSession() {
        if (!Objects.equals(filter.getText(), openTelemetrySession.getFilter())) {
            filter.setText(openTelemetrySession.getFilter());
        }

        for (JCheckBox checkBox: telemetryTypeCheckBoxes()) {
            var type = (TelemetryType) checkBox.getClientProperty("TelemetryType");
            if (type == null) {
                continue;
            }

            var selected = openTelemetrySession.isTelemetryVisible(type);
            if (checkBox.isSelected() != selected) {
                checkBox.setSelected(selected);
            }
        }
    }

    @NotNull
    private JCheckBox[] telemetryTypeCheckBoxes() {
        return new JCheckBox[]{
                metricCheckBox, exceptionCheckBox, messageCheckBox, eventCheckBox,
                dependencyCheckBox, requestCheckBox, activityCheckBox};
    }

    private static void setTelemetryType(JComponent counter, JComponent checkBox, TelemetryType telemetryType)
    {
        counter.putClientProperty("TelemetryType", telemetryType);
        checkBox.putClientProperty("TelemetryType", telemetryType);
    }


    private void updateJsonPreview(String json) {
        var finalJson = json.replace("\r", "");
        ApplicationManager.getApplication().runWriteAction(() -> jsonPreviewDocument.setText(finalJson));
        updateFoldRegions(jsonEditor);
    }

    private void updateSqlPreview(@Nullable String sql) {
        if (sql == null) {
            //No SQL for this telemetry. Disable the SQL tab and select the first tab
            tabbedPane.setEnabledAt(SQL_TAB, false);
            if (tabbedPane.getSelectedIndex() == SQL_TAB)
                tabbedPane.setSelectedIndex(FORMATTED_TAB);
            return;
        }
        tabbedPane.setEnabledAt(SQL_TAB, true);
        var finalSql = sql.replace("\r", "");
        ApplicationManager.getApplication().runWriteAction(() -> sqlPreviewDocument.setText(finalSql));
        updateFoldRegions(sqlEditor);
    }

    private void updateExceptionView(@Nullable Telemetry telemetry) {
        if (telemetry == null || telemetry.getException() == null) {
            //No exception for this telemetry. Disable the exception tab and select the first tab
            tabbedPane.setEnabledAt(EXCEPTION_TAB, false);
            if (tabbedPane.getSelectedIndex() == EXCEPTION_TAB)
                tabbedPane.setSelectedIndex(FORMATTED_TAB);
            exceptionConsole.clear();
            return;
        }
        tabbedPane.setEnabledAt(EXCEPTION_TAB, true);
        var exception = normalizeExceptionForConsole(telemetry.getException());
        exceptionConsole.clear();
        exceptionConsole.print(exception + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
        var consoleView = getExceptionConsoleView();
        if (consoleView != null) {
            consoleView.performWhenNoDeferredOutput(this::scrollExceptionConsoleToTop);
        } else {
            scrollExceptionConsoleToTop();
        }
    }

    private void scrollExceptionConsoleToTop() {
        var consoleView = getExceptionConsoleView();
        if (consoleView == null) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            var editor = consoleView.getEditor();
            if (editor == null || editor.isDisposed()) {
                return;
            }

            var scrollingModel = editor.getScrollingModel();
            scrollingModel.disableAnimation();
            editor.getCaretModel().moveToOffset(0);
            scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
            scrollingModel.scrollVertically(0);
            scrollingModel.scrollHorizontally(0);
        });
    }

    @Nullable
    private ConsoleViewImpl getExceptionConsoleView() {
        if (!(exceptionConsole instanceof ConsoleViewImpl)) {
            return null;
        }

        return (ConsoleViewImpl) exceptionConsole;
    }

    private static String normalizeExceptionForConsole(@NotNull String text) {
        // Normalize common escaped newline forms while preserving Windows path backslashes.
        // We intentionally avoid full Java unescape because it can consume valid path sequences
        // like "\t" and break clickable file paths.
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
                .replace("\r", "");
    }

    private void applySoftWrapSettingToExceptionConsole() {
        var consoleView = getExceptionConsoleView();
        if (consoleView == null) {
            return;
        }

        var editor = consoleView.getEditor();
        if (editor == null) {
            return;
        }

        // Toggling soft wraps in editor components tends to move console viewport to the end.
        // Capture caret and scroll offsets so users keep their reading position.
        var scrollingModel = editor.getScrollingModel();
        var caretOffset = editor.getCaretModel().getOffset();
        var verticalOffset = scrollingModel.getVerticalScrollOffset();
        var horizontalOffset = scrollingModel.getHorizontalScrollOffset();

        var useSoftWraps = PropertiesComponent.getInstance().getBoolean("jeremymorren.opentelemetry.useSoftWrap");
        scrollingModel.disableAnimation();
        editor.getSettings().setUseSoftWraps(useSoftWraps);
        consoleView.performWhenNoDeferredOutput(() -> ApplicationManager.getApplication().invokeLater(() -> {
            var currentEditor = consoleView.getEditor();
            if (currentEditor == null || currentEditor.isDisposed()) {
                return;
            }
            var currentScrollingModel = currentEditor.getScrollingModel();
            currentScrollingModel.disableAnimation();
            var boundedCaretOffset = Math.min(caretOffset, currentEditor.getDocument().getTextLength());
            currentEditor.getCaretModel().moveToOffset(boundedCaretOffset);
            currentScrollingModel.scrollToCaret(ScrollType.RELATIVE);
            currentScrollingModel.scrollVertically(verticalOffset);
            currentScrollingModel.scrollHorizontally(horizontalOffset);
        }));
    }

    private static final class ExistingFilePathFilter implements Filter {
        private static final Pattern PATH_WITH_LINE = Pattern.compile("([A-Za-z]:\\\\[^\\r\\n:]+?):(?:line\\s+)?(\\d+)");

        @NotNull
        private final Project project;

        private ExistingFilePathFilter(@NotNull Project project) {
            this.project = project;
        }

        @Nullable
        @Override
        public Result applyFilter(@NotNull String line, int entireLength) {
            var matcher = PATH_WITH_LINE.matcher(line);
            while (matcher.find()) {
                var path = matcher.group(1);
                var lineNumberText = matcher.group(2);
                if (path == null || lineNumberText == null) {
                    continue;
                }

                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
                if (file == null || !file.exists()) {
                    continue;
                }

                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(lineNumberText);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                int startOffset = entireLength - line.length() + matcher.start(1);
                int endOffset = entireLength - line.length() + matcher.end(2);
                HyperlinkInfo hyperlink = (project1) -> new OpenFileDescriptor(project, file, Math.max(0, lineNumber - 1), 0).navigate(true);
                return new Result(startOffset, endOffset, hyperlink);
            }

            return null;
        }
    }

    private void updateFoldRegions(@NotNull Editor editor) {
        if (editor.isDisposed()) {
            return;
        }

        var editorProject = editor.getProject();
        if (editorProject == null || editorProject.isDisposed()) {
            return;
        }

        CodeFoldingManager.getInstance(editorProject).scheduleAsyncFoldingUpdate(editor);
    }

    private void updateFormattedDisplay(@NotNull Telemetry telemetry) {
        if (details == null) {
            details = new TelemetryDetailsPanel(formattedInfo, value -> filter.setText(value));
        }

        details.begin();
        if (telemetry.getActivity() != null) {
            describeActivity(telemetry.getActivity());
        }
        if (telemetry.getMetric() != null) {
            describeMetric(telemetry.getMetric());
        }
        if (telemetry.getLog() != null) {
            describeLog(telemetry.getLog());
        }
        if (telemetry.getTraceIds() != null) {
            details.section("Trace");
            for (Map.Entry<String, String> entry : telemetry.getTraceIds().entrySet()) {
                details.row(entry.getKey(), entry.getValue());
            }
        }
        details.end();
    }

    private void describeActivity(@NotNull Activity activity) {
        String status = activity.getResponseStatusCode();
        String duration = activity.getDuration() == null
                ? null
                : DurationFormatter.Companion.format(activity.getDuration());
        details.header(
                activityTitle(activity),
                join(status, duration),
                activity.isError() ? JBColor.namedColor("OpenTelemetry.SeverityLevel.Error", JBColor.red) : null);

        details.section("Overview");
        details.row("Type", activity.getTypeDisplay());
        if (activity.getSource() != null) {
            details.row("Source", activity.getSource().getName());
        }
        details.row("Display name", activity.getDisplayName());
        details.row("Operation", activity.getOperationName());
        details.row("Status description", activity.getStatusDescription());
        details.row("Error", activity.getErrorDisplay());

        describeHttp(activity);
        describeDatabase(activity);
        describeTiming(activity);

        if (activity.getTags() != null) {
            details.section("Tags");
            for (Map.Entry<String, String> entry : activity.getTags().getDisplayValues().entrySet()) {
                details.row(entry.getKey(), entry.getValue());
            }
        }
    }

    @NotNull
    private static String activityTitle(@NotNull Activity activity) {
        HttpTelemetryRequest request = HttpTelemetryRequest.from(activity);
        if (request != null) {
            return request.getMethod() + " " + request.getUrl();
        }
        if (activity.getDbQuery() != null) {
            String database = activity.getDbName();
            return database == null ? "Database query" : "Database query - " + database;
        }
        return activity.getDisplayName() != null ? activity.getDisplayName() : activity.getTypeDisplay();
    }

    /**
     * Groups the request and response tags of an HTTP span. Tags keep the names the instrumentation
     * gave them; understanding what a tag means is a reason to group it, not to rename it.
     */
    private void describeHttp(@NotNull Activity activity) {
        if (HttpTelemetryRequest.from(activity) == null) {
            return;
        }

        details.section("Request");
        tagRows(activity, "http.request.", "http.route", "url.", "server.", "client.", "network.",
                "user_agent.");

        details.section("Response");
        tagRows(activity, "http.response.", "error.");
    }

    /**
     * Adds a row per tag whose name starts with one of the prefixes, under its own name.
     */
    private void tagRows(@NotNull Activity activity, @NotNull String... prefixes) {
        if (activity.getTags() == null) {
            return;
        }
        for (Map.Entry<String, String> entry : activity.getTags().getDisplayValues().entrySet()) {
            for (String prefix : prefixes) {
                if (entry.getKey().startsWith(prefix)) {
                    details.row(entry.getKey(), entry.getValue());
                    break;
                }
            }
        }
    }

    private void describeDatabase(@NotNull Activity activity) {
        if (activity.getDbQuery() == null) {
            return;
        }

        details.section("Database");
        tagRows(activity, "db.", "server.", "network.");
    }

    /**
     * Timing section, with a breakdown bar when the span recorded when the first response arrived.
     */
    private void describeTiming(@NotNull Activity activity) {
        Duration total = activity.getDuration();
        if (total == null) {
            return;
        }

        details.section("Timing");
        if (activity.getStartTime() != null) {
            details.row("Started", activity.getStartTime().toString());
        }
        details.row("Duration", DurationFormatter.Companion.format(total));

        Duration query = activity.getDbQueryTime();
        Duration read = activity.getDbReadTime();
        if (query == null || read == null || total.isZero() || total.isNegative()) {
            return;
        }

        List<TimingBar.Segment> segments = List.of(
                new TimingBar.Segment("Query", query, TimingBar.Colors.QUERY,
                        "Sending the statement and waiting for the first response"),
                new TimingBar.Segment("Read", read, TimingBar.Colors.READ,
                        "Reading the result set once the first response arrived"));
        details.component(new TimingBar(total, TimingBar.Colors.pad(segments, total), eventMarkers(activity, total)));
    }

    /**
     * Span events placed on the timing bar, other than the one that already splits it.
     */
    @NotNull
    private static List<TimingBar.Marker> eventMarkers(@NotNull Activity activity, @NotNull Duration total) {
        List<TimingBar.Marker> markers = new ArrayList<>();
        if (activity.getEvents() == null || activity.getStartTime() == null) {
            return markers;
        }

        for (var event : activity.getEvents()) {
            if (event.getName() == null || event.getTimestamp() == null
                    || event.getName().equals("received-first-response")) {
                continue;
            }
            Duration offset = Duration.between(activity.getStartTime(), event.getTimestamp());
            if (offset.isNegative() || offset.compareTo(total) > 0) {
                continue;
            }
            markers.add(new TimingBar.Marker(event.getName(), offset));
        }
        return markers;
    }

    private void describeMetric(@NotNull jeremymorren.opentelemetry.models.Metric metric) {
        details.header(
                metric.getName() != null ? metric.getName() : "Metric",
                join(metric.getMetricType(), metric.getTemporality()),
                null);

        details.section("Overview");
        details.row("Name", metric.getName());
        details.row("Description", metric.getDescription());
        details.row("Type", metric.getMetricType());
        details.row("Temporality", metric.getTemporality());
        details.row("Meter", metric.getMeter());
        details.row("Unit", metric.getUnit());
        if (metric.getDuration() != null) {
            details.row("Duration", DurationFormatter.Companion.format(metric.getDuration()));
        }

        if (metric.getTaggedPoints() == null) {
            return;
        }
        details.section("Points");
        for (var point : metric.getTaggedPoints()) {
            if (point.getLongSum() != null) {
                details.row("Sum", format(point.getLongSum()));
            }
            if (point.getDoubleSum() != null) {
                details.row("Sum", format(point.getDoubleSum()));
            }
            if (point.getLongGauge() != null) {
                details.row("Gauge", format(point.getLongGauge()));
            }
            if (point.getDoubleGauge() != null) {
                details.row("Gauge", format(point.getDoubleGauge()));
            }
            if (point.getHistogramCount() != null) {
                details.row("Histogram count", format(point.getHistogramCount()));
            }
            if (point.getHistogramSum() != null) {
                details.row("Histogram sum", format(point.getHistogramSum()));
            }
            if (point.getTags() != null) {
                for (Map.Entry<String, String> entry : point.getTags().getDisplayValues().entrySet()) {
                    details.row(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void describeLog(@NotNull LogMessage log) {
        details.header(
                log.getCustomEventName() != null ? log.getCustomEventName() : log.getType().toString(),
                join(log.getLogLevel() == null ? null : log.getLogLevel().toString(), log.getCategoryName()),
                logColor(log));

        details.section("Overview");
        details.row("Event", log.getCustomEventName());
        details.row("Message", log.getFormattedMessage());
        if (log.getLogLevel() != null) {
            details.row("Level", log.getLogLevel().toString());
        }
        details.row("Category", log.getCategoryName());
        if (log.getEventId() != null) {
            details.row("EventId.Id", Integer.toString(log.getEventId().getId()));
            details.row("EventId.Name", log.getEventId().getName());
        }

        if (log.getException() != null) {
            details.section("Exception");
            details.row("Type", log.getException().getType());
            details.row("Message", log.getException().getMessage());
        }

        if (log.getAttributes() != null) {
            details.section("Attributes");
            for (Map.Entry<String, String> entry : log.getAttributes().getDisplayValues().entrySet()) {
                details.row(entry.getKey(), entry.getValue());
            }
        }
    }

    @Nullable
    private static Color logColor(@NotNull LogMessage log) {
        if (log.getLogLevel() == null) {
            return null;
        }
        return switch (log.getLogLevel()) {
            case Warning -> JBColor.namedColor("OpenTelemetry.SeverityLevel.Warning", JBColor.orange);
            case Error, Critical -> JBColor.namedColor("OpenTelemetry.SeverityLevel.Error", JBColor.red);
            default -> null;
        };
    }

    @Nullable
    private static String join(@Nullable String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append("  \u00b7  ");
            }
            joined.append(part);
        }
        return joined.isEmpty() ? null : joined.toString();
    }

    /**
     * Formats a long value to a human-readable string (with K and M suffixes)
     */
    @NotNull
    public static String format(Integer value) {
        return format(value.longValue());
    }

    /**
     * Formats a long value to a human-readable string (with K and M suffixes)
     */
    @NotNull
    public static String format(Long value) {
        if (value < 1_000) {
            return value.toString();
        }
        var formatter = new DecimalFormat("#,###.00");
        if (value < 1_000_000) {
            return formatter.format(value / 1_000.0) + " K";
        }
        return formatter.format(value / 1_000_000.0) + " M";
    }

    /**
     * Formats a long value to a human-readable string (with K and M suffixes)
     */
    @NotNull
    public static String format(Double value) {
        if (value < 1_000.0) {
            return value.toString();
        }
        var formatter = new DecimalFormat("#,###.0");
        if (value < 1_000_000.0) {
            return formatter.format(value / 1_000.0) + " K";
        }
        return formatter.format(value / 1_000_000.0) + " M";
    }
}

