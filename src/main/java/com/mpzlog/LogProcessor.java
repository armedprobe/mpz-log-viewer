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

public final class LogProcessor {

    private LogProcessor() {
    }

    public static void process(Path path, ModeOptions opts, TerminalPrinter printer, Path outputFile) {
        if (!Files.exists(path)) {
            printer.line("Файл не найден: " + path.toAbsolutePath());
            printer.close();
            return;
        }

        if (!Files.isReadable(path)) {
            printer.line("Нет доступа для чтения: " + path.toAbsolutePath());
            printer.close();
            return;
        }

        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            printer.line("Ошибка чтения файла: " + e.getMessage());
            printer.close();
            return;
        }

        MpzLogParser parser = new MpzLogParser();
        try {
            parser.parse(path);
        } catch (IOException e) {
            printer.line("Ошибка при парсинге: " + e.getMessage());
            printer.close();
            return;
        }

        if (opts.isDefault()) {
            opts.setAnalyze(true);
        }

        ProcessAnalyzer pa = new ProcessAnalyzer(parser.getEntries());
        ModeHandler mode = ModeFactory.create(opts);
        mode.execute(new ModeContext(pa, parser.getEntries(), printer,
                outputFile, parser, opts));
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
