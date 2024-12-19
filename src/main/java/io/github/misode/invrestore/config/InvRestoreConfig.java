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
import java.time.ZoneId;
import java.util.Optional;

public record InvRestoreConfig(QueryFormat queryFormat, StoreLimits storeLimits) {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String FILE_NAME = "invrestore/config.json";
    public static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    public static final InvRestoreConfig DEFAULT = new InvRestoreConfig(QueryFormat.DEFAULT, StoreLimits.DEFAULT);

    public static final Codec<InvRestoreConfig> CODEC = RecordCodecBuilder.create(b -> b.group(
            optionalField(QueryFormat.CODEC, "query_format", DEFAULT.queryFormat).forGetter(InvRestoreConfig::queryFormat),
            optionalField(StoreLimits.CODEC, "store_limits", DEFAULT.storeLimits).forGetter(InvRestoreConfig::storeLimits)
    ).apply(b, InvRestoreConfig::new));

    public static Optional<InvRestoreConfig> load() {
        try (JsonReader reader = new JsonReader(new FileReader(FILE_PATH.toFile()))) {
            return InvRestoreConfig.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(reader, JsonElement.class))
                    .resultOrPartial(Util.prefix("Failed to load " + FILE_NAME + ":", InvRestore.LOGGER::error));
        } catch (FileNotFoundException e) {
            InvRestore.LOGGER.info("Creating default config " + FILE_NAME);
            DEFAULT.save();
            return Optional.of(DEFAULT);
        } catch (IOException | JsonSyntaxException e) {
            InvRestore.LOGGER.error("Failed to read config " + FILE_NAME, e);
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

    public record QueryFormat(int maxResults, ZoneId defaultZone) {
        public static final QueryFormat DEFAULT = new QueryFormat(5, ZoneId.of("UTC"));
        public static final Codec<QueryFormat> CODEC = RecordCodecBuilder.create(b -> b.group(
                optionalField(Codec.intRange(1, 10), "max_results", DEFAULT.maxResults).forGetter(QueryFormat::maxResults),
                optionalField(Codec.STRING.xmap(ZoneId::of, ZoneId::toString), "default_timezone", DEFAULT.defaultZone).forGetter(QueryFormat::defaultZone)
        ).apply(b, QueryFormat::new));
    }

    public record StoreLimits(int maxPerPlayer, int maxTotal) {
        public static final StoreLimits DEFAULT = new StoreLimits(50, 10_000);
        public static final Codec<StoreLimits> CODEC = RecordCodecBuilder.create(b -> b.group(
                optionalField(Codec.intRange(1, Integer.MAX_VALUE), "max_per_player", DEFAULT.maxPerPlayer).forGetter(StoreLimits::maxPerPlayer),
                optionalField(Codec.intRange(1, Integer.MAX_VALUE), "max_total", DEFAULT.maxTotal).forGetter(StoreLimits::maxTotal)
        ).apply(b, StoreLimits::new));
    }

    private static <T> MapCodec<T> optionalField(Codec<T> codec, String name, T defaultValue) {
        return new OptionalFieldCodec<>(name, codec, false)
                .xmap(a -> a.orElse(defaultValue), Optional::of);
    }
}
