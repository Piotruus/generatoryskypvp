package pl.generatory;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GeneratorsPlugin extends JavaPlugin {

    private Messages messages;
    private GeneratorManager manager;
    private SelectionManager selection;
    private GuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        manager = new GeneratorManager(this);
        manager.load();
        selection = new SelectionManager(this);
        gui = new GuiManager(this);

        getServer().getPluginManager().registerEvents(selection, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new GeneratorListener(this), this);

        GeneratorCommand cmd = new GeneratorCommand(this);
        PluginCommand pc = getCommand("generator");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        manager.startTasks();
        selection.startTask();
        gui.startTask();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GeneratorExpansion(this).register();
            getLogger().info("Zarejestrowano placeholdery PlaceholderAPI (%generator_...%).");
        }
        getLogger().info("Generatory wlaczone.");
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.closeAll();
        if (manager != null) manager.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
    }

    public Messages msg() { return messages; }
    public GeneratorManager manager() { return manager; }
    public SelectionManager selection() { return selection; }
    public GuiManager gui() { return gui; }
}
