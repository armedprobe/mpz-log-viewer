package com.mpzlog.mode;

import com.mpzlog.parser.LogEntry;

import java.util.List;

public class ProcessMode implements ModeHandler {

    @Override
    public void execute(ModeContext ctx) {
        if (!ctx.getPa().hasProcess(ctx.getOpts().getProcessId())) {
            ctx.getPrinter().line("Процесс с ID " + ctx.getOpts().getProcessId() + " не найден");
            ctx.getPrinter().close();
            return;
        }
        ctx.getPrinter().printProcessLine(ctx.getPa().getProcess(ctx.getOpts().getProcessId()));
        List<LogEntry> processEntries = ctx.getPa().getEntriesForProcess(ctx.getOpts().getProcessId());
        ctx.getPrinter().printEntries(processEntries);
        ctx.getPrinter().close();
    }
}
