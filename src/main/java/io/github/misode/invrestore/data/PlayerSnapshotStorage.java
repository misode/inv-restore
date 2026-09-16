package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record PlayerSnapshotStorage(int format, List<Snapshot> snapshots) {
    public static final Codec<PlayerSnapshotStorage> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.INT.fieldOf("format_version").forGetter(PlayerSnapshotStorage::format),
            Snapshot.CODEC.listOf().fieldOf("snapshots").orElse(List.of()).forGetter(PlayerSnapshotStorage::snapshots)
    ).apply(b, PlayerSnapshotStorage::new));

    public PlayerSnapshotStorage(int format, List<Snapshot> snapshots) {
        this.format = format;
        this.snapshots = new ArrayList<>(snapshots);
    }

    public PlayerSnapshotStorage() {
        this(InvRestoreDatabase.FORMAT_VERSION, List.of());
    }
}
