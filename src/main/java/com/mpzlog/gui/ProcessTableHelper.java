package com.mpzlog.gui;

import com.mpzlog.model.LogLine;
import com.mpzlog.model.ProcessElement;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;

import java.util.List;
import java.util.function.Consumer;

public final class ProcessTableHelper {

    private static final PseudoClass ACTIVE_PROCESS = PseudoClass.getPseudoClass("active-process");

    private ProcessTableHelper() {
    }

    public static TableView<ProcessElement> createTable(
            ViewController vc,
            Consumer<ProcessElement> onSingleClick,
            Consumer<ProcessElement> onDoubleClick,
            List<ProcessElement> originalOrder) {

        TableView<ProcessElement> table = new TableView<>();
        table.getStyleClass().add("process-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<ProcessElement, ProcessElement> iconCol = createIconColumn();
        TableColumn<ProcessElement, ProcessElement> pidCol = createPidColumn();
        TableColumn<ProcessElement, ProcessElement> nameCol = createNameColumn();
        TableColumn<ProcessElement, ProcessElement> procCountCol = createCountColumn();

        table.getColumns().add(iconCol);
        table.getColumns().add(pidCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(procCountCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        SortResetHelper.setupSortReset(table, originalOrder);

        table.setRowFactory(tv -> {
            TableRow<ProcessElement> row = new TableRow<>();
            vc.selectedProcessProperty().addListener((obs, oldP, newP) -> {
                ProcessElement item = row.getItem();
                if (item != null && (item == oldP || item == newP)) {
                    row.pseudoClassStateChanged(ACTIVE_PROCESS, item == newP);
                }
            });
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                row.pseudoClassStateChanged(ACTIVE_PROCESS, newItem != null && vc.isProcessSelected(newItem));
            });
            row.setOnMouseClicked(e -> {
                ProcessElement item = row.getItem();
                if (item == null) {
                    return;
                }
                if (e.getClickCount() == 2) {
                    onDoubleClick.accept(item);
                } else if (e.getClickCount() == 1) {
                    onSingleClick.accept(item);
                }
            });
            return row;
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Копировать PID");
        copyItem.setOnAction(e -> {
            ProcessElement sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                ClipboardUtil.copyText(sel.pidLabel());
            }
        });
        contextMenu.getItems().add(copyItem);
        table.setContextMenu(contextMenu);

        table.setPlaceholder(new Label("Нет процессов для отображения"));

        return table;
    }

    public static int reqRespCount(ProcessElement p) {
        int count = 0;
        for (LogLine line : p.getLines()) {
            if (line.isRequest() || line.isResponse()) {
                count++;
            }
        }
        return count;
    }

    private static TableColumn<ProcessElement, ProcessElement> createIconColumn() {
        TableColumn<ProcessElement, ProcessElement> col = new TableColumn<>("");
        col.setMaxWidth(26);
        col.setMinWidth(26);
        col.setSortable(false);
        col.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        col.setCellFactory(colFactory -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                setGraphic(empty || p == null ? null : IconFactory.statusIcon(p));
            }
        });
        return col;
    }

    private static TableColumn<ProcessElement, ProcessElement> createPidColumn() {
        double pidWidth = IconFactory.measureTextWidth("88888888",
                Font.font("Consolas", FontPosture.REGULAR, 11)) + 18;
        TableColumn<ProcessElement, ProcessElement> col = new TableColumn<>("PID");
        col.setPrefWidth(pidWidth);
        col.setMinWidth(pidWidth);
        col.setMaxWidth(pidWidth);
        col.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        col.setCellFactory(colFactory -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setFont(Font.font("Consolas", FontPosture.REGULAR, 11));
                    setText(p.pidLabel());
                    setTooltip(new Tooltip(p.getStatus().getLabel()));
                }
            }
        });
        col.setComparator((a, b) -> {
            String pa = a.getPid();
            String pb = b.getPid();
            if (pa == null && pb == null) return 0;
            if (pa == null) return -1;
            if (pb == null) return 1;
            return Long.compare(Long.parseLong(pa), Long.parseLong(pb));
        });
        return col;
    }

    private static TableColumn<ProcessElement, ProcessElement> createNameColumn() {
        TableColumn<ProcessElement, ProcessElement> col = new TableColumn<>("Имя процесса");
        col.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        col.setCellFactory(colFactory -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                } else {
                    setFont(Font.font(11));
                    String pname = p.getProcessName();
                    setText(pname != null ? pname : "?");
                }
            }
        });
        col.setComparator((a, b) -> {
            String na = a.getProcessName();
            String nb = b.getProcessName();
            if (na == null && nb == null) return 0;
            if (na == null) return 1;
            if (nb == null) return -1;
            return na.compareToIgnoreCase(nb);
        });
        return col;
    }

    private static TableColumn<ProcessElement, ProcessElement> createCountColumn() {
        TableColumn<ProcessElement, ProcessElement> col = new TableColumn<>("Запросов/ответов");
        col.setPrefWidth(52);
        col.setMinWidth(52);
        col.setMaxWidth(52);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        col.setCellFactory(colFactory -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                setAlignment(Pos.CENTER_RIGHT);
                setText(empty || p == null ? null : String.valueOf(reqRespCount(p)));
            }
        });
        col.setComparator((a, b) ->
                Integer.compare(reqRespCount(a), reqRespCount(b)));
        return col;
    }

}
