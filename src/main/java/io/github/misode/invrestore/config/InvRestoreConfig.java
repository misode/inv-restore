package io.github.misode.invrestore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.misode.invrestore.InvRestore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record InvRestoreConfig(int format, StoreLimits storeLimits) {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String FILE_NAME = "invrestore/config.json";
    public static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    public static final InvRestoreConfig DEFAULT = new InvRestoreConfig(1, StoreLimits.DEFAULT);

    public static final Codec<InvRestoreConfig> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.INT.fieldOf("format_version").forGetter(InvRestoreConfig::format),
            optionalConfig(StoreLimits.CODEC, "store_limits", DEFAULT.storeLimits).forGetter(InvRestoreConfig::storeLimits)
    ).apply(b, InvRestoreConfig::new));

    public static InvRestoreConfig load() {
        try (JsonReader reader = new JsonReader(new FileReader(FILE_PATH.toFile()))) {
            return InvRestoreConfig.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(reader, JsonElement.class))
                    .resultOrPartial(Util.prefix("Failed to load " + FILE_NAME + ":", InvRestore.LOGGER::error))
                    .orElse(DEFAULT);
        } catch (FileNotFoundException e) {
            InvRestore.LOGGER.info("Creating default config " + FILE_NAME);
            DEFAULT.save();
            return DEFAULT;
        } catch (IOException | JsonSyntaxException e) {
            InvRestore.LOGGER.error("Failed to read config " + FILE_NAME, e);
            return DEFAULT;
        }
    }

    public static Optional<InvRestoreConfig> reload() {
        try (JsonReader reader = new JsonReader(new FileReader(FILE_PATH.toFile()))) {
            return InvRestoreConfig.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(reader, JsonElement.class))
                    .resultOrPartial(Util.prefix("Failed to reload " + FILE_NAME + ":", InvRestore.LOGGER::error));
        } catch (IOException | JsonSyntaxException e) {
            InvRestore.LOGGER.error("Failed to reload " + FILE_NAME, e);
            return Optional.empty();
        }
    }

    public void save() {
        InvRestoreConfig.CODEC.encodeStart(JsonOps.INSTANCE, DEFAULT)
                .resultOrPartial(Util.prefix("Failed to save config: ", InvRestore.LOGGER::error))
                .ifPresent(element -> {
                    try {
                        Files.createDirectories(FILE_PATH.getParent());
                        Files.writeString(FILE_PATH, GSON.toJson(element));
                    } catch (IOException ex) {
                        InvRestore.LOGGER.error("Failed to write to " + FILE_NAME, ex);
                    }
                });
    }

    public record StoreLimits(int maxPerPlayer) {
        public static final StoreLimits DEFAULT = new StoreLimits(10);
        public static final Codec<StoreLimits> CODEC = RecordCodecBuilder.create(b -> b.group(
                optionalConfig(Codec.INT, "max_per_player", DEFAULT.maxPerPlayer).forGetter(StoreLimits::maxPerPlayer)
        ).apply(b, StoreLimits::new));
    }

    public static <T> MapCodec<T> optionalConfig(Codec<T> codec, String name, T defaultValue) {
        return new OptionalFieldCodec<>(name, codec, false)
                .xmap(a -> a.orElse(defaultValue), Optional::of);
    }
}
