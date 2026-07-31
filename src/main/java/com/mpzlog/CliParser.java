package com.mpzlog;

import java.util.ArrayList;
import java.util.List;

public class CliParser {

    private boolean help = false;
    private List<String> files = new ArrayList<>();
    private ModeOptions mode = new ModeOptions();

    private CliParser() {}

    public boolean isHelp() { return help; }
    public List<String> getFiles() { return files; }
    public ModeOptions getMode() { return mode; }
    public void addFile(String file) { files.add(file); }

    public static CliParser parse(String[] args) {
        CliParser cli = new CliParser();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                case "-h":
                    cli.help = true;
                    break;

                case "--process":
                case "-p":
                    if (i + 1 < args.length) {
                        cli.mode.setProcessId(args[++i]);
                    }
                    break;
                case "--grep":
                case "-g":
                    if (i + 1 < args.length) {
                        cli.mode.setGrepText(args[++i]);
                    }
                    break;
                case "--analyze":
                case "-a":
                    cli.mode.setAnalyze(true);
                    break;
                case "--list-processes":
                case "-lp":
                    cli.mode.setListProcesses(true);
                    break;
                case "--output-file":
                case "-of":
                    cli.mode.setOutputFile(true);
                    break;
                default:
                    if (!args[i].startsWith("-")) {
                        cli.files.add(args[i]);
                    }
                    break;
            }
        }
        return cli;
    }

    public static void printUsage() {
        System.out.println();
        System.out.println("MPZ Log Viewer — утилита для просмотра и анализа логов МПЗ");
        System.out.println();
        System.out.println("Использование:");
        System.out.println("  java -jar mpz-log-viewer.jar <файл1> [файл2 ...] [опции]");
        System.out.println();
        System.out.println("Опции:");
        System.out.println("  -h, --help                  Показать эту справку");
        System.out.println("  -a, --analyze               Сводная информация о логе (по умолчанию)");
        System.out.println("  -p, --process <id>          Показать записи только для процесса МПЗ");
        System.out.println("  -g, --grep <text>           Поиск процессов, содержащих текст в записях");
        System.out.println("  -lp, --list-processes       Список найденных процессов МПЗ");
        System.out.println("  -of, --output-file          Сохранить вывод в файл");
        System.out.println();
        System.out.println("Примеры:");
        System.out.println("  java -jar mpz-log-viewer.jar server.log");
        System.out.println("  java -jar mpz-log-viewer.jar mpz.log --list-processes -of");
        System.out.println("  java -jar mpz-log-viewer.jar mpz.log -p 8090892 -of");
        System.out.println();
    }
}
