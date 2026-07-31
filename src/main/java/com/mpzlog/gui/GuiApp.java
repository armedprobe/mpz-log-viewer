package com.mpzlog.gui;

import com.mpzlog.LogProcessor;
import com.mpzlog.ModeOptions;
import com.mpzlog.ui.TerminalPrinter;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
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

public class GuiApp extends Application {

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private TextArea outputArea;
    private Label statusBar;
    private GuiSettings settings;
    private long openSeq;

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

        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("Файл");

        MenuItem openItem = new MenuItem("Открыть файл");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openItem.setOnAction(e -> openFile(stage));

        MenuItem exitItem = new MenuItem("Выйти");
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(openItem, new SeparatorMenuItem(), exitItem);
        menuBar.getMenus().add(fileMenu);

        statusBar = new Label("Файл не выбран");
        statusBar.setMaxWidth(Double.MAX_VALUE);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        BorderPane.setMargin(outputArea, new Insets(8));
        root.setCenter(outputArea);
        BorderPane.setMargin(statusBar, new Insets(0, 8, 8, 8));
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1000, 650);
        stage.setTitle("MPZ Log Viewer");
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
    }

    private void bindWindowBounds(Stage stage) {
        stage.xProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.yProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.widthProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
        stage.heightProperty().addListener((obs, oldV, newV) -> saveBounds(stage));
    }

    private void saveBounds(Stage stage) {
        settings.setWindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
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
        updateFileInfo(stage, path);
        process(path);
    }

    private void updateFileInfo(Stage stage, Path path) {
        stage.setTitle(path.getFileName() + " — MPZ Log Viewer");
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

    private void process(Path path) {
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
            ModeOptions opts = new ModeOptions();
            if (opts.isDefault()) {
                opts.setAnalyze(true);
            }
            LogProcessor.process(path, opts, printer, null);
            Platform.runLater(() -> {
                if (seq != openSeq) {
                    return;
                }
                outputArea.selectRange(0, 0);
                outputArea.positionCaret(0);
            });
        }, "mpz-log-worker");
        worker.setDaemon(true);
        worker.start();
    }
}
