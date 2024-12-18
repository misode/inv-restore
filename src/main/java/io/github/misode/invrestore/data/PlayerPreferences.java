package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.ZoneId;
import java.util.Optional;

public record PlayerPreferences(Optional<ZoneId> timezone) {
    public static final PlayerPreferences DEFAULT = new PlayerPreferences(Optional.empty());
    public static final Codec<PlayerPreferences> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.STRING.xmap(ZoneId::of, ZoneId::toString).optionalFieldOf("timezone").forGetter(PlayerPreferences::timezone)
    ).apply(b, PlayerPreferences::new));

    public PlayerPreferences withTimezone(ZoneId timezone) {
        return new PlayerPreferences(Optional.of(timezone));
    }
}
