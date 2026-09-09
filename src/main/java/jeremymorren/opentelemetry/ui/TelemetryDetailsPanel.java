package jeremymorren.opentelemetry.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import jeremymorren.opentelemetry.ui.components.DetailsPanel;
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
 * Builds the Formatted tab: a summary header, then collapsible sections of key/value rows which the
 * container packs into columns when there is width for them.
 *
 * <p>The key column is sized to the widest key of the whole render, so keys line up across sections.
 */
public final class TelemetryDetailsPanel {
    private static final int MAX_KEY_WIDTH = 240;
    private static final int ROW_INDENT = 12;

    /** Enough for the 16px icon buttons a row shows on hover. */
    private static final int ROW_MIN_HEIGHT = 18;

    @NotNull
    private final DetailsPanel container;
    @NotNull
    private final Consumer<String> onFilter;

    /** Sections the user collapsed, remembered so selecting another row does not reopen them. */
    @NotNull
    private final Set<String> collapsed = new HashSet<>();

    private final List<JLabel> keyLabels = new ArrayList<>();
    private Section section;

    public TelemetryDetailsPanel(@NotNull DetailsPanel container, @NotNull Consumer<String> onFilter) {
        this.container = container;
        this.onFilter = onFilter;
    }

    /** Starts a render; everything added afterwards replaces what was on screen. */
    public void begin() {
        container.removeAll();
        keyLabels.clear();
        section = null;
    }

    /** Finishes a render, sizing the key column to the widest key so the values line up. */
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

        container.revalidate();
        container.repaint();
    }

    /** The line at the top of the pane: what this telemetry is, in the terms of whatever it is. */
    public void header(@NotNull String title, @Nullable String subtitle, @Nullable Color titleColor) {
        JPanel header = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(4));

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

        section = null;
        container.add(header, DetailsPanel.FULL_WIDTH);
    }

    /** Opens a collapsible section. Rows added afterwards belong to it until the next section starts. */
    public void section(@NotNull String title) {
        section = new Section(title, !collapsed.contains(title));
        container.add(section.block, DetailsPanel.SECTION);
    }

    /** Adds a key/value row: dimmed key, selectable value, and copy/filter buttons on hover. */
    public void row(@Nullable String key, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String text = value.replace("\r", "").replace("\n", " ");

        JPanel rowPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        rowPanel.setOpaque(false);
        rowPanel.setBorder(JBUI.Borders.empty(0, ROW_INDENT, 0, 0));

        JLabel keyLabel = new JLabel(key == null ? "" : key);
        keyLabel.setForeground(UIUtil.getContextHelpForeground());
        keyLabel.setToolTipText(key);
        keyLabel.setVerticalAlignment(SwingConstants.TOP);
        keyLabels.add(keyLabel);
        rowPanel.add(keyLabel, BorderLayout.WEST);

        JComponent valueComponent = value(text);
        rowPanel.add(valueComponent, BorderLayout.CENTER);
        JComponent actions = actions(value);
        // Pinned to the row's height so revealing the buttons on hover cannot make the row jump.
        actions.setPreferredSize(new Dimension(
                actions.getPreferredSize().width, valueComponent.getPreferredSize().height));
        rowPanel.add(actions, BorderLayout.EAST);
        installHover(rowPanel, actions);

        add(rowPanel);
    }

    /** Adds a component that spans the section's width, such as a timing bar. */
    public void component(@NotNull JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(1, ROW_INDENT, 1, 0));
        wrapper.add(component, BorderLayout.CENTER);
        add(wrapper);
    }

    private void add(@NotNull JComponent component) {
        if (section == null) {
            container.add(component, DetailsPanel.FULL_WIDTH);
            return;
        }
        section.add(component);
    }

    /** A selectable, transparent, read only view of the value. */
    @NotNull
    private static JComponent value(@NotNull String text) {
        JTextField field = new JTextField(text);
        field.setEditable(false);
        field.setOpaque(false);
        field.setBorder(JBUI.Borders.empty());
        field.setForeground(UIUtil.getLabelForeground());
        field.setCaretPosition(0);
        field.setToolTipText(text);
        field.setMargin(JBUI.emptyInsets());
        // A text field is sized for a form control - around 28px tall - which is far more than a line of
        // text needs, and these rows are a dense list rather than a form. One line height it is, with
        // room for the row's icon buttons.
        int height = Math.max(field.getFontMetrics(field.getFont()).getHeight(), JBUI.scale(ROW_MIN_HEIGHT));
        // A long value must not widen the pane; the row stretches it to whatever width is going.
        field.setPreferredSize(new Dimension(JBUI.scale(120), height));
        field.setMinimumSize(new Dimension(0, height));
        return field;
    }

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

    /** Reveals the row's buttons while the pointer is anywhere over the row, buttons included. */
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
     * A section: a header that shows and hides the rows stacked underneath it. The whole thing is one
     * component, so the container can move it between columns as a unit.
     */
    private final class Section {
        private final String title;
        private final JPanel block = new JPanel(new GridBagLayout());
        private final List<JComponent> rows = new ArrayList<>();
        private final JLabel label;
        private boolean expanded;
        private int row;

        private Section(@NotNull String title, boolean expanded) {
            this.title = title;
            this.expanded = expanded;

            block.setOpaque(false);
            block.setBorder(JBUI.Borders.emptyBottom(6));

            label = new JLabel(title, icon(), SwingConstants.LEADING);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setIconTextGap(JBUI.scale(4));

            JPanel header = new JPanel(new BorderLayout(JBUI.scale(8), 0));
            header.setOpaque(false);
            header.setBorder(JBUI.Borders.empty(4, 0, 1, 0));
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.add(label, BorderLayout.WEST);
            header.add(separator(), BorderLayout.CENTER);

            MouseAdapter toggle = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle();
                }
            };
            header.addMouseListener(toggle);
            label.addMouseListener(toggle);

            block.add(header, constraints(row++));
        }

        private void add(@NotNull JComponent component) {
            rows.add(component);
            component.setVisible(expanded);
            block.add(component, constraints(row++));
        }

        private void toggle() {
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
