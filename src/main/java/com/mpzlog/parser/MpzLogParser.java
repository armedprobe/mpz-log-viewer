package com.mpzlog.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class MpzLogParser {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

    private final List<LogEntry> entries = new ArrayList<>();
    private long parseTimeMs;
    private int totalLines;

    public void parse(Path filePath) throws IOException {
        long start = System.currentTimeMillis();
        LogEntry current = null;
        int lineNo = 0;

        try (BufferedReader r = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = r.readLine()) != null) {
                lineNo++;
                totalLines++;
                if (line.isEmpty()) continue;

                if (isTimeLine(line)) {
                    if (current != null) {
                        entries.add(current);
                    }
                    current = parseHeader(line, lineNo);
                } else {
                    if (current != null) {
                        current.appendMessage("\n" + line);
                    }
                }
            }
        }

        if (current != null) {
            entries.add(current);
        }

        parseTimeMs = System.currentTimeMillis() - start;
    }

    private LogEntry parseHeader(String line, int lineNo) {
        LogEntry e = new LogEntry();
        e.setLineNumber(lineNo);
        e.setRawLine(line);

        if (line.length() >= 12) {
            try {
                e.setTimestamp(LocalTime.parse(line.substring(0, 12), TIME_FMT));
            } catch (DateTimeParseException ex) {
                e.setTimestamp(null);
            }
        }

        int levelEnd = line.indexOf(' ', 13);
        if (levelEnd > 13) {
            e.setLevel(line.substring(13, levelEnd));
        }

        int bracketOpen = line.indexOf('[', levelEnd);
        int bracketClose = bracketOpen >= 0 ? line.indexOf(']', bracketOpen) : -1;
        if (bracketOpen >= 0 && bracketClose > bracketOpen) {
            e.setSource(line.substring(bracketOpen + 1, bracketClose));
        }

        int parenOpen = line.indexOf('(', bracketClose);
        int parenClose = parenOpen >= 0 ? line.indexOf(')', parenOpen) : -1;
        if (parenOpen >= 0 && parenClose > parenOpen) {
            e.setThreadName(line.substring(parenOpen + 1, parenClose));
            if (parenClose + 2 < line.length()) {
                e.setMessage(line.substring(parenClose + 2));
            }
        } else if (levelEnd > 13) {
            e.setMessage(line.substring(levelEnd + 1));
        }

        return e;
    }

    private static boolean isTimeLine(String s) {
        int len = s.length();
        if (len < 13) return false;
        if (s.charAt(2) != ':' || s.charAt(5) != ':' || s.charAt(8) != ',' || s.charAt(12) != ' ')
            return false;
        for (int i = 0; i <= 11; i++) {
            if (i == 2 || i == 5 || i == 8) continue;
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public List<LogEntry> getEntries() { return entries; }
    public long getParseTimeMs() { return parseTimeMs; }
    public int getEntryCount() { return entries.size(); }
    public int getTotalPhysicalLines() { return totalLines; }
}
