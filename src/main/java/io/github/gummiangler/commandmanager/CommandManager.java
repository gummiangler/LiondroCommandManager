package io.github.gummiangler.commandmanager;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;


public final class CommandManager extends JavaPlugin {
    private static CommandManager instance;

    @Override
    public void onEnable() {
        instance = this;

        getServer().getMessenger().registerIncomingPluginChannel(this, "proxycommandmanager:teleport", new TeleportMessageListener());

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(ChallengeCommand.createCommand().build());
        });


    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Logger getPluginLogger() {
        return instance.getLogger();
    }
}
