package io.github.misode.invrestore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.misode.invrestore.InvRestore;
import io.github.misode.invrestore.Styles;
import io.github.misode.invrestore.data.Snapshot;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class InvRestoreCommand {
    private static final DynamicCommandExceptionType ERROR_INVALID_TYPE = new DynamicCommandExceptionType(
            type -> Component.literal("Invalid snapshot type " + type)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralCommandNode<CommandSourceStack> ir = dispatcher.register(literal("invrestore")
                .requires(ctx -> Permissions.check(ctx, "invrestore", 2))
                .then(literal("list")
                        .then(argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(InvRestore.getPlayerNames(), builder))
                                .executes((ctx) -> listPlayerSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                                .then(argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Snapshot.EventType.REGISTRY.keySet().stream().map(ResourceLocation::getPath), builder))
                                        .executes((ctx) -> listPlayerSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "type")))))));
        dispatcher.register(literal("ir")
                .requires(ctx -> Permissions.check(ctx, "invrestore", 2))
                .redirect(ir));
    }

    private static int listPlayerSnapshot(CommandSourceStack ctx, String playerName) {
        List<Snapshot> snapshots = InvRestore
                .findSnapshots(s -> s.playerName().equals(playerName));
        return sendSnapshots(ctx, playerName, snapshots);
    }

    private static int listPlayerSnapshot(CommandSourceStack ctx, String playerName, String type) throws CommandSyntaxException {
        Snapshot.EventType<?> eventType = Snapshot.EventType.REGISTRY.getValue(InvRestore.id(type));
        if (eventType == null) {
            throw ERROR_INVALID_TYPE.create(type);
        }
        List<Snapshot> snapshots = InvRestore
                .findSnapshots(s -> s.playerName().equals(playerName) && s.event().getType().equals(eventType));
        return sendSnapshots(ctx, playerName, snapshots);
    }

    private static int sendSnapshots(CommandSourceStack ctx, String playerName, List<Snapshot> snapshots) {
        if (snapshots.isEmpty()) {
            ctx.sendSystemMessage(Component.literal("No matching snapshots found").withStyle(Styles.HEADER_DEFAULT));
            return 0;
        }
        ctx.sendSystemMessage(Component.empty()
                .append(Component.literal("--- Listing snapshots of ").withStyle(Styles.HEADER_DEFAULT))
                .append(Component.literal(playerName).withStyle(Styles.HEADER_HIGHLIGHT))
                .append(" ---").withStyle(Styles.HEADER_DEFAULT));
        snapshots.forEach(snapshot -> {
            ctx.sendSystemMessage(snapshot.format());
        });
        return snapshots.size();
    }
}
