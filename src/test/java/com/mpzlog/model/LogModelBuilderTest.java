package com.mpzlog.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Тесты построения модели лога МПЗ ({@link LogModelBuilder}).
 * <p>
 * Строки строятся вручную (в формате, который выдаёт {@code MpzLogParser}:
 * запись + продолжения сообщения, склеенные переводом строки).
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

    @Test
    public void completedProcess() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_OK", "0")));
        entries.add(entry(2, "task-1", "обычная строка процесса"));
        entries.add(entry(3, "task-1", response("PRC_OK", "100", "END_OF_PROCESS")));

        LogModel model = builder.build(entries);

        assertEquals(3, model.getAllLines().size());
        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertEquals("100", p.getPid());
        assertEquals(ProcessElement.Status.COMPLETED, p.getStatus());
        assertEquals(3, p.getLines().size());
        assertTrue(p.getLines().get(0).isRequest());
        assertTrue(p.getLines().get(2).isResponse());
        assertTrue(p.getErrors().isEmpty());
    }

    @Test
    public void completedWithErrorResponse() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_ERR", "0")));
        entries.add(entry(2, "task-1", response("PRC_ERR", "200", "ERROR")));

        LogModel model = builder.build(entries);

        ProcessElement p = model.getProcesses().get(0);
        assertEquals("200", p.getPid());
        assertEquals(ProcessElement.Status.COMPLETED_WITH_ERROR, p.getStatus());
    }

    @Test
    public void interruptedNoEnd() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_INTR", "0")));
        entries.add(entry(2, "task-1", response("PRC_INTR", "300", "SOME_OTHER_TYPE")));

        LogModel model = builder.build(entries);

        ProcessElement p = model.getProcesses().get(0);
        assertEquals("300", p.getPid());
        assertEquals(ProcessElement.Status.INTERRUPTED, p.getStatus());
    }

    @Test
    public void exceptionProcessFailed() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_FAIL", "0")));
        entries.add(entry(2, "task-1",
                "SEVERE: by.softclub.mpz.core.MpzException: Oracle call PKG : ORA-12899: value too large\n"
                        + "at by.softclub.mpz.core.MpzException.<init>(MpzException.java:10)\n"
                        + "at by.softclub.mpz.MpzFacade.call(MpzFacade.java:42)\n"
                        + "at com.example.ExternalCaller.run(ExternalCaller.java:7)"));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertNull(p.getPid());
        assertEquals(ProcessElement.Status.FAILED, p.getStatus());
        assertEquals(1, p.getErrors().size());
        ErrorElement err = p.getErrors().get(0);
        assertNotNull(err.getErrorKey());
        assertTrue(err.getShortText().contains("MpzException"));
        assertTrue(err.getShortText().contains("at by.softclub.mpz.MpzFacade.call"));
        assertTrue(!err.getShortText().contains("com.example.ExternalCaller"));
    }

    @Test
    public void unresolvedProcess() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_UNRES", "0")));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertNull(p.getPid());
        assertEquals(ProcessElement.Status.UNRESOLVED, p.getStatus());
    }

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
        // повторный вызов на другом трэде — поле task должно обновиться
        entries.add(entry(3, "task-2", request("PRC_OK", "100")));
        // прочие записи на новом трэде должны попадать в тот же процесс
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
        // первый процесс с PID=100 завершается
        entries.add(entry(1, "task-1", request("PRC_OK", "0")));
        entries.add(entry(2, "task-1", response("PRC_OK", "100", "END_OF_PROCESS")));
        // стартовый request с pi=0, но ответ приходит с уже известным PID=100
        entries.add(entry(3, "task-2", request("PRC_OK", "0")));
        entries.add(entry(4, "task-2", response("PRC_OK", "100", "END_OF_PROCESS")));

        LogModel model = builder.build(entries);

        assertEquals(1, model.getProcesses().size());
        ProcessElement p = model.getProcesses().get(0);
        assertEquals("100", p.getPid());
        assertEquals(4, p.getLines().size());
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
        // ошибка на трэде без открытого запроса — попадает в процесс по трэду
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

    @Test
    public void errorKeyMasking() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, "task-1", request("PRC_OK", "0")));
        entries.add(entry(2, "task-1",
                "SEVERE: by.softclub.mpz.core.MpzException: bad value 'ABC' for key 42\n"
                        + "at by.softclub.mpz.core.MpzException.<init>(MpzException.java:10)\n"
                        + "at by.softclub.mpz.MpzFacade.call(MpzFacade.java:42)\n"
                        + "at com.example.ExternalCaller.run(ExternalCaller.java:7)"));

        LogModel model = builder.build(entries);

        ProcessElement p = model.getProcesses().get(0);
        String key = p.getErrors().get(0).getErrorKey();
        assertTrue(key.contains("'?'"));
        assertTrue(key.contains("#"));
        assertTrue(!key.contains("ABC"));
        assertTrue(!key.contains("42"));
    }
}
