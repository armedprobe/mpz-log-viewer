package com.mpzlog.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessAnalyzer {

    private static final Pattern PI_PATTERN =
            Pattern.compile("<process-instance>(\\d+)</process-instance>");
    private static final Pattern PROC_ID_PATTERN =
            Pattern.compile("<process-id>([^<]+)</process-id>");

    private final List<LogEntry> entries;
    private final Map<String, ProcessInfo> processes = new LinkedHashMap<>();

    public ProcessAnalyzer(List<LogEntry> entries) {
        this.entries = entries;
        analyze();
    }

    private void analyze() {
        Map<String, ProcessInfo> startByThread = new LinkedHashMap<>();
        Map<String, ProcessInfo> activeOnThread = new LinkedHashMap<>();

        for (int i = 0; i < entries.size(); i++) {
            LogEntry e = entries.get(i);
            String msg = e.getMessage();
            if (msg == null) continue;

            String pi = extractProcessInstance(msg);
            boolean isRequest = msg.contains("handle-request:");
            boolean isResponse = msg.contains("handle-response:");

            String thread = e.getThreadName();
            ProcessInfo active = activeOnThread.get(thread);

            if (isRequest && "0".equals(pi)) {
                ProcessInfo old = startByThread.get(thread);
                ProcessInfo p = new ProcessInfo(null);
                if (old != null && old.pid != null) {
                    p.parentId = old.pid;
                }
                p.processId = extractProcessId(msg);
                p.startIndices.add(i);
                p.allEntries.add(e);
                p.entryCount++;
                p.reqRespCount++;
                startByThread.put(thread, p);
                activeOnThread.put(thread, p);
            }

            if (isRequest && pi != null && !"0".equals(pi)) {
                ProcessInfo known = processes.get(pi);
                if (known != null) {
                    known.allEntries.add(e);
                    known.entryCount++;
                    known.reqRespCount++;
                    activeOnThread.put(thread, known);
                } else if (active != null) {
                    active.allEntries.add(e);
                    active.entryCount++;
                    active.reqRespCount++;
                }
            }

            if (isResponse && pi != null && !"0".equals(pi)) {
                ProcessInfo pend = startByThread.get(thread);
                if (pend != null) {
                    pend.allEntries.add(e);
                    pend.entryCount++;
                    pend.reqRespCount++;
                    if (pend.pid == null) {
                        pend.pid = pi;
                        ProcessInfo existing = processes.get(pi);
                        if (existing != null) {
                            mergeInto(existing, pend);
                        } else {
                            processes.put(pi, pend);
                        }
                    }
                } else if (active != null) {
                    active.allEntries.add(e);
                    active.entryCount++;
                    active.reqRespCount++;
                }
                activeOnThread.put(thread, null);
            }

            if (isResponse && "0".equals(pi)) {
                ProcessInfo pend = startByThread.get(thread);
                if (pend != null && pend.pid == null && pend.parentId != null) {
                    ProcessInfo parent = processes.get(pend.parentId);
                    if (parent != null) {
                        for (LogEntry pe : pend.allEntries) {
                            parent.allEntries.add(pe);
                            parent.entryCount++;
                            if (pe.getMessage() != null
                                    && (pe.getMessage().contains("handle-request:")
                                        || pe.getMessage().contains("handle-response:"))) {
                                parent.reqRespCount++;
                            }
                        }
                        parent.allEntries.add(e);
                        parent.entryCount++;
                        parent.reqRespCount++;
                    } else if (active != null) {
                        active.allEntries.add(e);
                        active.entryCount++;
                        active.reqRespCount++;
                    }
                } else if (active != null) {
                    active.allEntries.add(e);
                    active.entryCount++;
                    active.reqRespCount++;
                }
                activeOnThread.put(thread, null);
            }

            if (!isRequest && !isResponse) {
                if (active != null) {
                    active.allEntries.add(e);
                    active.entryCount++;
                }
            }
        }
    }

    private void mergeInto(ProcessInfo target, ProcessInfo source) {
        target.allEntries.addAll(source.allEntries);
        target.entryCount += source.entryCount;
        target.reqRespCount += source.reqRespCount;
        target.startIndices.addAll(source.startIndices);
        if (source.processId != null && target.processId == null) {
            target.processId = source.processId;
        }
        if (source.parentId != null && target.parentId == null) {
            target.parentId = source.parentId;
        }
    }

    public List<String> getProcessIds() {
        return new ArrayList<>(processes.keySet());
    }

    public ProcessInfo getProcess(String id) {
        return processes.get(id);
    }

    public boolean hasProcess(String id) {
        return processes.containsKey(id);
    }

    public List<ProcessInfo> getAllProcesses() {
        return new ArrayList<>(processes.values());
    }

    public List<LogEntry> getEntriesForProcess(String id) {
        ProcessInfo p = processes.get(id);
        return p != null ? p.allEntries : Collections.emptyList();
    }

    private String extractProcessInstance(String text) {
        Matcher m = PI_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String extractProcessId(String text) {
        Matcher m = PROC_ID_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    public static class ProcessInfo {
        public String pid;
        public String processId;
        public String parentId;
        public final Set<String> threads = new LinkedHashSet<>();
        public final List<LogEntry> allEntries = new ArrayList<>();
        public final Set<Integer> startIndices = new LinkedHashSet<>();
        public int entryCount;
        public int reqRespCount;

        ProcessInfo(String pid) {
            this.pid = pid;
        }
    }
}
