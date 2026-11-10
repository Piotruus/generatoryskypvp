package pl.generatory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GuiManager implements Listener {

    public enum View { SETTINGS, BLOCKS }

    public static final class GuiHolder implements InventoryHolder {
        final Generator generator;
        final View view;
        Inventory inventory;

        GuiHolder(Generator generator, View view) {
            this.generator = generator;
            this.view = view;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    // sloty ustawien
    private static final int S_INFO = 4, S_INTERVAL = 10, S_TRIGGER = 11, S_PERCENT = 12, S_TYPE = 13,
            S_DURATION = 14, S_PARTICLE = 15, S_SOUND = 16, S_COUNTDOWN = 19, S_TELEPORT = 20,
            S_PROTECT = 21, S_ENABLED = 22, S_BLOCKS = 24, S_REGEN = 28, S_RECOUNT = 29, S_TP = 30,
            S_CLOSE = 49, S_DELETE = 53;

    // sloty blokow
    private static final int B_INFO = 45, B_BACK = 48, B_ADD = 49, B_DEFAULTS = 50, B_CLOSE = 53;

    private final GeneratorsPlugin plugin;
    private final Map<UUID, Long> deleteConfirm = new HashMap<>();

    public GuiManager(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================= otwieranie
    public void open(Player p, Generator g) {
        GuiHolder h = new GuiHolder(g, View.SETTINGS);
        Inventory inv = Bukkit.createInventory(h, 54, plugin.msg().raw("gui-title-settings", "name", g.getName()));
        h.inventory = inv;
        renderSettings(inv, g);
        p.openInventory(inv);
    }

    public void openBlocks(Player p, Generator g) {
        GuiHolder h = new GuiHolder(g, View.BLOCKS);
        Inventory inv = Bukkit.createInventory(h, 54, plugin.msg().raw("gui-title-blocks", "name", g.getName()));
        h.inventory = inv;
        renderBlocks(inv, g);
        p.openInventory(inv);
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder) p.closeInventory();
        }
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Inventory top = p.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof GuiHolder h && h.view == View.SETTINGS) {
                    renderSettings(top, h.generator);
                }
            }
        }, 20L, 20L);
    }

    // ================================================================= renderowanie ustawien
    private void renderSettings(Inventory inv, Generator g) {
        ItemStack filler = Util.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        GeneratorManager mgr = plugin.manager();
        String timer;
        if (g.getState() == GeneratorState.WAITING
                || (g.getState() == GeneratorState.ACTIVE && g.getTrigger() == TriggerMode.PERIODIC)) {
            timer = Util.formatTime(g.getTimeLeft());
        } else {
            timer = "-";
        }

        inv.setItem(S_INFO, Util.item(Material.OAK_SIGN, "&6&l" + g.getName(),
                "&7Swiat: &f" + g.getWorldName(),
                "&7Rozmiar: &f" + g.sizeText() + " &7(" + g.volume() + " blokow)",
                "&7Wypelnienie: &f" + g.fillPercent() + "% &7(" + g.getRemaining() + "/" + g.volume() + ")",
                "&7Status: " + mgr.statusText(g),
                "&7Nastepna regeneracja za: &f" + timer));

        inv.setItem(S_INTERVAL, Util.item(Material.CLOCK, "&eInterwal regeneracji",
                "&7Aktualnie: &a" + Util.formatTime(g.getIntervalSeconds()) + " &7(" + g.getIntervalSeconds() + "s)",
                "",
                "&8LPM &7+5s   &8PPM &7-5s",
                "&8Shift+LPM &7+60s   &8Shift+PPM &7-60s"));

        inv.setItem(S_TRIGGER, Util.item(Material.REPEATER, "&eTryb odliczania",
                "&7Aktualnie: &a" + g.getTrigger().getDisplay(),
                "&8" + g.getTrigger().getDescription(),
                "",
                "&8Kliknij, aby zmienic"));

        inv.setItem(S_PERCENT, Util.item(Material.COMPARATOR, "&eProg wykopania",
                "&7Aktualnie: &a" + g.getTriggerPercent() + "%",
                "&8Ile % obszaru musi byc wykopane,",
                "&8zeby ruszylo odliczanie (tryb po wykopaniu)",
                "",
                "&8LPM &7+5%   &8PPM &7-5%",
                "&8Shift+LPM &7+1%   &8Shift+PPM &7-1%"));

        List<String> typeLore = new ArrayList<>();
        typeLore.add("&7Aktualnie: &a" + g.getRegenType().getDisplay());
        typeLore.add("&8" + g.getRegenType().getDescription());
        typeLore.add("");
        for (RegenType t : RegenType.values()) {
            typeLore.add((t == g.getRegenType() ? "&a\u25b6 " : "&7  ") + t.getDisplay());
        }
        typeLore.add("");
        typeLore.add("&8LPM &7nastepny   &8PPM &7poprzedni");
        inv.setItem(S_TYPE, Util.item(g.getRegenType().getIcon(), "&eTyp regeneracji", typeLore));

        inv.setItem(S_DURATION, Util.item(Material.HOPPER, "&eCzas animacji",
                "&7Aktualnie: &a" + g.getRegenSeconds() + "s",
                "&8Jak dlugo trwa odnawianie obszaru",
                "",
                "&8LPM &7+1s   &8PPM &7-1s",
                "&8Shift+LPM &7+10s   &8Shift+PPM &7-10s"));

        inv.setItem(S_PARTICLE, Util.item(Material.BLAZE_POWDER, "&eCzasteczki",
                "&7Aktualnie: &a" + g.getParticle(),
                "",
                "&8LPM &7nastepne   &8PPM &7poprzednie"));

        inv.setItem(S_SOUND, Util.item(Material.NOTE_BLOCK, "&eDzwiek",
                "&7Aktualnie: &a" + g.getSound(),
                "",
                "&8LPM &7nastepny   &8PPM &7poprzedni"));

        inv.setItem(S_COUNTDOWN, Util.item(Material.BELL, "&eOdliczanie dla graczy",
                "&7Aktualnie: &a" + g.getCountdown().getDisplay(),
                "&8Pokazuje graczom w poblizu czas do regeneracji",
                "",
                "&8LPM &7nastepne   &8PPM &7poprzednie"));

        inv.setItem(S_TELEPORT, Util.item(Material.ENDER_PEARL, "&ePrzenos graczy z obszaru",
                "&7Stan: " + Util.onOff(g.isTeleportOut()),
                "&8Gracze wewnatrz sa wynoszeni na gore",
                "&8przed regeneracja (bez uduszenia)"));

        inv.setItem(S_PROTECT, Util.item(Material.SHIELD, "&eOchrona obszaru",
                "&7Stan: " + Util.onOff(g.isProtect()),
                "&8Blokuje stawianie blokow, plyny, tloki"));

        inv.setItem(S_ENABLED, Util.item(Material.LEVER, "&eGenerator",
                "&7Stan: " + Util.onOff(g.isEnabled()),
                "&8Wylaczony generator nie regeneruje sie"));

        List<String> blocksLore = new ArrayList<>();
        blocksLore.add("&7Rodzajow: &f" + g.getBlocks().size());
        int shown = 0;
        for (Map.Entry<Material, Integer> e : g.getBlocks().entrySet()) {
            if (shown++ >= 6) {
                blocksLore.add("&8...");
                break;
            }
            blocksLore.add("&7- &f" + Util.niceName(e.getKey()) + " &8(" + percent(g, e.getValue()) + "%)");
        }
        blocksLore.add("");
        blocksLore.add("&8Kliknij, aby edytowac bloki i szanse");
        inv.setItem(S_BLOCKS, Util.item(Material.DIAMOND_PICKAXE, "&6&lBloki generatora", blocksLore));

        inv.setItem(S_REGEN, Util.item(Material.EMERALD, "&aWymus regeneracje",
                "&7Odnawia generator teraz"));
        inv.setItem(S_RECOUNT, Util.item(Material.SPYGLASS, "&ePrzelicz bloki",
                "&7Liczy zajete bloki w obszarze",
                "&8Uzyj po zmianach WorldEditem itp."));
        inv.setItem(S_TP, Util.item(Material.COMPASS, "&eTeleportuj sie", "&7Przenosi Cie nad generator"));
        inv.setItem(S_CLOSE, Util.item(Material.BARRIER, "&cZamknij"));
        inv.setItem(S_DELETE, Util.item(Material.TNT, "&4Usun generator",
                "&7Kliknij dwa razy, aby potwierdzic"));
    }

    private String percent(Generator g, int weight) {
        int total = Math.max(1, g.totalWeight());
        double p = weight * 100.0 / total;
        return String.format(java.util.Locale.ROOT, "%.1f", p);
    }

    // ================================================================= renderowanie blokow
    private void renderBlocks(Inventory inv, Generator g) {
        ItemStack filler = Util.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        for (int i = 0; i < 45; i++) inv.setItem(i, null);

        int slot = 0;
        for (Map.Entry<Material, Integer> e : g.getBlocks().entrySet()) {
            if (slot >= 45) break;
            Material m = e.getKey();
            ItemStack icon = m.isItem()
                    ? Util.item(m, "&f" + Util.niceName(m),
                    "&7Waga: &a" + e.getValue(),
                    "&7Szansa: &a" + percent(g, e.getValue()) + "%",
                    "",
                    "&8LPM &7+1   &8PPM &7-1",
                    "&8Shift+LPM &7+10   &8Shift+PPM &7-10",
                    "&8Q / srodkowy &7usun")
                    : Util.item(Material.BARRIER, "&f" + m.name(), "&7Waga: &a" + e.getValue());
            inv.setItem(slot++, icon);
        }

        inv.setItem(B_INFO, Util.item(Material.PAPER, "&6Szanse blokow",
                "&7Generator wybiera blok losowo",
                "&7proporcjonalnie do wagi.",
                "&7Suma wag: &f" + g.totalWeight()));
        inv.setItem(B_BACK, Util.item(Material.ARROW, "&eWroc do ustawien"));
        inv.setItem(B_ADD, Util.item(Material.HOPPER, "&aDodaj blok",
                "&7Kliknij blok w swoim ekwipunku",
                "&7albo poloz go tu kursorem."));
        inv.setItem(B_DEFAULTS, Util.item(Material.LAVA_BUCKET, "&cPrzywroc domyslne bloki",
                "&7Z config.yml (sekcja defaults.blocks)"));
        inv.setItem(B_CLOSE, Util.item(Material.BARRIER, "&cZamknij"));
    }

    // ================================================================= klikniecia
    private static int step(ClickType c, int normal, int shift) {
        return switch (c) {
            case LEFT -> normal;
            case RIGHT -> -normal;
            case SHIFT_LEFT -> shift;
            case SHIFT_RIGHT -> -shift;
            default -> 0;
        };
    }

    private static int dir(ClickType c) {
        return (c == ClickType.RIGHT || c == ClickType.SHIFT_RIGHT) ? -1 : 1;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("generator.admin")) {
            p.closeInventory();
            return;
        }
        Generator g = h.generator;
        if (plugin.manager().get(g.getName()) != g) { // usuniety
            p.closeInventory();
            return;
        }

        Inventory top = e.getInventory();
        boolean topClick = e.getClickedInventory() != null && e.getClickedInventory().equals(top);

        if (h.view == View.SETTINGS) {
            if (topClick) clickSettings(p, g, top, e.getSlot(), e.getClick());
        } else {
            clickBlocks(p, g, top, e, topClick);
        }
    }

    private void clickSettings(Player p, Generator g, Inventory inv, int slot, ClickType c) {
        GeneratorManager mgr = plugin.manager();
        switch (slot) {
            case S_INTERVAL -> {
                int v = step(c, 5, 60);
                if (v == 0) return;
                g.setIntervalSeconds(Math.min(86400, g.getIntervalSeconds() + v));
                mgr.onIntervalChanged(g);
            }
            case S_TRIGGER -> {
                g.setTrigger(Util.cycle(TriggerMode.values(), g.getTrigger(), 1));
                mgr.resetTimer(g);
            }
            case S_PERCENT -> {
                int v = step(c, 5, 1);
                if (v == 0) return;
                g.setTriggerPercent(g.getTriggerPercent() + v);
                mgr.evaluateTrigger(g);
            }
            case S_TYPE -> g.setRegenType(Util.cycle(RegenType.values(), g.getRegenType(), dir(c)));
            case S_DURATION -> {
                int v = step(c, 1, 10);
                if (v == 0) return;
                g.setRegenSeconds(g.getRegenSeconds() + v);
            }
            case S_PARTICLE -> g.setParticle(Util.cycleString(Util.availableParticles(), g.getParticle(), dir(c)));
            case S_SOUND -> {
                g.setSound(Util.cycleString(Util.SOUNDS, g.getSound(), dir(c)));
                if (!g.getSound().equalsIgnoreCase("none")) {
                    p.playSound(p.getLocation(), g.getSound(), 1f, 1f);
                }
            }
            case S_COUNTDOWN -> g.setCountdown(Util.cycle(CountdownMode.values(), g.getCountdown(), dir(c)));
            case S_TELEPORT -> g.setTeleportOut(!g.isTeleportOut());
            case S_PROTECT -> g.setProtect(!g.isProtect());
            case S_ENABLED -> {
                g.setEnabled(!g.isEnabled());
                if (g.isEnabled()) mgr.evaluateTrigger(g);
            }
            case S_BLOCKS -> {
                Bukkit.getScheduler().runTask(plugin, () -> openBlocks(p, g));
                return;
            }
            case S_REGEN -> {
                if (mgr.startRegen(g)) plugin.msg().send(p, "regen-started", "name", g.getName());
                else plugin.msg().send(p, "regen-already", "name", g.getName());
            }
            case S_RECOUNT -> {
                int n = mgr.recountSync(g);
                if (n >= 0) plugin.msg().send(p, "recount-done", "name", g.getName(), "count", n, "total", g.volume());
            }
            case S_TP -> {
                if (g.getWorld() != null) {
                    p.closeInventory();
                    p.teleport(g.center(g.getWorld()).add(0, g.sizeY() / 2.0 + 2, 0));
                    plugin.msg().send(p, "tp-done", "name", g.getName());
                }
                return;
            }
            case S_CLOSE -> {
                p.closeInventory();
                return;
            }
            case S_DELETE -> {
                long now = System.currentTimeMillis();
                Long last = deleteConfirm.get(p.getUniqueId());
                if (last != null && now - last < 5000) {
                    deleteConfirm.remove(p.getUniqueId());
                    String name = g.getName();
                    p.closeInventory();
                    mgr.delete(g);
                    plugin.msg().send(p, "deleted", "name", name);
                } else {
                    deleteConfirm.put(p.getUniqueId(), now);
                    plugin.msg().send(p, "delete-confirm");
                }
                return;
            }
            default -> {
                return;
            }
        }
        mgr.markDirty();
        renderSettings(inv, g);
    }

    private void clickBlocks(Player p, Generator g, Inventory inv, InventoryClickEvent e, boolean topClick) {
        GeneratorManager mgr = plugin.manager();

        // klikniecie w wlasny ekwipunek -> dodaj blok
        if (!topClick) {
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType().isAir()) return;
            addMaterial(p, g, inv, cur.getType());
            return;
        }

        int slot = e.getSlot();
        ClickType c = e.getClick();

        if (slot < 45) {
            List<Material> list = new ArrayList<>(g.getBlocks().keySet());
            if (slot >= list.size()) return;
            Material m = list.get(slot);
            if (c == ClickType.DROP || c == ClickType.CONTROL_DROP || c == ClickType.MIDDLE) {
                removeMaterial(p, g, m);
            } else {
                int v = step(c, 1, 10);
                if (v == 0) return;
                int w = g.getWeight(m) + v;
                if (w < 1) removeMaterial(p, g, m);
                else g.setWeight(m, Math.min(1000, w));
            }
        } else {
            switch (slot) {
                case B_BACK -> {
                    Bukkit.getScheduler().runTask(plugin, () -> open(p, g));
                    return;
                }
                case B_ADD -> {
                    ItemStack cursor = e.getCursor();
                    if (cursor != null && !cursor.getType().isAir()) addMaterial(p, g, inv, cursor.getType());
                    return;
                }
                case B_DEFAULTS -> mgr.applyDefaultBlocks(g);
                case B_CLOSE -> {
                    p.closeInventory();
                    return;
                }
                default -> {
                    return;
                }
            }
        }
        mgr.markDirty();
        renderBlocks(inv, g);
    }

    private void addMaterial(Player p, Generator g, Inventory inv, Material m) {
        if (!m.isBlock() || !m.isItem() || m.isAir()) {
            plugin.msg().send(p, "gui-not-block");
            return;
        }
        if (g.getBlocks().size() >= 45 && !g.getBlocks().containsKey(m)) return;
        if (!g.getBlocks().containsKey(m)) g.setWeight(m, 10);
        plugin.msg().send(p, "gui-block-added", "material", Util.niceName(m));
        plugin.manager().markDirty();
        renderBlocks(inv, g);
    }

    private void removeMaterial(Player p, Generator g, Material m) {
        if (g.getBlocks().size() <= 1) {
            plugin.msg().send(p, "block-last");
            return;
        }
        g.removeBlock(m);
    }
}
