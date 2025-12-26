package io.github.misode.invrestore;

import io.github.misode.invrestore.commands.InvRestoreCommand;
import io.github.misode.invrestore.config.InvRestoreConfig;
import io.github.misode.invrestore.data.InvRestoreDatabase;
import io.github.misode.invrestore.data.PlayerPreferences;
import io.github.misode.invrestore.data.Snapshot;
import io.github.misode.invrestore.gui.SnapshotGui;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class InvRestore implements ModInitializer {
    public static final String MOD_ID = "invrestore";
    public static final Logger LOGGER = LoggerFactory.getLogger(InvRestore.class);

    public static final Identifier VIEW_ACTION = InvRestore.id("view_snapshot");
    public static final Identifier TELEPORT_ACTION = InvRestore.id("teleport_to_snapshot");
    public static final Identifier CHANGE_PAGE_ACTION = InvRestore.id("change_page");

    private static InvRestoreDatabase database;
    public static InvRestoreConfig config = InvRestoreConfig.DEFAULT;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            InvRestoreCommand.register(dispatcher, buildContext);
        });

        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            try {
                InvRestore.config = InvRestoreConfig.load().orElse(InvRestoreConfig.DEFAULT);
                InvRestore.database = InvRestoreDatabase.load(server);
            } catch (Exception e) {
                InvRestore.LOGGER.error("Something went wrong during startup:", e);
            }
        });
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            try {
                if (database == null) {
                    throw new IllegalStateException("The database isn't loaded.");
                }
                database.save(server);
            } catch (Exception e) {
                LOGGER.error("Failed to save database", e);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            addSnapshot(Snapshot.fromJoin(listener.player));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            addSnapshot(Snapshot.fromDisconnect(listener.player));
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            addSnapshot(Snapshot.fromLevelChange(player, origin.dimension()));
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static PlayerPreferences getPlayerPreferences(ServerPlayer player) {
        if (database == null) {
            return PlayerPreferences.DEFAULT;
        }
        return database.preferences().getOrDefault(player.getUUID(), PlayerPreferences.DEFAULT);
    }

    public static void updatePlayerPreferences(ServerPlayer player, Function<PlayerPreferences, PlayerPreferences> update) {
        if (database == null) {
            throw new IllegalStateException("The database isn't loaded");
        }
        PlayerPreferences oldPreferences = getPlayerPreferences(player);
        database.preferences().put(player.getUUID(), update.apply(oldPreferences));
    }

    public static int sendSnapshotList(ServerPlayer receiver, String playerName, Optional<Snapshot.EventType<?>> eventType, int page) {
        InvRestoreConfig.QueryResults config = InvRestore.config.queryResults();
        int startIndex = (page - 1) * config.maxResults();
        if (startIndex < 0) {
            return 0;
        }

        List<Snapshot> snapshots = InvRestore
                .findSnapshots(s -> s.playerName().equals(playerName) && (eventType.isEmpty() || s.event().getType().equals(eventType.get())));
        if (snapshots.isEmpty()) {
            return 0;
        }

        int endIndex = Math.min(startIndex + config.maxResults(), snapshots.size());
        if (endIndex <= startIndex) {
            return 0;
        }

        receiver.sendSystemMessage(Component.empty()
                .append(Component.literal("--- Listing snapshots of ").withStyle(Styles.HEADER_DEFAULT))
                .append(Component.literal(playerName).withStyle(Styles.HEADER_HIGHLIGHT))
                .append(" ---").withStyle(Styles.HEADER_DEFAULT));

        snapshots.subList(startIndex, endIndex).forEach(snapshot -> {
            ZoneId zone = InvRestore.getPlayerPreferences(receiver).timezone().orElse(config.defaultZone());
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
            hoverItem.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(snapshot.contents().inventoryItems().toList()));
            CompoundTag snapshotPayload = new CompoundTag();
            snapshotPayload.put("id", StringTag.valueOf(snapshot.id()));
            Component items = Component.literal("(" + snapshot.contents().stackCount() + " stacks)").withStyle(Styles.LIST_HIGHLIGHT
                    .withHoverEvent(new HoverEvent.ShowItem(hoverItem))
                    .withClickEvent(new ClickEvent.Custom(InvRestore.VIEW_ACTION, Optional.of(snapshotPayload))));

            BlockPos pos = BlockPos.containing(snapshot.position());
            String posFormat = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            Component position = Component.literal(posFormat).withStyle(Styles.LIST_DEFAULT
                    .withHoverEvent(new HoverEvent.ShowText(Component.empty()
                            .append(Component.literal(snapshot.formatPos()).withStyle(Styles.LIST_HIGHLIGHT))
                            .append(Component.literal("\n" + snapshot.dimension().identifier()).withStyle(Styles.LIST_DEFAULT))
                            .append(Component.literal("\n(click to teleport)").withStyle(Styles.LIST_DEFAULT))))
                    .withClickEvent(new ClickEvent.Custom(InvRestore.TELEPORT_ACTION, Optional.of(snapshotPayload))));

            receiver.sendSystemMessage(Component.empty()
                    .append(snapshot.event().formatEmoji(false))
                    .append(" ").append(time)
                    .append(" ").append(player)
                    .append(" ").append(verb)
                    .append(" ").append(items)
                    .append(" ").append(position));
        });

        int maxPage = Math.ceilDiv(snapshots.size(), config.maxResults());
        CompoundTag pagePayload = new CompoundTag();
        pagePayload.put("player_name", StringTag.valueOf(playerName));
        eventType.ifPresent(type -> pagePayload.put("event_type",
                StringTag.valueOf(Objects.requireNonNull(Snapshot.EventType.REGISTRY.getKey(type)).toString())));
        CompoundTag prevPagePayload = pagePayload.copy();
        prevPagePayload.put("page", IntTag.valueOf(page - 1));
        CompoundTag nextPagePayload = pagePayload.copy();
        nextPagePayload.put("page", IntTag.valueOf(page + 1));
        Style prevButtonStyle = Styles.HEADER_HIGHLIGHT;
        if (page > 1) {
            prevButtonStyle = prevButtonStyle
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("(previous page)").withStyle(Styles.LIST_DEFAULT)))
                    .withClickEvent(new ClickEvent.Custom(InvRestore.CHANGE_PAGE_ACTION, Optional.of(prevPagePayload)));
        }
        Style nextButtonStyle = Styles.HEADER_HIGHLIGHT;
        if (page < maxPage) {
            nextButtonStyle = nextButtonStyle
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("(next page)").withStyle(Styles.LIST_DEFAULT)))
                    .withClickEvent(new ClickEvent.Custom(InvRestore.CHANGE_PAGE_ACTION, Optional.of(nextPagePayload)));
        }
        receiver.sendSystemMessage(Component.empty()
                .append(Component.literal("------").withStyle(Styles.HEADER_DEFAULT))
                .append(Component.literal(" << ").withStyle(prevButtonStyle))
                .append(Component.literal("Page " + page + " of " + maxPage).withStyle(Styles.HEADER_DEFAULT))
                .append(Component.literal(" >> ").withStyle(nextButtonStyle))
                .append("------").withStyle(Styles.HEADER_DEFAULT));

        return snapshots.size();
    }

    public static void handleCustomClickAction(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (id.equals(InvRestore.CHANGE_PAGE_ACTION)) {
            payload
                    .flatMap(Tag::asCompound)
                    .ifPresent(tag -> {
                        Optional<String> playerName = tag.getString("player_name");
                        Optional<Snapshot.EventType<?>> eventType = tag.getString("event_type")
                                .flatMap(e -> Snapshot.EventType.REGISTRY.get(Identifier.parse(e)))
                                .map(Holder.Reference::value);
                        Optional<Integer> page = tag.getInt("page");
                        if (playerName.isEmpty() || page.isEmpty()) {
                            return;
                        }
                        sendSnapshotList(player, playerName.get(), eventType, page.get());
                    });
            return;
        }
        payload
                .flatMap(Tag::asCompound)
                .flatMap(c -> c.getString("id"))
                .flatMap(string -> InvRestore
                        .findSnapshots(s -> s.id().equals(string))
                        .stream().findAny())
                .ifPresent(snapshot -> {
                    if (id.equals(InvRestore.VIEW_ACTION)) {
                        try {
                            SnapshotGui gui = new SnapshotGui(player, snapshot);
                            gui.open();
                        } catch (Exception e) {
                            InvRestore.LOGGER.error("Failed to open GUI", e);
                        }
                    } else if (id.equals(InvRestore.TELEPORT_ACTION)) {
                        ServerLevel level = player.level().getServer().getLevel(snapshot.dimension());
                        if (level != null) {
                            player.teleportTo(level, snapshot.position().x, snapshot.position().y, snapshot.position().z, Set.of(), 0f, 0f, false);
                        }
                    }
                });
    }

    private static Stream<Snapshot> getSnapshots() {
        if (database == null) {
            return Stream.of();
        }
        return database.snapshots().stream();
    }

    public static void addSnapshot(Snapshot snapshot) {
        try {
            if (database == null) {
                throw new IllegalStateException("The database isn't loaded");
            }
            database.snapshots().add(snapshot);
        } catch (Exception e) {
            LOGGER.error("Couldn't save snapshot {} for player {}", snapshot.event(), snapshot.playerName(), e);
        }
    }

    public static List<String> getPlayerNames() {
        return getSnapshots()
                .sorted()
                .map(Snapshot::playerName)
                .distinct()
                .toList();
    }

    public static List<Snapshot> findSnapshots(Predicate<Snapshot> predicate) {
        return getSnapshots()
                .filter(predicate)
                .sorted()
                .toList();
    }

    public static List<String> getAllIds() {
        return getSnapshots()
                .map(Snapshot::id)
                .toList();
    }
}
