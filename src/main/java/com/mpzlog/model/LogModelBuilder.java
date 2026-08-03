package com.mpzlog.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Строит {@link LogModel} единым проходом по записям лога.
 * <p>
 * Алгоритм (по AGENTS.md, раздел «Модель»):
 * <ul>
 *   <li>{@code handle-request pi=0} — старт нового процесса (status {@code UNRESOLVED});</li>
 *   <li>{@code handle-request pi=PID} — повторный вызов: в известный процесс, иначе новый процесс с этим PID;</li>
 *   <li>прочая запись — в процесс с равным трэдом;</li>
 *   <li>{@code handle-response}: PID известен → в него; PID неизвестен → в процесс с равным трэдом с
 *       неопределённым PID (ответ на первый запрос); иначе непривязанный (пер-трэдовый процесс);</li>
 *   <li>запись-ошибка добавляется в процесс и его Ошибки, считается ключ ошибки;</li>
 *   <li>непривязанные записи объединяются по трэду;</li>
 *   <li>если стартовый request получил ответ с уже известным PID — новый процесс сливается с существующим.</li>
 * </ul>
 * Статусы процессов вычисляются после прохода ({@link ProcessElement.Status}).
 */
public final class LogModelBuilder {

    private static final Pattern PI_PATTERN =
            Pattern.compile("<process-instance>(\\d+)</process-instance>");
    private static final Pattern PROC_ID_PATTERN =
            Pattern.compile("<process-id>([^<]+)</process-id>");
    private static final Pattern END_OF_PROCESS_PATTERN =
            Pattern.compile("<type>\\s*END_OF_PROCESS\\s*</type>");
    private static final Pattern ERROR_TYPE_PATTERN =
            Pattern.compile("<type>\\s*ERROR\\s*</type>");
    private static final Pattern KEY_TOKEN =
            Pattern.compile("ORA-\\d+|'[^']*'|\\d+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final LogModel model = new LogModel();
    private final Map<String, Deque<ProcessElement>> stackByTask = new LinkedHashMap<>();
    private final Map<String, ProcessElement> unboundByTask = new LinkedHashMap<>();
    private final Map<String, ProcessElement> byPid = new LinkedHashMap<>();

    public LogModel build(List<LogEntry> entries) {
        for (LogEntry e : entries) {
            model.addLine(e);
            String msg = e.getMessage();
            String pi = msg == null ? null : extractProcessInstance(msg);
            boolean isRequest = msg != null && msg.contains("handle-request:");
            boolean isResponse = msg != null && msg.contains("handle-response:");
            String task = e.getThreadName() != null ? e.getThreadName() : "";

            ProcessElement owner;
            if (isRequest) {
                owner = handleRequest(e, task, pi);
            } else if (isResponse) {
                owner = handleResponse(e, task, pi);
            } else {
                owner = handleOrdinary(e, task);
            }

            if (owner != null && isException(msg) && !hasErrorFor(owner, e)) {
                owner.getErrors().add(makeError(e));
            }
        }
        finalizeStatuses();
        return model;
    }

    // ---- не удаляет дубли: ошибка добавляется в первый подходящий процесс ----

    ProcessElement handleRequest(LogEntry e, String task, String pi) {
        Deque<ProcessElement> stack = stackByTask.computeIfAbsent(task, k -> new ArrayDeque<>());
        if ("0".equals(pi)) {
            ProcessElement p = new ProcessElement();
            p.setTask(task);
            p.setProcessName(extractName(e.getMessage()));
            p.setStatus(ProcessElement.Status.UNRESOLVED);
            p.getLines().add(new LogLine(e, true, false));
            model.addProcess(p);
            stack.push(p);
            return p;
        }
        if (pi != null) {
            ProcessElement known = byPid.get(pi);
            if (known != null) {
                known.setTask(task);
                known.getLines().add(new LogLine(e, true, false));
                stack.push(known);
                return known;
            }
            ProcessElement p = new ProcessElement();
            p.setPid(pi);
            p.setTask(task);
            p.setProcessName(extractName(e.getMessage()));
            p.setStatus(ProcessElement.Status.UNRESOLVED);
            p.getLines().add(new LogLine(e, true, false));
            model.addProcess(p);
            byPid.put(pi, p);
            stack.push(p);
            return p;
        }
        return handleOrdinary(e, task);
    }

    ProcessElement handleResponse(LogEntry e, String task, String pi) {
        Deque<ProcessElement> stack = stackByTask.computeIfAbsent(task, k -> new ArrayDeque<>());
        boolean piReal = pi != null && !"0".equals(pi);

        if (piReal) {
            ProcessElement known = byPid.get(pi);
            if (known != null) {
                ProcessElement pending = findPendingStart(stack, e.getMessage());
                if (pending != null && pending != known) {
                    // стартовый request получил ответ с уже известным PID — слияние
                    mergeInto(known, pending);
                    model.removeProcess(pending);
                    stack.remove(pending);
                }
                known.getLines().add(new LogLine(e, false, true));
                closeOwnerWindow(stack, known);
                return known;
            }
            ProcessElement pending = findPendingStart(stack, e.getMessage());
            if (pending != null) {
                pending.setPid(pi);
                pending.setTask(task);
                pending.getLines().add(new LogLine(e, false, true));
                byPid.put(pi, pending);
                closeOwnerWindow(stack, pending);
                return pending;
            }
        }
        // ответ непривязанный (pi=0 / null)
        return unboundProcess(task, e, false, true);
    }

    ProcessElement handleOrdinary(LogEntry e, String task) {
        Deque<ProcessElement> stack = stackByTask.get(task);
        if (stack != null && !stack.isEmpty()) {
            ProcessElement top = stack.peek();
            top.getLines().add(new LogLine(e, false, false));
            return top;
        }
        return unboundProcess(task, e, false, false);
    }

    private ProcessElement unboundProcess(String task, LogEntry e, boolean isRequest, boolean isResponse) {
        ProcessElement u = unboundByTask.get(task);
        if (u == null) {
            u = new ProcessElement();
            u.setTask(task);
            u.setStatus(ProcessElement.Status.UNRESOLVED);
            model.addProcess(u);
            unboundByTask.put(task, u);
        }
        u.getLines().add(new LogLine(e, isRequest, isResponse));
        return u;
    }

    /**
     * Ищет среди открытых окон треда самый старый процесс с неопределённым PID.
     * Сначала — по совпадению process-name, при отсутствии совпадений — самый старый.
     */
    private ProcessElement findPendingStart(Deque<ProcessElement> stack, String msg) {
        String procId = extractName(msg);
        ProcessElement fallback = null;
        for (Iterator<ProcessElement> it = stack.descendingIterator(); it.hasNext(); ) {
            ProcessElement p = it.next();
            if (p.getPid() != null) {
                continue;
            }
            if (procId != null && procId.equals(p.getProcessName())) {
                return p;
            }
            if (fallback == null) {
                fallback = p;
            }
        }
        return fallback;
    }

    /** Закрывает окно владельца и все более старые открытые окна треда. */
    private void closeOwnerWindow(Deque<ProcessElement> stack, ProcessElement owner) {
        if (!stack.contains(owner)) {
            return;
        }
        for (Iterator<ProcessElement> it = stack.descendingIterator(); it.hasNext(); ) {
            ProcessElement p = it.next();
            it.remove();
            if (p == owner) {
                break;
            }
        }
    }

    private void mergeInto(ProcessElement target, ProcessElement source) {
        target.getLines().addAll(source.getLines());
        target.getErrors().addAll(source.getErrors());
        if (source.getTask() != null) {
            target.setTask(source.getTask());
        }
        if (target.getProcessName() == null) {
            target.setProcessName(source.getProcessName());
        }
        if (target.getStatus() == null && source.getStatus() != null) {
            target.setStatus(source.getStatus());
        }
    }

    private void finalizeStatuses() {
        for (ProcessElement p : model.getProcesses()) {
            boolean endOfProcess = false;
            boolean errorResponse = false;
            for (LogLine line : p.getLines()) {
                if (!line.isResponse() || line.getEntry().getMessage() == null) {
                    continue;
                }
                String m = line.getEntry().getMessage();
                if (END_OF_PROCESS_PATTERN.matcher(m).find()) {
                    endOfProcess = true;
                }
                if (ERROR_TYPE_PATTERN.matcher(m).find()) {
                    errorResponse = true;
                }
            }
            boolean hasPid = p.getPid() != null;
            if (hasPid && endOfProcess) {
                p.setStatus(ProcessElement.Status.COMPLETED);
            } else if (hasPid && errorResponse) {
                p.setStatus(ProcessElement.Status.COMPLETED_WITH_ERROR);
            } else if (hasPid) {
                p.setStatus(ProcessElement.Status.INTERRUPTED);
            } else if (!p.getErrors().isEmpty()) {
                p.setStatus(ProcessElement.Status.FAILED);
            } else {
                p.setStatus(ProcessElement.Status.UNRESOLVED);
            }
        }
    }

    // ---- извлечение данных ----

    private static boolean isException(String msg) {
        if (msg == null) {
            return false;
        }
        String first = msg;
        int nl = msg.indexOf('\n');
        if (nl > 0) {
            first = msg.substring(0, nl);
        }
        return first.contains("Exception") && !first.startsWith("Caused by");
    }

    private ErrorElement makeError(LogEntry e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String shortText = extractErrorText(msg);
        return new ErrorElement(e, msg, normalizeKey(shortText), shortText);
    }

    private static boolean hasErrorFor(ProcessElement owner, LogEntry e) {
        for (ErrorElement err : owner.getErrors()) {
            if (err.getEntry() == e) {
                return true;
            }
        }
        return false;
    }

    private static String extractErrorText(String msg) {
        StringBuilder sb = new StringBuilder();
        for (String line : msg.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") && !trimmed.startsWith("at by.softclub.mpz.")) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private static String normalizeKey(String s) {
        StringBuilder masked = new StringBuilder(s.length());
        Matcher m = KEY_TOKEN.matcher(s);
        int last = 0;
        while (m.find()) {
            masked.append(s, last, m.start());
            String token = m.group();
            if (token.startsWith("ORA-")) {
                masked.append(token);
            } else if (token.startsWith("'")) {
                masked.append("'?'");
            } else {
                masked.append("#");
            }
            last = m.end();
        }
        masked.append(s, last, s.length());

        StringBuilder sb = new StringBuilder(masked.length());
        for (String line : masked.toString().split("\n")) {
            String trimmed = WHITESPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private static String extractProcessInstance(String text) {
        Matcher m = PI_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String extractName(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = PROC_ID_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}