package jeremymorren.opentelemetry.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;

import javax.swing.*;
import java.awt.event.KeyListener;

public class ClearTextFieldExtension implements ExtendableTextComponent.Extension {
    private final ExtendableTextField textField;

    public ClearTextFieldExtension(ExtendableTextField textField) {
        this.textField = textField;
    }

    @Override
    public Icon getIcon(boolean hovered) {
        return hovered ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close;
    }

    @Override
    public String getTooltip() {
        return "Clear";
    }

    @Override
    public Runnable getActionOnClick() {
        return () -> {
            if (!textField.getText().isEmpty()) {
                textField.setText(null);
            }
            // Trigger key events to update the UI
            for (KeyListener keyListener : textField.getKeyListeners()) {
                keyListener.keyTyped(null);
                keyListener.keyReleased(null);
            }
        };
    }
}
