package com.mpzlog.gui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public final class SortResetHelper {

    private SortResetHelper() {
    }

    public static <T> void setupSortReset(TableView<T> table, List<T> originalOrderRef) {
        table.sortPolicyProperty().set(t -> {
            if (t.getSortOrder().isEmpty()) {
                t.getItems().setAll(originalOrderRef);
                return true;
            }
            return TableView.DEFAULT_SORT_POLICY.call(t);
        });
        for (TableColumn<T, ?> col : table.getColumns()) {
            if (!col.isSortable()) {
                continue;
            }
            col.sortTypeProperty().addListener((obs, oldType, newType) -> {
                if (oldType == TableColumn.SortType.DESCENDING
                        && newType == TableColumn.SortType.ASCENDING) {
                    Platform.runLater(() -> table.getSortOrder().clear());
                }
            });
        }
    }

}
