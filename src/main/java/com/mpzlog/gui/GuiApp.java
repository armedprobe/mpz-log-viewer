package com.mpzlog.gui;

import com.mpzlog.LogProcessor;
import com.mpzlog.ModeOptions;
import com.mpzlog.ui.TerminalPrinter;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
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
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class GuiApp extends Application {

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final Color ICON_COLOR = Color.web("#3C3C3C");

    private TextArea outputArea;
    private Label statusBar;
    private GuiSettings settings;
    private long openSeq;
    private Path currentPath;
    private TextField searchField;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        settings = new GuiSettings();
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(false);
        outputArea.setFont(Font.font("Consolas", 14));

        Button openButton = new Button("Открыть файл", folderIcon());
        openButton.setTooltip(new Tooltip("Открыть лог-файл МПЗ (Ctrl+O)"));
        openButton.setOnAction(e -> openFile(stage));

        Button processButton = new Button("Процесс", processIcon());
        processButton.setTooltip(new Tooltip("Показать записи процесса МПЗ по process-instance (Ctrl+P)"));
        processButton.setOnAction(e -> showProcessDialog(stage));

        Button listButton = new Button("Список процессов", listIcon());
        listButton.setTooltip(new Tooltip("Список процессов МПЗ текущего файла (Ctrl+L)"));
        listButton.setOnAction(e -> showListProcesses());

        Button grepButton = new Button("Grep", grepIcon());
        grepButton.setTooltip(new Tooltip("Найти процессы МПЗ, содержащие строку (Ctrl+G)"));
        grepButton.setOnAction(e -> showGrepDialog(stage));

        Button exitButton = new Button("Выйти", exitIcon());
        exitButton.setTooltip(new Tooltip("Закрыть программу (Ctrl+Q)"));
        exitButton.setOnAction(e -> Platform.exit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMaxWidth(Double.MAX_VALUE);

        ToolBar toolBar = new ToolBar(openButton, processButton, listButton, grepButton, spacer, exitButton);
        toolBar.setStyle("-fx-padding: 8 10 8 10;");
        HBox.setHgrow(openButton, Priority.NEVER);
        HBox.setHgrow(processButton, Priority.NEVER);
        HBox.setHgrow(listButton, Priority.NEVER);
        HBox.setHgrow(grepButton, Priority.NEVER);
        HBox.setHgrow(exitButton, Priority.NEVER);

        statusBar = new Label("Файл не выбран");
        statusBar.getStyleClass().add("status-bar");
        statusBar.setMaxWidth(Double.MAX_VALUE);

        Label searchLabel = new Label("Поиск:");
        searchField = new TextField();
        searchField.setPromptText("Строка поиска");
        searchField.setMinWidth(100);

        Button nextButton = new Button("Далее", nextIcon());
        Button prevButton = new Button("Предыдущий", prevIcon());

        nextButton.setOnAction(e -> findInOutput(searchField.getText(), true));
        prevButton.setOnAction(e -> findInOutput(searchField.getText(), false));
        searchField.setOnAction(e -> findInOutput(searchField.getText(), true));

        HBox searchPanel = new HBox(8, searchLabel, searchField, nextButton, prevButton);
        searchPanel.getStyleClass().add("search-panel");

        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        BorderPane.setMargin(outputArea, new Insets(10));
        root.setCenter(outputArea);
        BorderPane.setMargin(searchPanel, new Insets(0));
        BorderPane.setMargin(statusBar, new Insets(0));
        VBox bottomBox = new VBox(0, searchPanel, statusBar);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(
                GuiApp.class.getResource("/gui/app.css").toExternalForm());
        searchField.prefWidthProperty().bind(scene.widthProperty().multiply(0.25));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                openButton::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN),
                processButton::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                listButton::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN),
                grepButton::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> {
                    searchField.requestFocus();
                    searchField.selectAll();
                });
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN),
                () -> Platform.exit());
        stage.setTitle("MPZ Log Viewer");
        stage.getIcons().add(createAppIcon());
        stage.setScene(scene);
        restoreWindowBounds(stage);
        bindWindowBounds(stage);
        stage.setOnCloseRequest(e -> settings.flush());
        stage.show();
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

    private void openFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Открыть лог-файл МПЗ");
        File lastDir = lastDirectory();
        if (lastDir != null) {
            chooser.setInitialDirectory(lastDir);
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        settings.setLastDirectory(file.getParent());
        Path path = file.toPath();
        currentPath = path;
        updateFileInfo(path);
        ModeOptions opts = new ModeOptions();
        if (opts.isDefault()) {
            opts.setAnalyze(true);
        }
        process(path, opts);
    }

    private void showProcessDialog(Stage stage) {
        if (currentPath == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Сначала откройте лог-файл.", ButtonType.OK);
            alert.setTitle("MPZ Log Viewer");
            alert.setHeaderText("Файл не выбран");
            alert.showAndWait();
            return;
        }
        String clipboard = Clipboard.getSystemClipboard().getString();
        TextInputDialog dialog = new TextInputDialog(clipboard);
        dialog.setTitle("Процесс МПЗ");
        dialog.setHeaderText("Введите process-instance");
        dialog.setContentText("process-instance:");
        TextField field = dialog.getEditor();
        if (clipboard != null && !clipboard.isEmpty()) {
            field.selectAll();
        }
        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) {
            return;
        }
        String pid = result.get().trim();
        if (pid.isEmpty()) {
            return;
        }
        ModeOptions opts = new ModeOptions();
        opts.setProcessId(pid);
        process(currentPath, opts);
    }

    private void showGrepDialog(Stage stage) {
        if (currentPath == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Сначала откройте лог-файл.", ButtonType.OK);
            alert.setTitle("MPZ Log Viewer");
            alert.setHeaderText("Файл не выбран");
            alert.showAndWait();
            return;
        }
        String clipboard = Clipboard.getSystemClipboard().getString();
        TextInputDialog dialog = new TextInputDialog(clipboard);
        dialog.setTitle("Grep");
        dialog.setHeaderText("Введите строку для поиска процессов");
        dialog.setContentText("Строка:");
        TextField field = dialog.getEditor();
        if (clipboard != null && !clipboard.isEmpty()) {
            field.selectAll();
        }
        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) {
            return;
        }
        String text = result.get().trim();
        if (text.isEmpty()) {
            return;
        }
        ModeOptions opts = new ModeOptions();
        opts.setGrepText(text);
        process(currentPath, opts);
    }

    private void showListProcesses() {
        if (currentPath == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Сначала откройте лог-файл.", ButtonType.OK);
            alert.setTitle("MPZ Log Viewer");
            alert.setHeaderText("Файл не выбран");
            alert.showAndWait();
            return;
        }
        ModeOptions opts = new ModeOptions();
        opts.setListProcesses(true);
        process(currentPath, opts);
    }

    private void findInOutput(String query, boolean forward) {
        if (query == null || query.isEmpty()) {
            return;
        }
        String text = outputArea.getText();
        int idx;
        if (forward) {
            idx = text.indexOf(query, outputArea.getCaretPosition());
            if (idx < 0) {
                idx = text.indexOf(query);
            }
        } else {
            int from = Math.max(0, outputArea.getAnchor() - 1);
            idx = text.lastIndexOf(query, from);
            if (idx < 0) {
                idx = text.lastIndexOf(query);
            }
        }
        if (idx >= 0) {
            outputArea.selectRange(idx, idx + query.length());
            outputArea.requestFocus();
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

    private static Region iconIn(Node shape) {
        StackPane pane = new StackPane(shape);
        pane.setPrefSize(16, 16);
        pane.setMinSize(16, 16);
        return pane;
    }

    private static Node folderIcon() {
        javafx.scene.shape.Path p = new javafx.scene.shape.Path();
        p.setFill(Color.web("#D9A441"));
        p.setStroke(ICON_COLOR);
        p.setStrokeWidth(1.2);
        p.getElements().addAll(
                new MoveTo(1, 3.5),
                new LineTo(6.5, 3.5),
                new LineTo(7.8, 5),
                new LineTo(15, 5),
                new LineTo(15, 12.5),
                new LineTo(1, 12.5),
                new ClosePath());
        return iconIn(p);
    }

    private static Node processIcon() {
        Group g = new Group();
        Circle outer = new Circle(8, 8, 5);
        outer.setFill(null);
        outer.setStroke(ICON_COLOR);
        outer.setStrokeWidth(1.4);
        Circle inner = new Circle(8, 8, 2);
        inner.setFill(ICON_COLOR);
        Line stem = new Line(12, 12, 14.5, 14.5);
        stem.setStroke(ICON_COLOR);
        stem.setStrokeWidth(1.4);
        g.getChildren().addAll(outer, inner, stem);
        return iconIn(g);
    }

    private static Node listIcon() {
        Group g = new Group();
        for (int i = 0; i < 3; i++) {
            double y = 4 + i * 4.5;
            Circle bullet = new Circle(3.2, y, 1.6);
            bullet.setFill(ICON_COLOR);
            Line line = new Line(6.2, y, 14, y);
            line.setStroke(ICON_COLOR);
            line.setStrokeWidth(1.4);
            g.getChildren().addAll(bullet, line);
        }
        return iconIn(g);
    }

    private static Node grepIcon() {
        Group g = new Group();
        Circle lens = new Circle(6.5, 6.5, 4.3);
        lens.setFill(null);
        lens.setStroke(ICON_COLOR);
        lens.setStrokeWidth(1.4);
        Line handle = new Line(9.8, 9.8, 14, 14);
        handle.setStroke(ICON_COLOR);
        handle.setStrokeWidth(1.6);
        g.getChildren().addAll(lens, handle);
        return iconIn(g);
    }

    private static Node exitIcon() {
        Group g = new Group();
        Line l1 = new Line(3.5, 3.5, 12.5, 12.5);
        Line l2 = new Line(12.5, 3.5, 3.5, 12.5);
        l1.setStroke(ICON_COLOR);
        l1.setStrokeWidth(1.6);
        l2.setStroke(ICON_COLOR);
        l2.setStrokeWidth(1.6);
        g.getChildren().addAll(l1, l2);
        return iconIn(g);
    }

    private static Node nextIcon() {
        Group g = new Group();
        Line l = new Line(8, 3, 8, 13);
        l.setStroke(ICON_COLOR);
        l.setStrokeWidth(1.3);
        javafx.scene.shape.Path arrow = new javafx.scene.shape.Path();
        arrow.setFill(ICON_COLOR);
        arrow.getElements().addAll(
                new MoveTo(3.5, 8),
                new LineTo(8, 12.5),
                new LineTo(12.5, 8),
                new ClosePath());
        g.getChildren().addAll(l, arrow);
        return iconIn(g);
    }

    private static Node prevIcon() {
        Group g = new Group();
        Line l = new Line(8, 13, 8, 3);
        l.setStroke(ICON_COLOR);
        l.setStrokeWidth(1.3);
        javafx.scene.shape.Path arrow = new javafx.scene.shape.Path();
        arrow.setFill(ICON_COLOR);
        arrow.getElements().addAll(
                new MoveTo(3.5, 8),
                new LineTo(8, 3.5),
                new LineTo(12.5, 8),
                new ClosePath());
        g.getChildren().addAll(l, arrow);
        return iconIn(g);
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

    private void process(Path path, ModeOptions opts) {
        final long seq = ++openSeq;
        outputArea.clear();
        outputArea.selectRange(0, 0);

        Writer sink = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) {
                String text = new String(cbuf, off, len);
                if (text.isEmpty()) {
                    return;
                }
                Platform.runLater(() -> {
                    if (seq != openSeq) {
                        return;
                    }
                    outputArea.appendText(text);
                });
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        TerminalPrinter printer = new TerminalPrinter(new PrintWriter(sink));

        Thread worker = new Thread(() -> {
            if (opts.isDefault()) {
                opts.setAnalyze(true);
            }
            long parseTimeMs = LogProcessor.process(path, opts, printer, null);
            Platform.runLater(() -> {
                if (seq != openSeq) {
                    return;
                }
                outputArea.selectRange(0, 0);
                outputArea.positionCaret(0);
                statusBar.setText(statusBar.getText()
                        + "   |   Время парсинга: " + parseTimeMs + " мс");
            });
        }, "mpz-log-worker");
        worker.setDaemon(true);
        worker.start();
    }
}
