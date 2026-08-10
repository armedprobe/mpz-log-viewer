## Context

См. `proposal.md` — Why. Текущее поведение: оба двойных клика (process, error) после переключения содержимого главной области делают `scrollTo(0)`.

Связь данных для навигации:
- `ErrorElement.getEntry()` → `LogEntry.getLineNumber()` (1-based)
- `LogEntry.getLineNumber()` → `allRawLines[lineNumber - 1]` (сырая строка)
- Сырая строка → индекс в `rawContentList.getItems()`

## Goals / Non-Goals

**Goals:**
- При двойном выделении процесса, если уже выбрана частая ошибка, скроллить к первой строке этой ошибки внутри процесса
- При двойном выделении ошибки, если уже выбран процесс, скроллить к первой строке этой ошибки внутри процесса

**Non-Goals:**
- Изменение модели данных (LogEntry, LogLine, ErrorElement, ProcessElement)
- Изменение компоновки окна или таблиц
- Single-click поведение

## Decisions

| Решение | Выбор | Альтернативы |
|---|---|---|
| **Где искать ошибку** | `ProcessElement.getErrors()` — фильтр по `errorKey`, минимум `entry.lineNumber` | Хранить мапу `errorKey → firstLine` в модели — избыточно для одного места использования |
| **Метод навигации** | Новый private-метод `scrollToProcessError(p, errorKey)` в `GuiApp.java` | Вынести в `RawContentListHelper` — метод слишком специфичен для одного вызывающего |
| **Падение, если строка не найдена** | `scrollTo(0)` — безопасный fallback | Игнорировать — пользователь не увидит обратной связи |

### Алгоритм `scrollToProcessError(ProcessElement p, String errorKey)`

```
1. Пройти p.getErrors(), отобрать ErrorElement с errorKey == target
2. Выбрать с минимальным entry.lineNumber
3. Если не найдено → scrollTo(0), return
4. targetLine = allRawLines[lineNumber - 1]
5. index = rawContentList.getItems().indexOf(targetLine)
6. Если < 0 → scrollTo(0), return
7. clearSelection(); select(index); scrollTo(index)
```

### Модификация `onProcessDoubleClick` (select)

```java
vc.selectProcess(p);
rawContentList.getItems().setAll(computeProcessLines(p));
if (vc.isErrorSelected()) {
    scrollToProcessError(p, vc.getSelectedErrorKey());
} else {
    rawContentList.scrollTo(0);
}
```

### Модификация `onErrorDoubleClick` (select)

```java
// после applyErrorFilter и возможного сброса selectedProcess
rawContentList.getItems().setAll(allRawLines);
ProcessElement p = vc.getSelectedProcess();
if (p != null) {
    scrollToProcessError(p, info.getErrorKey());
} else {
    rawContentList.scrollTo(0);
}
```

## Risks / Trade-offs

- **[Строка не найдена в current items]** → fallback `scrollTo(0)`. Возможно при рассинхроне allRawLines и computeProcessLines — не наблюдалось на практике.
- **[Дублирование поиска]** — на каждый double-click проход по errors процесса (O(n)). Ошибок на процесс обычно < 10, overhead незначителен.
- **[Откат]** — удалить 1 новый метод + изменить 2 блока в `GuiApp.java`.
