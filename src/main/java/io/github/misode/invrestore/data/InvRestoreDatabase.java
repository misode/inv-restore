package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.playerdata.api.PlayerDataApi;
import io.github.misode.invrestore.InvRestore;
import net.minecraft.util.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public record InvRestoreDatabase(int format, List<Snapshot> snapshots, Map<UUID, PlayerPreferences> preferences, Map<String, UUID> savedPlayers) {
    public static final String FILE_NAME = "invrestore.dat";
    public static final int FORMAT_VERSION = 2;
    public static final Codec<InvRestoreDatabase> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.INT.fieldOf("format_version").forGetter(InvRestoreDatabase::format),
            Snapshot.CODEC.listOf().fieldOf("snapshots").orElse(List.of()).forGetter(InvRestoreDatabase::snapshots),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerPreferences.CODEC).fieldOf("player_preferences").orElse(Map.of()).forGetter(InvRestoreDatabase::preferences),
            Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC).fieldOf("saved_players").orElse(Map.of()).forGetter(InvRestoreDatabase::savedPlayers)
    ).apply(b, InvRestoreDatabase::new));

    public InvRestoreDatabase(int format, List<Snapshot> snapshots, Map<UUID, PlayerPreferences> preferences, Map<String, UUID> savedPlayers) {
        this.format = format;
        this.snapshots = new ArrayList<>(snapshots);
        this.preferences = new HashMap<>(preferences);
        this.savedPlayers = new HashMap<>(savedPlayers);
    }

    public InvRestoreDatabase() {
        this(FORMAT_VERSION, List.of(), Map.of(), Map.of());
    }

    public static InvRestoreDatabase load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(FILE_NAME);
        InvRestoreDatabase database;
        try {
            RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            database = InvRestoreDatabase.CODEC.decode(ops, tag).getOrThrow().getFirst();
        } catch (IOException e) {
            InvRestore.LOGGER.info("Creating new file " + FILE_NAME);
            InvRestoreDatabase newDatabase = new InvRestoreDatabase();
            newDatabase.save(server);
            return newDatabase;
        }
        if (database.format < FORMAT_VERSION) {
            return migrate(database, server);
        }
        return database;
    }

    public void save(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(FILE_NAME);
        RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        InvRestoreDatabase.CODEC.encodeStart(ops, this)
                .ifSuccess(tag -> {
                    try {
                        NbtIo.writeCompressed((CompoundTag) tag, path);
                    } catch (IOException e) {
                        InvRestore.LOGGER.error("Failed to save " + FILE_NAME, e);
                    }
                })
                .resultOrPartial(Util.prefix("Failed to save " + FILE_NAME + ": ", InvRestore.LOGGER::error));
    }

    private static InvRestoreDatabase migrate(InvRestoreDatabase database, MinecraftServer server) {
        InvRestore.LOGGER.info("Migrating database with {} snapshots...", database.snapshots.size());
        if (database.format < 2) {
            Map<String, UUID> savedPlayers = new HashMap<>(database.savedPlayers);
            for (Snapshot snapshot : database.snapshots()) {
                PlayerSnapshotStorage storage = PlayerDataApi.getCustomDataFor(server, snapshot.playerUuid(), InvRestore.PLAYER_DATA_STORAGE);
                if (storage == null) {
                    storage = new PlayerSnapshotStorage();
                }
                storage.snapshots().add(snapshot);
                PlayerDataApi.setCustomDataFor(server, snapshot.playerUuid(), InvRestore.PLAYER_DATA_STORAGE, storage);
                savedPlayers.put(snapshot.playerName(), snapshot.playerUuid());
            }
            database = new InvRestoreDatabase(2, List.of(), database.preferences, savedPlayers);
        }
        InvRestore.LOGGER.info("Migration done!");
        database.save(server);
        return database;
    }
}
