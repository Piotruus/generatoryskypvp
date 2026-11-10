package pl.generatory;

import org.bukkit.Material;

public enum RegenType {
    WAVE("Fala warstwami", Material.WATER_BUCKET, "Odnawia sie warstwa po warstwie od dolu do gory"),
    CENTER_OUT("Od srodka na zewnatrz", Material.HEART_OF_THE_SEA, "Fala rozchodzi sie ze srodka obszaru"),
    RANDOM_POP("Losowe pop", Material.FIREWORK_STAR, "Bloki pojawiaja sie w losowej kolejnosci"),
    GROWING("Rosnace bloki", Material.BONE_MEAL, "Bloki rosna jako animowane kostki (najefektowniejsze)"),
    INSTANT("Natychmiastowa", Material.REDSTONE, "Bez animacji, lekka dla serwera");

    private final String display;
    private final Material icon;
    private final String description;

    RegenType(String display, Material icon, String description) {
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplay() { return display; }
    public Material getIcon() { return icon; }
    public String getDescription() { return description; }
}
