package pl.generatory;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.List;

public final class GeneratorListener implements Listener {

    private final GeneratorsPlugin plugin;
    private final GeneratorManager mgr;

    public GeneratorListener(GeneratorsPlugin plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
    }

    private boolean denyExplosions() {
        return "DENY".equalsIgnoreCase(plugin.getConfig().getString("settings.explosions", "ALLOW"));
    }

    // ------------------------------------------------------------ kopanie
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Generator g = mgr.getAt(e.getBlock());
        if (g == null) return;
        g.decRemaining();
        mgr.markDirty();
        mgr.evaluateTrigger(g);
    }

    // ------------------------------------------------------------ stawianie
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Generator g = mgr.getAt(e.getBlockPlaced());
        if (g == null || !g.isProtect()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("generator.bypass")) return;
        e.setCancelled(true);
        plugin.msg().send(p, "place-blocked");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceMonitor(BlockPlaceEvent e) {
        Generator g = mgr.getAt(e.getBlockPlaced());
        if (g == null) return;
        if (e.getBlockReplacedState().getType().isAir() || e.getBlockReplacedState().getBlockData().getMaterial().isAir()) {
            g.incRemaining();
            mgr.markDirty();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Generator g = mgr.getAt(e.getBlock());
        if (g == null || !g.isProtect() || e.getPlayer().hasPermission("generator.bypass")) return;
        e.setCancelled(true);
        plugin.msg().send(e.getPlayer(), "place-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent e) {
        Generator g = mgr.getAt(e.getToBlock());
        if (g != null && g.isProtect()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        Generator g = mgr.getAt(e.getBlock());
        if (g != null && g.isProtect()) e.setCancelled(true);
    }

    // ------------------------------------------------------------ tloki
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (touchesProtected(e.getBlocks(), e.getDirection().getModX(), e.getDirection().getModY(), e.getDirection().getModZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (touchesProtected(e.getBlocks(), e.getDirection().getModX(), e.getDirection().getModY(), e.getDirection().getModZ())) {
            e.setCancelled(true);
        }
    }

    private boolean touchesProtected(List<Block> blocks, int dx, int dy, int dz) {
        for (Block b : blocks) {
            Generator a = mgr.getAt(b);
            if (a != null && a.isProtect()) return true;
            Generator c = mgr.getAt(b.getX() + dx, b.getY() + dy, b.getZ() + dz, b.getWorld().getName());
            if (c != null && c.isProtect()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ wybuchy
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (denyExplosions()) e.blockList().removeIf(b -> mgr.getAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (denyExplosions()) e.blockList().removeIf(b -> mgr.getAt(b) != null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplodeMonitor(EntityExplodeEvent e) {
        countExploded(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplodeMonitor(BlockExplodeEvent e) {
        countExploded(e.blockList());
    }

    private void countExploded(List<Block> blocks) {
        for (Block b : blocks) {
            Generator g = mgr.getAt(b);
            if (g != null && Util.isFilled(b)) {
                g.decRemaining();
                mgr.markDirty();
                mgr.evaluateTrigger(g);
            }
        }
    }
}
