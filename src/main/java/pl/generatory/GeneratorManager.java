package pl.generatory;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GeneratorManager {

    private final GeneratorsPlugin plugin;
    private final Map<String, Generator> generators = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<Generator>>> index = new HashMap<>();
    private final Map<String, BossBar> bars = new HashMap<>();
    private final File file;
    private boolean dirty = false;
    private BukkitTask secondTask;
    private BukkitTask saveTask;

    public GeneratorManager(GeneratorsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "generators.yml");
    }

    // =========================================================== dostep
    public Collection<Generator> all() { return generators.values(); }

    public Generator get(String name) {
        return name == null ? null : generators.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) { return get(name) != null; }

    public void markDirty() { dirty = true; }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public Generator getAt(int x, int y, int z, String world) {
        Map<Long, List<Generator>> m = index.get(world);
        if (m == null) return null;
        List<Generator> l = m.get(chunkKey(x >> 4, z >> 4));
        if (l == null) return null;
        for (Generator g : l) {
            if (g.contains(x, y, z)) return g;
        }
        return null;
    }

    public Generator getAt(Block b) {
        return getAt(b.getX(), b.getY(), b.getZ(), b.getWorld().getName());
    }

    public Generator getAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return getAt(l.getBlockX(), l.getBlockY(), l.getBlockZ(), l.getWorld().getName());
    }

    public Generator getNearest(Location l, int radius) {
        Generator inside = getAt(l);
        if (inside != null) return inside;
        Generator best = null;
        double bestDist = Double.MAX_VALUE;
        for (Generator g : generators.values()) {
            if (!g.isNear(l, radius)) continue;
            World w = l.getWorld();
            double d = g.center(w).distanceSquared(l);
            if (d < bestDist) {
                bestDist = d;
                best = g;
            }
        }
        return best;
    }

    public Generator findOverlap(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (Generator g : generators.values()) {
            if (g.overlaps(world, x1, y1, z1, x2, y2, z2)) return g;
        }
        return null;
    }

    // =========================================================== dodawanie / usuwanie
    public void add(Generator g) {
        generators.put(g.getName().toLowerCase(Locale.ROOT), g);
        indexAdd(g);
        markDirty();
    }

    public void delete(Generator g) {
        if (g.getJob() != null) g.getJob().stop(false);
        clearBar(g);
        indexRemove(g);
        generators.remove(g.getName().toLowerCase(Locale.ROOT));
        markDirty();
    }

    private void indexAdd(Generator g) {
        Map<Long, List<Generator>> m = index.computeIfAbsent(g.getWorldName(), k -> new HashMap<>());
        for (int cx = g.getMinX() >> 4; cx <= g.getMaxX() >> 4; cx++) {
            for (int cz = g.getMinZ() >> 4; cz <= g.getMaxZ() >> 4; cz++) {
                m.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(g);
            }
        }
    }

    private void indexRemove(Generator g) {
        Map<Long, List<Generator>> m = index.get(g.getWorldName());
        if (m == null) return;
        for (int cx = g.getMinX() >> 4; cx <= g.getMaxX() >> 4; cx++) {
            for (int cz = g.getMinZ() >> 4; cz <= g.getMaxZ() >> 4; cz++) {
                long key = chunkKey(cx, cz);
                List<Generator> l = m.get(key);
                if (l != null) {
                    l.remove(g);
                    if (l.isEmpty()) m.remove(key);
                }
            }
        }
    }

    /** Wartosci poczatkowe z config.yml (sekcja defaults). */
    public void applyDefaults(Generator g) {
        var c = plugin.getConfig();
        g.setIntervalSeconds(c.getInt("defaults.interval-seconds", 60));
        g.setTrigger(Util.parseEnum(TriggerMode.class, c.getString("defaults.trigger"), TriggerMode.WHEN_EMPTY));
        g.setTriggerPercent(c.getInt("defaults.trigger-percent", 100));
        g.setRegenType(Util.parseEnum(RegenType.class, c.getString("defaults.regen-type"), RegenType.WAVE));
        g.setRegenSeconds(c.getInt("defaults.regen-seconds", 5));
        g.setParticle(c.getString("defaults.particle", "END_ROD"));
        g.setSound(c.getString("defaults.sound", "block.amethyst_block.chime"));
        g.setCountdown(Util.parseEnum(CountdownMode.class, c.getString("defaults.countdown"), CountdownMode.ACTIONBAR));
        g.setTeleportOut(c.getBoolean("defaults.teleport-players-out", true));
        g.setProtect(c.getBoolean("defaults.protect", true));
        applyDefaultBlocks(g);
        g.setTimeLeft(g.getIntervalSeconds());
    }

    /** Przywraca liste blokow z config.yml (defaults.blocks). */
    public void applyDefaultBlocks(Generator g) {
        g.clearBlocks();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("defaults.blocks");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null && m.isBlock() && m.isItem() && !m.isAir()) {
                    g.setWeight(m, Math.max(1, sec.getInt(key, 1)));
                }
            }
        }
        if (g.getBlocks().isEmpty()) g.setWeight(Material.STONE, 1);
        markDirty();
    }

    // =========================================================== stan / regeneracja
    public String statusText(Generator g) {
        if (!g.isEnabled()) return plugin.msg().raw("status-disabled");
        return switch (g.getState()) {
            case ACTIVE -> plugin.msg().raw("status-active");
            case WAITING -> plugin.msg().raw("status-waiting");
            case REGENERATING -> plugin.msg().raw("status-regenerating");
        };
    }

    public boolean startRegen(Generator g) {
        if (g.getState() == GeneratorState.REGENERATING || g.getJob() != null) return false;
        World w = g.getWorld();
        if (w == null) return false;
        g.setState(GeneratorState.REGENERATING);
        RegenJob job = new RegenJob(plugin, g, w);
        g.setJob(job);
        job.start();
        markDirty();
        return true;
    }

    public void evaluateTrigger(Generator g) {
        if (!g.isEnabled() || g.getState() != GeneratorState.ACTIVE || g.getTrigger() != TriggerMode.WHEN_EMPTY) return;
        if (g.shouldTrigger()) {
            if (g.getIntervalSeconds() <= 0) {
                startRegen(g);
            } else {
                g.setState(GeneratorState.WAITING);
                g.setTimeLeft(g.getIntervalSeconds());
            }
            markDirty();
        }
    }

    public void resetTimer(Generator g) {
        if (g.getState() == GeneratorState.REGENERATING) return;
        g.setState(GeneratorState.ACTIVE);
        g.setTimeLeft(g.getIntervalSeconds());
        evaluateTrigger(g);
        markDirty();
    }

    public void onIntervalChanged(Generator g) {
        if (g.getState() == GeneratorState.WAITING) {
            g.setTimeLeft(Math.min(g.getTimeLeft(), g.getIntervalSeconds()));
        } else if (g.getState() == GeneratorState.ACTIVE) {
            g.setTimeLeft(g.getIntervalSeconds());
        }
        markDirty();
    }

    /** Synchroniczne przeliczenie zajetych blokow w obszarze. */
    public int recountSync(Generator g) {
        World w = g.getWorld();
        if (w == null) return -1;
        int count = 0;
        for (int y = g.getMinY(); y <= g.getMaxY(); y++) {
            for (int x = g.getMinX(); x <= g.getMaxX(); x++) {
                for (int z = g.getMinZ(); z <= g.getMaxZ(); z++) {
                    if (Util.isFilled(w.getBlockAt(x, y, z))) count++;
                }
            }
        }
        g.setRemaining(count);
        markDirty();
        return count;
    }

    // =========================================================== zadania cykliczne
    public void startTasks() {
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, this::secondTick, 20L, 20L);
        long period = Math.max(10, plugin.getConfig().getInt("settings.autosave-seconds", 60)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, period, period);
    }

    private void secondTick() {
        for (Generator g : new ArrayList<>(generators.values())) {
            World w = g.getWorld();
            if (w == null) continue;

            if (g.isEnabled()) {
                switch (g.getState()) {
                    case ACTIVE -> {
                        if (g.getTrigger() == TriggerMode.PERIODIC) tickDown(g);
                        else evaluateTrigger(g);
                    }
                    case WAITING -> tickDown(g);
                    case REGENERATING -> { }
                }
            }
            updateCountdown(g, w);
        }
    }

    private void tickDown(Generator g) {
        g.setTimeLeft(g.getTimeLeft() - 1);
        if (g.getTimeLeft() <= 0) startRegen(g);
    }

    // =========================================================== odliczanie
    private void updateCountdown(Generator g, World w) {
        CountdownMode mode = g.getCountdown();
        boolean regen = g.getState() == GeneratorState.REGENERATING;
        boolean timer = !regen && g.getTimeLeft() > 0
                && (g.getState() == GeneratorState.WAITING || g.getTrigger() == TriggerMode.PERIODIC);

        if (mode == CountdownMode.NONE || !g.isEnabled() || (!regen && !timer)) {
            clearBar(g);
            return;
        }

        int radius = plugin.getConfig().getInt("settings.countdown-radius", 12);
        Set<Player> near = new HashSet<>();
        for (Player p : w.getPlayers()) {
            if (g.isNear(p.getLocation(), radius)) near.add(p);
        }

        String text = regen
                ? plugin.msg().raw("countdown-regenerating", "name", g.getName(), "percent", g.fillPercent())
                : plugin.msg().raw("countdown-timer", "name", g.getName(), "time", Util.formatTime(g.getTimeLeft()));
        double progress = regen
                ? g.fillFraction()
                : (g.getIntervalSeconds() <= 0 ? 0 : (double) g.getTimeLeft() / g.getIntervalSeconds());
        progress = Math.max(0, Math.min(1, progress));

        if (mode == CountdownMode.ACTIONBAR) {
            clearBar(g);
            for (Player p : near) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
            }
        } else {
            String key = g.getName().toLowerCase(Locale.ROOT);
            BossBar bar = bars.get(key);
            if (bar == null) {
                bar = Bukkit.createBossBar(text, regen ? BarColor.BLUE : BarColor.GREEN, BarStyle.SOLID);
                bars.put(key, bar);
            }
            bar.setTitle(text);
            bar.setColor(regen ? BarColor.BLUE : BarColor.GREEN);
            bar.setProgress(progress);
            for (Player p : new ArrayList<>(bar.getPlayers())) {
                if (!near.contains(p)) bar.removePlayer(p);
            }
            for (Player p : near) bar.addPlayer(p);
        }
    }

    private void clearBar(Generator g) {
        BossBar bar = bars.remove(g.getName().toLowerCase(Locale.ROOT));
        if (bar != null) bar.removeAll();
    }

    // =========================================================== zapis / odczyt
    public void load() {
        generators.clear();
        index.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("generators");
        if (root == null) return;

        List<Generator> resume = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Generator g = readGenerator(key, s);
                if (g == null) continue;
                add(g);
                if ("REGENERATING".equalsIgnoreCase(s.getString("state"))) resume.add(g);
            } catch (Exception e) {
                plugin.getLogger().warning("Nie mozna wczytac generatora '" + key + "': " + e.getMessage());
            }
        }
        dirty = false;
        plugin.getLogger().info("Wczytano generatorow: " + generators.size());

        // wznow przerwane regeneracje po starcie serwera
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Generator g : resume) {
                g.setState(GeneratorState.ACTIVE);
                startRegen(g);
            }
        });
    }

    private Generator readGenerator(String name, ConfigurationSection s) {
        String world = s.getString("world");
        if (world == null) return null;
        Generator g = new Generator(name, world,
                s.getInt("min.x"), s.getInt("min.y"), s.getInt("min.z"),
                s.getInt("max.x"), s.getInt("max.y"), s.getInt("max.z"));
        g.setIntervalSeconds(s.getInt("interval", 60));
        g.setTrigger(Util.parseEnum(TriggerMode.class, s.getString("trigger"), TriggerMode.WHEN_EMPTY));
        g.setTriggerPercent(s.getInt("trigger-percent", 100));
        g.setRegenType(Util.parseEnum(RegenType.class, s.getString("regen-type"), RegenType.WAVE));
        g.setRegenSeconds(s.getInt("regen-seconds", 5));
        g.setParticle(s.getString("particle", "END_ROD"));
        g.setSound(s.getString("sound", "block.amethyst_block.chime"));
        g.setCountdown(Util.parseEnum(CountdownMode.class, s.getString("countdown"), CountdownMode.ACTIONBAR));
        g.setTeleportOut(s.getBoolean("teleport-out", true));
        g.setProtect(s.getBoolean("protect", true));
        g.setEnabled(s.getBoolean("enabled", true));

        ConfigurationSection bs = s.getConfigurationSection("blocks");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null && m.isBlock() && !m.isAir()) g.setWeight(m, bs.getInt(k, 1));
            }
        }
        if (g.getBlocks().isEmpty()) g.setWeight(Material.STONE, 1);

        GeneratorState st = Util.parseEnum(GeneratorState.class, s.getString("state"), GeneratorState.ACTIVE);
        g.setState(st == GeneratorState.REGENERATING ? GeneratorState.ACTIVE : st);
        g.setTimeLeft(s.getInt("time-left", g.getIntervalSeconds()));
        g.setRemaining(s.getInt("remaining", (int) Math.min(Integer.MAX_VALUE, g.volume())));
        return g;
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Generator g : generators.values()) {
            String p = "generators." + g.getName() + ".";
            yml.set(p + "world", g.getWorldName());
            yml.set(p + "min.x", g.getMinX());
            yml.set(p + "min.y", g.getMinY());
            yml.set(p + "min.z", g.getMinZ());
            yml.set(p + "max.x", g.getMaxX());
            yml.set(p + "max.y", g.getMaxY());
            yml.set(p + "max.z", g.getMaxZ());
            yml.set(p + "interval", g.getIntervalSeconds());
            yml.set(p + "trigger", g.getTrigger().name());
            yml.set(p + "trigger-percent", g.getTriggerPercent());
            yml.set(p + "regen-type", g.getRegenType().name());
            yml.set(p + "regen-seconds", g.getRegenSeconds());
            yml.set(p + "particle", g.getParticle());
            yml.set(p + "sound", g.getSound());
            yml.set(p + "countdown", g.getCountdown().name());
            yml.set(p + "teleport-out", g.isTeleportOut());
            yml.set(p + "protect", g.isProtect());
            yml.set(p + "enabled", g.isEnabled());
            yml.set(p + "state", g.getState().name());
            yml.set(p + "time-left", g.getTimeLeft());
            yml.set(p + "remaining", g.getRemaining());
            for (Map.Entry<Material, Integer> e : g.getBlocks().entrySet()) {
                yml.set(p + "blocks." + e.getKey().name(), e.getValue());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac generators.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (secondTask != null) secondTask.cancel();
        if (saveTask != null) saveTask.cancel();
        for (Generator g : generators.values()) {
            if (g.getJob() != null) g.getJob().stop(true);
        }
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
        save();
    }
}
