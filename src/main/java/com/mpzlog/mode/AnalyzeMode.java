package com.mpzlog.mode;

import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.ProcessAnalyzer;
import com.mpzlog.parser.ProcessElement;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnalyzeMode implements ModeHandler {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

    private static final String CONTINUATION_INDENT = "             ";

    @Override
    public void execute(ModeContext ctx) {
        int processCount = ctx.getPa().getAllProcesses().size();
        int totalLines = ctx.getParser().getTotalPhysicalLines();
        int criticalErrors = ctx.getPa().getCriticalErrorsCount();
        ctx.getPrinter().printHeader(processCount, totalLines, criticalErrors);
        if (criticalErrors > 0) {
            Map<String, List<LogEntry>> errorGroups = new LinkedHashMap<>();
            for (ProcessElement p : ctx.getPa().getAllProcesses()) {
                for (LogEntry e : p.allEntries) {
                    if (e.isException()) {
                        errorGroups.computeIfAbsent(e.getErrorKey(), k -> new ArrayList<>()).add(e);
                    }
                }
            }

            List<Map.Entry<String, List<LogEntry>>> topErrors = errorGroups.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .limit(10)
                    .collect(Collectors.toList());

            if (!topErrors.isEmpty()) {
                ctx.getPrinter().printFrequentErrorsTitle();
                for (Map.Entry<String, List<LogEntry>> group : topErrors) {
                    String masked = group.getKey();
                    if (masked.isEmpty()) {
                        masked = group.getValue().get(0).getErrorKey();
                    }
                    String[] lines = masked.split("\n", -1);
                    ctx.getPrinter().line(String.format("%3d раз(а) — %s", group.getValue().size(), lines[0]));
                    for (int i = 1; i < lines.length; i++) {
                        ctx.getPrinter().line(CONTINUATION_INDENT + lines[i]);
                    }
                }
            }

            ctx.getPrinter().printErrorProcessesTitle();
            boolean firstProcess = true;
            for (ProcessElement p : ctx.getPa().getAllProcesses()) {
                boolean hasError = false;
                for (LogEntry e : p.allEntries) {
                    if (e.isException()) {
                        hasError = true;
                        break;
                    }
                }
                if (!hasError) continue;
                if (firstProcess) {
                    firstProcess = false;
                }
                ctx.getPrinter().printProcessHeader(p);
                for (LogEntry e : p.allEntries) {
                    if (!e.isException()) continue;
                    StringBuilder sb = new StringBuilder();
                    if (e.getTimestamp() != null) {
                        sb.append(e.getTimestamp().format(TIME_FMT)).append(" ");
                    }
                    sb.append(e.getLevel()).append(" ");
                    if (e.getThreadName() != null) {
                        sb.append("(").append(e.getThreadName()).append(") ");
                    }
                    sb.append(e.getFirstLine());
                    ctx.getPrinter().line(sb.toString());
                    String errorText = e.getErrorText();
                    if (errorText != null && !errorText.isEmpty()) {
                        for (String line : errorText.split("\n")) {
                            ctx.getPrinter().line(line);
                        }
                    }
                }
            }
        }
        ctx.getPrinter().printTotalTime(ctx.getParser().getParseTimeMs());
        ctx.getPrinter().close();
        ctx.getPrinter().printSaved(ctx.getOutputFile());
    }
}