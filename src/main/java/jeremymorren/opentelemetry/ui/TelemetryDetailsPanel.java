package jeremymorren.opentelemetry.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds the Formatted tab: a summary header followed by collapsible sections of key/value rows.
 *
 * <p>Rows are laid out one per grid line rather than as a two column grid, and the key column is sized
 * to the widest key of the whole render, so keys line up across every section.
 */
public final class TelemetryDetailsPanel {
    private static final int MAX_KEY_WIDTH = 240;
    private static final int SECTION_INDENT = 12;

    @NotNull
    private final JPanel container;
    @NotNull
    private final Consumer<String> onFilter;

    /** Sections the user collapsed, remembered so selecting another row does not reopen them. */
    @NotNull
    private final Set<String> collapsed = new HashSet<>();

    private final List<JLabel> keyLabels = new ArrayList<>();
    private Section section;
    private int row;

    public TelemetryDetailsPanel(@NotNull JPanel container, @NotNull Consumer<String> onFilter) {
        this.container = container;
        this.onFilter = onFilter;
    }

    /**
     * Starts a new render. Everything added afterwards replaces what was on screen.
     */
    public void begin() {
        container.removeAll();
        keyLabels.clear();
        section = null;
        row = 0;
    }

    /**
     * Finishes a render, sizing the key column to the widest key so the values line up.
     */
    public void end() {
        int width = 0;
        for (JLabel key : keyLabels) {
            width = Math.max(width, key.getPreferredSize().width);
        }
        width = Math.min(width, JBUI.scale(MAX_KEY_WIDTH));
        for (JLabel key : keyLabels) {
            Dimension size = new Dimension(width, key.getPreferredSize().height);
            key.setPreferredSize(size);
            key.setMinimumSize(size);
        }

        GridBagConstraints filler = constraints(row);
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        container.add(new JPanel() {{ setOpaque(false); }}, filler);

        container.revalidate();
        container.repaint();
    }

    /**
     * The line at the top of the pane: what this telemetry is, in the terms of whatever it is.
     */
    public void header(@NotNull String title, @Nullable String subtitle, @Nullable Color titleColor) {
        JPanel header = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(6));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 1f));
        if (titleColor != null) {
            titleLabel.setForeground(titleColor);
        }
        titleLabel.setToolTipText(title);
        header.add(titleLabel, BorderLayout.CENTER);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setForeground(UIUtil.getContextHelpForeground());
            header.add(subtitleLabel, BorderLayout.EAST);
        }

        add(header);
    }

    /**
     * Opens a collapsible section. Rows added afterwards belong to it until the next section starts.
     */
    public void section(@NotNull String title) {
        Section opened = new Section(title, !collapsed.contains(title));
        section = opened;
        add(opened.header);
    }

    /**
     * Adds a key/value row: dimmed key, selectable value, and copy/filter buttons on hover.
     */
    public void row(@Nullable String key, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String text = value.replace("\r", "").replace("\n", " ");

        JPanel rowPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        rowPanel.setOpaque(false);
        rowPanel.setBorder(JBUI.Borders.empty(1, SECTION_INDENT, 1, 0));

        JLabel keyLabel = new JLabel(key == null ? "" : key);
        keyLabel.setForeground(UIUtil.getContextHelpForeground());
        keyLabel.setToolTipText(key);
        keyLabel.setVerticalAlignment(SwingConstants.TOP);
        keyLabels.add(keyLabel);
        rowPanel.add(keyLabel, BorderLayout.WEST);

        rowPanel.add(value(text), BorderLayout.CENTER);
        JComponent actions = actions(value);
        rowPanel.add(actions, BorderLayout.EAST);
        installHover(rowPanel, actions);

        add(rowPanel);
    }

    /**
     * Adds a component that spans the whole width, such as a timing bar.
     */
    public void component(@NotNull JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(2, SECTION_INDENT, 2, 0));
        wrapper.add(component, BorderLayout.CENTER);
        add(wrapper);
    }

    /**
     * A selectable, transparent, read only view of the value.
     */
    @NotNull
    private static JComponent value(@NotNull String text) {
        JTextField field = new JTextField(text);
        field.setEditable(false);
        field.setOpaque(false);
        field.setBorder(JBUI.Borders.empty());
        field.setForeground(UIUtil.getLabelForeground());
        field.setCaretPosition(0);
        field.setToolTipText(text);
        // A long value must not widen the whole pane; the row stretches it to whatever width is going.
        field.setPreferredSize(new Dimension(JBUI.scale(200), field.getPreferredSize().height));
        field.setMinimumSize(new Dimension(0, field.getPreferredSize().height));
        return field;
    }

    /**
     * Copy and filter buttons, revealed while the pointer is over the row.
     */
    @NotNull
    private JComponent actions(@NotNull String value) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
        actions.setOpaque(false);
        actions.setVisible(false);

        actions.add(new InplaceButton("Copy value", AllIcons.Actions.Copy,
                event -> CopyPasteManager.getInstance().setContents(new StringSelection(value))));
        actions.add(new InplaceButton("Filter by this value", AllIcons.General.Filter,
                event -> onFilter.accept(value)));
        return actions;
    }

    /**
     * Reveals the row's buttons while the pointer is anywhere over the row, buttons included.
     */
    private static void installHover(@NotNull JPanel rowPanel, @NotNull JComponent actions) {
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                actions.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // getMousePosition covers the whole row, so moving onto a button does not hide it.
                if (rowPanel.getMousePosition(true) == null) {
                    actions.setVisible(false);
                }
            }
        };
        addListener(rowPanel, hover);
    }

    private static void addListener(@NotNull Container container, @NotNull MouseAdapter hover) {
        container.addMouseListener(hover);
        for (Component child : container.getComponents()) {
            child.addMouseListener(hover);
            if (child instanceof Container nested) {
                addListener(nested, hover);
            }
        }
    }

    private void add(@NotNull JComponent component) {
        container.add(component, constraints(row++));
        if (section != null) {
            section.register(component);
        }
    }

    @NotNull
    private static GridBagConstraints constraints(int gridY) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridY;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    /**
     * A section header that shows and hides the rows added after it.
     */
    private final class Section {
        private final String title;
        private final JPanel header;
        private final List<JComponent> rows = new ArrayList<>();
        private boolean expanded;

        private Section(@NotNull String title, boolean expanded) {
            this.title = title;
            this.expanded = expanded;

            JLabel label = new JLabel(title, icon(), SwingConstants.LEADING);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setIconTextGap(JBUI.scale(4));

            header = new JPanel(new BorderLayout(JBUI.scale(8), 0));
            header.setOpaque(false);
            header.setBorder(JBUI.Borders.empty(6, 0, 2, 0));
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.add(label, BorderLayout.WEST);
            header.add(separator(), BorderLayout.CENTER);
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle(label);
                }
            });
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle(label);
                }
            });
        }

        private void register(@NotNull JComponent component) {
            if (component == header) {
                return;
            }
            rows.add(component);
            component.setVisible(expanded);
        }

        private void toggle(@NotNull JLabel label) {
            expanded = !expanded;
            if (expanded) {
                collapsed.remove(title);
            } else {
                collapsed.add(title);
            }
            label.setIcon(icon());
            for (JComponent component : rows) {
                component.setVisible(expanded);
            }
            container.revalidate();
            container.repaint();
        }

        @NotNull
        private Icon icon() {
            return expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight;
        }

        @NotNull
        private JComponent separator() {
            JPanel line = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(JBColor.namedColor("Group.separatorColor", JBColor.border()));
                    g.fillRect(0, getHeight() / 2, getWidth(), JBUI.scale(1));
                }
            };
            line.setOpaque(false);
            return line;
        }
    }
}
