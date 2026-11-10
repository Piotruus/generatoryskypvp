package pl.generatory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SelectionManager implements Listener {

    private final GeneratorsPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Particle outline;

    public SelectionManager(GeneratorsPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "wand");
        Particle p = Util.resolveParticle("END_ROD");
        if (p == null) p = Util.resolveParticle("FLAME");
        this.outline = p;
    }

    // ------------------------------------------------------------ rozdzka
    public ItemStack createWand() {
        ItemStack it = Util.item(Material.BLAZE_ROD, "&6&lRozdzka generatora",
                "&7LPM &8- &epozycja 1",
                "&7PPM &8- &epozycja 2",
                "&7Potem: &e/generator create <nazwa>");
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    public boolean isWand(ItemStack it) {
        if (it == null || it.getType() != Material.BLAZE_ROD || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ zaznaczenie
    public Location[] get(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
    }

    public boolean isComplete(Player p) {
        Location[] s = selections.get(p.getUniqueId());
        return s != null && s[0] != null && s[1] != null;
    }

    public void setPos(Player p, int which, Location blockLoc) {
        get(p)[which] = blockLoc.getBlock().getLocation();
        Location[] s = get(p);
        String info = "";
        if (s[0] != null && s[1] != null && s[0].getWorld() != null && s[0].getWorld().equals(s[1].getWorld())) {
            int x = Math.abs(s[0].getBlockX() - s[1].getBlockX()) + 1;
            int y = Math.abs(s[0].getBlockY() - s[1].getBlockY()) + 1;
            int z = Math.abs(s[0].getBlockZ() - s[1].getBlockZ()) + 1;
            info = plugin.msg().raw("selection-info", "size", x + "x" + y + "x" + z, "volume", (long) x * y * z);
        }
        Location l = s[which];
        plugin.msg().send(p, which == 0 ? "pos1-set" : "pos2-set",
                "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "info", info);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        if (!isWand(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("generator.admin")) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        setPos(p, a == Action.LEFT_CLICK_BLOCK ? 0 : 1, b.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (isWand(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        selections.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ podglad ramki
    public void startTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (outline == null) return;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!isWand(p.getInventory().getItemInMainHand())) continue;
                Location[] s = selections.get(p.getUniqueId());
                if (s == null) continue;
                Location a = s[0], b = s[1];
                if (a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())
                        && a.getWorld().equals(p.getWorld())) {
                    drawBox(p, a, b);
                } else {
                    if (a != null && a.getWorld() != null && a.getWorld().equals(p.getWorld())) drawBox(p, a, a);
                    if (b != null && b.getWorld() != null && b.getWorld().equals(p.getWorld())) drawBox(p, b, b);
                }
            }
        }, 10L, 10L);
    }

    private void drawBox(Player p, Location a, Location b) {
        double x1 = Math.min(a.getBlockX(), b.getBlockX());
        double y1 = Math.min(a.getBlockY(), b.getBlockY());
        double z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        double x2 = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double y2 = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        double z2 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2}, {x1, y2, z1, x2, y2, z1}, {x1, y2, z2, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2},
                {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2}, {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2}
        };
        for (double[] e : edges) line(p, e[0], e[1], e[2], e[3], e[4], e[5]);
    }

    private void line(Player p, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = (int) Math.min(40, Math.max(1, Math.ceil(len)));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            p.spawnParticle(outline, x1 + dx * t, y1 + dy * t, z1 + dz * t, 1, 0, 0, 0, 0);
        }
    }

    @SuppressWarnings("unused")
    private static List<String> unused() { return List.of(); }
}
