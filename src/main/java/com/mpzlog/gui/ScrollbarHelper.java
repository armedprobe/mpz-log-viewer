package com.mpzlog.gui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;

public final class ScrollbarHelper {

    private ScrollbarHelper() {
    }

    public static void keepHorizontalScrollbarVisible(Node root) {
        for (Node node : root.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar) {
                ScrollBar sb = (ScrollBar) node;
                if (sb.getOrientation() == Orientation.HORIZONTAL) {
                    configure(sb);
                    return;
                }
            }
        }
    }

    private static void configure(ScrollBar hbar) {
        hbar.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (!isVisible) {
                hbar.setVisible(true);
            }
        });
        hbar.setVisible(true);

        hbar.maxProperty().addListener(o -> updateDisable(hbar));
        hbar.visibleAmountProperty().addListener(o -> updateDisable(hbar));
        updateDisable(hbar);
    }

    private static void updateDisable(ScrollBar hbar) {
        hbar.setDisable(hbar.getMax() - hbar.getVisibleAmount() <= 0.5);
    }

}
