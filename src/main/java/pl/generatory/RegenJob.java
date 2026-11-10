package pl.generatory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zadanie regeneracji jednego generatora. Przetwarza obszar porcjami (na ticki),
 * wiec nawet bardzo duze generatory nie lagguja serwera.
 */
public final class RegenJob extends BukkitRunnable {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final class Pending {
        final Block block;
        final Material material;
        final BlockDisplay display;
        final int spawnTick;
        boolean started;

        Pending(Block block, Material material, BlockDisplay display, int spawnTick) {
            this.block = block;
            this.material = material;
            this.display = display;
            this.spawnTick = spawnTick;
        }
    }

    private final GeneratorsPlugin plugin;
    private final Generator g;
    private final World world;
    private final RegenType type;

    private final int sx, sy, sz, layer, total;
    private int[] order;
    private int cursor = 0;
    private final int budget;

    private final List<Integer> deferred = new ArrayList<>();
    private int deferredTicks = 0;
    private final List<Pending> pending = new ArrayList<>();
    private int tickCounter = 0;

    private final Particle particle;
    private final String soundKey;
    private final int maxParticles;
    private final int soundEvery;
    private final int maxDisplays;
    private final int growTicks;

    public RegenJob(GeneratorsPlugin plugin, Generator g, World world) {
        this.plugin = plugin;
        this.g = g;
        this.world = world;
        this.type = g.getRegenType();
        this.sx = g.sizeX();
        this.sy = g.sizeY();
        this.sz = g.sizeZ();
        this.layer = sx * sz;
        this.total = (int) g.volume();

        var cfg = plugin.getConfig();
        this.maxParticles = cfg.getInt("effects.max-particles-per-tick", 30);
        this.soundEvery = Math.max(1, cfg.getInt("effects.sound-every-ticks", 4));
        this.maxDisplays = cfg.getInt("effects.max-displays-per-tick", 6);
        this.growTicks = Math.max(2, cfg.getInt("effects.grow-ticks", 10));
        this.particle = Util.resolveParticle(g.getParticle());
        this.soundKey = (g.getSound() == null || g.getSound().equalsIgnoreCase("none")) ? null : g.getSound();

        int maxPerTick = Math.max(1, cfg.getInt("settings.max-blocks-per-tick", 5000));
        if (type == RegenType.INSTANT) {
            this.budget = Math.max(1, cfg.getInt("settings.instant-blocks-per-tick", 20000));
        } else {
            int ticks = Math.max(1, g.getRegenSeconds() * 20);
            int perTick = (int) Math.ceil(total / (double) ticks);
            this.budget = Math.max(1, Math.min(maxPerTick, perTick));
        }

        switch (type) {
            case CENTER_OUT, GROWING -> order = buildCenterOut();
            case RANDOM_POP -> order = buildShuffled();
            default -> order = null; // WAVE / INSTANT: kolejnosc naturalna (warstwami od dolu)
        }
    }

    // ------------------------------------------------------------ kolejnosc
    private int[] buildCenterOut() {
        long[] keys = new long[total];
        for (int i = 0; i < total; i++) {
            int y = i / layer;
            int rem = i - y * layer;
            int x = rem / sz;
            int z = rem - x * sz;
            long dx = 2L * x - (sx - 1);
            long dy = 2L * y - (sy - 1);
            long dz = 2L * z - (sz - 1);
            long dist = dx * dx + dy * dy + dz * dz;
            keys[i] = (dist << 32) | i;
        }
        java.util.Arrays.sort(keys);
        int[] out = new int[total];
        for (int i = 0; i < total; i++) out[i] = (int) (keys[i] & 0xFFFFFFFFL);
        return out;
    }

    private int[] buildShuffled() {
        int[] out = new int[total];
        for (int i = 0; i < total; i++) out[i] = i;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = total - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return out;
    }

    private Block blockAt(int idx) {
        int y = idx / layer;
        int rem = idx - y * layer;
        int x = rem / sz;
        int z = rem - x * sz;
        return world.getBlockAt(g.getMinX() + x, g.getMinY() + y, g.getMinZ() + z);
    }

    // ------------------------------------------------------------ start / stop
    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    /** Przerywa zadanie. Jesli place=true, dokancza rozpoczete animacje (stawia bloki). */
    public void stop(boolean place) {
        try {
            cancel();
        } catch (IllegalStateException ignored) {
            // nie byl zaplanowany
        }
        for (Pending p : pending) finishPending(p, place);
        pending.clear();
        if (g.getJob() == this) g.setJob(null);
    }

    // ------------------------------------------------------------ glowna petla
    @Override
    public void run() {
        if (!g.isEnabled()) return; // wstrzymane
        tickCounter++;

        processPending();
        Set<Long> occupied = handlePlayers();

        int processed = 0;
        int particles = 0;
        int displays = 0;
        boolean soundPlayed = false;

        while (cursor < total && processed < budget) {
            int idx = order == null ? cursor : order[cursor];
            cursor++;
            processed++;

            Block b = blockAt(idx);
            if (Util.isFilled(b)) continue;
            if (occupied.contains(Util.blockKey(b.getX(), b.getY(), b.getZ()))) {
                deferred.add(idx);
                continue;
            }

            Material m = g.randomMaterial();
            if (type == RegenType.GROWING && displays < maxDisplays && isSurface(b)) {
                startGrow(b, m);
                displays++;
                continue;
            }

            placeBlock(b, m);
            if (particle != null && particles < maxParticles) {
                spawnParticle(b);
                particles++;
            }
            if (!soundPlayed && soundKey != null && tickCounter % soundEvery == 0) {
                playSound(b);
                soundPlayed = true;
            }
        }

        if (cursor >= total) {
            processDeferred(occupied);
        }

        if (cursor >= total && deferred.isEmpty() && pending.isEmpty()) {
            finish();
        }
    }

    private void processDeferred(Set<Long> occupied) {
        if (deferred.isEmpty()) return;
        deferredTicks++;
        boolean force = deferredTicks > 400; // po ~20 s stawiamy mimo graczy
        Iterator<Integer> it = deferred.iterator();
        while (it.hasNext()) {
            int idx = it.next();
            Block b = blockAt(idx);
            if (Util.isFilled(b)) {
                it.remove();
                continue;
            }
            if (!force && occupied.contains(Util.blockKey(b.getX(), b.getY(), b.getZ()))) continue;
            placeBlock(b, g.randomMaterial());
            it.remove();
        }
    }

    // ------------------------------------------------------------ gracze
    private Set<Long> handlePlayers() {
        Set<Long> occ = new HashSet<>();
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (!g.isNear(l, 1)) continue;

            if (g.isTeleportOut() && (g.contains(l) || g.contains(p.getEyeLocation()))) {
                Location safe = findSafe(p);
                if (safe != null) {
                    p.teleport(safe);
                    plugin.msg().send(p, "teleported-out");
                    l = p.getLocation();
                }
            }
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            occ.add(Util.blockKey(x, y, z));
            occ.add(Util.blockKey(x, y + 1, z));
            int ey = p.getEyeLocation().getBlockY();
            occ.add(Util.blockKey(x, ey, z));
        }
        return occ;
    }

    private Location findSafe(Player p) {
        Location pl = p.getLocation();
        int x = pl.getBlockX();
        int z = pl.getBlockZ();
        int startY = g.getMaxY() + 1;
        int limit = Math.min(world.getMaxHeight() - 2, startY + 80);
        for (int y = startY; y < limit; y++) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()) {
                return new Location(world, pl.getX(), y, pl.getZ(), pl.getYaw(), pl.getPitch());
            }
        }
        return null;
    }

    // ------------------------------------------------------------ rosnace bloki
    private boolean isSurface(Block b) {
        for (BlockFace f : FACES) {
            if (b.getRelative(f).getType().isAir()) return true;
        }
        return false;
    }

    private void startGrow(Block b, Material m) {
        Location loc = b.getLocation();
        BlockDisplay d = world.spawn(loc, BlockDisplay.class);
        d.setBlock(m.createBlockData());
        d.setPersistent(false);
        d.setTransformation(new Transformation(
                new Vector3f(0.475f, 0.475f, 0.475f),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(0.05f, 0.05f, 0.05f),
                new AxisAngle4f(0, 0, 0, 1)));
        pending.add(new Pending(b, m, d, tickCounter));
    }

    private void processPending() {
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (tickCounter >= p.spawnTick + growTicks + 2) {
                finishPending(p, true);
                it.remove();
                continue;
            }
            if (!p.started && tickCounter > p.spawnTick) {
                p.started = true;
                if (p.display.isValid()) {
                    p.display.setInterpolationDelay(0);
                    p.display.setInterpolationDuration(growTicks);
                    p.display.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 0, 1),
                            new Vector3f(1, 1, 1),
                            new AxisAngle4f(0, 0, 0, 1)));
                }
            }
        }
    }

    private void finishPending(Pending p, boolean place) {
        if (p.display.isValid()) p.display.remove();
        if (place && !Util.isFilled(p.block)) {
            placeBlock(p.block, p.material);
            if (particle != null) spawnParticle(p.block);
        }
    }

    // ------------------------------------------------------------ pomocnicze
    private void placeBlock(Block b, Material m) {
        b.setType(m, false);
        g.incRemaining();
    }

    private void spawnParticle(Block b) {
        world.spawnParticle(particle, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5,
                3, 0.3, 0.3, 0.3, 0.01);
    }

    private void playSound(Block b) {
        float progress = total == 0 ? 1f : (float) cursor / total;
        float pitch = 0.6f + 1.2f * Math.max(0f, Math.min(1f, progress));
        world.playSound(b.getLocation().add(0.5, 0.5, 0.5), soundKey, SoundCategory.BLOCKS, 0.6f, pitch);
    }

    private void finish() {
        try {
            cancel();
        } catch (IllegalStateException ignored) {
            // juz anulowane
        }
        g.setJob(null);
        g.setState(GeneratorState.ACTIVE);
        g.setTimeLeft(g.getIntervalSeconds());

        String fin = plugin.getConfig().getString("effects.finish-sound", "entity.player.levelup");
        if (fin != null && !fin.equalsIgnoreCase("none")) {
            world.playSound(g.center(world), fin, SoundCategory.BLOCKS, 0.7f, 1.2f);
        }
        plugin.manager().markDirty();
        plugin.manager().evaluateTrigger(g);
    }
}
