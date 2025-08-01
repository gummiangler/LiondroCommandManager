package io.github.gummiangler.commandmanager;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.destination.DestinationInstance;
import org.mvplugins.multiverse.core.teleportation.TeleportFailureReason;
import org.mvplugins.multiverse.core.utils.result.AsyncAttemptsAggregate;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.mvplugins.multiverse.core.utils.result.FailureReason;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.external.vavr.control.Option;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.logging.Logger;

public class TeleportMessageListener implements PluginMessageListener {
    private final Logger logger = CommandManager.getPluginLogger();

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals("proxycommandmanager:teleport")) return;

        try {
            DataInput input = new DataInputStream(new ByteArrayInputStream(message));
            String targetWorldName = input.readUTF();
            if (player.getWorld().getName().equals(targetWorldName)) return;

            Option<LoadedMultiverseWorld> maybeWorld = MultiverseCoreApi.get().getWorldManager().getLoadedWorld(targetWorldName);

            if (maybeWorld.isEmpty()) {
                player.sendMessage("Welt " + targetWorldName + " ist nicht geladen!");
                return;
            }

            LoadedMultiverseWorld world = maybeWorld.get();

            teleportPlayerToWorld(player, world);
            player.sendMessage(Component.text("Teleportiert nach '" + targetWorldName + "'."));
        } catch (IOException e) {
            logger.severe("Fehler bei Aktion: " + e.getMessage());
        }
    }

    public static AsyncAttemptsAggregate<Void, TeleportFailureReason> teleportPlayerToWorld(Player player, MultiverseWorld world) {
        Attempt<DestinationInstance<?, ?>, FailureReason> destinationAttempt =
                MultiverseCoreApi.get().getDestinationsProvider().parseDestination("w:" + world.getName());

        if (!destinationAttempt.isSuccess()) {
            return AsyncAttemptsAggregate.allOf(); // Kein Versuch, weil keine gültige Destination
        }

        DestinationInstance<?, ?> destination = destinationAttempt.get(); // sicher, weil vorher geprüft
        return MultiverseCoreApi.get().getSafetyTeleporter()
                .to(destination)
                .teleportSingle(player);
    }

}
