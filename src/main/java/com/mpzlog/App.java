package com.mpzlog;

import com.mpzlog.mode.ModeContext;
import com.mpzlog.mode.ModeFactory;
import com.mpzlog.mode.ModeHandler;
import com.mpzlog.parser.MpzLogParser;
import com.mpzlog.parser.ProcessAnalyzer;
import com.mpzlog.ui.TerminalPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App {

    public static final int GREP_LIMIT = 25;

    public static void main(String[] args) {
        CliParser cli = CliParser.parse(args);

        if (cli.isHelp() || cli.getFiles().isEmpty()) {
            CliParser.printUsage();
            return;
        }

        for (String fileArg : cli.getFiles()) {
            processFile(fileArg, cli);
        }
    }

    private static void processFile(String fileArg, CliParser cli) {
        Path path = Paths.get(fileArg);

        if (!Files.exists(path)) {
            System.err.println("Файл не найден: " + path.toAbsolutePath());
            return;
        }

        if (!Files.isReadable(path)) {
            System.err.println("Нет доступа для чтения: " + path.toAbsolutePath());
            return;
        }

        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
            return;
        }

        TerminalPrinter printer = new TerminalPrinter();

        ModeOptions modeOpts = cli.getMode();
        if (!modeOpts.isAnalyze() && !modeOpts.isListProcesses() && modeOpts.getProcessId() == null && modeOpts.getGrepText() == null) {
            modeOpts.setAnalyze(true);
        }
        Path outputFile = null;
        if (modeOpts.isOutputFile()) {
            if (modeOpts.isListProcesses()) {
                outputFile = buildOutputPath(path, "_processes.txt");
            } else if (modeOpts.getProcessId() != null) {
                outputFile = buildOutputPath(path, "_" + modeOpts.getProcessId() + ".txt");
            } else if (modeOpts.getGrepText() != null) {
                outputFile = buildOutputPath(path,
                        "_grep_" + sanitizeForFilename(modeOpts.getGrepText()) + ".txt");
            } else {
                outputFile = buildOutputPath(path, "_analyze.txt");
            }
            try {
                printer.setOutputFile(outputFile);
            } catch (IOException e) {
                System.err.println("Ошибка создания файла вывода: " + e.getMessage());
                return;
            }
        }

        System.out.println();
        System.out.println("Файл: " + path.toAbsolutePath() + " (" + formatSize(fileSize) + ")");

        MpzLogParser parser = new MpzLogParser();
        try {
            parser.parse(path);
        } catch (IOException e) {
            System.err.println("Ошибка при парсинге: " + e.getMessage());
            printer.close();
            return;
        }

        ProcessAnalyzer pa = new ProcessAnalyzer(parser.getEntries());

        ModeHandler mode = ModeFactory.create(modeOpts);
        mode.execute(new ModeContext(pa, parser.getEntries(), printer,
                outputFile, parser, modeOpts));
    }

    private static Path buildOutputPath(Path sourcePath, String suffix) {
        String fileName = sourcePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            fileName = fileName.substring(0, dot) + suffix;
        } else {
            fileName = fileName + suffix;
        }
        return sourcePath.resolveSibling(fileName);
    }

    private static String sanitizeForFilename(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= 31 || c == '<' || c == '>' || c == ':'
                    || c == '"' || c == '/' || c == '\\'
                    || c == '|' || c == '?' || c == '*') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
