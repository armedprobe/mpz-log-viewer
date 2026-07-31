package com.mpzlog.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализирует лог и строит модель в виде дерева элементов.
 * <p>
 * Правила атрибуции записей процессу:
 * <ul>
 *   <li>старт процесса — {@code handle-request} с {@code process-instance=0};</li>
 *   <li>следующий за ним {@code handle-response} с новым {@code process-instance}
 *       определяет PID процесса;</li>
 *   <li>все последующие {@code handle-request}/{@code handle-response} с этим же
 *       PID относятся к этому же процессу;</li>
 *   <li>прочие записи относятся к процессу, только если их task (имя треда)
 *       совпадает с task у request и они лежат между этим request и следующим
 *       response;</li>
 *   <li>если request не завершился response (ошибка или response не найден),
 *       записи с тем же task после него включаются в процесс, но не те, что
 *       следуют после response другого процесса с тем же task.</li>
 * </ul>
 * Результат — корень дерева {@link #getRoot()} (процессы и непривязанные
 * записи в порядке лога) и плоский список процессов {@link #getProcesses()}.
 */
public class ProcessAnalyzer {

    private static final Pattern PI_PATTERN =
            Pattern.compile("<process-instance>(\\d+)</process-instance>");
    private static final Pattern PROC_ID_PATTERN =
            Pattern.compile("<process-id>([^<]+)</process-id>");
    private static final Pattern END_OF_PROCESS_PATTERN =
            Pattern.compile("<type>\\s*END_OF_PROCESS\\s*</type>");
    private static final Pattern ERROR_PATTERN =
            Pattern.compile("<type>\\s*ERROR\\s*</type>");
    private static final Pattern KEY_TOKEN =
            Pattern.compile("ORA-\\d+|'[^']*'|\\d+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final List<LogEntry> entries;
    private final List<ProcessElement> processes = new ArrayList<>();
    private final Map<String, ProcessElement> byPid = new LinkedHashMap<>();
    private final List<Element> root = new ArrayList<>();
    private int criticalErrors;

    public ProcessAnalyzer(List<LogEntry> entries) {
        this.entries = entries;
        analyze();
    }

    /** Количество критических ошибок (записей с Exception), вычисляется один раз при анализе. */
    public int getCriticalErrorsCount() {
        return criticalErrors;
    }

    private void analyze() {
        Map<String, Deque<ProcessElement>> stackByTask = new LinkedHashMap<>();

        for (int i = 0; i < entries.size(); i++) {
            LogEntry e = entries.get(i);
            String msg = e.getMessage();
            String pi = msg == null ? null : extractProcessInstance(msg);
            boolean isRequest = msg != null && msg.contains("handle-request:");
            boolean isResponse = msg != null && msg.contains("handle-response:");

            String key = e.getThreadName() != null ? e.getThreadName() : "";
            Deque<ProcessElement> stack = stackByTask.computeIfAbsent(key, k -> new ArrayDeque<>());
            ProcessElement top = stack.peek();

            if (isRequest && "0".equals(pi)) {
                ProcessElement p = new ProcessElement();
                p.task = e.getThreadName();
                p.processId = extractProcessId(msg);
                p.startIndex = i;
                p.status = ProcessElement.Status.UNRESOLVED;
                addHandle(p, e, pi, true);
                processes.add(p);
                root.add(p);
                stack.push(p);
            } else if (isRequest && pi != null) {
                ProcessElement known = byPid.get(pi);
                if (known != null) {
                    addHandle(known, e, pi, true);
                    stack.push(known);
                } else if (top != null) {
                    addHandle(top, e, pi, true);
                } else {
                    root.add(new LogElement(e, pi, true, false));
                }
            } else if (isResponse) {
                boolean endOfProcess = isEndOfProcess(msg);
                ProcessElement known = pi != null && !"0".equals(pi) ? byPid.get(pi) : null;
                ProcessElement owner;
                if (known != null) {
                    owner = known;
                    closeOwnerWindow(stack, owner);
                } else {
                    owner = findOpenOwner(stack, msg);
                    if (owner == null && !stack.isEmpty()) {
                        owner = stack.peekLast();
                    }
                    if (owner != null) {
                        ProcessElement stackOwner = owner;
                        if (owner.pid == null) {
                            if (pi != null && !"0".equals(pi)) {
                                ProcessElement existing = byPid.get(pi);
                                if (existing != null && existing != owner) {
                                    mergeInto(existing, owner);
                                    processes.remove(owner);
                                    root.remove(owner);
                                    owner = existing;
                                } else {
                                    owner.pid = pi;
                                    byPid.put(pi, owner);
                                }
                            }
                        }
                        closeOwnerWindow(stack, stackOwner);
                    }
                }
                if (owner != null) {
                    addHandle(owner, e, pi, false);
                    updateStatus(owner, endOfProcess, isErrorResponse(msg));
                } else {
                    root.add(new LogElement(e, pi, false, true));
                }
            } else {
                if (top != null) {
                    addRecord(top, e);
                } else {
                    root.add(new LogElement(e, null, false, false));
                }
            }
        }
        computeErrorFields();
    }

    private void addHandle(ProcessElement p, LogEntry e, String pid, boolean request) {
        p.allEntries.add(e);
        p.entryCount++;
        p.reqRespCount++;
        p.children.add(new LogElement(e, pid, request, !request));
    }

    private void addRecord(ProcessElement p, LogEntry e) {
        p.allEntries.add(e);
        p.entryCount++;
        p.children.add(new LogElement(e, null, false, false));
    }

    /**
     * Обновляет статус процесса после получения response.
     * «Завершён» — response содержит {@code <type>END_OF_PROCESS</type>}.
     * «Завершён с ошибкой» — response содержит {@code <type>ERROR</type>}
     *     (штатное завершение с прикладной ошибкой).
     * «Прерван» — ответ получен, но ни END_OF_PROCESS, ни ERROR не найдены.
     * «Ошибка» — PID так и не появился (process-instance=0).
     * Уже завершённый процесс не понижается последующими ответами.
     */
    private void updateStatus(ProcessElement p, boolean endOfProcess, boolean errorResponse) {
        if (endOfProcess) {
            p.status = ProcessElement.Status.COMPLETED;
        } else if (errorResponse) {
            p.status = ProcessElement.Status.COMPLETED_WITH_ERROR;
        } else if (p.status != ProcessElement.Status.COMPLETED
                && p.status != ProcessElement.Status.COMPLETED_WITH_ERROR) {
            if (p.pid == null) {
                p.status = ProcessElement.Status.FAILED;
            } else {
                p.status = ProcessElement.Status.INTERRUPTED;
            }
        }
    }

    private boolean isEndOfProcess(String msg) {
        return msg != null && END_OF_PROCESS_PATTERN.matcher(msg).find();
    }

    private boolean isErrorResponse(String msg) {
        return msg != null && ERROR_PATTERN.matcher(msg).find();
    }

    /**
     * Вычисляет и сохраняет в каждой записи поля ошибки:
     * {@link LogEntry#exception}, {@link LogEntry#errorText}, {@link LogEntry#errorKey}.
     */
    private void computeErrorFields() {
        criticalErrors = 0;
        for (LogEntry e : entries) {
            e.setException(isExceptionEntry(e));
            if (e.isException()) {
                criticalErrors++;
                e.setErrorText(extractErrorText(e));
                e.setErrorKey(normalizeKey(e.getErrorText()));
            }
        }
    }

    static boolean isExceptionEntry(LogEntry e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return e.getFirstLine().contains("Exception") && !e.getFirstLine().startsWith("Caused by");
    }

    static String extractErrorText(LogEntry e) {
        String msg = e.getMessage();
        if (msg == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : msg.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") && !trimmed.startsWith("at by.softclub.mpz.")) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    static String normalizeKey(String s) {
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
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * Ищет среди открытых окон треда самый старый процесс с тем же {@code process-id},
     * что и в сообщении ответа. Если совпадений нет — {@code null}.
     */
    private ProcessElement findOpenOwner(Deque<ProcessElement> stack, String msg) {
        String processId = extractProcessId(msg);
        if (processId == null) {
            return null;
        }
        ProcessElement oldest = null;
        for (Iterator<ProcessElement> it = stack.descendingIterator(); it.hasNext(); ) {
            ProcessElement p = it.next();
            if (processId.equals(p.processId)) {
                oldest = p;
                break;
            }
        }
        return oldest;
    }

    /**
     * Закрывает окно владельца и все более старые открытые окна треда (все,
     * что открыты раньше него — они уже не получат ответ). Младшие окна остаются.
     */
    private void closeOwnerWindow(Deque<ProcessElement> stack, ProcessElement owner) {
        if (!stack.contains(owner)) {
            return;
        }
        for (Iterator<ProcessElement> it = stack.descendingIterator(); it.hasNext(); ) {
            ProcessElement p = it.next();
            if (p == owner) {
                it.remove();
                break;
            }
            it.remove();
        }
    }

    private void mergeInto(ProcessElement target, ProcessElement source) {
        target.children.addAll(source.children);
        target.allEntries.addAll(source.allEntries);
        target.entryCount += source.entryCount;
        target.reqRespCount += source.reqRespCount;
        if (source.processId != null && target.processId == null) {
            target.processId = source.processId;
        }
    }

    /** Плоский список процессов в порядке появления. */
    public List<ProcessElement> getProcesses() {
        return processes;
    }

    /** Плоский список процессов в порядке появления. */
    public List<ProcessElement> getAllProcesses() {
        return processes;
    }

    /** Корень дерева модели лога: процессы и непривязанные записи в порядке лога. */
    public List<Element> getRoot() {
        return root;
    }

    public List<String> getProcessIds() {
        List<String> ids = new ArrayList<>();
        for (ProcessElement p : processes) {
            if (p.pid != null) {
                ids.add(p.pid);
            }
        }
        return ids;
    }

    public ProcessElement getProcess(String id) {
        return byPid.get(id);
    }

    public boolean hasProcess(String id) {
        return byPid.containsKey(id);
    }

    public List<LogEntry> getEntriesForProcess(String id) {
        ProcessElement p = byPid.get(id);
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
}
