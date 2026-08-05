package com.mpzlog.gui;

import com.mpzlog.model.ErrorGroupInfo;
import com.mpzlog.model.LogEntry;
import com.mpzlog.model.LogLine;
import com.mpzlog.model.LogModel;
import com.mpzlog.model.LogModelBuilder;
import com.mpzlog.model.ProcessElement;
import com.mpzlog.parser.MpzLogParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuiApp extends Application {

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final double LEFT_PANEL_RATIO = 0.20;
    private static final double BOTTOM_PANEL_RATIO = 0.25;
    private static final Color ICON_COLOR = Color.web("#3C3C3C");
    private static final PseudoClass ACTIVE_PROCESS = PseudoClass.getPseudoClass("active-process");

    private ListView<String> rawContentList;
    private TableView<ErrorGroupInfo> errorTable;
    private TableView<ProcessElement> processListView;
    private Label displayModeLabel;
    private Button cancelButton;
    private Label statusBar;
    private GuiSettings settings;
    private Stage stage;
    private long openSeq;
    private Path currentPath;
    private List<ProcessElement> allProcesses;
    private List<ProcessElement> errorProcesses;
    private List<String> allRawLines = new ArrayList<>();
    private List<LogEntry> allEntries = new ArrayList<>();
    private ProcessElement activeProcess;
    private boolean scrollbarConfigured;
    private String activeErrorFilter;
    private List<ProcessElement> originalProcessOrder;
    private List<ErrorGroupInfo> originalErrorOrder;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
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
        MenuItem goToItem = new MenuItem("Перейти");
        goToItem.setOnAction(e -> showGoToLineDialog());
        rawContentMenu.getItems().add(goToItem);
        rawContentList.setContextMenu(rawContentMenu);
        rawContentList.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(this::findRawHorizontalScrollbar);
            }
        });

        errorTable = new TableView<>();
        errorTable.getStyleClass().add("error-table");
        errorTable.setPlaceholder(new Label("Ошибки не найдены"));

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

        errorTable.getColumns().add(errCountCol);
        errorTable.getColumns().add(errKeyCol);
        errKeyCol.setComparator(String::compareToIgnoreCase);
        errKeyCol.prefWidthProperty().bind(errorTable.widthProperty().subtract(errCountCol.getPrefWidth() + 4));

        ContextMenu errorContextMenu = new ContextMenu();
        MenuItem copyErrorItem = new MenuItem("Копировать ошибку");
        copyErrorItem.setOnAction(e -> {
            ErrorGroupInfo sel = errorTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                ClipboardContent content = new ClipboardContent();
                content.putString(sel.getErrorKey());
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        errorContextMenu.getItems().add(copyErrorItem);
        errorTable.setContextMenu(errorContextMenu);

        errorTable.setRowFactory(tv -> {
            TableRow<ErrorGroupInfo> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    ErrorGroupInfo item = row.getItem();
                    if (item != null) {
                        toggleErrorFilter(item);
                    }
                }
            });
            return row;
        });

        originalErrorOrder = new ArrayList<>();
        setupSortReset(errorTable, originalErrorOrder);

        processListView = new TableView<>();
        processListView.getStyleClass().add("process-table");
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
        pidCol.setComparator((a, b) -> {
            String pa = a.getPid();
            String pb = b.getPid();
            if (pa == null && pb == null) return 0;
            if (pa == null) return -1;
            if (pb == null) return 1;
            return Long.compare(Long.parseLong(pa), Long.parseLong(pb));
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
        nameCol.setComparator((a, b) -> {
            String na = a.getProcessName();
            String nb = b.getProcessName();
            if (na == null && nb == null) return 0;
            if (na == null) return 1;
            if (nb == null) return -1;
            return na.compareToIgnoreCase(nb);
        });

        TableColumn<ProcessElement, ProcessElement> procCountCol = new TableColumn<>("Запросов/ответов");
        procCountCol.setPrefWidth(52);
        procCountCol.setMaxWidth(52);
        procCountCol.setResizable(false);
        procCountCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        procCountCol.setCellFactory(col -> new TableCell<ProcessElement, ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement p, boolean empty) {
                super.updateItem(p, empty);
                setAlignment(Pos.CENTER_RIGHT);
                setText(empty || p == null ? null : String.valueOf(reqRespCount(p)));
            }
        });
        procCountCol.setComparator((a, b) ->
                Integer.compare(reqRespCount(a), reqRespCount(b)));

        processListView.getColumns().add(iconCol);
        processListView.getColumns().add(pidCol);
        processListView.getColumns().add(nameCol);
        processListView.getColumns().add(procCountCol);

        double fixedWidth = iconCol.getMaxWidth() + pidCol.getPrefWidth() + procCountCol.getPrefWidth() + 8;
        nameCol.prefWidthProperty().bind(
                processListView.widthProperty().subtract(fixedWidth + 4));

        originalProcessOrder = new ArrayList<>();
        setupSortReset(processListView, originalProcessOrder);

        processListView.setRowFactory(tv -> new TableRow<ProcessElement>() {
            @Override
            protected void updateItem(ProcessElement item, boolean empty) {
                super.updateItem(item, empty);
                boolean active = !empty && item == activeProcess;
                pseudoClassStateChanged(ACTIVE_PROCESS, active);
            }
        });

        ContextMenu processContextMenu = new ContextMenu();
        MenuItem copyPidItem = new MenuItem("Копировать PID");
        copyPidItem.setOnAction(e -> {
            ProcessElement sel = processListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                ClipboardContent content = new ClipboardContent();
                content.putString(sel.pidLabel());
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        processContextMenu.getItems().add(copyPidItem);
        processListView.setContextMenu(processContextMenu);

        processListView.setOnMouseClicked(e -> {
            ProcessElement sel = processListView.getSelectionModel().getSelectedItem();
            if (sel == null) {
                return;
            }
            if (e.getClickCount() == 2) {
                toggleActiveProcess(sel);
            } else if (e.getClickCount() == 1 && activeProcess == null) {
                displayModeLabel.setText("Все строки");
                if (activeErrorFilter != null) {
                    activeErrorFilter = null;
                    errorTable.getSelectionModel().clearSelection();
                    originalProcessOrder.clear();
                    originalProcessOrder.addAll(allProcesses);
                    processListView.getItems().setAll(allProcesses);
                }
                rawContentList.getItems().setAll(allRawLines);
                showProcessStart(sel);
            }
        });
        processListView.setPlaceholder(new Label("Нет процессов для отображения"));

        Button openButton = new Button("Открыть файл");
        openButton.setOnAction(e -> openFile(stage));

        ToolBar filterToolbar = new ToolBar(openButton);
        filterToolbar.getStyleClass().add("panel-toolbar");
        HBox.setHgrow(openButton, Priority.NEVER);

        VBox leftPanel = new VBox(filterToolbar, processListView);
        leftPanel.getStyleClass().add("left-panel");
        VBox.setMargin(processListView, new Insets(8, 0, 0, 0));
        VBox.setVgrow(processListView, Priority.ALWAYS);

        VBox bottomPanel = new VBox(4, errorTable);
        bottomPanel.setPadding(new Insets(8));
        bottomPanel.getStyleClass().add("bottom-panel");
        VBox.setVgrow(errorTable, Priority.ALWAYS);

        statusBar = new Label("Файл не выбран");
        statusBar.getStyleClass().add("status-bar");
        statusBar.setMaxWidth(Double.MAX_VALUE);

        displayModeLabel = new Label("Все строки");
        displayModeLabel.getStyleClass().add("toolbar-label");
        displayModeLabel.setStyle("-fx-font-weight: bold;");

        cancelButton = new Button("✕");
        cancelButton.getStyleClass().addAll("toolbar-button", "cancel-button");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        cancelButton.setOnAction(e -> {
            if (activeProcess != null) {
                toggleActiveProcess(activeProcess);
            }
        });

        ToolBar centerToolbar = new ToolBar(displayModeLabel, cancelButton);
        centerToolbar.getStyleClass().add("panel-toolbar");

        VBox centerBox = new VBox(centerToolbar, rawContentList);
        centerBox.getStyleClass().add("center-panel");
        VBox.setVgrow(rawContentList, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setLeft(leftPanel);
        root.setCenter(centerBox);

        VBox bottomWithStatus = new VBox(0, bottomPanel, statusBar);
        root.setBottom(bottomWithStatus);

        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(
                GuiApp.class.getResource("/gui/light_theme.css").toExternalForm());
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
                    Platform.runLater(this::findRawHorizontalScrollbar);
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
        activeErrorFilter = null;
        scrollbarConfigured = false;
        allEntries = new ArrayList<>();
        processListView.getItems().clear();
        errorTable.getItems().clear();
        displayModeLabel.setText("Все строки");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
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
                    updateErrorsPanel(model);
                    statusBar.setText(statusBar.getText()
                            + "   |   Процессов: " + finalAll.size()
                            + "   |   Ошибок: " + finalErrors.size());
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (seq != openSeq) {
                        return;
                    }
                    errorTable.setPlaceholder(new Label("Ошибка анализа: " + e.getMessage()));
                });
            }
        }, "mpz-analyze-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void populateProcessList() {
        processListView.getItems().clear();
        originalProcessOrder.clear();
        if (allProcesses == null || allProcesses.isEmpty()) {
            return;
        }
        originalProcessOrder.addAll(allProcesses);
        processListView.getItems().setAll(allProcesses);
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
            displayModeLabel.setText("Все строки");
            rawContentList.getItems().setAll(allRawLines);
            rawContentList.scrollTo(0);
            processListView.getSelectionModel().clearSelection();
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);
        } else {
            activeProcess = p;
            displayModeLabel.setText("PID: " + p.pidLabel());
            showProcessLines(p);
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);
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
        rawContentList.scrollTo(0);
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

    private void updateErrorsPanel(LogModel model) {
        List<ErrorGroupInfo> items = model.getFrequentErrors();
        if (items.isEmpty()) {
            errorTable.getItems().clear();
            originalErrorOrder.clear();
        } else {
            originalErrorOrder.clear();
            originalErrorOrder.addAll(items);
            errorTable.getItems().setAll(items);
        }
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

    private void findRawHorizontalScrollbar() {
        if (scrollbarConfigured) {
            return;
        }
        for (Node node : rawContentList.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar) {
                ScrollBar sb = (ScrollBar) node;
                if (sb.getOrientation() == Orientation.HORIZONTAL) {
                    setupRawHorizontalScrollbar(sb);
                    return;
                }
            }
        }
    }

    private void setupRawHorizontalScrollbar(ScrollBar hbar) {
        scrollbarConfigured = true;
        hbar.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (!isVisible) {
                hbar.setVisible(true);
            }
        });
        hbar.setVisible(true);

        hbar.maxProperty().addListener(o -> updateRawHbarDisable(hbar));
        hbar.visibleAmountProperty().addListener(o -> updateRawHbarDisable(hbar));
        updateRawHbarDisable(hbar);
    }

    private void updateRawHbarDisable(ScrollBar hbar) {
        hbar.setDisable(hbar.getMax() - hbar.getVisibleAmount() <= 0.5);
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

    private static <T> void setupSortReset(TableView<T> table, List<T> originalOrderRef) {
        List<T> original = new ArrayList<>(originalOrderRef);
        table.sortPolicyProperty().set(t -> {
            if (t.getSortOrder().isEmpty()) {
                t.getItems().setAll(original);
                return true;
            }
            return TableView.DEFAULT_SORT_POLICY.call(t);
        });
        for (TableColumn<T, ?> col : table.getColumns()) {
            if (!col.isSortable()) continue;
            col.sortTypeProperty().addListener((obs, oldType, newType) -> {
                if (oldType == TableColumn.SortType.DESCENDING
                        && newType == TableColumn.SortType.ASCENDING) {
                    Platform.runLater(() -> table.getSortOrder().clear());
                }
            });
        }
    }

    private void showGoToLineDialog() {
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

    private void toggleErrorFilter(ErrorGroupInfo info) {
        if (info.getErrorKey().equals(activeErrorFilter)) {
            activeErrorFilter = null;
            errorTable.getSelectionModel().clearSelection();
            originalProcessOrder.clear();
            originalProcessOrder.addAll(allProcesses);
            processListView.getItems().setAll(allProcesses);
        } else {
            activeErrorFilter = info.getErrorKey();
            List<ProcessElement> filtered = new ArrayList<>(info.getProcesses());
            filtered.sort(Comparator.comparingInt(ProcessElement::firstLineNumber));
            originalProcessOrder.clear();
            originalProcessOrder.addAll(filtered);
            processListView.getItems().setAll(filtered);
        }
    }

}