package at.noahb.invsee.invsee.session;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.session.Session;
import com.destroystokyo.paper.MaterialSetTag;
import com.destroystokyo.paper.MaterialTags;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;


public class InvseeSession implements Session {

    private final UUID uuid;
    private final Set<UUID> subscribers;
    private final Inventory inventory;
    private final ReentrantLock lock = new ReentrantLock();
    private final Cache<UUID, Player> playerCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.SECONDS)
            .build();

    public InvseeSession(OfflinePlayer offlinePlayer, UUID subscriber) {
        this.uuid = offlinePlayer.getUniqueId();
        this.subscribers = new HashSet<>();

        if (offlinePlayer instanceof Player player) {
            this.inventory = Bukkit.createInventory(player, 45, player.name().append(text("'s inventory")));
        } else {
            String name = offlinePlayer.getName() == null ? "unknown" : offlinePlayer.getName();
            this.inventory = InvseePlugin.getInstance().getServer().createInventory(null, 45, text(name).append(text("'s inventory")));
        }

        updateSubscriberInventory();
        addSubscriber(subscriber);
    }

    @Override
    public UUID getUniqueIdOfObservedPlayer() {
        return this.uuid;
    }

    @Override
    public Set<UUID> getSubscribers() {
        return this.subscribers;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    private CompletableFuture<PlayerInventory> getPlayerInventory(OfflinePlayer offlinePlayer) {
        if (offlinePlayer instanceof Player player) {
            CompletableFuture<PlayerInventory> future = new CompletableFuture<>();
            player.getScheduler().run(PLUGIN,
                    task -> future.complete(player.getInventory()),
                    () -> future.complete(null));
            return future;
        }

        return getPlayerOffline(offlinePlayer)
                .thenApply(opt -> {
                    System.out.println("getPlayerInventory: resolved player " + opt);
                    return opt.map(Player::getInventory).orElse(null);
                })
                .exceptionally(throwable -> {
                    PLUGIN.getLogger().log(Level.SEVERE, "Failed to resolve player inventory", throwable);
                    return null;
                });
    }

    @Override
    public void removeSubscriber(UUID subscriber) {
        this.subscribers.remove(subscriber);
    }

    @Override
    public void updateSubscriberInventory() {
        update(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(this.uuid);

            getPlayerInventory(offlinePlayer).thenAccept(playerInv -> {
                System.out.println("updateSubscriberInventory: got player inventory " + playerInv);
                if (playerInv == null) return;

                Runnable readAndApply = () -> {
                    ItemStack[] snapshot = new ItemStack[41];
                    for (int i = 0; i < 41; i++) {
                        ItemStack item = playerInv.getItem(i);
                        snapshot[i] = item == null ? null : item.clone();
                    }
                    ItemStack cursor = (playerInv.getHolder() instanceof Player p)
                            ? cloneOrNull(p.getItemOnCursor()) : null;

                    Bukkit.getGlobalRegionScheduler().run(PLUGIN, task -> {
                        for (int i = 0; i < 41; i++) {
                            this.inventory.setItem(i, snapshot[i]);
                        }
                        if (cursor != null) {
                            this.inventory.setItem(41, cursor);
                        }
                        replaceEmptyPlaceholderSpots();
                    });
                };

                // read player state on the player's own thread
                if (playerInv.getHolder() instanceof Player player) {
                    player.getScheduler().run(PLUGIN, t -> readAndApply.run(), null);
                } else {
                    readAndApply.run(); // offline: depends on getPlayerOffline's contract
                }
                System.out.println("updateSubscriberInventory: scheduled inventory update for " + playerInv);
            }).exceptionally(throwable -> {
                PLUGIN.getLogger().log(Level.SEVERE, "Failed to update subscriber inventory", throwable);
                return null;
            });
        });
    }

    private void replaceEmptyPlaceholderSpots() {
        if (this.inventory.getItem(36) == null) this.inventory.setItem(36, Placeholders.BOOTS);
        if (this.inventory.getItem(37) == null) this.inventory.setItem(37, Placeholders.LEGGINGS);
        if (this.inventory.getItem(38) == null) this.inventory.setItem(38, Placeholders.CHESTPLATE);
        if (this.inventory.getItem(39) == null) this.inventory.setItem(39, Placeholders.HELMET);
        if (this.inventory.getItem(40) == null) this.inventory.setItem(40, Placeholders.OFF_HAND);
        if (this.inventory.getItem(41) == null) this.inventory.setItem(41, Placeholders.CURSOR);
        for (int i = 42; i < 45; i++) {
            if (this.inventory.getItem(i) == null) this.inventory.setItem(i, Placeholders.NO_USAGE);
        }
    }

    @Override
    public boolean hasSubscriber(UUID uuid) {
        return this.subscribers.contains(uuid);
    }

    @Override
    public void updateObservedInventory() {
        update(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

            getPlayerInventory(offlinePlayer).thenAccept(playerInventory -> {
                System.out.println("updateObservedInventory: got player inventory " + playerInventory);
                if (playerInventory == null) return;

                Bukkit.getGlobalRegionScheduler().run(PLUGIN, guiTask -> {
                    int size = playerInventory.getSize();
                    ItemStack[] snapshot = new ItemStack[size];
                    boolean[] skip = new boolean[size];
                    for (int i = 0; i < size; i++) {
                        ItemStack guiItem = this.inventory.getItem(i);
                        if (Placeholders.isPlaceholder(guiItem)) {
                            skip[i] = true;
                        } else {
                            snapshot[i] = cloneOrNull(guiItem);
                        }
                    }
                    ItemStack cursorItem = this.inventory.getItem(41);
                    boolean writeCursor = !Placeholders.isPlaceholder(cursorItem);
                    ItemStack cursorSnapshot = cloneOrNull(cursorItem);

                    replaceEmptyPlaceholderSpots();

                    Runnable writeBack = () -> {
                        for (int i = 0; i < size; i++) {
                            if (skip[i]) continue;
                            playerInventory.setItem(i, snapshot[i]);
                        }
                        if (writeCursor && playerInventory.getHolder() instanceof Player player) {
                            player.setItemOnCursor(cursorSnapshot);
                        }
                    };

                    if (playerInventory.getHolder() instanceof Player player) {
                        player.getScheduler().run(PLUGIN, t -> writeBack.run(), null);
                    } else {
                        writeBack.run(); // offline write-back: see caveat
                    }
                    System.out.println("updateObservedInventory: scheduled inventory write-back for " + playerInventory);
                });
            }).exceptionally(throwable -> {
                PLUGIN.getLogger().log(Level.SEVERE, "Failed to write observed inventory", throwable);
                return null;
            });
        });
    }

    @Override
    public ReentrantLock getLock() {
        return this.lock;
    }

    @Override
    public void cache(Player player) {
        this.playerCache.put(this.uuid, player);
    }

    @Override
    public Player getCachedPlayer() {
        return this.playerCache.getIfPresent(this.uuid);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        InvseeSession session = (InvseeSession) object;
        return Objects.equals(uuid, session.uuid);
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    public enum ArmorSlot {
        HELMET(MaterialTags.HELMETS),
        CHESTPLATE(MaterialTags.CHESTPLATES),
        LEGGINGS(MaterialTags.LEGGINGS),
        BOOTS(MaterialTags.BOOTS);

        private final MaterialSetTag tag;

        ArmorSlot(MaterialSetTag tag) {
            this.tag = tag;
        }

        public boolean checkIfItemFitsSlot(ItemStack itemStack) {
            return this.tag.isTagged(itemStack);
        }
    }

    public static class Placeholders {
        static final ItemStack HELMET = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        static final ItemStack CHESTPLATE = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        static final ItemStack LEGGINGS = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        static final ItemStack BOOTS = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        static final ItemStack OFF_HAND = new ItemStack(Material.BARRIER);
        static final ItemStack CURSOR = new ItemStack(Material.BARRIER);
        static final ItemStack NO_USAGE = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);

        static final NamespacedKey OFF_HAND_KEY = new NamespacedKey(InvseePlugin.getInstance(), "offhand");
        static final NamespacedKey CURSOR_KEY = new NamespacedKey(InvseePlugin.getInstance(), "cursor");
        static final NamespacedKey INVSEE_KEY = new NamespacedKey(InvseePlugin.getInstance(), "invsee");
        static {
            List<Component> lore = List.of(text("empty", RED).decoration(ITALIC, false));
            HELMET.editMeta(itemMeta -> {
                itemMeta.displayName(text("Helmet slot", GOLD).decoration(ITALIC, false));
                itemMeta.lore(lore);
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
            });
            CHESTPLATE.editMeta(itemMeta -> {
                itemMeta.displayName(text("Chestplate slot", GOLD).decoration(ITALIC, false));
                itemMeta.lore(lore);
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
            });
            LEGGINGS.editMeta(itemMeta -> {
                itemMeta.displayName(text("Leggings slot", GOLD).decoration(ITALIC, false));
                itemMeta.lore(lore);
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
            });
            BOOTS.editMeta(itemMeta -> {
                itemMeta.displayName(text("Boots slot", GOLD).decoration(ITALIC, false));
                itemMeta.lore(lore);
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
            });
            OFF_HAND.editMeta(itemMeta -> {
                itemMeta.displayName(text("Off Hand", GOLD).decoration(ITALIC, false));
                itemMeta.lore(lore);
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
                itemMeta.getPersistentDataContainer().set(OFF_HAND_KEY, PersistentDataType.BOOLEAN, true);
            });
            CURSOR.editMeta(itemMeta -> {
                itemMeta.displayName(text("Cursor", GOLD).decoration(ITALIC, false));
                itemMeta.lore(lore);
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
                itemMeta.getPersistentDataContainer().set(CURSOR_KEY, PersistentDataType.BOOLEAN, true);
            });
            NO_USAGE.editMeta(itemMeta -> {
                itemMeta.displayName(Component.empty());
                itemMeta.getPersistentDataContainer().set(INVSEE_KEY, PersistentDataType.BOOLEAN, true);
            });
        }

        public static boolean isOffHandPlaceholder(ItemStack itemStack) {
            return itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(OFF_HAND_KEY, PersistentDataType.BOOLEAN);
        }

        public static boolean isCursorPlaceholder(ItemStack itemStack) {
            return itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(CURSOR_KEY, PersistentDataType.BOOLEAN);
        }

        public static boolean isPlaceholder(ItemStack itemStack) {
            return itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(INVSEE_KEY, PersistentDataType.BOOLEAN);
        }
    }

}