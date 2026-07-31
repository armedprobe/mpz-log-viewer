package com.mpzlog.mode;

import com.mpzlog.parser.ProcessElement;

import java.util.List;

public class ListProcessesMode implements ModeHandler {

    @Override
    public void execute(ModeContext ctx) {
        List<ProcessElement> list = ctx.getPa().getAllProcesses();
        if (list.isEmpty()) {
            ctx.getPrinter().line("Процессы МПЗ не найдены");
        } else {
            ctx.getPrinter().line("Найденные процессы МПЗ:");
            for (ProcessElement p : list) {
                ctx.getPrinter().printProcessLine(p);
            }
        }
        ctx.getPrinter().printTotalTime(ctx.getParser().getParseTimeMs());
        ctx.getPrinter().close();
        ctx.getPrinter().printSaved(ctx.getOutputFile());
    }
}
