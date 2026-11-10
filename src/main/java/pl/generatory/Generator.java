package pl.generatory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Generator {

    private final String name;
    private final String worldName;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    private final Map<Material, Integer> blocks = new LinkedHashMap<>();
    private Material[] cacheMats = new Material[0];
    private int[] cacheCum = new int[0];
    private int cacheTotal = 0;

    // ustawienia
    private int intervalSeconds = 60;
    private TriggerMode trigger = TriggerMode.WHEN_EMPTY;
    private int triggerPercent = 100;
    private RegenType regenType = RegenType.WAVE;
    private int regenSeconds = 5;
    private String particle = "END_ROD";
    private String sound = "block.amethyst_block.chime";
    private CountdownMode countdown = CountdownMode.ACTIONBAR;
    private boolean teleportOut = true;
    private boolean protect = true;
    private boolean enabled = true;

    // stan dzialania
    private GeneratorState state = GeneratorState.ACTIVE;
    private int timeLeft = 0;
    private int remaining = 0;
    private RegenJob job;

    public Generator(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    // ---------- geometria ----------
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equals(worldName)
                && contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean isNear(Location l, int r) {
        if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= minX - r && x <= maxX + r && y >= minY - r && y <= maxY + r && z >= minZ - r && z <= maxZ + r;
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }
    public String sizeText() { return sizeX() + "x" + sizeY() + "x" + sizeZ(); }

    public Location center(World w) {
        return new Location(w, (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }

    public boolean overlaps(Generator o) {
        return worldName.equals(o.worldName)
                && minX <= o.maxX && maxX >= o.minX
                && minY <= o.maxY && maxY >= o.minY
                && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public boolean overlaps(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return worldName.equals(world)
                && minX <= Math.max(x1, x2) && maxX >= Math.min(x1, x2)
                && minY <= Math.max(y1, y2) && maxY >= Math.min(y1, y2)
                && minZ <= Math.max(z1, z2) && maxZ >= Math.min(z1, z2);
    }

    // ---------- bloki ----------
    public Map<Material, Integer> getBlocks() { return blocks; }

    public void setWeight(Material m, int weight) {
        blocks.put(m, Math.max(1, weight));
        rebuildCache();
    }

    public void removeBlock(Material m) {
        blocks.remove(m);
        rebuildCache();
    }

    public void clearBlocks() {
        blocks.clear();
        rebuildCache();
    }

    public int getWeight(Material m) { return blocks.getOrDefault(m, 0); }

    public int totalWeight() { return cacheTotal; }

    public void rebuildCache() {
        cacheMats = new Material[blocks.size()];
        cacheCum = new int[blocks.size()];
        int sum = 0, i = 0;
        for (Map.Entry<Material, Integer> e : blocks.entrySet()) {
            sum += e.getValue();
            cacheMats[i] = e.getKey();
            cacheCum[i] = sum;
            i++;
        }
        cacheTotal = sum;
    }

    public Material randomMaterial() {
        if (cacheTotal <= 0) return Material.STONE;
        int r = ThreadLocalRandom.current().nextInt(cacheTotal);
        for (int i = 0; i < cacheCum.length; i++) {
            if (r < cacheCum[i]) return cacheMats[i];
        }
        return cacheMats[cacheMats.length - 1];
    }

    // ---------- stan ----------
    public int fillPercent() {
        long v = volume();
        return v == 0 ? 0 : (int) Math.min(100, remaining * 100L / v);
    }

    public double fillFraction() {
        long v = volume();
        return v == 0 ? 0 : Math.max(0, Math.min(1, (double) remaining / v));
    }

    public boolean shouldTrigger() {
        return (long) remaining * 100 <= volume() * (100 - triggerPercent);
    }

    public void incRemaining() {
        if (remaining < volume()) remaining++;
    }

    public void decRemaining() {
        if (remaining > 0) remaining--;
    }

    public World getWorld() { return Bukkit.getWorld(worldName); }

    // ---------- gettery / settery ----------
    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int v) { this.intervalSeconds = Math.max(0, v); }
    public TriggerMode getTrigger() { return trigger; }
    public void setTrigger(TriggerMode t) { this.trigger = t; }
    public int getTriggerPercent() { return triggerPercent; }
    public void setTriggerPercent(int v) { this.triggerPercent = Math.max(1, Math.min(100, v)); }
    public RegenType getRegenType() { return regenType; }
    public void setRegenType(RegenType t) { this.regenType = t; }
    public int getRegenSeconds() { return regenSeconds; }
    public void setRegenSeconds(int v) { this.regenSeconds = Math.max(1, Math.min(300, v)); }
    public String getParticle() { return particle; }
    public void setParticle(String p) { this.particle = p; }
    public String getSound() { return sound; }
    public void setSound(String s) { this.sound = s; }
    public CountdownMode getCountdown() { return countdown; }
    public void setCountdown(CountdownMode c) { this.countdown = c; }
    public boolean isTeleportOut() { return teleportOut; }
    public void setTeleportOut(boolean b) { this.teleportOut = b; }
    public boolean isProtect() { return protect; }
    public void setProtect(boolean b) { this.protect = b; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean b) { this.enabled = b; }

    public GeneratorState getState() { return state; }
    public void setState(GeneratorState s) { this.state = s; }
    public int getTimeLeft() { return timeLeft; }
    public void setTimeLeft(int t) { this.timeLeft = Math.max(0, t); }
    public int getRemaining() { return remaining; }
    public void setRemaining(int r) { this.remaining = (int) Math.max(0, Math.min(volume(), r)); }
    public RegenJob getJob() { return job; }
    public void setJob(RegenJob j) { this.job = j; }
}
