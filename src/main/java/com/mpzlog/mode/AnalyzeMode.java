package com.mpzlog.mode;

import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.ProcessAnalyzer;
import com.mpzlog.parser.ProcessElement;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AnalyzeMode implements ModeHandler {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

    private static final String CONTINUATION_INDENT = "             ";

    private static final Pattern KEY_TOKEN =
            Pattern.compile("ORA-\\d+|'[^']*'|\\d+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static String normalizeKey(String s) {
        StringBuilder masked = new StringBuilder(s.length());
        Matcher m = KEY_TOKEN.matcher(s);
        int last = 0;
        while (m.find()) {
            masked.append(s, last, m.start());
            String token = m.group();
            if (token.startsWith("ORA-")) {
                masked.append(token);
            } else if (token.startsWith("'")) {
                masked.append("'?'");
            } else {
                masked.append("#");
            }
            last = m.end();
        }
        masked.append(s, last, s.length());

        StringBuilder sb = new StringBuilder(masked.length());
        for (String line : masked.toString().split("\n")) {
            String trimmed = WHITESPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private static String errorText(LogEntry e) {
        String msg = e.getMessage();
        if (msg == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : msg.split("\n")) {
            if (line.trim().startsWith("at ")) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static boolean isExceptionEntry(LogEntry e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return e.getFirstLine().contains("Exception") && !e.getFirstLine().startsWith("Caused by");
    }

    private static int countCriticalErrors(ProcessAnalyzer pa) {
        int count = 0;
        for (ProcessElement p : pa.getAllProcesses()) {
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
            for (ProcessElement p : ctx.getPa().getAllProcesses()) {
                for (LogEntry e : p.allEntries) {
                    if (isExceptionEntry(e)) {
                        String key = normalizeKey(errorText(e));
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
                    String masked = group.getKey();
                    if (masked.isEmpty()) {
                        masked = normalizeKey(errorText(group.getValue().get(0)));
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
                String pid = p.pidLabel();
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
