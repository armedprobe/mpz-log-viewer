package com.mpzlog.gui;

import com.mpzlog.model.LogEntry;
import com.mpzlog.model.LogModel;
import com.mpzlog.model.LogModelBuilder;
import com.mpzlog.model.ProcessElement;
import com.mpzlog.parser.MpzLogParser;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class FileService {

    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final GuiSettings settings;

    public FileService(GuiSettings settings) {
        this.settings = settings;
    }

    public void loadRawContent(Path path, long seq, Consumer<List<String>> onSuccess, Consumer<String> onError) {
        Thread worker = new Thread(() -> {
            try {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
                Platform.runLater(() -> {
                    if (seq != FileService.this.seq) {
                        return;
                    }
                    onSuccess.accept(lines);
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (seq != FileService.this.seq) {
                        return;
                    }
                    onError.accept(e.getMessage());
                });
            }
        }, "mpz-raw-reader");
        worker.setDaemon(true);
        worker.start();
    }

    private long seq;

    public long bumpSeq() {
        return ++seq;
    }

    public void analyzeFile(Path path, long seq, Consumer<AnalysisResult> onSuccess, Consumer<String> onError) {
        Thread worker = new Thread(() -> {
            try {
                MpzLogParser parser = new MpzLogParser();
                parser.parse(path);
                LogModel model = new LogModelBuilder().build(parser.getEntries());

                List<ProcessElement> processes = model.getProcesses();
                List<LogEntry> allEntries = model.getAllLines();
                List<ProcessElement> errors = new ArrayList<>();
                for (ProcessElement p : processes) {
                    if (!p.getErrors().isEmpty()) {
                        errors.add(p);
                    }
                }

                final AnalysisResult result = new AnalysisResult(processes, allEntries, errors, model);

                Platform.runLater(() -> {
                    if (seq != FileService.this.seq) {
                        return;
                    }
                    onSuccess.accept(result);
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (seq != FileService.this.seq) {
                        return;
                    }
                    onError.accept(e.getMessage());
                });
            }
        }, "mpz-analyze-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public Path openFileDialog(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Открыть лог-файл МПЗ");
        File lastDir = lastDirectory();
        if (lastDir != null) {
            chooser.setInitialDirectory(lastDir);
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return null;
        }
        settings.setLastDirectory(file.getParent());
        return file.toPath();
    }

    public String formatFileInfo(Path path) {
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
        return sb.toString();
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public void exportToFile(Path dest, List<String> lines, Runnable onSuccess, Consumer<String> onError) {
        Thread worker = new Thread(() -> {
            try {
                writeLines(dest, lines);
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (IOException e) {
                if (onError != null) {
                    onError.accept(e.getMessage());
                }
            }
        }, "mpz-export-writer");
        worker.setDaemon(true);
        worker.start();
    }

    static void writeLines(Path dest, List<String> lines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(dest, StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines.size(); i++) {
                writer.write(lines.get(i));
                if (i + 1 < lines.size()) {
                    writer.newLine();
                }
            }
        }
    }

    public Path saveFileDialog(Stage stage, String defaultName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт процесса");
        chooser.setInitialFileName(defaultName);
        File lastDir = lastDirectory();
        if (lastDir != null) {
            chooser.setInitialDirectory(lastDir);
        }
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return null;
        }
        return file.toPath();
    }

    private File lastDirectory() {
        String dir = settings.getLastDirectory();
        if (dir == null) {
            return null;
        }
        File f = new File(dir);
        return f.isDirectory() ? f : null;
    }

    public static final class AnalysisResult {
        private final List<ProcessElement> processes;
        private final List<LogEntry> allEntries;
        private final List<ProcessElement> errorProcesses;
        private final LogModel model;

        public AnalysisResult(List<ProcessElement> processes, List<LogEntry> allEntries,
                              List<ProcessElement> errorProcesses, LogModel model) {
            this.processes = Objects.requireNonNull(processes);
            this.allEntries = Objects.requireNonNull(allEntries);
            this.errorProcesses = Objects.requireNonNull(errorProcesses);
            this.model = Objects.requireNonNull(model);
        }

        public List<ProcessElement> getProcesses() {
            return processes;
        }

        public List<LogEntry> getAllEntries() {
            return allEntries;
        }

        public List<ProcessElement> getErrorProcesses() {
            return errorProcesses;
        }

        public LogModel getModel() {
            return model;
        }
    }

}
