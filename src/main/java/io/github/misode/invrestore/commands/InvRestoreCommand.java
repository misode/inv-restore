package io.github.misode.invrestore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.misode.invrestore.InvRestore;
import io.github.misode.invrestore.config.InvRestoreConfig;
import io.github.misode.invrestore.data.Snapshot;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class InvRestoreCommand {
    private static final DynamicCommandExceptionType ERROR_INVALID_EVENT_TYPE = new DynamicCommandExceptionType(
            type -> Component.literal("Invalid event type " + type)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_ZONE = new DynamicCommandExceptionType(
            zone -> Component.literal("Invalid zone " + zone)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralCommandNode<CommandSourceStack> ir = dispatcher.register(literal("invrestore")
                .requires(ctx -> Permissions.check(ctx, "invrestore", PermissionLevel.GAMEMASTERS))
                .then(literal("list")
                        .requires(ctx -> Permissions.check(ctx, "invrestore.list", PermissionLevel.GAMEMASTERS))
                        .then(argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(InvRestore.getPlayerNames(), builder))
                                .executes((ctx) -> listPlayerSnapshots(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                                .then(argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Snapshot.EventType.REGISTRY.keySet().stream().map(Identifier::getPath), builder))
                                        .executes((ctx) -> listPlayerSnapshots(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "type")))
                                )))
                .then(literal("timezone")
                        .then(argument("zone", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ZoneId.getAvailableZoneIds(), builder))
                                .executes((ctx) -> changePreferredZone(ctx.getSource(), StringArgumentType.getString(ctx, "zone")))))
                .then(literal("reload")
                        .requires(ctx -> Permissions.check(ctx, "invrestore.reload", PermissionLevel.ADMINS))
                        .executes((ctx) -> reloadConfig(ctx.getSource()))));
        dispatcher.register(literal("ir")
                .requires(ctx -> Permissions.check(ctx, "invrestore", PermissionLevel.GAMEMASTERS))
                .redirect(ir));
    }

    private static int listPlayerSnapshots(CommandSourceStack ctx, String playerName) {
        int result = InvRestore.sendSnapshotList(ctx.getPlayer(), playerName, Optional.empty(), 1);
        if (result == 0) {
            ctx.sendFailure(Component.literal("No matching snapshots found"));
            return 0;
        }
        return result;
    }

    private static int listPlayerSnapshots(CommandSourceStack ctx, String playerName, String type) throws CommandSyntaxException {
        Snapshot.EventType<?> eventType = Snapshot.EventType.REGISTRY.getValue(InvRestore.id(type));
        if (eventType == null) {
            throw ERROR_INVALID_EVENT_TYPE.create(type);
        }
        int result = InvRestore.sendSnapshotList(ctx.getPlayer(), playerName, Optional.of(eventType), 1);
        if (result == 0) {
            ctx.sendFailure(Component.literal("No matching snapshots found"));
            return 0;
        }
        return result;
    }

    private static int changePreferredZone(CommandSourceStack ctx, String zone) throws CommandSyntaxException {
        try {
            ZoneId zoneId = ZoneId.of(zone);
            InvRestore.updatePlayerPreferences(ctx.getPlayer(), (old) -> {
                return old.withTimezone(zoneId);
            });
            ctx.sendSuccess(() -> Component.literal("Updated your timezone preference to " + zoneId), false);
            return 1;
        } catch (DateTimeException e) {
            throw ERROR_INVALID_ZONE.create(zone);
        }
    }

    private static int reloadConfig(CommandSourceStack ctx) {
        Optional<InvRestoreConfig> config = InvRestoreConfig.load();
        if (config.isEmpty()) {
            ctx.sendFailure(Component.literal("Failed to reload the config! Check server console for more info."));
            return 0;
        }
        InvRestore.config = config.get();
        ctx.sendSuccess(() -> Component.literal("Reloaded config!"), false);
        return 1;
    }
}
