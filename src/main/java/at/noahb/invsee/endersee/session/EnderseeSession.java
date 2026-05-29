package at.noahb.invsee.endersee.session;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.session.Session;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static net.kyori.adventure.text.Component.text;

public class EnderseeSession implements Session {

    private final UUID uuid;

    private final Set<UUID> subscribers;

    private final Inventory enderchest;
    private final Cache<UUID, Player> playerCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.SECONDS)
            .build();
    private final ReentrantLock lock = new ReentrantLock();

    public EnderseeSession(OfflinePlayer offlinePlayer, UUID subscriber) {
        this.uuid = offlinePlayer.getUniqueId();
        this.subscribers = new HashSet<>();

        if (offlinePlayer instanceof Player player) {
            this.enderchest = Bukkit.createInventory(player, InventoryType.ENDER_CHEST, player.name().append(text("'s enderchest")));
        } else {
            String name = offlinePlayer.getName() == null ? "unknown" : offlinePlayer.getName();
            this.enderchest = InvseePlugin.getInstance().getServer().createInventory(null, InventoryType.ENDER_CHEST, text(name).append(text("'s enderchest")));
        }

        updateSubscriberInventory();
        addSubscriber(subscriber);
    }

    private CompletableFuture<Inventory> getEnderChest(OfflinePlayer offline) {
        if (offline instanceof Player player) {
            CompletableFuture<Inventory> future = new CompletableFuture<>();
            player.getScheduler().run(PLUGIN,
                    task -> future.complete(player.getEnderChest()),
                    () -> future.complete(null));   // retired: player gone
            return future;
        }

        return getPlayerOffline(offline)
                .thenApply(opt -> opt.map(HumanEntity::getEnderChest).orElse(null))
                .exceptionally(throwable -> {
                    PLUGIN.getLogger().log(Level.SEVERE, "Failed to resolve ender chest", throwable);
                    throw new RuntimeException(throwable);
                });
    }

    @Override
    public UUID getUniqueIdOfObservedPlayer() {
        return this.uuid;
    }

    @Override
    public void updateObservedInventory() {
        update(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(this.uuid);
            int size = InventoryType.ENDER_CHEST.getDefaultSize();

            Bukkit.getGlobalRegionScheduler().run(PLUGIN, guiTask -> {
                ItemStack[] snapshot = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    ItemStack item = this.enderchest.getItem(i);
                    snapshot[i] = item == null ? null : item.clone();
                }

                getEnderChest(offlinePlayer).thenAccept(enderChest -> {
                    if (enderChest == null) return;

                    if (enderChest.getHolder() instanceof Player target) {
                        target.getScheduler().run(PLUGIN, task -> {
                            for (int i = 0; i < size; i++) {
                                enderChest.setItem(i, snapshot[i]);
                            }
                        }, null);
                    } else {
                        // offline write-back: depends entirely on what getPlayerOffline does
                        for (int i = 0; i < size; i++) {
                            enderChest.setItem(i, snapshot[i]);
                        }
                    }
                }).exceptionally(throwable -> {
                    PLUGIN.getLogger().log(Level.SEVERE, "Failed to write observed enderchest", throwable);
                    return null;
                });
            });
        });
    }

    @Override
    public void updateSubscriberInventory() {
        update(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            int size = InventoryType.ENDER_CHEST.getDefaultSize();

            getEnderChest(offlinePlayer).thenAccept(enderChest -> {
                if (enderChest == null) return;

                ItemStack[] snapshot = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    ItemStack item = enderChest.getItem(i);
                    snapshot[i] = item == null ? null : item.clone();
                }

                Bukkit.getGlobalRegionScheduler().run(PLUGIN, task -> {
                    for (int i = 0; i < size; i++) {
                        this.enderchest.setItem(i, snapshot[i]);
                    }
                });
            }).exceptionally(throwable -> {
                PLUGIN.getLogger().log(Level.SEVERE, "Failed to update subscriber enderchest", throwable);
                return null;
            });
        });
    }

    @Override
    public Set<UUID> getSubscribers() {
        return this.subscribers;
    }

    @Override
    public Inventory getInventory() {
        return this.enderchest;
    }

    @Override
    public void removeSubscriber(UUID subscriber) {
        this.subscribers.remove(subscriber);
    }

    @Override
    public boolean hasSubscriber(UUID subscriber) {
        return this.subscribers.contains(subscriber);
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
        EnderseeSession that = (EnderseeSession) object;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
