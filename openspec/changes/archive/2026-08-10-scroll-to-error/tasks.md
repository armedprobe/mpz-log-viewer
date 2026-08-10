## 1. Helper-метод scrollToProcessError

- [x] 1.1 Добавить private-метод `scrollToProcessError(ProcessElement p, String errorKey)` в `GuiApp.java`
- [x] 1.2 Реализовать поиск первого ErrorElement с совпадающим errorKey через `p.getErrors()`, выбор по минимальному `entry.lineNumber`
- [x] 1.3 Реализовать поиск сырой строки в `allRawLines` через lineNumber и скроллинг к ней в `rawContentList`
- [x] 1.4 Добавить fallback на `rawContentList.scrollTo(0)` если ошибка или строка не найдены

## 2. Модификация onProcessDoubleClick

- [x] 2.1 В select-ветку `onProcessDoubleClick` добавить проверку `vc.isErrorSelected()` и вызов `scrollToProcessError()` вместо `scrollTo(0)`

## 3. Модификация onErrorDoubleClick

- [x] 3.1 В select-ветку `onErrorDoubleClick` перечитать `selectedProcess` после возможного сброса и вызвать `scrollToProcessError()` при его наличии вместо `scrollTo(0)`

## 4. Проверка

- [x] 4.1 Собрать проект: `mvn clean package`
- [x] 4.2 Проверить сценарии: процесс → ошибка, ошибка → процесс, одиночное выделение без второго, снятие выделения
