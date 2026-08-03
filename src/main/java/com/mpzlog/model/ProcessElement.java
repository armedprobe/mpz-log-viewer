package com.mpzlog.model;

import java.util.List;

/**
 * Элемент Процесс.
 * <p>
 * Поля: PID, processName, Status, трэд последнего найденного запроса (для
 * сопоставления с прочими записями). Подчинены элементы Строка лога (все
 * строки, относящиеся к процессу) и элементы Ошибка.
 */
public final class ProcessElement {

    public enum Status {
        COMPLETED("завершён успешно"),
        COMPLETED_WITH_ERROR("завершён с ошибкой"),
        INTERRUPTED("прерван"),
        FAILED("ошибка"),
        UNRESOLVED("без ответа");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private String pid;
    private String processName;
    private Status status;
    private String task;
    private final List<LogLine> lines = new java.util.ArrayList<>();
    private final List<ErrorElement> errors = new java.util.ArrayList<>();

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /** Трэд последнего найденного запроса (для сопоставления с прочими записями). */
    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    /** Подчинённые элементы Строка лога (все строки, относящиеся к процессу). */
    public List<LogLine> getLines() {
        return lines;
    }

    /** Подчинённые элементы Ошибка. */
    public List<ErrorElement> getErrors() {
        return errors;
    }

    /** PID процесса в едином формате ({@code #0}, если PID не определён). */
    public String pidLabel() {
        return pid != null ? pid : "#0";
    }

    /** Номер первой строки журнала, относящейся к процессу. */
    public int firstLineNumber() {
        return lines.isEmpty() ? 0 : lines.get(0).getEntry().getLineNumber();
    }
}
