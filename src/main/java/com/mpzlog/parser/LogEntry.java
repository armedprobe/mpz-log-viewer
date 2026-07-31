package com.mpzlog.parser;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogEntry {
    private LocalTime timestamp;
    private String level;
    private String source;
    private String threadName;
    private StringBuilder messageBuf;
    private String message;
    private final Map<String, String> fields;
    private int lineNumber;
    private String rawLine;

    public LogEntry() {
        this.fields = new LinkedHashMap<>();
    }

    public LocalTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalTime timestamp) { this.timestamp = timestamp; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }

    public String getMessage() {
        if (messageBuf != null && message == null) {
            message = messageBuf.toString();
        }
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        this.messageBuf = null;
    }

    public void appendMessage(String text) {
        if (messageBuf == null) {
            messageBuf = new StringBuilder();
            if (message != null) {
                messageBuf.append(message);
                message = null;
            }
        }
        messageBuf.append(text);
    }

    public Map<String, String> getFields() { return fields; }
    public void addField(String key, String value) { fields.put(key, value); }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public String getFirstLine() {
        String msg = getMessage();
        if (msg == null) return "";
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl) : msg;
    }

    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }
}
