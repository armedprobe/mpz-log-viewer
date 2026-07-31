package com.mpzlog;

public class ModeOptions {
    private boolean analyze = false;
    private boolean listProcesses = false;
    private boolean outputFile = false;
    private String processId = null;
    private String grepText = null;

    public boolean isDefault() {
        return !analyze && !listProcesses && processId == null && grepText == null;
    }

    public boolean isAnalyze() { return analyze; }
    public void setAnalyze(boolean analyze) { this.analyze = analyze; }

    public boolean isListProcesses() { return listProcesses; }
    public void setListProcesses(boolean listProcesses) { this.listProcesses = listProcesses; }

    public boolean isOutputFile() { return outputFile; }
    public void setOutputFile(boolean outputFile) { this.outputFile = outputFile; }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public String getGrepText() { return grepText; }
    public void setGrepText(String grepText) { this.grepText = grepText; }
}
