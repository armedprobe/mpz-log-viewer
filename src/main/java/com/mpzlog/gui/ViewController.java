package com.mpzlog.gui;

import com.mpzlog.model.ProcessElement;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class ViewController {

    private final ObjectProperty<ProcessElement> selectedProcess = new SimpleObjectProperty<>(this, "selectedProcess");
    private final StringProperty selectedErrorKey = new SimpleStringProperty(this, "selectedErrorKey");

    public ReadOnlyObjectProperty<ProcessElement> selectedProcessProperty() {
        return selectedProcess;
    }

    public ProcessElement getSelectedProcess() {
        return selectedProcess.get();
    }

    public ReadOnlyStringProperty selectedErrorKeyProperty() {
        return selectedErrorKey;
    }

    public String getSelectedErrorKey() {
        return selectedErrorKey.get();
    }

    public boolean isProcessSelected() {
        return selectedProcess.get() != null;
    }

    public boolean isProcessSelected(ProcessElement p) {
        return p != null && p == selectedProcess.get();
    }

    public boolean isErrorSelected(String key) {
        return key != null && key.equals(selectedErrorKey.get());
    }

    public void selectProcess(ProcessElement p) {
        selectedProcess.set(p);
    }

    public void clearProcessSelection() {
        selectedProcess.set(null);
    }

    public void selectErrorKey(String key) {
        selectedErrorKey.set(key);
    }

    public void clearErrorSelection() {
        selectedErrorKey.set(null);
    }

    public void clearAll() {
        selectedProcess.set(null);
        selectedErrorKey.set(null);
    }

}
