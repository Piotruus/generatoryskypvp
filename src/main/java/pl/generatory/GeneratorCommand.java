package pl.generatory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class GeneratorCommand implements CommandExecutor, TabCompleter {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9\\-]{1,32}$");
    private static final List<String> SUBS = List.of("help", "wand", "pos1", "pos2", "create", "delete", "list",
            "info", "gui", "edit", "regen", "recount", "tp", "setinterval", "settype", "toggle",
            "addblock", "removeblock", "reload");

    private final GeneratorsPlugin plugin;

    public GeneratorCommand(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages m() { return plugin.msg(); }
    private GeneratorManager mgr() { return plugin.manager(); }

    // ================================================================= komenda
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("generator.admin")) {
            m().send(s, "no-permission");
            return true;
        }
        if (a.length == 0) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(s);
            case "wand" -> {
                Player p = player(s);
                if (p == null) return true;
                p.getInventory().addItem(plugin.selection().createWand());
                m().send(p, "wand-given");
            }
            case "pos1", "pos2" -> {
                Player p = player(s);
                if (p == null) return true;
                plugin.selection().setPos(p, sub.equals("pos1") ? 0 : 1, p.getLocation());
            }
            case "create" -> create(s, a);
            case "delete", "remove" -> {
                Generator g = resolve(s, a, 1);
                if (g == null) return true;
                String name = g.getName();
                mgr().delete(g);
                m().send(s, "deleted", "name", name);
            }
            case "list" -> list(s);
            case "info" -> {
                Generator g = resolve(s, a, 1);
                if (g != null) info(s, g);
            }
            case "gui", "edit" -> {
                Player p = player(s);
                if (p == null) return true;
                Generator g = resolve(s, a, 1);
                if (g != null) plugin.gui().open(p, g);
            }
            case "regen" -> {
                Generator g = resolve(s, a, 1);
                if (g == null) return true;
                if (g.getWorld() == null) m().send(s, "regen-failed");
                else if (mgr().startRegen(g)) m().send(s, "regen-started", "name", g.getName());
                else m().send(s, "regen-already", "name", g.getName());
            }
            case "recount" -> {
                Generator g = resolve(s, a, 1);
                if (g == null) return true;
                int n = mgr().recountSync(g);
                if (n < 0) m().send(s, "regen-failed");
                else m().send(s, "recount-done", "name", g.getName(), "count", n, "total", g.volume());
            }
            case "tp" -> {
                Player p = player(s);
                if (p == null) return true;
                Generator g = resolve(s, a, 1);
                if (g == null) return true;
                World w = g.getWorld();
                if (w == null) {
                    m().send(s, "regen-failed");
                    return true;
                }
                p.teleport(g.center(w).add(0, g.sizeY() / 2.0 + 2, 0));
                m().send(p, "tp-done", "name", g.getName());
            }
            case "toggle" -> {
                Generator g = resolve(s, a, 1);
                if (g == null) return true;
                g.setEnabled(!g.isEnabled());
                if (g.isEnabled()) mgr().evaluateTrigger(g);
                mgr().markDirty();
                m().send(s, g.isEnabled() ? "toggled-on" : "toggled-off", "name", g.getName());
            }
            case "setinterval" -> setInterval(s, a);
            case "settype" -> setType(s, a);
            case "addblock" -> addBlock(s, a);
            case "removeblock" -> removeBlock(s, a);
            case "reload" -> {
                plugin.reloadAll();
                m().send(s, "reloaded");
            }
            default -> m().send(s, "unknown-sub");
        }
        return true;
    }

    // ================================================================= podkomendy
    private void help(CommandSender s) {
        for (String line : m().list("help")) s.sendMessage(line);
    }

    private void create(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) {
            m().send(s, "usage", "usage", "/generator create <nazwa>");
            return;
        }
        String name = a[1];
        if (!NAME.matcher(name).matches() || name.equalsIgnoreCase("nearest") || name.equalsIgnoreCase("count")) {
            m().send(s, "name-invalid");
            return;
        }
        if (mgr().exists(name)) {
            m().send(s, "name-exists", "name", name);
            return;
        }
        if (!plugin.selection().isComplete(p)) {
            m().send(s, "selection-missing");
            return;
        }
        Location[] sel = plugin.selection().get(p);
        Location p1 = sel[0], p2 = sel[1];
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            m().send(s, "selection-world");
            return;
        }

        long vx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1L;
        long vy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1L;
        long vz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1L;
        long volume = vx * vy * vz;
        long max = plugin.getConfig().getLong("settings.max-volume", 1_000_000L);
        if (volume > max) {
            m().send(s, "too-big", "volume", volume, "max", max);
            return;
        }

        String world = p1.getWorld().getName();
        Generator other = mgr().findOverlap(world, p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        if (other != null) {
            m().send(s, "overlap", "other", other.getName());
            return;
        }

        Generator g = new Generator(name, world,
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        mgr().applyDefaults(g);
        mgr().recountSync(g);
        mgr().add(g);
        mgr().startRegen(g); // pierwsze wypelnienie obszaru

        m().send(p, "created", "name", name, "size", g.sizeText(), "volume", g.volume());
        if (plugin.getConfig().getBoolean("settings.open-gui-after-create", true)) {
            plugin.gui().open(p, g);
        }
    }

    private void list(CommandSender s) {
        if (mgr().all().isEmpty()) {
            m().send(s, "list-empty");
            return;
        }
        m().sendPlain(s, "list-header", "count", mgr().all().size());
        for (Generator g : mgr().all()) {
            m().sendPlain(s, "list-entry", "name", g.getName(), "world", g.getWorldName(),
                    "size", g.sizeText(), "status", mgr().statusText(g), "percent", g.fillPercent());
        }
    }

    private void info(CommandSender s, Generator g) {
        m().sendPlain(s, "info-header", "name", g.getName());
        line(s, "Swiat", g.getWorldName());
        line(s, "Rozmiar", g.sizeText() + " (" + g.volume() + " blokow)");
        line(s, "Status", mgr().statusText(g));
        line(s, "Wypelnienie", g.fillPercent() + "% (" + g.getRemaining() + "/" + g.volume() + ")");
        line(s, "Interwal", Util.formatTime(g.getIntervalSeconds()));
        line(s, "Tryb odliczania", g.getTrigger().getDisplay() + " (prog " + g.getTriggerPercent() + "%)");
        line(s, "Typ regeneracji", g.getRegenType().getDisplay() + ", " + g.getRegenSeconds() + "s");
        line(s, "Czasteczki / dzwiek", g.getParticle() + " / " + g.getSound());
        line(s, "Odliczanie", g.getCountdown().getDisplay());
        line(s, "Ochrona / wynoszenie graczy", g.isProtect() + " / " + g.isTeleportOut());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> e : g.getBlocks().entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Util.niceName(e.getKey())).append(" ").append(e.getValue());
        }
        line(s, "Bloki (waga)", sb.toString());
    }

    private void line(CommandSender s, String label, String value) {
        m().sendPlain(s, "info-line", "label", label, "value", value);
    }

    private void setInterval(CommandSender s, String[] a) {
        Generator g;
        String val;
        if (a.length >= 3) {
            g = mgr().get(a[1]);
            val = a[2];
            if (g == null) {
                m().send(s, "not-found", "name", a[1]);
                return;
            }
        } else if (a.length == 2) {
            g = inside(s);
            val = a[1];
            if (g == null) {
                m().send(s, "not-in-generator");
                return;
            }
        } else {
            m().send(s, "usage", "usage", "/generator setinterval [nazwa] <sekundy>");
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(val);
        } catch (NumberFormatException e) {
            m().send(s, "invalid-number");
            return;
        }
        g.setIntervalSeconds(Math.min(86400, Math.max(0, sec)));
        mgr().onIntervalChanged(g);
        m().send(s, "interval-set", "name", g.getName(), "value", g.getIntervalSeconds());
    }

    private void setType(CommandSender s, String[] a) {
        Generator g;
        String val;
        if (a.length >= 3) {
            g = mgr().get(a[1]);
            val = a[2];
            if (g == null) {
                m().send(s, "not-found", "name", a[1]);
                return;
            }
        } else if (a.length == 2) {
            g = inside(s);
            val = a[1];
            if (g == null) {
                m().send(s, "not-in-generator");
                return;
            }
        } else {
            m().send(s, "usage", "usage", "/generator settype [nazwa] <typ>");
            return;
        }
        RegenType t = null;
        for (RegenType rt : RegenType.values()) {
            if (rt.name().equalsIgnoreCase(val)) t = rt;
        }
        if (t == null) {
            m().send(s, "invalid-type", "types", Arrays.toString(RegenType.values()));
            return;
        }
        g.setRegenType(t);
        mgr().markDirty();
        m().send(s, "type-set", "name", g.getName(), "value", t.getDisplay());
    }

    private void addBlock(CommandSender s, String[] a) {
        if (a.length < 3) {
            m().send(s, "usage", "usage", "/generator addblock <nazwa> <BLOK> [waga]");
            return;
        }
        Generator g = mgr().get(a[1]);
        if (g == null) {
            m().send(s, "not-found", "name", a[1]);
            return;
        }
        Material mat = Material.matchMaterial(a[2]);
        if (mat == null || !mat.isBlock() || !mat.isItem() || mat.isAir()) {
            m().send(s, "invalid-material", "material", a[2]);
            return;
        }
        int weight = 10;
        if (a.length >= 4) {
            try {
                weight = Math.max(1, Math.min(1000, Integer.parseInt(a[3])));
            } catch (NumberFormatException e) {
                m().send(s, "invalid-number");
                return;
            }
        }
        g.setWeight(mat, weight);
        mgr().markDirty();
        m().send(s, "block-added", "material", Util.niceName(mat), "weight", weight, "name", g.getName());
    }

    private void removeBlock(CommandSender s, String[] a) {
        if (a.length < 3) {
            m().send(s, "usage", "usage", "/generator removeblock <nazwa> <BLOK>");
            return;
        }
        Generator g = mgr().get(a[1]);
        if (g == null) {
            m().send(s, "not-found", "name", a[1]);
            return;
        }
        Material mat = Material.matchMaterial(a[2]);
        if (mat == null || !g.getBlocks().containsKey(mat)) {
            m().send(s, "block-missing", "material", a[2]);
            return;
        }
        if (g.getBlocks().size() <= 1) {
            m().send(s, "block-last");
            return;
        }
        g.removeBlock(mat);
        mgr().markDirty();
        m().send(s, "block-removed", "material", Util.niceName(mat), "name", g.getName());
    }

    // ================================================================= pomocnicze
    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        m().send(s, "players-only");
        return null;
    }

    private Generator inside(CommandSender s) {
        if (s instanceof Player p) return mgr().getAt(p.getLocation());
        return null;
    }

    /** Nazwa z argumentu a[idx] albo generator, w ktorym stoi gracz. */
    private Generator resolve(CommandSender s, String[] a, int idx) {
        if (a.length > idx) {
            Generator g = mgr().get(a[idx]);
            if (g == null) m().send(s, "not-found", "name", a[idx]);
            return g;
        }
        Generator g = inside(s);
        if (g == null) m().send(s, "not-in-generator");
        return g;
    }

    // ================================================================= tab complete
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (!s.hasPermission("generator.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            filter(out, SUBS, a[0]);
            return out;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            switch (sub) {
                case "delete", "remove", "info", "gui", "edit", "regen", "recount", "tp", "toggle",
                     "setinterval", "settype", "addblock", "removeblock" -> filter(out, names(), a[1]);
                default -> { }
            }
            return out;
        }
        if (a.length == 3) {
            switch (sub) {
                case "settype" -> filter(out, Arrays.stream(RegenType.values()).map(Enum::name).toList(), a[2]);
                case "addblock" -> {
                    String pre = a[2].toUpperCase(Locale.ROOT);
                    for (Material mat : Material.values()) {
                        if (out.size() >= 40) break;
                        if (mat.isBlock() && mat.isItem() && !mat.isAir() && mat.name().startsWith(pre)) out.add(mat.name());
                    }
                }
                case "removeblock" -> {
                    Generator g = mgr().get(a[1]);
                    if (g != null) {
                        filter(out, g.getBlocks().keySet().stream().map(Enum::name).toList(), a[2]);
                    }
                }
                default -> { }
            }
        }
        return out;
    }

    private List<String> names() {
        List<String> l = new ArrayList<>();
        for (Generator g : mgr().all()) l.add(g.getName());
        return l;
    }

    private void filter(List<String> out, List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
    }

    @SuppressWarnings("unused")
    private static ItemStack unused() { return null; }
}
