package com.mpzlog.mode;

import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.ProcessAnalyzer;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AnalyzeMode implements ModeHandler {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("'[^']*'");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static String normalizeKey(String s) {
        String normalized = DIGITS_PATTERN.matcher(s).replaceAll("#");
        normalized = QUOTED_VALUE_PATTERN.matcher(normalized).replaceAll("'?'");
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    private static boolean isExceptionEntry(LogEntry e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return e.getFirstLine().contains("Exception") && !e.getFirstLine().startsWith("Caused by");
    }

    private static int countCriticalErrors(ProcessAnalyzer pa) {
        int count = 0;
        for (ProcessAnalyzer.ProcessInfo p : pa.getAllProcesses()) {
            for (LogEntry e : p.allEntries) {
                if (isExceptionEntry(e)) count++;
            }
        }
        return count;
    }

    @Override
    public void execute(ModeContext ctx) {
        int processCount = ctx.getPa().getAllProcesses().size();
        int totalLines = ctx.getParser().getTotalPhysicalLines();
        int criticalErrors = countCriticalErrors(ctx.getPa());
        ctx.getPrinter().printHeader(processCount, totalLines, criticalErrors);
        if (criticalErrors > 0) {
            Map<String, List<LogEntry>> errorGroups = new LinkedHashMap<>();
            for (ProcessAnalyzer.ProcessInfo p : ctx.getPa().getAllProcesses()) {
                for (LogEntry e : p.allEntries) {
                    if (isExceptionEntry(e)) {
                        String key = normalizeKey(e.getFirstLine());
                        errorGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
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
                    String key = group.getKey();
                    if (key.isEmpty()) {
                        key = group.getValue().get(0).getFirstLine();
                    }
                    ctx.getPrinter().line(String.format("%3d раз(а) — %s", group.getValue().size(), key));
                }
            }

            ctx.getPrinter().printErrorProcessesTitle();
            boolean firstProcess = true;
            for (ProcessAnalyzer.ProcessInfo p : ctx.getPa().getAllProcesses()) {
                boolean hasError = false;
                for (LogEntry e : p.allEntries) {
                    if (isExceptionEntry(e)) {
                        hasError = true;
                        break;
                    }
                }
                if (!hasError) continue;
                if (firstProcess) {
                    firstProcess = false;
                }
                String pname = p.processId != null ? p.processId : "?";
                String pid = p.pid != null ? p.pid : "?";
                ctx.getPrinter().printProcessHeader(pid, pname);
                for (LogEntry e : p.allEntries) {
                    if (!isExceptionEntry(e)) continue;
                    String msg = e.getMessage();
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
                    int nl = msg.indexOf('\n');
                    if (nl > 0) {
                        String rest = msg.substring(nl + 1);
                        for (String ml : rest.split("\n")) {
                            String trimmed = ml.trim();
                            if (trimmed.startsWith("at ") && !trimmed.startsWith("at by.softclub.mpz.")) break;
                            ctx.getPrinter().line(ml);
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
