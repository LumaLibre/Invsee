package at.noahb.invsee.common.session;

import at.noahb.invsee.InvseePlugin;
import com.mojang.authlib.GameProfile;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import static net.kyori.adventure.text.Component.text;

public interface Session {

    Plugin PLUGIN = InvseePlugin.getInstance();

    default void addSubscriber(UUID subscriber) {
        if (subscriber == null) return;
        if (hasSubscriber(subscriber)) return;
        Player player = InvseePlugin.getInstance().getServer().getPlayer(subscriber);
        if (player == null) return;

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(getUniqueIdOfObservedPlayer());

        getPlayerOffline(offlinePlayer).thenAccept(other -> {
            if (other.isEmpty()) return;

            getSubscribers().add(subscriber);
            player.getScheduler().run(InvseePlugin.getInstance(),
                    scheduledTask -> player.openInventory(getInventory()),
                    null);
        }).exceptionally(throwable -> {
            PLUGIN.getLogger()
                    .log(java.util.logging.Level.SEVERE, "Failed to add subscriber " + subscriber, throwable);
            return null;
        });
    }


    default void save() {
        Player cachedPlayer = getCachedPlayer();
        if (cachedPlayer != null) {
            cachedPlayer.saveData();
        }
    }

    default void update(Runnable runnable) {
        try {
            getLock().lock();
            runnable.run();
            if (isOffline()) {
                save();
            }
        } finally {
            if (getLock().isHeldByCurrentThread()) getLock().unlock();
        }
    }

    default boolean isOffline() {
        return !InvseePlugin.getInstance().getServer().getOfflinePlayer(getUniqueIdOfObservedPlayer()).isOnline();
    }

    default CompletableFuture<Optional<Player>> getPlayerOffline(OfflinePlayer offlinePlayer) {
        CompletableFuture<Optional<Player>> future = new CompletableFuture<>();
        Player cached = getCachedPlayer();
        if (cached != null) {
            future.complete(Optional.of(cached));
            return future;
        }

        GameProfile profile = new GameProfile(offlinePlayer.getUniqueId(),
                offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString());
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        MinecraftServer server = craftServer.getServer();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            PLUGIN.getComponentLogger().error(text("Unable to find overworld level", NamedTextColor.RED));
            future.complete(Optional.empty());
            return future;
        }

        ServerPlayer serverPlayer = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Player target = serverPlayer.getBukkitEntity();
        Bukkit.getRegionScheduler().run(PLUGIN, target.getLocation(), task -> {
            // mock loadData()
            craftServer.getHandle().playerIo.load(serverPlayer.nameAndId());
            target.loadData();
            cache(target);
            System.out.println("Cached player " + target);
            future.complete(Optional.of(target));
        });
        return future;
    }

    UUID getUniqueIdOfObservedPlayer();

    void updateObservedInventory();

    void updateSubscriberInventory();

    Inventory getInventory();

    Set<UUID> getSubscribers();

    void removeSubscriber(UUID subscriber);

    boolean hasSubscriber(UUID subscriber);

    ReentrantLock getLock();

    void cache(Player player);

    Player getCachedPlayer();
}
