package io.github.misode.invrestore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.misode.invrestore.InvRestore;
import io.github.misode.invrestore.Styles;
import io.github.misode.invrestore.config.InvRestoreConfig;
import io.github.misode.invrestore.data.Snapshot;
import io.github.misode.invrestore.gui.SnapshotGui;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
                .requires(ctx -> Permissions.check(ctx, "invrestore", 2))
                .then(literal("list")
                        .requires(ctx -> Permissions.check(ctx, "invrestore.list", 2))
                        .then(argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(InvRestore.getPlayerNames(), builder))
                                .executes((ctx) -> listPlayerSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                                .then(argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Snapshot.EventType.REGISTRY.keySet().stream().map(ResourceLocation::getPath), builder))
                                        .executes((ctx) -> listPlayerSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "type")))
                                )))
                .then(literal("view")
                        .requires(ctx -> Permissions.check(ctx, "invrestore.view", 2))
                        .then(argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(InvRestore.getAllIds(), builder))
                                .executes((ctx) -> viewSnapshot(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(literal("timezone")
                        .then(argument("zone", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ZoneId.getAvailableZoneIds(), builder))
                                .executes((ctx) -> changePreferredZone(ctx.getSource(), StringArgumentType.getString(ctx, "zone")))))
                .then(literal("reload")
                        .requires(ctx -> Permissions.check(ctx, "invrestore.reload", 3))
                        .executes((ctx) -> reloadConfig(ctx.getSource()))));
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
            throw ERROR_INVALID_EVENT_TYPE.create(type);
        }
        List<Snapshot> snapshots = InvRestore
                .findSnapshots(s -> s.playerName().equals(playerName) && s.event().getType().equals(eventType));
        return sendSnapshots(ctx, playerName, snapshots);
    }

    private static int sendSnapshots(CommandSourceStack ctx, String playerName, List<Snapshot> snapshots) {
        if (snapshots.isEmpty()) {
            ctx.sendFailure(Component.literal("No matching snapshots found"));
            return 0;
        }

        ctx.sendSuccess(() -> Component.empty()
                .append(Component.literal("--- Listing snapshots of ").withStyle(Styles.HEADER_DEFAULT))
                .append(Component.literal(playerName).withStyle(Styles.HEADER_HIGHLIGHT))
                .append(" ---").withStyle(Styles.HEADER_DEFAULT),
                false
        );

        InvRestoreConfig.QueryResults config = InvRestore.config.queryResults();

        snapshots.stream().limit(config.maxResults()).forEach(snapshot -> {
            ZoneId zone = InvRestore.getPlayerPreferences(ctx.getPlayer()).timezone().orElse(config.defaultZone());
            DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern(config.fullTimeFormat()).withZone(zone).withLocale(Locale.ROOT);
            String changeTimezoneCommand = "/invrestore timezone ";
            Component time = Component.literal(snapshot.formatTimeAgo()).withStyle(Styles.LIST_DEFAULT
                    .withHoverEvent(new HoverEvent.ShowText(Component.empty()
                            .append(Component.literal(timeFormat.format(snapshot.time())).withStyle(Styles.LIST_HIGHLIGHT))
                            .append(Component.literal("\n(click to change timezone)").withStyle(Styles.LIST_DEFAULT))))
                    .withClickEvent(new ClickEvent.SuggestCommand(changeTimezoneCommand))
            );

            String tellPlayerCommand = "/tell " + snapshot.playerName() + " ";
            Component player = Component.literal(snapshot.playerName()).withStyle(Styles.LIST_HIGHLIGHT
                    .withClickEvent(new ClickEvent.SuggestCommand(tellPlayerCommand))
            );

            Component verb = snapshot.event().formatVerb().withStyle(Styles.LIST_DEFAULT);

            ItemStack hoverItem = Items.BUNDLE.getDefaultInstance();
            hoverItem.set(DataComponents.ITEM_NAME, Component.literal("Inventory Preview").withStyle(Styles.LIST_HIGHLIGHT));
            hoverItem.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("(click to view)")
                    .withStyle(Styles.LIST_DEFAULT.withItalic(false))
            )));
            hoverItem.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(snapshot.contents().allItems().filter(item -> !item.isEmpty()).toList()));
            String viewSnapshotCommand = "/invrestore view "+ snapshot.id();
            Component items = Component.literal("(" + snapshot.contents().stackCount() + " stacks)").withStyle(Styles.LIST_HIGHLIGHT
                    .withHoverEvent(new HoverEvent.ShowItem(hoverItem))
                    .withClickEvent(new ClickEvent.RunCommand(viewSnapshotCommand)));

            BlockPos pos = BlockPos.containing(snapshot.position());
            String posFormat = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            String teleportCommand = "/execute in " + snapshot.dimension().location() + " run teleport @s " + snapshot.formatPos();
            Component position = Component.literal(posFormat).withStyle(Styles.LIST_DEFAULT
                    .withHoverEvent(new HoverEvent.ShowText(Component.empty()
                            .append(Component.literal(snapshot.formatPos()).withStyle(Styles.LIST_HIGHLIGHT))
                            .append(Component.literal("\n" + snapshot.dimension().location()).withStyle(Styles.LIST_DEFAULT))
                            .append(Component.literal("\n(click to teleport)").withStyle(Styles.LIST_DEFAULT))))
                    .withClickEvent(new ClickEvent.RunCommand(teleportCommand)));

            ctx.sendSuccess(() -> Component.empty()
                    .append(snapshot.event().formatEmoji(false))
                    .append(" ").append(time)
                    .append(" ").append(player)
                    .append(" ").append(verb)
                    .append(" ").append(items)
                    .append(" ").append(position),
                    false
            );
        });
        if (snapshots.size() > 5) {
            ctx.sendSuccess(() -> (Component.literal("and " + (snapshots.size() - 5) + " more...")
                    .withStyle(Styles.LIST_DEFAULT)),
                    false);
        }
        return snapshots.size();
    }

    private static int viewSnapshot(CommandSourceStack ctx, String id) {
        Optional<Snapshot> snapshot = InvRestore
                .findSnapshots(s -> s.id().equals(id))
                .stream().findAny();
        if (snapshot.isEmpty()) {
            ctx.sendFailure(Component.literal("Cannot find the snapshot \"" + id + "\""));
            return 0;
        }
        ServerPlayer player = ctx.getPlayer();
        if (player == null) {
            ctx.sendFailure(Component.literal("Only players can view a snapshot"));
            return 0;
        }
        try {
            SnapshotGui gui = new SnapshotGui(player, snapshot.get());
            gui.open();
            return 1;
        } catch (Exception e) {
            InvRestore.LOGGER.error("Failed to open GUI", e);
            ctx.sendFailure(Component.literal("Failed to open the snapshot GUI"));
            return 0;
        }
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
