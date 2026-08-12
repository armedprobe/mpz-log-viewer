## 1. Сборка и зависимости

- [x] 1.1 Обновить `pom.xml`: `maven.compiler.source` и `maven.compiler.target` с `1.8` на `21`
- [x] 1.2 Добавить в `pom.xml` зависимости `org.openjfx:javafx-controls:22` и `org.openjfx:javafx-graphics:22`
- [x] 1.3 Добавить в `pom.xml` зависимость `io.github.mkpaz:atlantafx-base:2.1.0`
- [x] 1.4 Обновить `maven-compiler-plugin` до версии 3.12.1
- [x] 1.5 Обновить `launch4j-maven-plugin`: `minVersion` с `8.0.0` на `21` в секции `<jre>`

## 2. Удаление JMetro

- [x] 2.1 Удалить `src/main/resources/gui/light_theme.css`
- [x] 2.2 Удалить `src/main/resources/gui/base.css`
- [x] 2.3 Удалить `src/main/resources/gui/base_extras.css`
- [x] 2.4 Удалить `src/main/resources/gui/panes.css`

## 3. Java-код

- [x] 3.1 В `GuiApp.start()` до создания `Scene` добавить `Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())` с импортом `atlantafx.base.theme.PrimerLight`
- [x] 3.2 В `MainLayoutBuilder.build()` убрать загрузку `light_theme.css` через `scene.getStylesheets().add(...)`, оставить только `app.css`
- [x] 3.3 В `MainLayoutBuilder.build()` убрать `displayModeLabel.setStyle("-fx-font-weight: bold;")` и добавить `displayModeLabel.getStyleClass().add("toolbar-label-bold")`

## 4. CSS

- [x] 4.1 Переписать `app.css` — удалить все стили стандартных контролов (selection, hover, dialog, column-header, font-family для UI), оставить только панели компоновки, тулбар, статус-бар, псевдокласс `:active-process`, моноширинный шрифт для кода, оверлей загрузки
- [x] 4.2 Заменить все хардкод-цвета `#hex` на AtlantaFX looked-up variables (`-color-bg-default`, `-color-border-muted`, `-color-accent-subtle`, `-color-fg-muted`, `-color-fg-subtle`, `-color-accent-emphasis`)
- [x] 4.3 Добавить правило `.toolbar-label-bold { -fx-font-weight: bold; }` для перенесённого inline-стиля

## 5. Документация

- [x] 5.1 Обновить `AGENTS.md`: заменить «Тема оформления: JMetro» на «Тема оформления: AtlantaFX PrimerLight»

## 6. Проверка

- [x] 6.1 Выполнить `mvn clean package`, убедиться что сборка проходит без ошибок
- [x] 6.2 Запустить `.exe`, проверить визуальное отображение всех панелей и контролов
- [x] 6.3 Проверить корректность псевдокласса `:active-process` при выделении процесса
- [x] 6.4 Проверить работу спиннера при загрузке файла
