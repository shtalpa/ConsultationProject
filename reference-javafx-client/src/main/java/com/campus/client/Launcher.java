package com.campus.client;

/**
 * Plain launcher so the JavaFX app starts both via {@code mvn javafx:run} and a bundled JAR.
 * (Launching an {@code Application} subclass directly requires JavaFX on the module path.)
 */
public final class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }

    private Launcher() {
    }
}
