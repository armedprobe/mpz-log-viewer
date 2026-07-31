package com.mpzlog.mode;

import java.util.List;

public class ListProcessesMode implements ModeHandler {

    @Override
    public void execute(ModeContext ctx) {
        List<String> pids = ctx.getPa().getProcessIds();
        if (pids.isEmpty()) {
            ctx.getPrinter().line("Процессы МПЗ не найдены");
        } else {
            ctx.getPrinter().line("Найденные процессы МПЗ:");
            for (String pid : pids) {
                ctx.getPrinter().printProcessLine(ctx.getPa().getProcess(pid));
            }
        }
        ctx.getPrinter().printTotalTime(ctx.getParser().getParseTimeMs());
        ctx.getPrinter().close();
        ctx.getPrinter().printSaved(ctx.getOutputFile());
    }
}
