package com.mpzlog.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель лога МПЗ.
 * <p>
 * Содержит список элементов Процесс и список Все Строки лога.
 * Модель строится единым проходом по записям лога ({@link LogModelBuilder}).
 */
public final class LogModel {

    private final List<ProcessElement> processes = new ArrayList<>();
    private final List<LogEntry> allLines = new ArrayList<>();
    private final List<ErrorGroupInfo> frequentErrors = new ArrayList<>();
    private final Map<String, ProcessElement> byPid = new LinkedHashMap<>();

    /** Список элементов Процесс. */
    public List<ProcessElement> getProcesses() {
        return processes;
    }

    /** Список Все Строки лога. */
    public List<LogEntry> getAllLines() {
        return allLines;
    }

    /** Список частых ошибок (топ-10 по маскированному ключу), либо пустой список. */
    public List<ErrorGroupInfo> getFrequentErrors() {
        return frequentErrors;
    }

    /** Процесс по PID, либо {@code null}. */
    public ProcessElement getProcess(String pid) {
        return byPid.get(pid);
    }

    public boolean hasProcess(String pid) {
        return byPid.containsKey(pid);
    }

    /** PID всех процессов (без процессов без PID). */
    public List<String> getProcessIds() {
        List<String> ids = new ArrayList<>();
        for (ProcessElement p : processes) {
            if (p.getPid() != null) {
                ids.add(p.getPid());
            }
        }
        return ids;
    }

    /** Строки лога процесса по PID (в порядке лога). */
    public List<LogEntry> getEntriesForProcess(String pid) {
        ProcessElement p = byPid.get(pid);
        if (p == null) {
            return Collections.emptyList();
        }
        List<LogEntry> entries = new ArrayList<>(p.getLines().size());
        for (LogLine line : p.getLines()) {
            entries.add(line.getEntry());
        }
        return entries;
    }

    void addProcess(ProcessElement p) {
        processes.add(p);
        if (p.getPid() != null) {
            byPid.put(p.getPid(), p);
        }
    }

    void removeProcess(ProcessElement p) {
        processes.remove(p);
        if (p.getPid() != null) {
            byPid.remove(p.getPid());
        }
    }

    void registerPid(ProcessElement p) {
        if (p.getPid() != null) {
            byPid.put(p.getPid(), p);
        }
    }

    void addLine(LogEntry e) {
        allLines.add(e);
    }

    void setFrequentErrors(List<ErrorGroupInfo> errors) {
        frequentErrors.clear();
        frequentErrors.addAll(errors);
    }
}
