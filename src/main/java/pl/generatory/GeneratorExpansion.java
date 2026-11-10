package pl.generatory;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Placeholdery:
 *  %generator_count%
 *  %generator_<nazwa>_<klucz>%  oraz  %generator_nearest_<klucz>%
 * Klucze: time, time_formatted, status, progress, percent, blocks_left, blocks_total,
 *         blocks_mined, regen_type, interval, trigger, enabled, world, size
 */
public final class GeneratorExpansion extends PlaceholderExpansion {

    private final GeneratorsPlugin plugin;

    public GeneratorExpansion(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "generator"; }
    @Override public String getAuthor() { return "Generatory"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        GeneratorManager mgr = plugin.manager();

        if (p.equals("count")) return String.valueOf(mgr.all().size());

        Generator g;
        String key;
        if (p.startsWith("nearest_")) {
            Player online = offline == null ? null : offline.getPlayer();
            if (online == null) return plugin.msg().raw("placeholder-none");
            int radius = plugin.getConfig().getInt("settings.nearest-radius", 16);
            g = mgr.getNearest(online.getLocation(), radius);
            key = p.substring("nearest_".length());
            if (g == null) return plugin.msg().raw("placeholder-none");
        } else {
            int i = p.indexOf('_');
            if (i < 0) return null;
            g = mgr.get(p.substring(0, i));
            key = p.substring(i + 1);
            if (g == null) return plugin.msg().raw("placeholder-unknown");
        }
        return value(g, key);
    }

    private int timerSeconds(Generator g) {
        if (!g.isEnabled()) return 0;
        if (g.getState() == GeneratorState.WAITING
                || (g.getState() == GeneratorState.ACTIVE && g.getTrigger() == TriggerMode.PERIODIC)) {
            return g.getTimeLeft();
        }
        return 0;
    }

    private String value(Generator g, String key) {
        switch (key) {
            case "time":
                return String.valueOf(timerSeconds(g));
            case "time_formatted": {
                if (g.getState() == GeneratorState.REGENERATING) return plugin.msg().raw("placeholder-now");
                int t = timerSeconds(g);
                return t > 0 ? Util.formatTime(t) : plugin.msg().raw("placeholder-none");
            }
            case "status":
                return plugin.manager().statusText(g);
            case "progress":
                return Util.progressBar(g.fillFraction(), 10);
            case "percent":
                return String.valueOf(g.fillPercent());
            case "blocks_left":
                return String.valueOf(g.getRemaining());
            case "blocks_total":
                return String.valueOf(g.volume());
            case "blocks_mined":
                return String.valueOf(Math.max(0, g.volume() - g.getRemaining()));
            case "regen_type":
                return g.getRegenType().getDisplay();
            case "interval":
                return String.valueOf(g.getIntervalSeconds());
            case "trigger":
                return g.getTrigger().getDisplay();
            case "enabled":
                return String.valueOf(g.isEnabled());
            case "world":
                return g.getWorldName();
            case "size":
                return g.sizeText();
            case "name":
                return g.getName();
            default:
                return null;
        }
    }
}
