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
    private JPanel formattedInfo;
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

        if (tabbedPane != null && tabbedPane.getTabCount() > 3) {
            tabbedPane.setComponentAt(3, exceptionPanel);
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

        actionGroup.add(new ToggleCaseInsensitiveSearchToolbarAction());

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
            tabbedPane.setEnabledAt(2, false);
            if (tabbedPane.getSelectedIndex() == 2)
                tabbedPane.setSelectedIndex(0);
            return;
        }
        tabbedPane.setEnabledAt(2, true);
        var finalSql = sql.replace("\r", "");
        ApplicationManager.getApplication().runWriteAction(() -> sqlPreviewDocument.setText(finalSql));
        updateFoldRegions(sqlEditor);
    }

    private void updateExceptionView(@Nullable Telemetry telemetry) {
        if (telemetry == null || telemetry.getException() == null) {
            //No exception for this telemetry. Disable the exception tab and select the first tab
            tabbedPane.setEnabledAt(3, false);
            if (tabbedPane.getSelectedIndex() == 3)
                tabbedPane.setSelectedIndex(0);
            exceptionConsole.clear();
            return;
        }
        tabbedPane.setEnabledAt(3, true);
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
        // Show information about the telemetry
        formattedInfo.removeAll();

        int indent = 30; //Indentation for subfields

        int row = 1;

        if (telemetry.getActivity() != null) {
            var activity = telemetry.getActivity();

            //Show activity information
            formattedInfo.add(createTitleLabel(activity.getTypeDisplay()), createConstraint(row++, 0));
            if (activity.getSource() != null && activity.getType() == TelemetryType.Activity) {
                formattedInfo.add(createFilterLabel("Source", activity.getSource().getName()), createConstraint(row++, indent));
            }
            if (activity.getDuration() != null) {
                var duration = DurationFormatter.Companion.format(activity.getDuration());
                formattedInfo.add(new JLabel("Duration: " + duration), createConstraint(row++, indent));
            }
            if (activity.getDisplayName() != null) {
                formattedInfo.add(createFilterLabel("Display name", activity.getDisplayName()), createConstraint(row++, indent));
            }
            if (activity.getOperationName() != null) {
                formattedInfo.add(createFilterLabel("Operation", activity.getOperationName()), createConstraint(row++, indent));
            }
            if (activity.getErrorDisplay() != null) {
                formattedInfo.add(createFilterLabel("Error", activity.getErrorDisplay()), createConstraint(row++, indent));
            }
            if (activity.getDbQueryTime() != null) {
                var label = new JLabel("DB Time: " + DurationFormatter.Companion.format(activity.getDbQueryTime()));
                label.setToolTipText("Time spent before first response received");
                formattedInfo.add(label, createConstraint(row++, indent));
            }
            if (activity.getDbReadTime() != null) {
                var label = new JLabel("Read Time: " + DurationFormatter.Companion.format(activity.getDbReadTime()));
                label.setToolTipText("Time spent reading data from the database");
                formattedInfo.add(label, createConstraint(row++, indent));
            }

            if (activity.getRequestPath() != null) {
                formattedInfo.add(createFilterLabel("Path", activity.getRequestPath()), createConstraint(row++, indent));
            }
            if (activity.getTags() != null) {
                formattedInfo.add(createTitleLabel("Tags"), createConstraint(row++, 0));
                for (Map.Entry<String, String> entry : activity.getTags().getDisplayValues().entrySet()) {
                    var label = createFilterLabel(entry.getKey(), entry.getValue());
                    formattedInfo.add(label, createConstraint(row++, indent));
                }
            }
        }
        if (telemetry.getMetric() != null) {
            var metric = telemetry.getMetric();
            if (metric.getName() != null) {
                formattedInfo.add(createTitleLabel(metric.getName() + " (" + metric.getTemporality() + ")"), createConstraint(row++, 0));
                formattedInfo.add(createFilterLabel("Name", metric.getName()), createConstraint(row++, indent));
            }
            if (metric.getDescription() != null) {
                formattedInfo.add(createFilterLabel("Description", metric.getDescription()), createConstraint(row++, indent));
            }
            if (metric.getTemporality() != null) {
                formattedInfo.add(createFilterLabel("Temporality", metric.getTemporality()), createConstraint(row++, indent));
            }
            if (metric.getMeterName() != null) {
                formattedInfo.add(createFilterLabel("Meter", metric.getMeterName()), createConstraint(row++, indent));
            }
            if (metric.getUnit() != null) {
                formattedInfo.add(createFilterLabel("Unit", metric.getUnit()), createConstraint(row++, indent));
            }
            if (metric.getDuration() != null) {
                var duration = DurationFormatter.Companion.format(metric.getDuration());
                formattedInfo.add(new JLabel("Duration: " + duration), createConstraint(row++, indent));
            }
            if (metric.getTaggedPoints() != null) {
                var taggedPoints = metric.getTaggedPoints();
                formattedInfo.add(createTitleLabel("Points"), createConstraint(row++, 0));
                for (var i = 0; i < taggedPoints.size(); i++) {
                    var point = taggedPoints.get(i);
                    if (point.getLongSum() != null) {
                        var sum = format(point.getLongSum());
                        formattedInfo.add(new JLabel("Sum: " + sum), createConstraint(row++, indent));
                    }
                    if (point.getDoubleSum() != null) {
                        var sum = format(point.getDoubleSum());
                        formattedInfo.add(new JLabel("Sum: " + sum), createConstraint(row++, indent));
                    }
                    if (point.getLongGauge() != null) {
                        var gauge = format(point.getLongGauge());
                        formattedInfo.add(new JLabel("Gauge: " + gauge), createConstraint(row++, indent));
                    }
                    if (point.getDoubleGauge() != null) {
                        var gauge = format(point.getDoubleGauge());
                        formattedInfo.add(new JLabel("Gauge: " + gauge), createConstraint(row++, indent));
                    }
                    if (point.getHistogramCount() != null) {
                        var count = format(point.getHistogramCount());
                        formattedInfo.add(new JLabel("Histogram Count: " + count), createConstraint(row++, indent));
                    }
                    if (point.getHistogramSum() != null) {
                        var sum = format(point.getHistogramSum());
                        formattedInfo.add(new JLabel("Histogram Sum: " + sum), createConstraint(row++, indent));
                    }
                    if (point.getTags() != null) {
                        for (Map.Entry<String, String> entry : point.getTags().getDisplayValues().entrySet()) {
                            var label = createFilterLabel(entry.getKey(), entry.getValue());
                            formattedInfo.add(label, createConstraint(row++, indent));
                        }
                    }
                    if (i < taggedPoints.size() - 1) {
                        //Add blank line between points
                        formattedInfo.add(new JPanel(), createConstraint(row++, 0));
                    }
                }
            }
        }
        if (telemetry.getLog() != null) {
            var log = telemetry.getLog();
            formattedInfo.add(createTitleLabel(log.getType().toString()), createConstraint(row++, 0));
            if (log.getCustomEventName() != null)
            {
                formattedInfo.add(createFilterLabel("Event", log.getCustomEventName()), createConstraint(row++, indent));
            }
            if (log.getFormattedMessage() != null)
            {
                formattedInfo.add(createFilterLabel("Message", log.getFormattedMessage()), createConstraint(row++, indent));
            }
            if (log.getLogLevel() != null)
            {
                formattedInfo.add(createFilterLabel("Level", log.getLogLevel().toString()), createConstraint(row++, indent));
            }
            if (log.getCategoryName() != null)
            {
                formattedInfo.add(createFilterLabel("Category", log.getCategoryName()), createConstraint(row++, indent));
            }
            if (log.getEventId() != null)
            {
                formattedInfo.add(createFilterLabel("EventId.Id", Integer.toString(log.getEventId().getId())), createConstraint(row++, indent));
                if (log.getEventId().getName() != null)
                {
                    formattedInfo.add(createFilterLabel("EventId.Name", log.getEventId().getName()), createConstraint(row++, indent));
                }
            }
            if (log.getException() != null) {
                if (log.getException().getType() != null)
                {
                    formattedInfo.add(createFilterLabel("Exception Type", log.getException().getType()), createConstraint(row++, indent));
                }
                if (log.getException().getMessage() != null)
                {
                    formattedInfo.add(createFilterLabel("Exception Message", log.getException().getMessage()), createConstraint(row++, indent));
                }
            }
            if (log.getAttributes() != null)
            {
                formattedInfo.add(createTitleLabel("Attributes"), createConstraint(row++, 0));
                for (Map.Entry<String, String> entry : log.getAttributes().getDisplayValues().entrySet()) {
                    var value = entry.getValue();
                    if (value == null) {
                        value = "";
                    }
                    var label = createFilterLabel(entry.getKey(), value);
                    formattedInfo.add(label, createConstraint(row++, indent));
                }
            }
        }

        //Add trace information
        if (telemetry.getTraceIds() != null) {
            formattedInfo.add(createTitleLabel("Trace"), createConstraint(row++, 0));
            for (Map.Entry<String, String> entry : telemetry.getTraceIds().entrySet()) {
                var label = createFilterLabel(entry.getKey(), entry.getValue());
                formattedInfo.add(label, createConstraint(row++, indent));
            }
        }

        // Padding
        {
            GridBagConstraints c = createConstraint(10_000, 0);
            c.weighty = 1;
            formattedInfo.add(new JPanel(), c);
        }

        formattedInfo.revalidate();
        formattedInfo.repaint();
    }

    @NotNull
    private JLabel createFilterLabel(@Nullable String label, @Nullable String value) {
        if (label == null) {
            label = "";
        }
        if (value == null) {
            value = "";
        }
        return createFilterLabelFinal(label, value);
    }

    @NotNull
    private JLabel createFilterLabelFinal(@NotNull String label, @NotNull String value) {
        var display = value.replace("\r", "").replace("\n", " ");
        if (display.length() > 100) {
            display = display.substring(0, 100) + "...";
        }
        JLabel jLabel = new JLabel("<html>" + escapeHtml(label) + ": " + "<a href=''>" + escapeHtml(display) + "</a></html>");
        jLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        String clicked = value;
        jLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Setting the text applies the filter through the document listener.
                filter.setText(clicked);

                // If the label was right-clicked, copy the value to the clipboard
                if (SwingUtilities.isRightMouseButton(e)) {
                    CopyPasteManager.getInstance().setContents(new StringSelection(clicked));
                }
            }
        });
        return jLabel;
    }

    @NotNull
    private JLabel createTitleLabel(@Nullable String label) {
        JLabel title = new JLabel("<html><b>" + escapeHtml(label) + "</b></html>");
        Font font = title.getFont();
        font.deriveFont(Font.BOLD);
        title.setFont(font);
        return title;
    }

    @NotNull
    private GridBagConstraints createConstraint(int y, int padX) {
        return createConstraint(0, y, padX);
    }

    @SuppressWarnings("SameParameterValue")
    private GridBagConstraints createConstraint(int x, int y, int padX) {
        GridBagConstraints gridConstraints = new GridBagConstraints();
        gridConstraints.gridx = x;
        gridConstraints.gridy = y;
        gridConstraints.gridheight = 1;
        gridConstraints.gridwidth = 1;
        gridConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridConstraints.weightx = 1;
        gridConstraints.weighty = 0;
        gridConstraints.anchor = GridBagConstraints.NORTHEAST;
        gridConstraints.insets = JBUI.insetsLeft(padX);
        return gridConstraints;
    }

    /**
     * Escapes a string for HTML display
     */
    @NotNull
    private static String escapeHtml(@Nullable String s) {
        if (s == null) {
            return "";
        }
        return StringUtil.escapeXmlEntities(s);
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

