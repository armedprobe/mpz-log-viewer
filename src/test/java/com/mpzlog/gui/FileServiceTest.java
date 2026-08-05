package com.mpzlog.gui;

import com.mpzlog.model.LogEntry;
import com.mpzlog.model.LogLine;
import com.mpzlog.model.LogModel;
import com.mpzlog.model.LogModelBuilder;
import com.mpzlog.model.ProcessElement;
import com.mpzlog.parser.MpzLogParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class FileServiceTest {

    @Test
    public void writeLinesWritesAllLines() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("line one");
        lines.add("line two");
        lines.add("line three");

        Path temp = Files.createTempFile("export-test-", ".log");
        try {
            FileService.writeLines(temp, lines);

            List<String> readBack = Files.readAllLines(temp);
            assertEquals(3, readBack.size());
            assertEquals("line one", readBack.get(0));
            assertEquals("line two", readBack.get(1));
            assertEquals("line three", readBack.get(2));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    public void writeLinesHandlesEmptyList() throws IOException {
        Path temp = Files.createTempFile("export-test-", ".log");
        try {
            FileService.writeLines(temp, new ArrayList<>());
            assertEquals(0, Files.size(temp));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    public void exportFromFixtureWritesOnlyThatProcessLines() throws IOException {
        LogModel model = parseFixture("completed.log");
        List<String> rawLines = Files.readAllLines(Paths.get("src/test/resources/fixtures/completed.log"));

        ProcessElement p = model.getProcesses().stream()
                .filter(pe -> "3988167".equals(pe.getPid()))
                .findFirst().orElse(null);
        assertNotNull("Process 3988167 not found", p);

        List<String> processLines = resolveProcessLines(p, model.getAllLines(), rawLines);

        Path temp = Files.createTempFile("export-completed-", ".log");
        try {
            FileService.writeLines(temp, processLines);
            List<String> written = Files.readAllLines(temp);

            assertFalse("Exported file should not be empty", written.isEmpty());

            String firstLine = written.get(0);
            assertTrue("First line should contain handle-request",
                    firstLine.contains("handle-request"));

            for (String line : written) {
                assertFalse("Exported file should not contain data from other processes",
                        line.contains("AGREEMENTS_FOR_CONFIRMATION"));
            }

            for (LogLine ll : p.getLines()) {
                LogEntry entry = ll.getEntry();
                if (entry.getMessage() != null && entry.getMessage().startsWith("handle-request")) {
                    String firstMsgLine = entry.getMessage().split("\n")[0];
                    assertTrue("Exported file should contain process request",
                            written.stream().anyMatch(l -> l.contains(firstMsgLine)));
                }
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    public void exportFromMultiProcessFixtureContainsOnlySelectedProcess() throws IOException {
        LogModel model = parseFixture("completed.log");
        List<String> rawLines = Files.readAllLines(Paths.get("src/test/resources/fixtures/completed.log"));

        List<ProcessElement> allProcesses = model.getProcesses();
        assertTrue("Fixture should contain multiple processes", allProcesses.size() > 1);

        ProcessElement firstP = allProcesses.get(0);
        List<String> firstLines = resolveProcessLines(firstP, model.getAllLines(), rawLines);

        ProcessElement secondP = allProcesses.get(1);
        List<String> secondLines = resolveProcessLines(secondP, model.getAllLines(), rawLines);

        assertFalse("First process should have lines", firstLines.isEmpty());
        assertFalse("Second process should have lines", secondLines.isEmpty());

        String firstJoined = String.join("\n", firstLines);
        String secondJoined = String.join("\n", secondLines);
        assertNotEquals("Exports of different processes should differ",
                firstJoined, secondJoined);
    }

    private static List<String> resolveProcessLines(ProcessElement p,
                                                     List<LogEntry> allEntries,
                                                     List<String> rawLines) {
        Set<LogEntry> processEntries = new HashSet<>();
        for (LogLine line : p.getLines()) {
            processEntries.add(line.getEntry());
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < allEntries.size(); i++) {
            LogEntry e = allEntries.get(i);
            if (!processEntries.contains(e)) continue;
            int start = e.getLineNumber();
            int end = i + 1 < allEntries.size()
                    ? allEntries.get(i + 1).getLineNumber()
                    : rawLines.size() + 1;
            for (int lineNo = start; lineNo < end; lineNo++) {
                if (lineNo >= 1 && lineNo <= rawLines.size()) {
                    result.add(rawLines.get(lineNo - 1));
                }
            }
        }
        return result;
    }

    private static LogModel parseFixture(String name) {
        try {
            MpzLogParser parser = new MpzLogParser();
            parser.parse(Paths.get("src/test/resources/fixtures/" + name));
            return new LogModelBuilder().build(parser.getEntries());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse fixture " + name, e);
        }
    }
}
