module com.mpzlog {
    requires javafx.controls;
    requires atlantafx.base;
    requires java.prefs;

    exports com.mpzlog.gui;

    opens com.mpzlog.gui to javafx.graphics;
}
