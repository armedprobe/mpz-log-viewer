package com.mpzlog.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Узел-процесс МПЗ. Содержит дерево своих элементов (children): в порядке лога
 * handle-request, обычные записи и handle-response процесса.
 * <p>
 * Процесс начинается с {@code handle-request} с {@code process-instance=0}.
 * PID присваивается из первого ответа с новым значением process-instance.
 * Если ответ не пришёл или вернул {@code process-instance=0} (ошибка), PID не
 * определяется — такие процессы хранятся отдельными записями с пометкой
 * {@link Status}.
 */
public final class ProcessElement extends Element {

    public enum Status {
        COMPLETED("завершён"),
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

    public String pid;
    public String processId;
    public String task;
    public Status status;
    public int startIndex;
    public final List<Element> children = new ArrayList<>();
    public final List<LogEntry> allEntries = new ArrayList<>();
    public int entryCount;
    public int reqRespCount;

    public String pidLabel() {
        return pid != null ? pid : "#0";
    }

    /** Номер первой строки журнала, относящейся к процессу. */
    public int firstLineNumber() {
        if (allEntries.isEmpty()) {
            return 0;
        }
        return allEntries.get(0).getLineNumber();
    }
}
