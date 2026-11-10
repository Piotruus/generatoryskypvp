package pl.generatory;

public enum TriggerMode {
    WHEN_EMPTY("Po wykopaniu obszaru", "Odliczanie rusza, gdy wykopano zadany % obszaru"),
    PERIODIC("Cyklicznie", "Regeneracja co X sekund, niezaleznie od wykopania");

    private final String display;
    private final String description;

    TriggerMode(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public String getDisplay() { return display; }
    public String getDescription() { return description; }
}
