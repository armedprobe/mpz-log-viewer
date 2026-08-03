package com.mpzlog.model;

/**
 * Элемент Строка лога — одна запись лога, относящаяся к процессу.
 */
public final class LogLine {

    private final LogEntry entry;
    private final boolean isRequest;
    private final boolean isResponse;

    public LogLine(LogEntry entry, boolean isRequest, boolean isResponse) {
        this.entry = entry;
        this.isRequest = isRequest;
        this.isResponse = isResponse;
    }

    public LogEntry getEntry() {
        return entry;
    }

    public boolean isRequest() {
        return isRequest;
    }

    public boolean isResponse() {
        return isResponse;
    }
}