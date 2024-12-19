package io.github.misode.invrestore;

import io.github.misode.invrestore.commands.InvRestoreCommand;
import io.github.misode.invrestore.config.InvRestoreConfig;
import io.github.misode.invrestore.data.InvRestoreDatabase;
import io.github.misode.invrestore.data.PlayerPreferences;
import io.github.misode.invrestore.data.Snapshot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class InvRestore implements ModInitializer {
    public static final String MOD_ID = "invrestore";
    public static final Logger LOGGER = LoggerFactory.getLogger(InvRestore.class);

    private static InvRestoreDatabase database;
    public static InvRestoreConfig config = InvRestoreConfig.DEFAULT;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            InvRestoreCommand.register(dispatcher, buildContext);
        });

        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            try {
                InvRestore.config = InvRestoreConfig.load();
                InvRestore.database = InvRestoreDatabase.load(server);
            } catch (Exception e) {
                InvRestore.LOGGER.error("Something went wrong during startup:", e);
            }
        });
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            try {
                if (database == null) {
                    throw new IllegalStateException("The database isn't loaded.");
                }
                database.save(server);
            } catch (Exception e) {
                LOGGER.error("Failed to save database", e);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            addSnapshot(Snapshot.fromJoin(listener.player));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            addSnapshot(Snapshot.fromDisconnect(listener.player));
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            addSnapshot(Snapshot.fromLevelChange(player, origin.dimension()));
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static PlayerPreferences getPlayerPreferences(ServerPlayer player) {
        if (database == null) {
            return PlayerPreferences.DEFAULT;
        }
        return database.preferences().getOrDefault(player.getUUID(), PlayerPreferences.DEFAULT);
    }

    public static void updatePlayerPreferences(ServerPlayer player, Function<PlayerPreferences, PlayerPreferences> update) {
        if (database == null) {
            throw new IllegalStateException("The database isn't loaded");
        }
        PlayerPreferences oldPreferences = getPlayerPreferences(player);
        database.preferences().put(player.getUUID(), update.apply(oldPreferences));
    }

    private static Stream<Snapshot> getSnapshots() {
        if (database == null) {
            return Stream.of();
        }
        return database.snapshots().stream();
    }

    public static void addSnapshot(Snapshot snapshot) {
        try {
            if (database == null) {
                throw new IllegalStateException("The database isn't loaded");
            }
            database.snapshots().add(snapshot);
        } catch (Exception e) {
            LOGGER.error("Couldn't save snapshot {} for player {}", snapshot.event(), snapshot.playerName(), e);
        }
    }

    public static List<String> getPlayerNames() {
        return getSnapshots()
                .sorted()
                .map(Snapshot::playerName)
                .distinct()
                .toList();
    }

    public static List<Snapshot> findSnapshots(Predicate<Snapshot> predicate) {
        return getSnapshots()
                .filter(predicate)
                .sorted()
                .toList();
    }

    public static List<String> getAllIds() {
        return getSnapshots()
                .map(Snapshot::id)
                .toList();
    }
}
