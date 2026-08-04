package com.mpzlog.model;

import java.util.Set;

/**
 * Группа ошибок: количество повторений, маскированный ключ, процессы,
 * в которых встретилась данная ошибка.
 */
public final class ErrorGroupInfo {

    private final int count;
    private final String errorKey;
    private final Set<ProcessElement> processes;

    public ErrorGroupInfo(int count, String errorKey, Set<ProcessElement> processes) {
        this.count = count;
        this.errorKey = errorKey;
        this.processes = processes;
    }

    public int getCount() {
        return count;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public Set<ProcessElement> getProcesses() {
        return processes;
    }
}
