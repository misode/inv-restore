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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

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
                                        .executes((ctx) -> listPlayerSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "type")))
                                )))
                .then(literal("view")
                        .then(argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(InvRestore.getAllIds(), builder))
                                .executes((ctx) -> viewSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))));
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
                .append(" ---").withStyle(Styles.HEADER_DEFAULT)
        );

        snapshots.forEach(snapshot -> {
            Component time = Component.literal(snapshot.formatTimeAgo()).withStyle(Styles.LIST_DEFAULT
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(snapshot.formatFullTime())
                            .withStyle(Styles.LIST_HIGHLIGHT)))
            );

            Component player = Component.literal(snapshot.playerName()).withStyle(Styles.LIST_HIGHLIGHT);

            Component verb = snapshot.event().formatVerb().withStyle(Styles.LIST_DEFAULT);

            ItemStack hoverItem = Items.BUNDLE.getDefaultInstance();
            hoverItem.set(DataComponents.ITEM_NAME, Component.literal("Inventory Preview").withStyle(Styles.LIST_HIGHLIGHT));
            hoverItem.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("(click to view)")
                    .withStyle(Styles.LIST_DEFAULT.withItalic(false))
            )));
            hoverItem.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(snapshot.contents().allItems().toList()));
            String viewSnapshotCommand = "/invrestore view "+ snapshot.id();
            Component items = Component.literal("(" + snapshot.contents().stackCount() + " stacks)").withStyle(Styles.LIST_HIGHLIGHT
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(hoverItem)))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, viewSnapshotCommand)));

            BlockPos pos = BlockPos.containing(snapshot.position());
            String posFormat = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            String teleportCommand = "/execute in " + snapshot.dimension().location() + " run teleport @s " + snapshot.formatPos();
            Component position = Component.literal(posFormat).withStyle(Styles.LIST_DEFAULT
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.empty()
                            .append(Component.literal(snapshot.formatPos()).withStyle(Styles.LIST_HIGHLIGHT))
                            .append(Component.literal("\n" + snapshot.dimension().location()).withStyle(Styles.LIST_DEFAULT))
                            .append(Component.literal("\n(click to teleport)").withStyle(Styles.LIST_DEFAULT))))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand)));

            ctx.sendSystemMessage(Component.empty()
                    .append(snapshot.event().formatEmoji(false))
                    .append(" ").append(time)
                    .append(" ").append(player)
                    .append(" ").append(verb)
                    .append(" ").append(items)
                    .append(" ").append(position)
            );
        });
        return snapshots.size();
    }

    private static int viewSnapshot(CommandSourceStack ctx, String id) {
        Optional<Snapshot> snapshot = InvRestore
                .findSnapshots(s -> s.id().equals(id))
                .stream().findAny();
        if (snapshot.isEmpty()) {
            ctx.sendFailure(Component.literal("Cannot find snapshot ID " + id));
            return 0;
        }
        try {
            new SnapshotGui(ctx.getPlayer(), snapshot.get()).open();
            return 1;
        } catch (Exception e) {
            InvRestore.LOGGER.error("Failed to open GUI", e);
            ctx.sendFailure(Component.literal("Failed to open the snapshot GUI"));
            return 0;
        }
    }
}
