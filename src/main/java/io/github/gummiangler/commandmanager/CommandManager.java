package io.github.gummiangler.commandmanager;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.gummiangler.lootrandomizer.LootRandomizer;
import io.github.gummiangler.lootrandomizer.MappingManager;


public final class CommandManager extends JavaPlugin {

    @Override
    public void onEnable() {

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(ChallengeCommand.createCommand().build());
        });

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
