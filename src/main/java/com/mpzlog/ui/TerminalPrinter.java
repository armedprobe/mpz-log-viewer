package com.mpzlog.ui;

import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.ProcessAnalyzer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TerminalPrinter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

    private PrintWriter fileWriter;

    public TerminalPrinter() {
    }

    public void setOutputFile(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        fileWriter = new PrintWriter(Files.newBufferedWriter(filePath, StandardCharsets.UTF_8));
    }

    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
    }

    public void printEntries(List<LogEntry> entries) {
        for (LogEntry e : entries) {
            printFull(e);
        }
    }

    public void printFull(LogEntry entry) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%4d ", entry.getLineNumber()));
        sb.append(entry.getTimestamp() != null ? entry.getTimestamp().format(TIME_FMT) : "??:??:??,???").append(" ");
        sb.append(entry.getLevel()).append(" ");

        if (entry.getSource() != null) {
            int lastDot = entry.getSource().lastIndexOf('.');
            String shortName = lastDot > 0 ? entry.getSource().substring(lastDot + 1) : entry.getSource();
            sb.append("[").append(shortName).append("] ");
        }

        if (entry.getThreadName() != null) {
            sb.append("(").append(entry.getThreadName()).append(") ");
        }

        if (entry.getMessage() != null) {
            String firstLine = entry.getFirstLine();
            sb.append(firstLine);
        }

        line(sb.toString());

        String msg = entry.getMessage();
        if (msg != null) {
            int nl = msg.indexOf('\n');
            if (nl > 0) {
                String rest = msg.substring(nl + 1);
                for (String ml : rest.split("\n")) {
                    line("        " + ml);
                }
            }
        }
    }

    public void line(String s) {
        System.out.println(s);
        if (fileWriter != null) {
            fileWriter.println(s);
        }
    }

    public void printSaved(Path outputFile) {
        if (outputFile != null) {
            System.out.println();
            System.out.println("Результат сохранён в файл: " + outputFile.toAbsolutePath());
        }
    }
    
    public void printTotalTime(long parseTimeMs) {
        line("═══════════════════════════════════════════════");
        line("Время парсинга: " + parseTimeMs + " мс");
        line("═══════════════════════════════════════════════");
    }

    public void printProcessLine(ProcessAnalyzer.ProcessInfo p) {
        String pname = p.processId != null ? p.processId : "?";
        line(pname + "=" + p.pid + " запросов/ответов: " + p.reqRespCount + ", строк: " + p.entryCount);
    }

    public void printHeader(int processCount, int totalLines, int criticalErrors) {
        line("═════════════════════════════════════════════");
        line("MPZ Log Viewer");
        line("═════════════════════════════════════════════");
        line("Процессов МПЗ: " + processCount);
        line("Всего строк: " + totalLines);
        line("Критических ошибок: " + criticalErrors);
    }

    public void printHeader(String process) {
        line("═════════════════════════════════════════════");
        line("MPZ Log Viewer");
        line("═════════════════════════════════════════════");
        line("Процесс МПЗ: " + process);
        line("═════════════════════════════════════════════");
    }
    
    public void printErrorProcessesTitle() {
        line("═════════════════════════════════════════════");
        line("Ошибочные процессы МПЗ:");
    }

    public void printFrequentErrorsTitle() {
        line("");
        line("═════════════════════════════════════════════");
        line(" Часто встречающиеся ошибки");
        line("═════════════════════════════════════════════");
    }

    public void printProcessHeader(String pid, String pname) {
        line("═════════════════════════════════════════════");
        line("Процесс: " + pid + " (" + pname + ")");
        line("═════════════════════════════════════════════");
    }
}
