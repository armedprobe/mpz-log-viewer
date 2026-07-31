package com.mpzlog.mode;

import com.mpzlog.ModeOptions;
import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.MpzLogParser;
import com.mpzlog.parser.ProcessAnalyzer;
import com.mpzlog.ui.TerminalPrinter;

import java.nio.file.Path;
import java.util.List;

public class ModeContext {
    private final ProcessAnalyzer pa;
    private final List<LogEntry> entries;
    private final TerminalPrinter printer;
    private final Path outputFile;
    private final MpzLogParser parser;
    private final ModeOptions opts;

    public ModeContext(ProcessAnalyzer pa, List<LogEntry> entries, TerminalPrinter printer,
                       Path outputFile, MpzLogParser parser, ModeOptions opts) {
        this.pa = pa;
        this.entries = entries;
        this.printer = printer;
        this.outputFile = outputFile;
        this.parser = parser;
        this.opts = opts;
    }

    public ProcessAnalyzer getPa() { return pa; }
    public List<LogEntry> getEntries() { return entries; }
    public TerminalPrinter getPrinter() { return printer; }
    public Path getOutputFile() { return outputFile; }
    public MpzLogParser getParser() { return parser; }
    public ModeOptions getOpts() { return opts; }
}
