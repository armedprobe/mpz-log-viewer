package com.mpzlog.mode;

import com.mpzlog.parser.LogEntry;
import com.mpzlog.parser.ProcessElement;

import java.util.ArrayList;
import java.util.List;

public class GrepMode implements ModeHandler {

    public static final int GREP_LIMIT = 25;

    private final int limit;

    public GrepMode(int limit) {
        this.limit = limit;
    }

    @Override
    public void execute(ModeContext ctx) {
        List<ProcessElement> matched = new ArrayList<>();
        for (ProcessElement p : ctx.getPa().getAllProcesses()) {
            for (LogEntry e : p.allEntries) {
                if (e.getMessage() != null && e.getMessage().contains(ctx.getOpts().getGrepText())) {
                    matched.add(p);
                    break;
                }
            }
        }
        if (matched.isEmpty()) {
            ctx.getPrinter().line("Процессы МПЗ не найдены");
        } else {
            boolean tooMany = ctx.getOpts().getGrepText().length() < 3 && matched.size() > limit;
            int actual = tooMany ? limit : matched.size();
            ctx.getPrinter().line("Найденные процессы МПЗ (содержат \"" + ctx.getOpts().getGrepText() + "\"):");
            for (int i = 0; i < actual; i++) {
                ctx.getPrinter().printProcessLine(matched.get(i));
            }
            if (tooMany) {
                ctx.getPrinter().line("Найдено более " + limit + " процессов, уточните поиск");
            }
        }
        ctx.getPrinter().close();
    }
}
