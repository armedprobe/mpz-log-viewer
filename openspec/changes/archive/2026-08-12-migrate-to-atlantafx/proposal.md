## Why

Текущая тема JMetro (2019) морально устарела: плоский Fluent Design без скруглений, без теней, жёсткие хардкод-цвета `#hex`, 4 CSS-файла (~4200 строк), привязанных к JDK 8. Пользователь хочет современный «воздушный» интерфейс с актуальным визуальным языком.

AtlantaFX даёт современную дизайн-систему на базе GitHub Primer: скруглённые контролы, лёгкие тени, система looked-up color variables, единый компактный CSS. Одновременно поднимаем JDK до 21 (LTS) и JavaFX до 22 — текущая версия `atlantafx-base:2.1.0` этого требует.

## What Changes

- **BREAKING**: JDK поднимается с 8 до 21, JavaFX больше не встроена — подключается как Maven-зависимость (`org.openjfx:javafx-controls:22`)
- JMetro (4 CSS-файла: `light_theme.css`, `base.css`, `base_extras.css`, `panes.css`) удаляются
- Добавляется зависимость `io.github.mkpaz:atlantafx-base:2.1.0`
- Тема устанавливается через `Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())`
- `app.css` переписывается: с ~318 строк до ~55 строк, все цвета заменены на AtlantaFX looked-up variables (`-color-bg-default`, `-color-border-muted`, `-color-accent-subtle` и т.д.)
- Inline-стиль `setStyle("-fx-font-weight: bold")` в `MainLayoutBuilder.java` переносится в CSS
- `pom.xml`: `source/target` 21, новые зависимости, обновление `maven-compiler-plugin`
- `launch4j`: `minVersion` меняется с 8.0.0 на 21
- `AGENTS.md`: обновляется описание темы

## Capabilities

### New Capabilities
- `atlantafx-theme`: Единая визуальная тема приложения на базе AtlantaFX PrimerLight с минимальным кастомным CSS

### Modified Capabilities
<!-- Нет изменений требований к функциональности — чисто визуальная миграция -->

## Impact

- `pom.xml` — JDK 21, JavaFX, AtlantaFX, обновление плагинов
- `GuiApp.java:58` — одна строка: `Application.setUserAgentStylesheet(...)`
- `MainLayoutBuilder.java:56,89-92` — удаление JMetro CSS, перенос inline-стиля
- `app.css` — полная переработка: 318 → 55 строк, все цвета → переменные
- `src/main/resources/gui/light_theme.css` — удаление
- `src/main/resources/gui/base.css` — удаление
- `src/main/resources/gui/base_extras.css` — удаление
- `src/main/resources/gui/panes.css` — удаление
- `AGENTS.md` — обновление строки про тему
- `launch4j` в `pom.xml` — `minVersion` 8 → 21

Ни один Java-файл, кроме `GuiApp.java` и `MainLayoutBuilder.java`, не затрагивается. Логика парсинга, модель, хелперы таблиц, иконки — без изменений.
