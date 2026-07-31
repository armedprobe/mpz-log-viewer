package com.mpzlog.mode;

import com.mpzlog.App;
import com.mpzlog.ModeOptions;

public class ModeFactory {
    public static ModeHandler create(ModeOptions opts) {
        if (opts.isAnalyze()) return new AnalyzeMode();
        if (opts.getGrepText() != null) return new GrepMode(App.GREP_LIMIT);
        if (opts.getProcessId() != null) return new ProcessMode();
        if (opts.isListProcesses()) return new ListProcessesMode();
        return new AnalyzeMode();
    }
}
