package com.mpzlog.model;

/**
 * Элемент Ошибка, подчинённый процессу.
 * <p>
 * Поля: Полный текст ошибки, Ключ ошибки, Краткий текст ошибки.
 */
public final class ErrorElement {

    private final LogEntry entry;
    private final String fullText;
    private final String errorKey;
    private final String shortText;

    public ErrorElement(LogEntry entry, String fullText, String errorKey, String shortText) {
        this.entry = entry;
        this.fullText = fullText;
        this.errorKey = errorKey;
        this.shortText = shortText;
    }

    /** Запись лога, из которой извлечена ошибка. */
    public LogEntry getEntry() {
        return entry;
    }

    /** Полный текст ошибки. */
    public String getFullText() {
        return fullText;
    }

    /** Ключ ошибки (маскированный, для группировки). */
    public String getErrorKey() {
        return errorKey;
    }

    /** Краткий текст ошибки (до первого non-mpz вызова стека). */
    public String getShortText() {
        return shortText;
    }
}
