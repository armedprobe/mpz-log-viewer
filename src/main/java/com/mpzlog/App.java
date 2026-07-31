package com.mpzlog;

import com.mpzlog.ui.TerminalPrinter;

import java.io.IOException;
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

        ModeOptions modeOpts = cli.getMode();
        if (modeOpts.isDefault()) {
            modeOpts.setAnalyze(true);
        }

        TerminalPrinter printer = new TerminalPrinter();

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

        LogProcessor.process(path, modeOpts, printer, outputFile);
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
}
