package jeremymorren.opentelemetry.ui.components;

import javax.swing.*;
import java.awt.*;

/** A block of solid color: the swatch next to each telemetry type filter. */
public class ColorBox extends JPanel {
    public ColorBox(Color color) {
        setOpaque(true);
        setBackground(color);
    }
}
