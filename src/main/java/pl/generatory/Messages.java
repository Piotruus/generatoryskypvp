package pl.generatory;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Messages {

    private final GeneratorsPlugin plugin;
    private YamlConfiguration cfg;

    public Messages(GeneratorsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    /** Wiadomosc z kolorami, bez prefiksu. Parametry: klucz1, wartosc1, klucz2, wartosc2... */
    public String raw(String key, Object... kv) {
        String s = cfg.getString(key);
        if (s == null) s = "&c[brak wiadomosci: " + key + "]";
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return Util.color(s);
    }

    public List<String> list(String key) {
        List<String> out = new ArrayList<>();
        for (String s : cfg.getStringList(key)) out.add(Util.color(s));
        return out;
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(raw("prefix") + raw(key, kv));
    }

    public void sendPlain(CommandSender to, String key, Object... kv) {
        to.sendMessage(raw(key, kv));
    }
}
