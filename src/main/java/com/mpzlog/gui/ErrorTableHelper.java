package com.mpzlog.gui;

import com.mpzlog.model.ErrorGroupInfo;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

import java.util.List;
import java.util.function.Consumer;

public final class ErrorTableHelper {

    private static final PseudoClass ACTIVE_ERROR = PseudoClass.getPseudoClass("active-error");

    private ErrorTableHelper() {
    }

    public static TableView<ErrorGroupInfo> createTable(
            ViewController vc,
            Consumer<ErrorGroupInfo> onDoubleClick,
            List<ErrorGroupInfo> originalOrder) {

        TableView<ErrorGroupInfo> table = new TableView<>();
        table.getStyleClass().add("error-table");
        table.setPlaceholder(new Label("Ошибки не найдены"));

        TableColumn<ErrorGroupInfo, Number> errCountCol = new TableColumn<>("Число ошибок");
        errCountCol.setPrefWidth(100);
        errCountCol.setMaxWidth(100);
        errCountCol.setResizable(false);
        errCountCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getCount()));
        errCountCol.setCellFactory(col -> new TableCell<ErrorGroupInfo, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                setText(empty || item == null ? null : String.valueOf(item.intValue()));
            }
        });
        errCountCol.setComparator((a, b) -> Integer.compare(a.intValue(), b.intValue()));

        TableColumn<ErrorGroupInfo, String> errKeyCol = new TableColumn<>("Ошибка");
        errKeyCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getErrorKey()));
        errKeyCol.setCellFactory(col -> new TableCell<ErrorGroupInfo, String>() {
            private Text text;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setPrefHeight(USE_COMPUTED_SIZE);
                } else {
                    if (text == null) {
                        text = new Text();
                        text.wrappingWidthProperty().bind(col.widthProperty().subtract(6));
                    }
                    text.setText(item);
                    setGraphic(text);
                    Platform.runLater(() ->
                            setPrefHeight(text.getLayoutBounds().getHeight() + 8));
                }
            }
        });
        errKeyCol.setComparator(String::compareToIgnoreCase);

        table.getColumns().add(errCountCol);
        table.getColumns().add(errKeyCol);
        errKeyCol.prefWidthProperty().bind(table.widthProperty().subtract(errCountCol.getPrefWidth() + 4));

        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Копировать ошибку");
        copyItem.setOnAction(e -> {
            ErrorGroupInfo sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                ClipboardUtil.copyText(sel.getErrorKey());
            }
        });
        contextMenu.getItems().add(copyItem);
        table.setContextMenu(contextMenu);

        table.setRowFactory(tv -> {
            TableRow<ErrorGroupInfo> row = new TableRow<>();
            vc.selectedErrorKeyProperty().addListener((obs, oldKey, newKey) -> {
                ErrorGroupInfo item = row.getItem();
                if (item != null && (item.getErrorKey().equals(oldKey) || item.getErrorKey().equals(newKey))) {
                    row.pseudoClassStateChanged(ACTIVE_ERROR, item.getErrorKey().equals(newKey));
                }
            });
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    row.pseudoClassStateChanged(ACTIVE_ERROR, newItem.getErrorKey().equals(vc.getSelectedErrorKey()));
                } else {
                    row.pseudoClassStateChanged(ACTIVE_ERROR, false);
                }
            });
            row.setOnMouseClicked(e -> {
                ErrorGroupInfo item = row.getItem();
                if (item == null) {
                    return;
                }
                if (e.getClickCount() == 2) {
                    onDoubleClick.accept(item);
                }
            });
            return row;
        });

        SortResetHelper.setupSortReset(table, originalOrder);

        return table;
    }

}
