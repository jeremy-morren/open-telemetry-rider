package jeremymorren.opentelemetry.ui.components;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

public class ColorBox extends JPanel {
    public ColorBox() {
        this(JBColor.blue);
    }

    public ColorBox(Color color) {
        setOpaque(true);
        setBackground(color);
    }
}
