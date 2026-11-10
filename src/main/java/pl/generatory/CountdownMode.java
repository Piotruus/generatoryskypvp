package pl.generatory;

public enum CountdownMode {
    ACTIONBAR("Actionbar"),
    BOSSBAR("Boss bar"),
    NONE("Wylaczone");

    private final String display;

    CountdownMode(String display) { this.display = display; }

    public String getDisplay() { return display; }
}
