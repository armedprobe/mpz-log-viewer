## 1. FileService — добавить метод экспорта

- [x] 1.1 Добавить `exportToFile(Path dest, List<String> lines, Runnable onSuccess, Consumer<String> onError)` — запись строк в файл в потоке-демоне
- [x] 1.2 Добавить `saveFileDialog(Stage stage, String defaultName)` — открыть диалог сохранения FileChooser и вернуть выбранный Path (или null)

## 2. GuiApp — добавить метод экспорта

- [x] 2.1 Добавить метод `exportProcess(ProcessElement p, Stage stage)` : получить имя файла по умолчанию (`<имя_исх_файла>-<PID>.log`), показать save dialog, вызвать `fileService.exportToFile()`
- [x] 2.2 Санитизировать имя файла (заменить `<>:"/\|?*` на `_`)

## 3. MainLayoutBuilder — добавить кнопку экспорта в центральный тулбар

- [x] 3.1 Принимать параметр `Button exportButton` и добавлять его в центральный тулбар после `cancelButton`
- [x] 3.2 Привязать видимость exportButton к `selectedProcess` не равному null

## 4. GuiApp — создать и подключить кнопку

- [x] 4.1 Создать `Button exportButton` с текстом «Экспорт»; action → вызов `exportProcess(currentSelectedProcess, stage)`
- [x] 4.2 Передать exportButton в `MainLayoutBuilder.build()`

## 5. Проверка

- [x] 5.1 Собрать проект командой `mvn clean package` и убедиться в отсутствии ошибок компиляции
- [x] 5.2 Добавить тест, в котором из тестового файла лога со многими процессами корректно экспортируются данные только одного процесса.
- [x] 5.3 Запустить тесты командой `mvn test`
