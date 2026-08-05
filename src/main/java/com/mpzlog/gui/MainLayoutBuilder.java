package com.mpzlog.gui;

import com.mpzlog.model.ErrorGroupInfo;
import com.mpzlog.model.ProcessElement;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class MainLayoutBuilder {

    private static final double LEFT_PANEL_RATIO = 0.20;
    private static final double BOTTOM_PANEL_RATIO = 0.25;

    private MainLayoutBuilder() {
    }

    public static Scene build(
            Stage stage,
            ViewController vc,
            TableView<ProcessElement> processTable,
            ListView<String> rawContentList,
            TableView<ErrorGroupInfo> errorTable,
            Label statusBar,
            Label displayModeLabel,
            Button cancelButton,
            Button openButton) {

        ToolBar filterToolbar = new ToolBar(openButton);
        filterToolbar.getStyleClass().add("panel-toolbar");
        HBox.setHgrow(openButton, Priority.NEVER);

        VBox leftPanel = new VBox(filterToolbar, processTable);
        leftPanel.getStyleClass().add("left-panel");
        VBox.setMargin(processTable, new Insets(8, 0, 0, 0));
        VBox.setVgrow(processTable, Priority.ALWAYS);

        VBox bottomPanel = new VBox(4, errorTable);
        bottomPanel.setPadding(new Insets(8));
        bottomPanel.getStyleClass().add("bottom-panel");
        VBox.setVgrow(errorTable, Priority.ALWAYS);

        statusBar.setMaxWidth(Double.MAX_VALUE);

        displayModeLabel.getStyleClass().add("toolbar-label");
        displayModeLabel.setStyle("-fx-font-weight: bold;");

        cancelButton.getStyleClass().addAll("toolbar-button", "cancel-button");
        cancelButton.visibleProperty().bind(vc.selectedProcessProperty().isNotNull());
        cancelButton.managedProperty().bind(vc.selectedProcessProperty().isNotNull());

        displayModeLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            ProcessElement p = vc.getSelectedProcess();
            if (p != null) {
                return "PID: " + p.pidLabel();
            }
            return "Все строки";
        }, vc.selectedProcessProperty()));

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

        scene.widthProperty().addListener((obs, oldV, newV) -> {
            leftPanel.setPrefWidth(newV.doubleValue() * LEFT_PANEL_RATIO);
        });
        scene.heightProperty().addListener((obs, oldV, newV) -> {
            bottomPanel.setPrefHeight(newV.doubleValue() * BOTTOM_PANEL_RATIO);
        });

        leftPanel.setPrefWidth(scene.getWidth() * LEFT_PANEL_RATIO);
        bottomPanel.setPrefHeight(scene.getHeight() * BOTTOM_PANEL_RATIO);

        return scene;
    }

}
