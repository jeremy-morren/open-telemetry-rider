package jeremymorren.opentelemetry.ui.components;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import jeremymorren.opentelemetry.util.DurationFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A proportional breakdown of how a span spent its time, with a legend and event markers.
 *
 * <p>Used for database dependencies, where the span records not just how long the whole call took but
 * when the first row came back - the split between waiting for the server and reading the results is
 * usually the interesting part.
 */
public final class TimingBar extends JPanel {
    private static final int BAR_HEIGHT = 10;
    private static final int SWATCH_SIZE = 9;

    /** One span of time within the bar. */
    public record Segment(@NotNull String name, @NotNull Duration duration, @NotNull Color color,
                          @Nullable String description) {
        public Segment(@NotNull String name, @NotNull Duration duration, @NotNull Color color) {
            this(name, duration, color, null);
        }
    }

    /** A point in time within the bar, drawn as a tick. */
    public record Marker(@NotNull String name, @NotNull Duration offset) {
    }

    public TimingBar(@NotNull Duration total, @NotNull List<Segment> segments, @NotNull List<Marker> markers) {
        super(new BorderLayout(0, JBUI.scale(4)));
        setOpaque(false);
        setBorder(JBUI.Borders.empty(2, 0, 4, 0));

        add(new BarCanvas(total, segments, markers), BorderLayout.NORTH);
        add(legend(total, segments, markers), BorderLayout.CENTER);
    }

    @NotNull
    private static JComponent legend(@NotNull Duration total,
                                     @NotNull List<Segment> segments,
                                     @NotNull List<Marker> markers) {
        JPanel legend = new JPanel();
        legend.setOpaque(false);
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));

        for (Segment segment : segments) {
            String text = DurationFormatter.Companion.format(segment.duration()) + percentage(segment.duration(), total);
            JLabel label = entry(new SwatchIcon(segment.color()), segment.name() + ": " + text);
            if (segment.description() != null) {
                label.setToolTipText(segment.description());
            }
            legend.add(label);
        }
        for (Marker marker : markers) {
            legend.add(entry(new SwatchIcon(markerColor()),
                    marker.name() + " at " + DurationFormatter.Companion.format(marker.offset())
                            + percentage(marker.offset(), total)));
        }

        return legend;
    }

    @NotNull
    private static JLabel entry(@NotNull Icon icon, @NotNull String text) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEADING);
        label.setIconTextGap(JBUI.scale(6));
        label.setFont(UIUtil.getFont(UIUtil.FontSize.SMALL, label.getFont()));
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    @NotNull
    private static String percentage(@NotNull Duration part, @NotNull Duration total) {
        long totalNanos = total.toNanos();
        if (totalNanos <= 0) {
            return "";
        }
        return String.format(" (%.0f%%)", 100.0 * part.toNanos() / totalNanos);
    }

    @NotNull
    private static Color markerColor() {
        return JBColor.namedColor("OpenTelemetry.Timing.Marker", new JBColor(0x8C8C8C, 0x9C9C9C));
    }

    /** The bar itself: segments end to end, with markers drawn over them. */
    private static final class BarCanvas extends JComponent {
        private final Duration total;
        private final List<Segment> segments;
        private final List<Marker> markers;

        private BarCanvas(Duration total, List<Segment> segments, List<Marker> markers) {
            this.total = total;
            this.segments = segments;
            this.markers = markers;
            setToolTipText(tooltip());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(JBUI.scale(120), JBUI.scale(BAR_HEIGHT));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, JBUI.scale(BAR_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = JBUI.scale(BAR_HEIGHT);
                int arc = JBUI.scale(3);
                long totalNanos = Math.max(total.toNanos(), 1);

                g2.setColor(UIUtil.getPanelBackground().darker());
                g2.fill(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));

                float x = 0;
                for (Segment segment : segments) {
                    float segmentWidth = width * (segment.duration().toNanos() / (float) totalNanos);
                    g2.setColor(segment.color());
                    g2.fill(new RoundRectangle2D.Float(x, 0, Math.max(segmentWidth, 1), height, arc, arc));
                    x += segmentWidth;
                }

                g2.setColor(markerColor());
                for (Marker marker : markers) {
                    int markerX = Math.round(width * (marker.offset().toNanos() / (float) totalNanos));
                    markerX = Math.min(Math.max(markerX, 0), width - 1);
                    g2.fillRect(markerX, 0, JBUI.scale(1), height);
                }
            } finally {
                g2.dispose();
            }
        }

        @NotNull
        private String tooltip() {
            StringBuilder tooltip = new StringBuilder("<html>Total: ")
                    .append(DurationFormatter.Companion.format(total));
            for (Segment segment : segments) {
                tooltip.append("<br>").append(segment.name()).append(": ")
                        .append(DurationFormatter.Companion.format(segment.duration()));
            }
            for (Marker marker : markers) {
                tooltip.append("<br>").append(marker.name()).append(" at ")
                        .append(DurationFormatter.Companion.format(marker.offset()));
            }
            return tooltip.append("</html>").toString();
        }
    }

    /** A small filled square used to tie a legend entry to its segment. */
    private record SwatchIcon(Color color) implements Icon {
        @Override
        public void paintIcon(Component component, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, getIconWidth(), getIconHeight());
        }

        @Override
        public int getIconWidth() {
            return JBUI.scale(SWATCH_SIZE);
        }

        @Override
        public int getIconHeight() {
            return JBUI.scale(SWATCH_SIZE);
        }
    }

    /** Colours used for the database breakdown. */
    public static final class Colors {
        public static final Color QUERY = JBColor.namedColor("OpenTelemetry.Timing.Query", new JBColor(0x3592C4, 0x3592C4));
        public static final Color READ = JBColor.namedColor("OpenTelemetry.Timing.Read", new JBColor(0x499C54, 0x57965C));
        public static final Color OTHER = JBColor.namedColor("OpenTelemetry.Timing.Other", new JBColor(0xC4C4C4, 0x6E6E6E));

        private Colors() {
        }

        /** Segments that make up a list of durations, padded with "other" when they fall short. */
        @NotNull
        public static List<Segment> pad(@NotNull List<Segment> segments, @NotNull Duration total) {
            Duration accounted = Duration.ZERO;
            for (Segment segment : segments) {
                accounted = accounted.plus(segment.duration());
            }
            Duration remainder = total.minus(accounted);
            if (remainder.isNegative() || remainder.isZero()) {
                return segments;
            }

            List<Segment> padded = new ArrayList<>(segments);
            padded.add(new Segment("Other", remainder, OTHER, "Time not attributed to a recorded stage"));
            return padded;
        }
    }
}
