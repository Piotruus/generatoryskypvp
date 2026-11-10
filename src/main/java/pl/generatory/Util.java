package pl.generatory;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Util {

    public static final List<String> PARTICLE_CANDIDATES = List.of(
            "NONE", "END_ROD", "HAPPY_VILLAGER", "FLAME", "SOUL_FIRE_FLAME",
            "CLOUD", "ENCHANT", "CRIT", "WAX_ON", "GLOW");

    public static final List<String> SOUNDS = List.of(
            "none", "block.amethyst_block.chime", "block.amethyst_cluster.place",
            "entity.experience_orb.pickup", "block.note_block.chime", "block.note_block.bell",
            "block.stone.place", "block.respawn_anchor.charge", "block.end_portal_frame.fill",
            "block.beacon.power_select");

    private static List<String> availableParticles;

    private Util() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String formatTime(int sec) {
        if (sec < 0) sec = 0;
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    /** Blok "zajety" = nie powietrze i nie plyn. */
    public static boolean isFilled(Block b) {
        return !b.getType().isAir() && !b.isLiquid();
    }

    public static String onOff(boolean b) {
        return b ? "&aWlaczone" : "&cWylaczone";
    }

    public static String progressBar(double fraction, int len) {
        fraction = Math.max(0, Math.min(1, fraction));
        int filled = (int) Math.round(fraction * len);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < len; i++) {
            if (i == filled) sb.append("&7");
            sb.append('\u258c');
        }
        return color(sb.toString());
    }

    public static Particle resolveParticle(String name) {
        if (name == null || name.equalsIgnoreCase("NONE")) return null;
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<String> availableParticles() {
        if (availableParticles == null) {
            List<String> l = new ArrayList<>();
            for (String s : PARTICLE_CANDIDATES) {
                if (s.equals("NONE") || resolveParticle(s) != null) l.add(s);
            }
            availableParticles = l;
        }
        return availableParticles;
    }

    public static <E extends Enum<E>> E parseEnum(Class<E> c, String s, E def) {
        if (s == null) return def;
        try {
            return Enum.valueOf(c, s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public static <E> E cycle(E[] values, E current, int dir) {
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) { idx = i; break; }
        }
        idx = Math.floorMod(idx + dir, values.length);
        return values[idx];
    }

    public static String cycleString(List<String> values, String current, int dir) {
        int idx = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(current)) { idx = i; break; }
        }
        idx = Math.floorMod(idx + dir, values.size());
        return values.get(idx);
    }

    public static String niceName(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(color(s));
            meta.setLore(l);
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack item(Material m, String name, List<String> lore) {
        return item(m, name, lore.toArray(new String[0]));
    }
}
