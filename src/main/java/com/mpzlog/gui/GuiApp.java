package com.mpzlog.gui;

import com.mpzlog.model.ErrorElement;
import com.mpzlog.model.LogEntry;
import com.mpzlog.model.LogLine;
import com.mpzlog.model.LogModel;
import com.mpzlog.model.LogModelBuilder;
import com.mpzlog.model.ProcessElement;
import com.mpzlog.parser.MpzLogParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GuiApp extends Application {

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final double LEFT_PANEL_RATIO = 0.20;
    private static final double BOTTOM_PANEL_RATIO = 0.25;
    private static final Color ICON_COLOR = Color.web("#3C3C3C");
    private static final PseudoClass ACTIVE_PROCESS = PseudoClass.getPseudoClass("active-process");

    private ListView<String> rawContentList;
    private TextArea errorsArea;
    private TableView<ProcessElement> processListView;
    private ComboBox<String> filterCombo;
    private ComboBox<String> displayModeCombo;
    private Label statusBar;
    private GuiSettings settings;
    private long openSeq;
    private Path currentPath;
    private List<ProcessElement> allProcesses;
    private List<ProcessElement> errorProcesses;
    private List<String> allRawLines = new ArrayList<>();
    private List<LogEntry> allEntries = new ArrayList<>();
    private ProcessElement activeProcess;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        settings = new GuiSettings();

        rawContentList = new ListView<>();
        rawContentList.setEditable(false);
        rawContentList.getStyleClass().add("raw-content-list");
        rawContentList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
        rawContentList.setPlaceholder(new Label("Содержимое файла будет отображено здесь после открытия"));

        ContextMenu rawContentMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(e -> copyRawContentSelection());
        rawContentMenu.getItems().add(copyItem);
        rawContentList.setContextMenu(rawContentMenu);

        errorsArea = new TextArea();
        errorsArea.setEditable(false);
        errorsArea.setWrapText(true);
        errorsArea.setPromptText("Сведения об ошибках будут отображены здесь");

        processListView = new TableView<>();
        processListView.getStyleClass().add("process-table");
        processListView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        processListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<ProcessElement, ProcessElement> iconCol = new TableColumn<>("");
        iconCol.setMaxWidth(26);
        iconCol.setMinWidth(26);
        iconCol.setSortable(false);
        iconCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        iconCol.setCellFactory(col -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                } else {
                    setGraphic(statusIcon(p));
                }
            }
        });

        TableColumn<ProcessElement, ProcessElement> pidCol = new TableColumn<>("PID");
        double pidWidth = measureTextWidth("88888888",
                Font.font("Consolas", FontPosture.REGULAR, 11)) + 18;
        pidCol.setPrefWidth(pidWidth);
        pidCol.setMinWidth(pidWidth);
        pidCol.setMaxWidth(pidWidth);
        pidCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        pidCol.setCellFactory(col -> new TableCell<ProcessElement, ProcessElement>() {
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

        TableColumn<ProcessElement, ProcessElement> nameCol = new TableColumn<>("Имя процесса");
        nameCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        nameCol.setCellFactory(col -> new TableCell<ProcessElement, ProcessElement>() {
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

        TableColumn<ProcessElement, ProcessElement> countCol = new TableColumn<>("Запросов/ответов");
        countCol.setPrefWidth(52);
        countCol.setMaxWidth(52);
        countCol.setResizable(false);
        countCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        countCol.setCellFactory(col -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                setAlignment(Pos.CENTER_RIGHT);
                setText(empty || p == null ? null : String.valueOf(reqRespCount(p)));
            }
        });

        processListView.getColumns().add(iconCol);
        processListView.getColumns().add(pidCol);
        processListView.getColumns().add(nameCol);
        processListView.getColumns().add(countCol);

        processListView.setRowFactory(tv -> new TableRow<ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement item, boolean empty) {
                super.updateItem(item, empty);
                boolean active = !empty && item == activeProcess;
                pseudoClassStateChanged(ACTIVE_PROCESS, active);
            }
        });
        processListView.setOnMouseClicked(e -> {
            ProcessElement sel = processListView.getSelectionModel().getSelectedItem();
            if (sel != null && e.getClickCount() == 1) {
                showProcessStart(sel);
            }
            if (sel != null && e.getClickCount() == 2) {
                toggleActiveProcess(sel);
            }
        });
        processListView.setPlaceholder(new Label("Нет процессов для отображения"));

        Label filterLabel = new Label("Фильтр:");
        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(
                "Только ошибочные",
                "Все с PID",
                "Все процессы"
        );
        filterCombo.setValue("Только ошибочные");
        filterCombo.setTooltip(new Tooltip("Фильтр списка процессов"));
        filterCombo.setOnAction(e -> applyFilter());

        Label processesLabel = new Label("Процессы МПЗ");
        processesLabel.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 2 0;");

        VBox leftPanel = new VBox(6, processesLabel, filterLabel, filterCombo, processListView);
        leftPanel.setPadding(new Insets(8));
        leftPanel.getStyleClass().add("left-panel");
        VBox.setVgrow(processListView, Priority.ALWAYS);

        Label errorsLabel = new Label("Частые ошибки");
        errorsLabel.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 2 0;");

        VBox bottomPanel = new VBox(4, errorsLabel, errorsArea);
        bottomPanel.setPadding(new Insets(8));
        bottomPanel.getStyleClass().add("bottom-panel");
        VBox.setVgrow(errorsArea, Priority.ALWAYS);

        statusBar = new Label("Файл не выбран");
        statusBar.getStyleClass().add("status-bar");
        statusBar.setMaxWidth(Double.MAX_VALUE);

        Label displayLabel = new Label("Отображение:");
        displayLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5a5e66;");
        displayModeCombo = new ComboBox<>();
        displayModeCombo.getItems().addAll(
                "Все строки",
                "Выбранный процесс"
        );
        displayModeCombo.setValue("Все строки");
        displayModeCombo.setTooltip(new Tooltip("Режим отображения в основной области"));
        displayModeCombo.setOnAction(e -> applyDisplayMode());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMaxWidth(Double.MAX_VALUE);

        ToolBar toolBar = new ToolBar(displayLabel, displayModeCombo, spacer);
        toolBar.setStyle("-fx-padding: 8 10 8 10;");
        HBox.setHgrow(displayLabel, Priority.NEVER);
        HBox.setHgrow(displayModeCombo, Priority.NEVER);

        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        root.setLeft(leftPanel);
        root.setCenter(rawContentList);

        VBox bottomWithStatus = new VBox(0, bottomPanel, statusBar);
        root.setBottom(bottomWithStatus);

        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(
                GuiApp.class.getResource("/gui/app.css").toExternalForm());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                () -> openFile(stage));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN),
                () -> Platform.exit());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN),
                () -> copyRawContentSelection());

        scene.widthProperty().addListener((obs, oldV, newV) -> {
            leftPanel.setPrefWidth(newV.doubleValue() * LEFT_PANEL_RATIO);
        });
        scene.heightProperty().addListener((obs, oldV, newV) -> {
            bottomPanel.setPrefHeight(newV.doubleValue() * BOTTOM_PANEL_RATIO);
        });

        stage.setTitle("MPZ Log Viewer");
        stage.getIcons().add(createAppIcon());
        stage.setScene(scene);
        restoreWindowBounds(stage);
        bindWindowBounds(stage);
        stage.setOnCloseRequest(e -> settings.flush());
        stage.show();

        if (!openFile(stage)) {
            Platform.exit();
        }

        leftPanel.setPrefWidth(scene.getWidth() * LEFT_PANEL_RATIO);
        bottomPanel.setPrefHeight(scene.getHeight() * BOTTOM_PANEL_RATIO);
    }

    private void restoreWindowBounds(Stage stage) {
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double x = settings.getWindowX();
        double y = settings.getWindowY();
        if (!Double.isNaN(x) && !Double.isNaN(y)
                && x >= vb.getMinX() - 60 && y >= vb.getMinY() - 40
                && x < vb.getMaxX() - 60 && y < vb.getMaxY() - 40) {
            stage.setX(x);
            stage.setY(y);
        }
        double w = settings.getWindowWidth();
        double h = settings.getWindowHeight();
        if (!Double.isNaN(w) && !Double.isNaN(h) && w >= 300 && h >= 200) {
            stage.setWidth(w);
            stage.setHeight(h);
        }
        stage.setMaximized(settings.isWindowMaximized());
    }

    private void bindWindowBounds(Stage stage) {
        stage.xProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.yProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.widthProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.heightProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.maximizedProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
    }

    private void saveBounds(Stage stage) {
        settings.setWindowMaximized(stage.isMaximized());
        if (!stage.isMaximized()) {
            settings.setWindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        }
    }

    private boolean openFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Открыть лог-файл МПЗ");
        File lastDir = lastDirectory();
        if (lastDir != null) {
            chooser.setInitialDirectory(lastDir);
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return false;
        }
        settings.setLastDirectory(file.getParent());
        Path path = file.toPath();
        currentPath = path;
        updateFileInfo(path);
        final long seq = ++openSeq;
        loadRawContent(path, seq);
        analyzeFile(path, seq);
        return true;
    }

    private void loadRawContent(Path path, long seq) {
        Thread worker = new Thread(() -> {
            try {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
                final int totalLines = lines.size();
                Platform.runLater(() -> {
                    if (seq != openSeq) {
                        return;
                    }
                    allRawLines = lines;
                    rawContentList.getItems().setAll(lines);
                    statusBar.setText(statusBar.getText()
                            + "   |   Строк: " + totalLines);
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (seq != openSeq) {
                        return;
                    }
                    rawContentList.getItems().setAll("Ошибка чтения файла: " + e.getMessage());
                });
            }
        }, "mpz-raw-reader");
        worker.setDaemon(true);
        worker.start();
    }

    private void analyzeFile(Path path, long seq) {
        allProcesses = null;
        errorProcesses = null;
        activeProcess = null;
        allEntries = new ArrayList<>();
        processListView.getItems().clear();
        errorsArea.clear();
        displayModeCombo.setValue("Все строки");
        rawContentList.getItems().clear();

        Thread worker = new Thread(() -> {
            try {
                MpzLogParser parser = new MpzLogParser();
                parser.parse(path);
                LogModel model = new LogModelBuilder().build(parser.getEntries());

                List<ProcessElement> processes = model.getProcesses();
                allEntries = model.getAllLines();
                List<ProcessElement> errors = new ArrayList<>();
                for (ProcessElement p : processes) {
                    if (!p.getErrors().isEmpty()) {
                        errors.add(p);
                    }
                }

                final List<ProcessElement> finalAll = processes;
                final List<ProcessElement> finalErrors = errors;

                Platform.runLater(() -> {
                    if (seq != openSeq) {
                        return;
                    }
                    allProcesses = finalAll;
                    errorProcesses = finalErrors;
                    populateProcessList();
                    updateErrorsPanel(processes);
                    statusBar.setText(statusBar.getText()
                            + "   |   Процессов: " + finalAll.size()
                            + "   |   Ошибок: " + finalErrors.size());
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (seq != openSeq) {
                        return;
                    }
                    errorsArea.setText("Ошибка анализа: " + e.getMessage());
                });
            }
        }, "mpz-analyze-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void populateProcessList() {
        processListView.getItems().clear();
        if (allProcesses == null || allProcesses.isEmpty()) {
            return;
        }
        boolean hasErrors = errorProcesses != null && !errorProcesses.isEmpty();
        String selectedFilter = filterCombo.getValue();
        if (selectedFilter == null || (!hasErrors && "Только ошибочные".equals(selectedFilter))) {
            selectedFilter = hasErrors ? "Только ошибочные" : "Все процессы";
            filterCombo.setValue(selectedFilter);
        }
        applyFilterWithSelection(selectedFilter);
    }

    private void applyFilter() {
        if (allProcesses == null) {
            return;
        }
        String filter = filterCombo.getValue();
        if (filter == null) {
            filter = "Все процессы";
        }
        applyFilterWithSelection(filter);
    }

    private void applyFilterWithSelection(String filter) {
        processListView.getItems().clear();
        List<ProcessElement> filtered;
        switch (filter) {
            case "Только ошибочные":
                filtered = errorProcesses != null ? errorProcesses : allProcesses;
                break;
            case "Все с PID":
                filtered = new ArrayList<>();
                for (ProcessElement p : allProcesses) {
                    if (p.getPid() != null) {
                        filtered.add(p);
                    }
                }
                break;
            case "Все процессы":
            default:
                filtered = allProcesses;
                break;
        }
        if (filtered.isEmpty()) {
            return;
        }
        processListView.getItems().setAll(filtered);
    }

    private void showProcessStart(ProcessElement p) {
        int firstLine = p.firstLineNumber();
        if (firstLine <= 0 || firstLine > allRawLines.size()) {
            return;
        }
        String target = allRawLines.get(firstLine - 1);
        int index = rawContentList.getItems().indexOf(target);
        if (index < 0) {
            return;
        }
        rawContentList.getSelectionModel().clearSelection();
        rawContentList.getSelectionModel().select(index);
        rawContentList.scrollTo(index);
    }

    private void toggleActiveProcess(ProcessElement p) {
        if (activeProcess == p) {
            activeProcess = null;
            displayModeCombo.setValue("Все строки");
        } else {
            activeProcess = p;
            displayModeCombo.setValue("Выбранный процесс");
        }
        applyDisplayMode();
    }

    private void applyDisplayMode() {
        if ("Выбранный процесс".equals(displayModeCombo.getValue())) {
            if (activeProcess != null) {
                showProcessLines(activeProcess);
            } else {
                rawContentList.getItems().setAll(allRawLines);
            }
        } else {
            activeProcess = null;
            rawContentList.getItems().setAll(allRawLines);
        }
        processListView.refresh();
    }

    private void showProcessLines(ProcessElement p) {
        Set<LogEntry> processEntries = new HashSet<>();
        for (LogLine line : p.getLines()) {
            processEntries.add(line.getEntry());
        }
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < allEntries.size(); i++) {
            LogEntry e = allEntries.get(i);
            if (!processEntries.contains(e)) {
                continue;
            }
            int start = e.getLineNumber();
            int end = i + 1 < allEntries.size()
                    ? allEntries.get(i + 1).getLineNumber()
                    : allRawLines.size() + 1;
            for (int lineNo = start; lineNo < end; lineNo++) {
                if (lineNo >= 1 && lineNo <= allRawLines.size()) {
                    shown.add(allRawLines.get(lineNo - 1));
                }
            }
        }
        if (shown.isEmpty()) {
            shown.add(p.pidLabel() + " — строки процесса не найдены");
        }
        rawContentList.getItems().setAll(shown);
    }

    private static int reqRespCount(ProcessElement p) {
        int count = 0;
        for (LogLine line : p.getLines()) {
            if (line.isRequest() || line.isResponse()) {
                count++;
            }
        }
        return count;
    }

    private void updateErrorsPanel(List<ProcessElement> processes) {
        errorsArea.clear();
        Map<String, List<ErrorElement>> errorGroups = new LinkedHashMap<>();
        for (ProcessElement p : processes) {
            for (ErrorElement err : p.getErrors()) {
                errorGroups.computeIfAbsent(err.getErrorKey(), k -> new ArrayList<>()).add(err);
            }
        }
        if (errorGroups.isEmpty()) {
            errorsArea.setText("Ошибки не найдены");
            return;
        }
        List<Map.Entry<String, List<ErrorElement>>> topErrors = errorGroups.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(10)
                .collect(Collectors.toList());
        if (topErrors.isEmpty()) {
            errorsArea.setText("Ошибки не найдены");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ErrorElement>> group : topErrors) {
            String masked = group.getKey();
            if (masked.isEmpty()) {
                masked = group.getValue().get(0).getErrorKey();
            }
            String[] lines = masked.split("\n", -1);
            sb.append(String.format("%3d раз(а) — %s", group.getValue().size(), lines[0]));
            sb.append("\n");
            for (int i = 1; i < lines.length; i++) {
                sb.append("             ").append(lines[i]);
                sb.append("\n");
            }
            sb.append("\n");
        }
        errorsArea.setText(sb.toString().trim());
    }

    private void updateFileInfo(Path path) {
        StringBuilder sb = new StringBuilder();
        sb.append(path.toAbsolutePath());
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            sb.append("   |   Размер: ").append(formatSize(attrs.size()));
            sb.append("   |   Создан: ").append(FILE_TIME_FMT.format(
                    LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault())));
        } catch (IOException e) {
            sb.append("   |   Размер/время создания недоступны");
        }
        statusBar.setText(sb.toString());
    }

    private static double measureTextWidth(String text, Font font) {
        Text t = new Text(text);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    private static Region iconIn(Node shape) {
        StackPane pane = new StackPane(shape);
        pane.setPrefSize(16, 16);
        pane.setMinSize(16, 16);
        return pane;
    }

    private static Node statusIcon(ProcessElement p) {
        Color color;
        switch (p.getStatus()) {
            case COMPLETED:
                color = Color.web("#3A8F3A");
                break;
            case COMPLETED_WITH_ERROR:
                color = Color.web("#E0A92F");
                break;
            case INTERRUPTED:
                color = Color.web("#C06028");
                break;
            case FAILED:
                color = Color.BLACK;
                break;
            case UNRESOLVED:
            default:
                color = Color.web("#8A8A8A");
                break;
        }
        Circle c = new Circle(5.0, color);
        return iconIn(c);
    }

    private static Image createAppIcon() {
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

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private File lastDirectory() {
        String dir = settings.getLastDirectory();
        if (dir == null) {
            return null;
        }
        File f = new File(dir);
        return f.isDirectory() ? f : null;
    }

    private void copyRawContentSelection() {
        ObservableList<String> selected = rawContentList.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(selected.get(i));
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }
}