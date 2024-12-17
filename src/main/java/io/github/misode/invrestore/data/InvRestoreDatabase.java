package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.misode.invrestore.InvRestore;
import net.minecraft.Util;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public record InvRestoreDatabase(int format, List<Snapshot> snapshots) {
    public static final String FILE_NAME = "invrestore.dat";
    public static final int FORMAT_VERSION = 1;
    public static final Codec<InvRestoreDatabase> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.INT.fieldOf("format_version").forGetter(InvRestoreDatabase::format),
            Snapshot.CODEC.listOf().fieldOf("snapshots").forGetter(InvRestoreDatabase::snapshots)
    ).apply(b, InvRestoreDatabase::new));

    public InvRestoreDatabase(int format, List<Snapshot> snapshots) {
        this.format = format;
        this.snapshots = new ArrayList<>(snapshots);
    }

    public static InvRestoreDatabase load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(FILE_NAME);
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return InvRestoreDatabase.CODEC.decode(NbtOps.INSTANCE, tag)
                    .getOrThrow().getFirst();
        } catch (IOException e) {
            InvRestore.LOGGER.info("Creating new file " + FILE_NAME);
            InvRestoreDatabase newStore = new InvRestoreDatabase(FORMAT_VERSION, new ArrayList<>());
            newStore.save(server);
            return newStore;
        }
    }

    public void save(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(FILE_NAME);
        InvRestoreDatabase.CODEC.encodeStart(NbtOps.INSTANCE, this)
                .ifSuccess(tag -> {
                    try {
                        NbtIo.writeCompressed((CompoundTag) tag, path);
                    } catch (IOException e) {
                        InvRestore.LOGGER.error("Failed to save " + FILE_NAME, e);
                    }
                })
                .resultOrPartial(Util.prefix("Failed to save " + FILE_NAME + ": ", InvRestore.LOGGER::error));
    }
}
