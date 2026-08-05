package com.mpzlog.gui;

import com.mpzlog.model.ErrorGroupInfo;
import com.mpzlog.model.LogEntry;
import com.mpzlog.model.LogLine;
import com.mpzlog.model.ProcessElement;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuiApp extends Application {

    private GuiSettings settings;
    private ViewController vc;
    private FileService fileService;

    private List<String> allRawLines = new ArrayList<>();
    private List<LogEntry> allEntries = new ArrayList<>();
    private List<ProcessElement> allProcesses;
    private Path currentPath;

    private ListView<String> rawContentList;
    private TableView<ProcessElement> processTable;
    private TableView<ErrorGroupInfo> errorTable;
    private List<ProcessElement> processOriginalOrder;
    private List<ErrorGroupInfo> errorOriginalOrder;
    private Label statusBar;

    private StackPane loadingOverlay;
    private boolean rawLoaded;
    private boolean analysisDone;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        settings = new GuiSettings();
        vc = new ViewController();
        fileService = new FileService(settings);

        statusBar = new Label("Файл не выбран");
        statusBar.getStyleClass().add("status-bar");

        Label displayModeLabel = new Label("Все строки");

        Button cancelButton = new Button("\u2715");
        cancelButton.setOnAction(e -> {
            vc.clearProcessSelection();
            rawContentList.getItems().setAll(allRawLines);
            rawContentList.scrollTo(0);
            processTable.refresh();
        });

        Button openButton = new Button("Открыть файл");
        openButton.setOnAction(e -> openFile(stage));

        processOriginalOrder = new ArrayList<>();
        errorOriginalOrder = new ArrayList<>();

        rawContentList = createRawContentList();
        errorTable = createErrorTable();
        processTable = createProcessTable();

        Scene scene = MainLayoutBuilder.build(
                stage, vc, processTable, rawContentList, errorTable,
                statusBar, displayModeLabel, cancelButton, openButton);

        loadingOverlay = createLoadingOverlay();
        StackPane rootStack = new StackPane(scene.getRoot(), loadingOverlay);
        scene.setRoot(rootStack);

        stage.setTitle("MPZ Log Viewer");
        stage.getIcons().add(IconFactory.createAppIcon());
        stage.setScene(scene);
        WindowStateManager.restoreWindowBounds(stage, settings);
        WindowStateManager.bindWindowBounds(stage, settings);
        stage.setOnCloseRequest(e -> settings.flush());
        stage.show();

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                () -> openFile(stage));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN),
                () -> Platform.exit());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN),
                () -> copyRawContentSelection());

        if (!openFile(stage)) {
            Platform.exit();
        }
    }

    private StackPane createLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        StackPane overlay = new StackPane(spinner);
        overlay.getStyleClass().add("loading-overlay");
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }

    private void showLoading() {
        loadingOverlay.setVisible(true);
        loadingOverlay.setManaged(true);
        rawLoaded = false;
        analysisDone = false;
    }

    private void hideLoading() {
        if (rawLoaded && analysisDone) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
        }
    }

    private ListView<String> createRawContentList() {
        return RawContentListHelper.createList(
                this::copyRawContentSelection,
                () -> RawContentListHelper.showGoToLineDialog(rawContentList, allRawLines),
                list -> ScrollbarHelper.keepHorizontalScrollbarVisible(list));
    }

    private TableView<ErrorGroupInfo> createErrorTable() {
        return ErrorTableHelper.createTable(vc, this::onErrorDoubleClick, errorOriginalOrder);
    }

    private TableView<ProcessElement> createProcessTable() {
        return ProcessTableHelper.createTable(vc, this::onProcessSingleClick, this::onProcessDoubleClick, processOriginalOrder);
    }

    private void onProcessSingleClick(ProcessElement p) {
        if (vc.isProcessSelected()) {
            return;
        }
        rawContentList.getItems().setAll(allRawLines);
        scrollToProcess(p);
    }

    private void onProcessDoubleClick(ProcessElement p) {
        if (vc.isProcessSelected(p)) {
            vc.clearProcessSelection();
            rawContentList.getItems().setAll(allRawLines);
            rawContentList.scrollTo(0);
        } else {
            vc.selectProcess(p);
            rawContentList.getItems().setAll(computeProcessLines(p));
            rawContentList.scrollTo(0);
        }
        processTable.refresh();
    }

    private void onErrorDoubleClick(ErrorGroupInfo info) {
        if (vc.isErrorSelected(info.getErrorKey())) {
            vc.clearErrorSelection();
            errorTable.getSelectionModel().clearSelection();

            processOriginalOrder.clear();
            processOriginalOrder.addAll(allProcesses);
            processTable.getItems().setAll(allProcesses);

            ProcessElement selectedP = vc.getSelectedProcess();
            if (selectedP != null) {
                int idx = processTable.getItems().indexOf(selectedP);
                if (idx >= 0) {
                    processTable.scrollTo(idx);
                }
            } else {
                processTable.scrollTo(0);
            }

            if (selectedP != null) {
                rawContentList.getItems().setAll(computeProcessLines(selectedP));
                rawContentList.scrollTo(0);
            } else {
                rawContentList.getItems().setAll(allRawLines);
                rawContentList.scrollTo(0);
            }
        } else {
            vc.selectErrorKey(info.getErrorKey());
            errorTable.getSelectionModel().select(info);
            applyErrorFilter(info.getErrorKey());

            ProcessElement selectedP = vc.getSelectedProcess();
            if (selectedP != null && !selectedP.hasErrorKey(info.getErrorKey())) {
                vc.clearProcessSelection();
            }

            rawContentList.getItems().setAll(allRawLines);
            rawContentList.scrollTo(0);
        }
        processTable.refresh();
    }

    private void applyErrorFilter(String errKey) {
        for (ErrorGroupInfo info : errorTable.getItems()) {
            if (info.getErrorKey().equals(errKey)) {
                List<ProcessElement> filtered = new ArrayList<>(info.getProcesses());
                filtered.sort(Comparator.comparingInt(ProcessElement::firstLineNumber));
                processOriginalOrder.clear();
                processOriginalOrder.addAll(filtered);
                processTable.getItems().setAll(filtered);
                return;
            }
        }
    }

    private boolean openFile(Stage stage) {
        Path path = fileService.openFileDialog(stage);
        if (path == null) {
            return false;
        }
        currentPath = path;

        allProcesses = null;
        allEntries = new ArrayList<>();
        allRawLines = new ArrayList<>();
        vc.clearAll();
        processTable.getItems().clear();
        errorTable.getItems().clear();
        rawContentList.getItems().clear();

        statusBar.setText(fileService.formatFileInfo(path));

        showLoading();

        long seq = fileService.bumpSeq();
        fileService.loadRawContent(path, seq,
                lines -> {
                    allRawLines = lines;
                    rawContentList.getItems().setAll(lines);
                    ScrollbarHelper.keepHorizontalScrollbarVisible(rawContentList);
                    statusBar.setText(statusBar.getText()
                            + "   |   Строк: " + lines.size());
                    rawLoaded = true;
                    hideLoading();
                },
                error -> {
                    rawContentList.getItems().setAll("Ошибка чтения файла: " + error);
                    rawLoaded = true;
                    hideLoading();
                });

        fileService.analyzeFile(path, seq,
                result -> {
                    allProcesses = result.getProcesses();
                    allEntries = result.getAllEntries();
                    processOriginalOrder.clear();
                    processOriginalOrder.addAll(allProcesses);
                    processTable.getItems().setAll(allProcesses);
                    errorOriginalOrder.clear();
                    errorOriginalOrder.addAll(result.getModel().getFrequentErrors());
                    errorTable.getItems().setAll(result.getModel().getFrequentErrors());
                    statusBar.setText(statusBar.getText()
                            + "   |   Процессов: " + allProcesses.size()
                            + "   |   Ошибок: " + result.getErrorProcesses().size());
                    analysisDone = true;
                    hideLoading();
                },
                error -> {
                    errorTable.setPlaceholder(new Label("Ошибка анализа: " + error));
                    analysisDone = true;
                    hideLoading();
                });

        return true;
    }

    private void scrollToProcess(ProcessElement p) {
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

    private List<String> computeProcessLines(ProcessElement p) {
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
        return shown;
    }

    private void copyRawContentSelection() {
        javafx.collections.ObservableList<String> selected =
                rawContentList.getSelectionModel().getSelectedItems();
        ClipboardUtil.copyLines(selected);
    }

}
