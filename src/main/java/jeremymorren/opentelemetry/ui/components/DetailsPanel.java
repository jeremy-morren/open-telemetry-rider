package jeremymorren.opentelemetry.ui.components;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The container behind the Formatted tab.
 *
 * <p>Sections are laid out in as many columns as the pane is wide enough for, so a widescreen shows
 * Overview, Request, Response and Tags side by side rather than as one long scroll. Components added
 * with {@link #FULL_WIDTH} - the summary header - always span the whole pane above the columns.
 */
public final class DetailsPanel extends JPanel implements Scrollable {
    /** Spans the whole pane, above the columns. */
    public static final String FULL_WIDTH = "full-width";

    /** A section: placed into whichever column is currently shortest. */
    public static final String SECTION = "section";

    public DetailsPanel() {
        super(new SectionColumnsLayout());
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return JBUI.scale(12);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    /** The pane fills the viewport, which is what lets the columns know how much room they have. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /**
     * Packs sections into balanced columns, and stacks full width components above them.
     */
    private static final class SectionColumnsLayout implements LayoutManager2 {
        private static final int MIN_COLUMN_WIDTH = 380;
        private static final int MAX_COLUMNS = 4;
        private static final int COLUMN_GAP = 20;

        private final List<Component> fullWidth = new ArrayList<>();
        private final List<Component> sections = new ArrayList<>();

        @Override
        public void addLayoutComponent(Component component, Object constraints) {
            (SECTION.equals(constraints) ? sections : fullWidth).add(component);
        }

        @Override
        public void addLayoutComponent(String name, Component component) {
            addLayoutComponent(component, name);
        }

        @Override
        public void removeLayoutComponent(Component component) {
            fullWidth.remove(component);
            sections.remove(component);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int available = Math.max(parent.getWidth() - insets.left - insets.right, JBUI.scale(MIN_COLUMN_WIDTH));
                int[] heights = new int[columnCount(available)];

                int height = 0;
                for (Component component : visible(fullWidth)) {
                    height += component.getPreferredSize().height;
                }
                for (Component component : visible(sections)) {
                    int shortest = shortestColumn(heights);
                    heights[shortest] += component.getPreferredSize().height;
                }

                int tallest = 0;
                for (int columnHeight : heights) {
                    tallest = Math.max(tallest, columnHeight);
                }
                return new Dimension(
                        insets.left + insets.right + JBUI.scale(MIN_COLUMN_WIDTH),
                        insets.top + insets.bottom + height + tallest);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public Dimension maximumLayoutSize(Container target) {
            return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int left = insets.left;
                int available = parent.getWidth() - insets.left - insets.right;
                if (available <= 0) {
                    return;
                }

                int y = insets.top;
                for (Component component : visible(fullWidth)) {
                    int height = component.getPreferredSize().height;
                    component.setBounds(left, y, available, height);
                    y += height;
                }

                int columns = columnCount(available);
                int gap = JBUI.scale(COLUMN_GAP);
                int columnWidth = (available - gap * (columns - 1)) / columns;
                int[] heights = new int[columns];

                for (Component component : visible(sections)) {
                    int column = shortestColumn(heights);
                    int height = component.getPreferredSize().height;
                    component.setBounds(left + column * (columnWidth + gap), y + heights[column], columnWidth, height);
                    heights[column] += height;
                }
            }
        }

        @Override
        public void invalidateLayout(Container target) {
        }

        @Override
        public float getLayoutAlignmentX(Container target) {
            return 0;
        }

        @Override
        public float getLayoutAlignmentY(Container target) {
            return 0;
        }

        private static int columnCount(int available) {
            int columns = available / JBUI.scale(MIN_COLUMN_WIDTH);
            return Math.min(Math.max(columns, 1), MAX_COLUMNS);
        }

        private static int shortestColumn(int[] heights) {
            int shortest = 0;
            for (int i = 1; i < heights.length; i++) {
                if (heights[i] < heights[shortest]) {
                    shortest = i;
                }
            }
            return shortest;
        }

        @NotNull
        private static List<Component> visible(@NotNull List<Component> components) {
            List<Component> result = new ArrayList<>(components.size());
            for (Component component : components) {
                if (component.isVisible()) {
                    result.add(component);
                }
            }
            return result;
        }
    }

    /**
     * Adds a component, defaulting to a full width one.
     */
    public void add(@NotNull Component component, @Nullable String constraint) {
        super.add(component, constraint == null ? FULL_WIDTH : constraint);
    }
}
