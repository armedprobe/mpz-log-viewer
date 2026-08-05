package com.mpzlog.gui;

import com.mpzlog.model.ProcessElement;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public final class IconFactory {

    private static final Color ICON_COLOR = Color.web("#3C3C3C");

    private IconFactory() {
    }

    public static double measureTextWidth(String text, Font font) {
        Text t = new Text(text);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    public static Region iconIn(Node shape) {
        StackPane pane = new StackPane(shape);
        pane.setPrefSize(16, 16);
        pane.setMinSize(16, 16);
        return pane;
    }

    public static Node statusIcon(ProcessElement p) {
        Color color;
        switch (p.getStatus()) {
            case COMPLETED:
                color = Color.GREEN;
                break;
            case COMPLETED_WITH_ERROR:
                color = Color.YELLOW;
                break;
            case INTERRUPTED:
                color = Color.RED;
                break;
            case FAILED:
                color = Color.BLACK;
                break;
            case UNRESOLVED:
            default:
                color = Color.DARKGREY;
                break;
        }
        Circle c = new Circle(5.0, color);
        return iconIn(c);
    }

    public static Image createAppIcon() {
        WritableImage img = new WritableImage(32, 32);
        Canvas canvas = new Canvas(32, 32);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#2F5D8A"));
        gc.fillRoundRect(0, 0, 32, 32, 7, 7);
        gc.setFill(Color.web("#E8F1F8"));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 17));
        gc.fillText("MP", 7, 23);
        canvas.snapshot(null, img);
        return img;
    }

}
