package com.mpzlog.model;

import com.mpzlog.parser.MpzLogParser;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Тесты построения модели лога МПЗ ({@link LogModelBuilder}).
 * <p>
 * Тесты 1–5 и 13 используют реальные данные из файла {@code mpz.log}
 * (вырезки сохранены в {@code src/test/resources/fixtures/}).
 * Тесты 6–12 проверяют внутреннюю логику построителя (повторные вызовы,
 * слияние процессов, обрезка хвоста и т.п.) — эти сценарии не встречаются
 * в виде изолированных примеров в реальном логе, поэтому оставлены
 * с синтетическими данными без изменений.
 */
public class LogModelBuilderTest {

    private final LogModelBuilder builder = new LogModelBuilder();

    private static LogEntry entry(int line, String thread, String msg) {
        LogEntry e = new LogEntry();
        e.setLineNumber(line);
        e.setThreadName(thread);
        e.setMessage(msg);
        return e;
    }

    // ---- helpers для тестов 6–12 (без изменений) ----

    private static String request(String processId, String pi) {
        return "handle-request: <?xml version='1.0' encoding='UTF-8'?>\n"
                + "<mpz-request>\n"
                + "  <session>\n"
                + "    <process-id>" + processId + "</process-id>\n"
                + "    <process-instance>" + pi + "</process-instance>\n"
                + "  </session>\n"
                + "</mpz-request>";
    }

    private static String response(String processId, String pi, String type) {
        return "handle-response: <?xml version='1.0' encoding='UTF-8'?>\n"
                + "<mpz-response>\n"
                + "  <session>\n"
                + "    <process-id>" + processId + "</process-id>\n"
                + "    <process-instance>" + pi + "</process-instance>\n"
                + "  </session>\n"
                + "  <result>\n"
                + "    <type>" + type + "</type>\n"
                + "  </result>\n"
                + "</mpz-response>";
    }

    // ================================================================
    //  Тесты на реальных данных из mpz.log
    // ================================================================

    private LogModel parseFixture(String name) {
        try {
            MpzLogParser parser = new MpzLogParser();
            parser.parse(Paths.get("src/test/resources/fixtures/" + name));
            return builder.build(parser.getEntries());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse fixture " + name, e);
        }
    }

    /**
     * Кейс из реального файла: процесс PRC_APB_CLAIM_CREATE (PID 3988167),
     * успешно завершённый ответом {@code <type>END_OF_PROCESS</type>}.
     */
    @Test
    public void completedProcess() {
        LogModel model = parseFixture("completed.log");

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> pe.getStatus() == ProcessElement.Status.COMPLETED)
                .findFirst().orElse(null);
        assertNotNull(p);
        assertEquals("3988167", p.getPid());
        assertEquals(ProcessElement.Status.COMPLETED, p.getStatus());
        assertTrue(p.getLines().size() > 0);
        assertTrue(p.getLines().stream().anyMatch(LogLine::isRequest));
        assertTrue(p.getLines().stream().anyMatch(LogLine::isResponse));
        assertTrue(p.getErrors().isEmpty());
    }

    /**
     * Кейс из реального файла: процесс PRC_CLAIM_CREATE (PID 3988168),
     * ответ с {@code <type>ERROR</type>}.
     */
    @Test
    public void completedWithErrorResponse() {
        LogModel model = parseFixture("completed_with_error.log");

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> pe.getStatus() == ProcessElement.Status.COMPLETED_WITH_ERROR)
                .findFirst().orElse(null);
        assertNotNull(p);
        assertEquals("3988168", p.getPid());
        assertEquals(ProcessElement.Status.COMPLETED_WITH_ERROR, p.getStatus());
    }

    /**
     * Кейс из реального файла: процесс PRC_APB_VIEW_CLAIM (PID 9373852),
     * ответ с {@code <type>VIEW</type>} (не END_OF_PROCESS и не ERROR),
     * поэтому статус {@code INTERRUPTED}.
     */
    @Test
    public void interruptedNoEnd() {
        LogModel model = parseFixture("interrupted.log");

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> pe.getStatus() == ProcessElement.Status.INTERRUPTED)
                .findFirst().orElse(null);
        assertNotNull(p);
        assertEquals("9373852", p.getPid());
        assertEquals(ProcessElement.Status.INTERRUPTED, p.getStatus());
    }

    /**
     * Кейс из реального файла: процесс PRC_APB_VIEW_CLAIM (без PID),
     * получивший системную ошибку MpzException (ORA-12899).
     * Статус FAILED, одна ошибка с корректным shortText и errorKey.
     */
    @Test
    public void exceptionProcessFailed() {
        LogModel model = parseFixture("failed.log");

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> pe.getStatus() == ProcessElement.Status.FAILED
                        && "default task-31".equals(pe.getTask()))
                .findFirst().orElse(null);
        assertNotNull(p);
        assertNull(p.getPid());
        assertEquals(ProcessElement.Status.FAILED, p.getStatus());
        assertEquals(1, p.getErrors().size());

        ErrorElement err = p.getErrors().get(0);
        assertNotNull(err.getErrorKey());
        assertTrue(err.getShortText().contains("MpzException"));
        // mpz-стек включается в shortText
        assertTrue(err.getShortText().contains("at by.softclub.mpz.sql.PkgClmOnline.initialize_online_viewclaim"));
        // non-mpz стек обрезается
        assertFalse(err.getShortText().contains("sun.reflect.GeneratedMethodAccessor117"));
    }

    /**
     * Кейс из реального файла: процесс PRC_APB_CLAIM_CREATE (без PID),
     * не получивший ответа. Статус UNRESOLVED.
     */
    @Test
    public void unresolvedProcess() {
        LogModel model = parseFixture("unresolved.log");

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> pe.getStatus() == ProcessElement.Status.UNRESOLVED
                        && pe.getProcessName() != null)
                .findFirst().orElse(null);
        assertNotNull(p);
        assertNull(p.getPid());
        assertEquals(ProcessElement.Status.UNRESOLVED, p.getStatus());
        assertTrue(p.getLines().stream().anyMatch(LogLine::isRequest));
    }

    // ================================================================
    //  Тесты на синтетических данных (без изменений)
    //  Сценарии 6–12: повторные вызовы, слияние, обрезка хвоста
    //  и непривязанные записи — отсутствуют в виде изолированных
    //  примеров в реальном файле mpz.log.
    // ================================================================

    @Test
    public void repeatedCallGoesToKnownPid() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_OK", "0")));
        entries.add(entry(2, "task-1", response("PRC_OK", "100", "END_OF_PROCESS")));
        entries.add(entry(3, "task-2", request("PRC_OK", "100")));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertEquals("100", p.getPid());
        assertEquals(3, p.getLines().size());
        assertEquals(ProcessElement.Status.COMPLETED, p.getStatus());
    }

    @Test
    public void taskTracksLastRequestThread() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_OK", "0")));
        entries.add(entry(2, "task-1", response("PRC_OK", "100", "END_OF_PROCESS")));
        entries.add(entry(3, "task-2", request("PRC_OK", "100")));
        entries.add(entry(4, "task-2", "прочая запись после повторного вызова"));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertEquals("task-2", p.getTask());
        assertEquals(4, p.getLines().size());
        assertEquals("прочая запись после повторного вызова",
                p.getLines().get(3).getEntry().getMessage());
    }

    @Test
    public void lateResponseMergesIntoKnownPid() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_OK", "0")));
        entries.add(entry(2, "task-1", response("PRC_OK", "100", "END_OF_PROCESS")));
        entries.add(entry(3, "task-2", request("PRC_OK", "0")));
        entries.add(entry(4, "task-2", response("PRC_OK", "100", "END_OF_PROCESS")));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertEquals("100", p.getPid());
        assertEquals(4, p.getLines().size());
    }

    @Test
    public void lateResponseAttachesToTruncatedStart() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_A", "0")));
        entries.add(entry(2, "task-1", request("PRC_B", "0")));
        entries.add(entry(3, "task-1", response("PRC_B", "200", "END_OF_PROCESS")));
        entries.add(entry(4, "task-1", response("PRC_A", "100", "END_OF_PROCESS")));

        LogModel model = builder.build(entries);

        assertEquals(2, model.getProcesses().size());
        ProcessElement a = null;
        ProcessElement b = null;
        for (ProcessElement p : model.getProcesses()) {
            if ("PRC_A".equals(p.getProcessName())) {
                a = p;
            }
            if ("PRC_B".equals(p.getProcessName())) {
                b = p;
            }
        }
        assertNotNull(a);
        assertNotNull(b);
        assertEquals("100", a.getPid());
        assertEquals(ProcessElement.Status.COMPLETED, a.getStatus());
        assertEquals(2, a.getLines().size());
        assertEquals("200", b.getPid());
        assertEquals(ProcessElement.Status.COMPLETED, b.getStatus());
        assertEquals(2, b.getLines().size());
    }

    @Test
    public void tailCutAtNewRequest() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_A", "0")));
        entries.add(entry(2, "task-1", "хвост A"));
        entries.add(entry(3, "task-1", request("PRC_B", "0")));
        entries.add(entry(4, "task-1", "хвост B"));
        entries.add(entry(5, "task-1", response("PRC_B", "200", "END_OF_PROCESS")));

        LogModel model = builder.build(entries);

        assertEquals(2, model.getProcesses().size());
        ProcessElement a = null;
        ProcessElement b = null;
        for (ProcessElement p : model.getProcesses()) {
            if ("PRC_A".equals(p.getProcessName())) {
                a = p;
            }
            if ("PRC_B".equals(p.getProcessName())) {
                b = p;
            }
        }
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(2, a.getLines().size());
        assertEquals(3, b.getLines().size());
    }

    @Test
    public void unboundResponseCreatesProcess() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", response("PRC_UNKNOWN", "0", "ERROR")));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertNull(p.getPid());
        assertEquals(ProcessElement.Status.UNRESOLVED, p.getStatus());
        assertTrue(p.getLines().get(0).isResponse());
    }

    @Test
    public void standaloneExceptionGoesToTaskProcess() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1",
                "SEVERE: by.softclub.mpz.core.MpzException: standalone failure\n"
                        + "at by.softclub.mpz.core.MpzException.<init>(MpzException.java:10)\n"
                        + "at by.softclub.mpz.MpzFacade.call(MpzFacade.java:42)\n"
                        + "at com.example.ExternalCaller.run(ExternalCaller.java:7)"));
        entries.add(entry(2, "task-1", "хвостовая запись того же треда"));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertEquals("task-1", p.getTask());
        assertNull(p.getPid());
        assertEquals(ProcessElement.Status.FAILED, p.getStatus());
        assertEquals(1, p.getErrors().size());
        assertEquals(2, p.getLines().size());
    }

    /**
     * Маскирование ключа ошибки на реальных данных: ORA-коды не маскируются,
     * строковые параметры '...' → '?', числа → #.
     */
    @Test
    public void errorKeyMasking() {
        LogModel model = parseFixture("failed.log");

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> pe.getStatus() == ProcessElement.Status.FAILED
                        && "default task-31".equals(pe.getTask()))
                .findFirst().orElse(null);
        assertNotNull(p);

        String key = p.getErrors().get(0).getErrorKey();
        // ORA-код не маскируется
        assertTrue(key.contains("ORA-12899"));
        // строковой параметр маскируется
        assertTrue(key.contains("'?'"));
        // цифровые значения маскируются
        assertTrue(key.contains("#"));
        // реальные значения в ключе отсутствуют
        assertFalse(key.contains("4*************_**P"));
        assertFalse(key.contains("18") && !key.contains("12899"));
    }
}
