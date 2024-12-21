package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.misode.invrestore.InvRestore;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public record InvRestoreDatabase(int format, List<Snapshot> snapshots, Map<UUID, PlayerPreferences> preferences) {
    public static final String FILE_NAME = "invrestore.dat";
    public static final int FORMAT_VERSION = 1;
    public static final Codec<InvRestoreDatabase> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.INT.fieldOf("format_version").forGetter(InvRestoreDatabase::format),
            Snapshot.CODEC.listOf().fieldOf("snapshots").orElse(List.of()).forGetter(InvRestoreDatabase::snapshots),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerPreferences.CODEC).fieldOf("player_preferences").orElse(Map.of()).forGetter(InvRestoreDatabase::preferences)
    ).apply(b, InvRestoreDatabase::new));

    public InvRestoreDatabase(int format, List<Snapshot> snapshots, Map<UUID, PlayerPreferences> preferences) {
        this.format = format;
        this.snapshots = new ArrayList<>(snapshots);
        this.preferences = new HashMap<>(preferences);
    }

    public InvRestoreDatabase() {
        this(FORMAT_VERSION, List.of(), Map.of());
    }

    public static InvRestoreDatabase load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(FILE_NAME);
        try {
            RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return InvRestoreDatabase.CODEC.decode(ops, tag)
                    .getOrThrow().getFirst();
        } catch (IOException e) {
            InvRestore.LOGGER.info("Creating new file " + FILE_NAME);
            InvRestoreDatabase newStore = new InvRestoreDatabase();
            newStore.save(server);
            return newStore;
        }
    }

    public void save(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(FILE_NAME);
        this.enforceLimits();
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

    public void enforceLimits() {
        int maxPerPlayer = InvRestore.config.storeLimits().maxPerPlayer();
        int maxTotal = InvRestore.config.storeLimits().maxTotal();
        List<Snapshot> newSnapshots = snapshots.stream()
                .collect(Collectors.groupingBy(Snapshot::playerUuid))
                .values().stream()
                .flatMap(playerSnapshots -> playerSnapshots.stream()
                        .sorted(Comparator.comparing(Snapshot::time))
                        .skip(Math.max(0, playerSnapshots.size() - maxPerPlayer)))
                .sorted(Comparator.comparing(Snapshot::time))
                .toList();
        if (newSnapshots.size() > maxTotal) {
            newSnapshots = newSnapshots.subList(newSnapshots.size() - maxTotal, newSnapshots.size());
        }
        this.snapshots.clear();
        this.snapshots.addAll(newSnapshots);
    }
}
