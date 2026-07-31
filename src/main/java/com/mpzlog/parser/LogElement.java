package com.mpzlog.parser;

/**
 * Лист дерева модели лога: одна запись лога (обычная, handle-request или
 * handle-response). Для handle-записей содержит значение process-instance.
 */
public final class LogElement extends Element {

    public final LogEntry entry;
    public final String pid;
    public final boolean isRequest;
    public final boolean isResponse;

    public LogElement(LogEntry entry, String pid, boolean isRequest, boolean isResponse) {
        this.entry = entry;
        this.pid = pid;
        this.isRequest = isRequest;
        this.isResponse = isResponse;
    }
}
