package com.mpzlog.ui;

import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.ProcessElement;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TerminalPrinter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

    private final PrintWriter console;
    private PrintWriter fileWriter;

    public TerminalPrinter() {
        this.console = null;
    }

    public TerminalPrinter(PrintWriter console) {
        this.console = console;
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

    public void line() {
        line("");
    }

    public void line(String s) {
        if (console != null) {
            console.println(s);
        } else {
            System.out.println(s);
        }
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
        line();
        line("Время парсинга: " + parseTimeMs + " мс");
    }

    public void printProcessLine(ProcessElement p) {
        line(processLabel(p.pidLabel(), p.processId) + " [" + p.status.getLabel() + "] запросов/ответов: "
                + p.reqRespCount + ", первая строка: #" + p.firstLineNumber());
    }

    public String processLabel(String pid, String pname) {
        return "PID: " + pid + " (" + (pname != null ? pname : "?") + ")";
    }

    public void printHeader(int processCount, int totalLines, int criticalErrors) {
        line("Процессов МПЗ      : " + processCount);
        line("Всего строк        : " + totalLines);
        line("Критических ошибок : " + criticalErrors);
    }

    public void printHeader(int processCount) {
        line("Процессов МПЗ : " + processCount);
    }
    
    public void printErrorProcessesTitle() {
        line();
        line("Процессы МПЗ с ошибками :");
    }

    public void printFrequentErrorsTitle() {
        line();
        line("Найденные ошибки :");
        line();
    }

    public void printProcessHeader(ProcessElement p) {
        line();
        printProcessLine(p);
    }
}
