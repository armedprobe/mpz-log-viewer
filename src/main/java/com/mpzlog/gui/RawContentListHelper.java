package com.mpzlog.gui;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;

import java.util.List;
import java.util.function.Consumer;

public final class RawContentListHelper {

    private RawContentListHelper() {
    }

    public static ListView<String> createList(
            Runnable onCopy,
            Runnable onGoTo,
            Consumer<ListView<String>> onSkinReady) {

        ListView<String> list = new ListView<>();
        list.setEditable(false);
        list.setFixedCellSize(GuiConstants.CELL_HEIGHT);
        list.getStyleClass().add("raw-content-list");
        list.setCellFactory(lv -> new ListCell<String>() {
            {
                setFont(GuiConstants.MONO_FONT);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        list.setPlaceholder(new Label("Содержимое файла будет отображено здесь после открытия"));

        ContextMenu menu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(e -> onCopy.run());
        menu.getItems().add(copyItem);
        MenuItem goToItem = new MenuItem("Перейти");
        goToItem.setOnAction(e -> onGoTo.run());
        menu.getItems().add(goToItem);
        list.setContextMenu(menu);

        list.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> onSkinReady.accept(list));
            }
        });

        return list;
    }

    public static void showGoToLineDialog(ListView<String> rawContentList, List<String> allRawLines) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Перейти к строке");
        dialog.setHeaderText(null);
        dialog.setContentText("Номер строки:");
        String result = dialog.showAndWait().orElse(null);
        if (result == null) {
            return;
        }
        try {
            int line = Integer.parseInt(result.trim());
            if (line < 1 || line > allRawLines.size()) {
                return;
            }
            String target = allRawLines.get(line - 1);
            int index = rawContentList.getItems().indexOf(target);
            if (index < 0) {
                rawContentList.scrollTo(line - 1);
            } else {
                rawContentList.getSelectionModel().clearSelection();
                rawContentList.getSelectionModel().select(index);
                rawContentList.scrollTo(index);
            }
        } catch (NumberFormatException ignored) {
        }
    }

}
