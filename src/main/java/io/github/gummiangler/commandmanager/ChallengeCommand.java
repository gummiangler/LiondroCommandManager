package io.github.gummiangler.commandmanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.gummiangler.lootrandomizer.ConfigManager;
import io.github.gummiangler.lootrandomizer.LootRandomizer;
import io.github.gummiangler.lootrandomizer.MappingManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.NotNull;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.destination.DestinationInstance;
import org.mvplugins.multiverse.core.teleportation.TeleportFailureReason;
import org.mvplugins.multiverse.core.utils.result.AsyncAttemptsAggregate;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.mvplugins.multiverse.core.utils.result.FailureReason;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.options.RegenWorldOptions;
import org.mvplugins.multiverse.external.vavr.control.Option;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;
import org.mvplugins.multiverse.inventories.profile.container.ProfileContainer;
import org.mvplugins.multiverse.inventories.profile.key.ContainerType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChallengeCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("challenge")
                .then(Commands.literal("reset")
                        .executes(ChallengeCommand::runResetLogic)
                );
    }

    private static int runResetLogic(CommandContext<CommandSourceStack> ctx) {
        Entity executor = ctx.getSource().getExecutor();
        CommandSender sender = ctx.getSource().getSender();

        if (!(executor instanceof Player player)) {
            sender.sendPlainMessage("Reset Command nur von Spielern ausführbar!");
            return Command.SINGLE_SUCCESS;
        }

        LootRandomizer lootRandomizer = JavaPlugin.getPlugin(LootRandomizer.class);
        MappingManager lootMappingManager = lootRandomizer.getMappingManager();
        ConfigManager lootConfigManager = lootRandomizer.getConfigManager();

        //Reset für Plugin LootRandomizer
        if (!lootConfigManager.isWorldDisabled(player.getWorld().getName())) {
            lootMappingManager.regenerateMapping();
            sender.sendPlainMessage("Challenge LootRandomizer wurde erfolgreich zurückgesetzt.");
        }

        List<String> worldsToRegen = getWorldsToRegen(player);
        List<Player> playersToTeleport = getPlayersInWorlds(worldsToRegen);

       regenWorldsSequentially(worldsToRegen, 0, sender, playersToTeleport);


        return Command.SINGLE_SUCCESS;

    }

    private static @NotNull List<String> getWorldsToRegen(Player player) {
        String baseWorldName = player.getWorld().getName();
        if (baseWorldName.endsWith("_nether")) {
            baseWorldName = baseWorldName.substring(0, baseWorldName.length() - "_nether".length());

        } else if (baseWorldName.endsWith("_the_end")) {
            baseWorldName = baseWorldName.substring(0, baseWorldName.length() - "_the_end".length());
        }

        List<String> worldsToRegen = new ArrayList<>();
        worldsToRegen.add(baseWorldName);
        worldsToRegen.add(baseWorldName + "_nether");
        worldsToRegen.add(baseWorldName + "_the_end");
        return worldsToRegen;
    }

    public static AsyncAttemptsAggregate<Void, TeleportFailureReason> transferFromWorldTo(LoadedMultiverseWorld from, World to) {
        return transferAllFromWorldToLocation(from, to.getSpawnLocation());
    }

    public static AsyncAttemptsAggregate<Void, TeleportFailureReason> transferAllFromWorldToLocation(LoadedMultiverseWorld world, Location location) {
        return world.getPlayers()
                .map(players -> MultiverseCoreApi.get().getSafetyTeleporter().to(location).teleport(players))
                .getOrElse(AsyncAttemptsAggregate::emptySuccess);
    }

    public static AsyncAttemptsAggregate<Void, TeleportFailureReason> teleportPlayersToWorld(List<Player> players, MultiverseWorld world) {
        Location spawnLocation = world.getSpawnLocation();
        return MultiverseCoreApi.get().getSafetyTeleporter().to(spawnLocation).teleport(players);
    }


    public static AsyncAttemptsAggregate<Void, TeleportFailureReason> removePlayers(List<Player> players) {
        if (players.isEmpty()) {
            return AsyncAttemptsAggregate.emptySuccess();
        }
        World toWorld = MultiverseCoreApi.get().getWorldManager().getDefaultWorld()
                .flatMap(LoadedMultiverseWorld::getBukkitWorld)
                .getOrElse(Bukkit.getWorlds().getFirst());
        Location targetLocation = toWorld.getSpawnLocation();
        return MultiverseCoreApi.get().getSafetyTeleporter().to(targetLocation).teleport(players);
    }

    public static List<Player> getPlayersInWorlds(List<String> worldsToRegen) {
        List<Player> allPlayers = new ArrayList<>();
        for (String worldName : worldsToRegen) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                allPlayers.addAll(world.getPlayers());
            }
        }
        return allPlayers;
    }

    public static void regenWorldsSequentially(List<String> worldsToRegen, int index, CommandSender sender, List<Player> playersToTeleport) {

        if (index >= worldsToRegen.size()) {
            sender.sendMessage("Alle Welten erfolgreich regeneriert!");
            return;
        }

        String worldName = worldsToRegen.get(index);
        Option<LoadedMultiverseWorld> maybeWorld = MultiverseCoreApi.get().getWorldManager().getLoadedWorld(worldName);

        if (maybeWorld.isEmpty()) {
            sender.sendMessage("Welt " + worldName + " ist nicht geladen!");
            return;
        }

        LoadedMultiverseWorld world = maybeWorld.get();

        RegenWorldOptions options = RegenWorldOptions.world(world)
                .randomSeed(true)
                .keepWorldConfig(true)
                .keepGameRule(true);


        removePlayers(playersToTeleport)
                .onFailure(failure -> sender.sendMessage("Teleportieren der Spieler aus Welt " + worldName + " fehlgeschlagen: " + failure))
                .onSuccess(v -> {

                    Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(CommandManager.class), () -> {

                        MultiverseCoreApi.get()
                            .getWorldManager()
                            .regenWorld(options)
                            .onSuccess(newWorld -> {
                                sender.sendMessage("Welt erfolgreich regeneriert: " + newWorld.getName());
                                deletePlayersInventoryGroup(playersToTeleport, worldsToRegen.getFirst()).thenRun(() -> {

                                    teleportPlayersToWorld(playersToTeleport, newWorld)
                                            .onSuccess(w -> {
                                                playersToTeleport.clear();
                                                regenWorldsSequentially(worldsToRegen, index + 1, sender, playersToTeleport);
                                                deletePlayersInventoryGroup(playersToTeleport, worldsToRegen.getFirst());
                                            })
                                            .onFailure(failure -> sender.sendMessage("Fehler beim Teleportieren der Spieler: " + failure));
                                });
                            })
                            .onFailure(failure -> sender.sendMessage("Fehler beim Regenerieren der Welt " + worldName + ": " + failure.getFailureMessage()));
                    }, 20L);
                });
    }


    public static CompletableFuture<Void> deletePlayersInventoryGroup(List<Player> players, String groupName) {
        ProfileContainer container = MultiverseInventoriesApi.get()
                .getProfileContainerStoreProvider()
                .getStore(ContainerType.GROUP)
                .getContainer(groupName);

        List<CompletableFuture<Void>> futures = players.stream()
                .map(container::deletePlayerFile)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}

